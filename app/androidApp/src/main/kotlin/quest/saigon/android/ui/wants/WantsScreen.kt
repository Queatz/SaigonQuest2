package quest.saigon.android.ui.wants

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import quest.saigon.Copy
import quest.saigon.Language
import quest.saigon.WantSpaceLayout
import quest.saigon.android.theme.LivvicFontFamily
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.ui.components.SaigonConfirmDialog
import quest.saigon.android.ui.components.SaigonPillChip
import quest.saigon.android.ui.components.SaigonSpinner
import quest.saigon.android.ui.components.WantMemberFaceStack
import quest.saigon.android.ui.components.WantNotebookCard
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.WantDto
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private data class StarSpec(
    val xFrac: Float,
    val yFrac: Float,
    val radiusDp: Float,
    val sparkle: Boolean,
    val depth: Float,
    val speed: Float,
    val phase: Float,
    val color: Color,
)

private data class ExplosionSpark(
    val angleRad: Float,
    val distDp: Float,
    val radiusDp: Float,
    val color: Color,
)

@Composable
fun WantsOverlayScreen(
    shared: TownSharedClient,
    explodeOnEnter: Boolean,
    interactive: Boolean = true,
    onRequireAuth: () -> Unit,
    onOpenWant: (WantDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessionState by shared.session.state.collectAsState()
    val wantsState by shared.wants.state.collectAsState()
    val language = sessionState.language
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val reducedMotion = remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        } catch (_: Throwable) {
            false
        }
    }

    var pendingWantLeave by remember { mutableStateOf<WantDto?>(null) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var velX by remember { mutableFloatStateOf(0f) }
    var velY by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    var startFrameMs by remember { mutableLongStateOf(0L) }
    var currentFrameMs by remember { mutableLongStateOf(0L) }
    var loadStartFrameMs by remember { mutableLongStateOf(0L) }
    var trackedGeneration by remember { mutableLongStateOf(-1L) }

    val explosionProgress = remember(explodeOnEnter) {
        Animatable(if (explodeOnEnter && !reducedMotion) 0f else 1f)
    }
    val overlayAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = if (explodeOnEnter) 380 else 180),
        label = "wantsOverlayAlpha",
    )

    LaunchedEffect(explodeOnEnter) {
        if (explodeOnEnter && !reducedMotion) {
            explosionProgress.snapTo(0f)
            explosionProgress.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(sessionState.me?.id) {
        shared.wants.loadAll()
    }

    LaunchedEffect(wantsState.loadGeneration, wantsState.isLoading) {
        if (!wantsState.isLoading && wantsState.loadGeneration != trackedGeneration) {
            trackedGeneration = wantsState.loadGeneration
            loadStartFrameMs = currentFrameMs
        }
    }

    LaunchedEffect(Unit) {
        var lastTickMs = 0L
        while (true) {
            withFrameMillis { nowMs ->
                if (startFrameMs == 0L) startFrameMs = nowMs
                if (loadStartFrameMs == 0L && !wantsState.isLoading) loadStartFrameMs = nowMs
                val dt = if (lastTickMs == 0L) 16f else (nowMs - lastTickMs).coerceIn(1L, 64L).toFloat()
                lastTickMs = nowMs
                currentFrameMs = nowMs
                if (!dragging && (abs(velX) > 0.1f || abs(velY) > 0.1f)) {
                    panX += velX * (dt / 16f)
                    panY += velY * (dt / 16f)
                    velX *= 0.92f
                    velY *= 0.92f
                }
            }
        }
    }

    val stars = remember {
        val colors = listOf(
            Color(0xFFFFFFFF),
            Color(0xFFFFF6C8),
            Color(0xFFD7E6FF),
            Color(0xFFFFD6CE),
        )
        var seed = 1337L
        fun nextRand(): Float {
            seed = (seed * 1664525L + 1013904223L) and 0x7FFFFFFFL
            return seed.toFloat() / 0x7FFFFFFFL.toFloat()
        }
        List(64) { idx ->
            StarSpec(
                xFrac = nextRand(),
                yFrac = nextRand(),
                radiusDp = 0.9f + nextRand() * 1.4f,
                sparkle = idx % 9 == 0,
                depth = 0.08f + nextRand() * 0.22f,
                speed = 1.4f + nextRand() * 2.2f,
                phase = nextRand() * (PI.toFloat() * 2f),
                color = colors[idx % colors.size],
            )
        }
    }

    val sparks = remember {
        val colors = listOf(
            Color(0xFFFFF6C8),
            Color(0xFFE9897C),
            Color(0xFF7F7CE9),
            Color.White,
        )
        List(36) { idx ->
            ExplosionSpark(
                angleRad = ((idx.toFloat() / 36f) * (PI.toFloat() * 2f)) + ((idx % 3) - 1) * 0.09f,
                distDp = 110f + ((idx * 73) % 230).toFloat(),
                radiusDp = 2.2f + ((idx * 17) % 4).toFloat(),
                color = colors[idx % colors.size],
            )
        }
    }

    val wants = wantsState.wants
    val positions = remember(wants.map { it.id }) {
        WantSpaceLayout.layout(wants.map { it.id })
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha }
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF191F3B),
                        Color(0xFF0D1022),
                        Color(0xFF060711),
                    ),
                ),
            )
            .then(
                if (interactive) {
                    Modifier.pointerInput(Unit) {
                        var velocityTracker = VelocityTracker()
                        detectDragGestures(
                            onDragStart = {
                                dragging = true
                                velX = 0f
                                velY = 0f
                                velocityTracker = VelocityTracker()
                            },
                            onDragEnd = {
                                dragging = false
                                val v = velocityTracker.calculateVelocity()
                                velX = (v.x * 0.016f).coerceIn(-65f, 65f)
                                velY = (v.y * 0.016f).coerceIn(-65f, 65f)
                            },
                            onDragCancel = {
                                dragging = false
                                velX = 0f
                                velY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                panX += dragAmount.x
                                panY += dragAmount.y
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val elapsedSec = ((currentFrameMs - startFrameMs).coerceAtLeast(0L)).toFloat() / 1000f
        val animElapsedMs = if (loadStartFrameMs > 0L) (currentFrameMs - loadStartFrameMs).coerceAtLeast(0L) else 0L

        // Twinkling starry background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            stars.forEach { star ->
                val rawX = star.xFrac * w + panX * star.depth
                val rawY = star.yFrac * h + panY * star.depth
                val x = ((rawX % w) + w) % w
                val y = ((rawY % h) + h) % h
                val twinkle = if (reducedMotion) {
                    0.85f
                } else {
                    0.26f + 0.74f * (0.5f + 0.5f * sin(elapsedSec * star.speed + star.phase))
                }
                val r = star.radiusDp.dp.toPx()
                val starColor = star.color.copy(alpha = twinkle)
                if (star.sparkle) {
                    val s = r * 2.5f
                    val path = Path().apply {
                        moveTo(x, y - s)
                        lineTo(x + s * 0.28f, y - s * 0.28f)
                        lineTo(x + s, y)
                        lineTo(x + s * 0.28f, y + s * 0.28f)
                        lineTo(x, y + s)
                        lineTo(x - s * 0.28f, y + s * 0.28f)
                        lineTo(x - s, y)
                        lineTo(x - s * 0.28f, y - s * 0.28f)
                        close()
                    }
                    drawPath(path, color = starColor)
                } else {
                    drawCircle(color = starColor, radius = r, center = Offset(x, y))
                }
            }
        }

        // Infinite 2D floating Wants field
        if (wants.isNotEmpty()) {
            Layout(
                content = {
                    wants.forEachIndexed { index, want ->
                        val pos = positions[want.id]
                        val delayMs = WantSpaceLayout.animateInDelayMs(index, wants.size)
                        val enterRaw = if (reducedMotion) {
                            1f
                        } else {
                            ((animElapsedMs - delayMs).toFloat() / WantSpaceLayout.ANIMATE_IN_ITEM_MS.toFloat()).coerceIn(0f, 1f)
                        }
                        val enterEased = FastOutSlowInEasing.transform(enterRaw)
                        val tiltDeg = if (reducedMotion || pos == null) 0f else pos.tiltDeg.toFloat()

                        WantFloatingCard(
                            want = want,
                            language = language,
                            interactive = interactive,
                            isMutating = want.id in wantsState.mutatingWantIds,
                            enterProgress = enterEased,
                            tiltDeg = tiltDeg,
                            onOpen = { onOpenWant(want) },
                            onToggleJoin = {
                                if (sessionState.me == null) {
                                    onRequireAuth()
                                } else if (want.joined) {
                                    pendingWantLeave = want
                                } else {
                                    scope.launch {
                                        shared.wants.toggleJoin(want.id, currentlyJoined = false)
                                    }
                                }
                            },
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
            ) { measurables, constraints ->
                val cardWidthPx = with(density) { WantSpaceLayout.CARD_WIDTH_PX.dp.roundToPx() }
                val childConstraints = Constraints(
                    minWidth = cardWidthPx,
                    maxWidth = cardWidthPx,
                    minHeight = 0,
                    maxHeight = constraints.maxHeight.coerceAtLeast(cardWidthPx),
                )
                val placeables = measurables.map { it.measure(childConstraints) }
                val centerX = constraints.maxWidth / 2f
                val centerY = constraints.maxHeight / 2f
                val tSec = elapsedSec.toDouble()
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeables.forEachIndexed { index, placeable ->
                        val want = wants.getOrNull(index) ?: return@forEachIndexed
                        val pos = positions[want.id] ?: return@forEachIndexed
                        val dxFloat = if (reducedMotion) 0.0 else sin(tSec * pos.floatSpeedX + pos.floatPhaseX) * pos.floatAmpX
                        val dyFloat = if (reducedMotion) 0.0 else cos(tSec * pos.floatSpeedY + pos.floatPhaseY) * pos.floatAmpY
                        val worldX = with(density) { (pos.x + dxFloat).dp.toPx() }
                        val worldY = with(density) { (pos.y + dyFloat).dp.toPx() }
                        val px = (centerX + panX + worldX - placeable.width / 2f).roundToInt()
                        val py = (centerY + panY + worldY - placeable.height / 2f).roundToInt()
                        placeable.placeRelative(px, py)
                    }
                }
            }
        }

        // Loading / error / empty feedback
        when {
            wantsState.isLoading && wants.isEmpty() -> {
                SaigonSpinner(modifier = Modifier.align(Alignment.Center))
            }
            wantsState.error != null && wants.isEmpty() -> {
                Text(
                    text = Copy.text(language, "unavailable"),
                    color = Color.White.copy(alpha = 0.82f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            !wantsState.isLoading && wants.isEmpty() -> {
                Text(
                    text = Copy.text(language, "noWants"),
                    color = Color.White.copy(alpha = 0.82f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        // Explosion burst particles rendered over the transition
        val ex = explosionProgress.value
        if (explodeOnEnter && !reducedMotion && ex < 0.99f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val fade = (1f - ex).coerceIn(0f, 1f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFFCE8).copy(alpha = 0.9f * fade),
                            Color(0xFFE9897C).copy(alpha = 0.45f * fade),
                            Color.Transparent,
                        ),
                        center = Offset(cx, cy),
                        radius = (36.dp.toPx() + 340.dp.toPx() * ex),
                    ),
                    radius = (36.dp.toPx() + 340.dp.toPx() * ex),
                    center = Offset(cx, cy),
                )
                drawCircle(
                    color = Color(0xFFFFF6C8).copy(alpha = 0.72f * fade),
                    radius = (24.dp.toPx() + 280.dp.toPx() * ex),
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx()),
                )
                sparks.forEach { spark ->
                    val dist = spark.distDp.dp.toPx() * ex
                    val sx = cx + cos(spark.angleRad) * dist
                    val sy = cy + sin(spark.angleRad) * dist
                    val sr = spark.radiusDp.dp.toPx() * (1f - 0.65f * ex)
                    drawCircle(
                        color = spark.color.copy(alpha = fade),
                        radius = sr.coerceAtLeast(1f),
                        center = Offset(sx, sy),
                    )
                }
            }
        }
    }

    pendingWantLeave?.let { want ->
        SaigonConfirmDialog(
            title = Copy.text(language, "leaveWantAsk", mapOf("want" to want.name)),
            hint = Copy.text(language, "leaveWantHint"),
            confirmLabel = Copy.text(language, "leaveWant"),
            cancelLabel = Copy.text(language, "cancel"),
            loading = want.id in wantsState.mutatingWantIds,
            onConfirm = {
                scope.launch {
                    shared.wants.toggleJoin(want.id, currentlyJoined = true)
                    pendingWantLeave = null
                }
            },
            onDismiss = { pendingWantLeave = null },
        )
    }
}

@Composable
private fun WantFloatingCard(
    want: WantDto,
    language: Language,
    interactive: Boolean,
    isMutating: Boolean,
    enterProgress: Float,
    tiltDeg: Float,
    onOpen: () -> Unit,
    onToggleJoin: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    val paper = if (tokens.isDark) Color(0xFF2A2521) else Color(0xFFFFFDF7)
    val scale = 0.65f + 0.35f * enterProgress
    val liftPx = with(LocalDensity.current) { (18f * (1f - enterProgress)).dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enterProgress
                scaleX = scale
                scaleY = scale
                translationY = liftPx
                rotationZ = tiltDeg
            }
            .shadow(14.dp, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
    ) {
        WantNotebookCard(
            photoUrl = want.photoUrl,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = interactive && enterProgress > 0.2f, onClick = onOpen),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = want.name,
                    color = tokens.text,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    fontFamily = LivvicFontFamily,
                    style = TextStyle(
                        shadow = Shadow(
                            color = paper.copy(alpha = 0.85f),
                            offset = Offset(0f, 1f),
                            blurRadius = 6f,
                        ),
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (want.memberCount >= 2L) {
                    WantMemberFaceStack(
                        members = want.members,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            SaigonPillChip(
                label = Copy.text(language, if (want.joined) "wantJoined" else "wantJoin"),
                selected = want.joined,
                enabled = interactive && !isMutating,
                onClick = onToggleJoin,
            )
        }
    }
}
