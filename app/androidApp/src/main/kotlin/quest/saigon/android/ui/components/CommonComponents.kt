package quest.saigon.android.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import quest.saigon.AppRoute
import quest.saigon.CalmGradients
import quest.saigon.CheckIns
import quest.saigon.Copy
import quest.saigon.Language
import quest.saigon.Limits
import quest.saigon.NotificationCopy
import quest.saigon.PostAge
import quest.saigon.TextLinks
import town.saigon.BuildConfig
import quest.saigon.android.theme.LivvicFontFamily
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.theme.SaigonBrushes
import quest.saigon.android.theme.SaigonColors
import quest.saigon.encodeLocationToken
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.CheckInDto
import quest.saigon.shared.model.CommunityDto
import quest.saigon.shared.model.MeDto
import quest.saigon.shared.model.NotificationDto
import quest.saigon.shared.model.PlaceSuggestionDto
import quest.saigon.shared.model.PostCardDto
import quest.saigon.shared.model.UserPhotoDto
import quest.saigon.shared.model.WantDto
import quest.saigon.shared.model.WantMemberDto
import quest.saigon.shared.platform.currentTimeMillis
import quest.saigon.shared.store.DirectChatCheckResult
import quest.saigon.shared.store.NotificationStore
import java.util.Calendar
import java.util.TimeZone

object AndroidIntents {
    fun openExternalUrl(context: Context, url: String) {
        val clean = url.trim().takeIf { it.isNotBlank() } ?: return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(clean)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openDirections(context: Context, name: String, lat: Double?, lng: Double?) {
        val uri = if (lat != null && lng != null) {
            Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(name)})")
        } else {
            Uri.parse("geo:0,0?q=${Uri.encode(name)}")
        }
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun shareText(context: Context, text: String, title: String = "Saigon.Town") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun copyPathLink(context: Context, path: String, language: Language) {
        val clean = if (path.startsWith("/")) path else "/$path"
        val url = "${BuildConfig.SERVER_ORIGIN.trimEnd('/')}$clean"
        copyToClipboard(context, "Saigon.Town", url)
        android.widget.Toast.makeText(context, Copy.text(language, "linkCopied"), android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun formatVnd(amount: Long): String {
    val digits = amount.toString()
    val grouped = buildString {
        digits.forEachIndexed { index, char ->
            if (index > 0 && (digits.length - index) % 3 == 0) append('.')
            append(char)
        }
    }
    return "$grouped đ"
}

fun openLocationDestination(
    shared: TownSharedClient,
    name: String,
    placeId: String? = null,
    locationId: String? = null,
    lat: Double? = null,
    lng: Double? = null,
    initialPlace: PlaceSuggestionDto? = null,
): String {
    val seedPlace = initialPlace ?: if (lat != null && lng != null) {
        PlaceSuggestionDto(
            id = locationId,
            name = name,
            placeId = placeId,
            lat = lat,
            lng = lng,
            custom = !locationId.isNullOrBlank(),
        )
    } else {
        null
    }
    shared.discover.openLocation(
        name = name,
        placeId = placeId,
        locationId = locationId,
        initialPlace = seedPlace,
    )
    return locationId?.takeIf { it.isNotBlank() }
        ?: placeId?.takeIf { it.isNotBlank() }
        ?: encodeLocationToken(name)
}

fun Modifier.dashedBorder(
    color: Color,
    strokeWidth: Dp = 2.dp,
    cornerRadius: Dp = 16.dp,
    dashLength: Dp = 6.dp,
    gapLength: Dp = 5.dp,
): Modifier = drawBehind {
    val strokePx = strokeWidth.toPx()
    val radiusPx = cornerRadius.toPx()
    drawRoundRect(
        color = color,
        style = Stroke(
            width = strokePx,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength.toPx(), gapLength.toPx()), 0f),
        ),
        cornerRadius = CornerRadius(radiusPx, radiusPx),
    )
}

@Composable
fun SaigonPageShell(
    title: String,
    onBack: (() -> Unit)? = null,
    backContentDescription: String = "Back",
    topActions: (@Composable RowScope.() -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SaigonBrushes.pageBackground(tokens.isDark)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.bg)
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            ) {
                if (onBack != null) {
                    SaigonIconButton(
                        onClick = onBack,
                        overlay = false,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backContentDescription,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Text(
                    text = title,
                    color = tokens.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.6).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (topActions != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        content = topActions,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (bottomBar == null) {
                            Modifier.windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))
                        } else {
                            Modifier
                        },
                    ),
                content = content,
            )
            if (bottomBar != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom),
                        ),
                ) {
                    bottomBar()
                }
            }
        }
        if (floatingActionButton != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 18.dp),
            ) {
                floatingActionButton()
            }
        }
    }
}

@Composable
fun SaigonIconButton(
    onClick: () -> Unit,
    overlay: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    val bg = if (overlay) Color.White.copy(alpha = 0.16f) else tokens.surface
    val border = if (overlay) Color.White else tokens.line
    val fg = if (overlay) Color.White else tokens.text
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, border, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            content()
        }
    }
}

@Composable
fun SaigonPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    danger: Boolean = false,
    dimWhenDisabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
) {
    val tokens = LocalSaigonTokens.current
    val shape = RoundedCornerShape(999.dp)
    val textColor = if (danger && tokens.isDark) tokens.bg else Color.White
    val visualAlpha = if (enabled || !dimWhenDisabled) 1f else 0.45f
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer { alpha = visualAlpha }
            .clip(shape)
            .drawBehind {
                if (danger) {
                    drawRect(color = tokens.danger)
                } else {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(SaigonColors.AccentSoft, SaigonColors.Tertiary),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                    )
                }
            }
            .border(
                width = 2.dp,
                color = if (danger) Color.Transparent else Color.White.copy(alpha = 0.42f),
                shape = shape,
            )
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(contentPadding),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = textColor,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text(
                text = text,
                color = textColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SaigonSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dimWhenDisabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
) {
    val shape = RoundedCornerShape(999.dp)
    val visualAlpha = if (enabled || !dimWhenDisabled) 1f else 0.45f
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer { alpha = visualAlpha }
            .clip(shape)
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(SaigonColors.SecondaryStart, SaigonColors.SecondaryEnd),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
            }
            .border(2.dp, Color.White.copy(alpha = 0.55f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
    ) {
        Text(
            text = text,
            color = Color(0xFF1C1916),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SaigonOutlinedPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
) {
    val tokens = LocalSaigonTokens.current
    val shape = RoundedCornerShape(999.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(tokens.surface, shape)
            .border(2.dp, tokens.line, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
    ) {
        Text(
            text = text,
            color = contentColor ?: tokens.text,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SaigonTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    accent: Boolean = false,
) {
    val tokens = LocalSaigonTokens.current
    val color = when {
        danger -> tokens.danger
        accent -> tokens.accent
        else -> tokens.muted
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

@Composable
fun SaigonPillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedBrush: Brush? = null,
) {
    val tokens = LocalSaigonTokens.current
    val shape = RoundedCornerShape(999.dp)
    val borderColor = if (selected && selectedBrush != null) Color(0xFF178997) else if (selected) tokens.text else tokens.line
    val textColor = if (selected) Color.White else tokens.text
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(shape)
            .drawBehind {
                if (selected) {
                    drawRect(
                        brush = selectedBrush ?: Brush.linearGradient(
                            colors = listOf(SaigonColors.AccentSoft, SaigonColors.Tertiary),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                    )
                } else {
                    drawRect(color = tokens.surface)
                }
            }
            .border(2.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = textColor,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SaigonTinyPill(
    label: String,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    danger: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalSaigonTokens.current
    val bg = when {
        danger -> tokens.danger
        selected -> tokens.accent
        else -> tokens.bg
    }
    val fg = when {
        danger && tokens.isDark -> tokens.bg
        danger || selected -> Color.White
        else -> tokens.text
    }
    val shape = RoundedCornerShape(999.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
    }
}

@Composable
fun SaigonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
    containerColor: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 2.dp,
    cornerRadius: Dp = 14.dp,
    autoFocus: Boolean = false,
) {
    val tokens = LocalSaigonTokens.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val resolvedBorderColor by animateColorAsState(
        if (focused) tokens.accent else (borderColor ?: tokens.line),
        label = "fieldBorder",
    )
    val shape = RoundedCornerShape(cornerRadius)
    val bg = containerColor ?: tokens.surface

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                color = tokens.text,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
            )
        }
        Row(
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(bg, shape)
                .border(borderWidth, resolvedBorderColor, shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        color = tokens.muted,
                        fontSize = 15.sp,
                        fontFamily = LivvicFontFamily,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    readOnly = readOnly,
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = maxLines,
                    textStyle = TextStyle(
                        fontFamily = LivvicFontFamily,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = tokens.text,
                    ),
                    cursorBrush = SolidColor(tokens.accent),
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    interactionSource = interactionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (autoFocus) Modifier.focusRequester(focusRequester) else Modifier),
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
        if (!supportingText.isNullOrBlank()) {
            Text(
                text = supportingText,
                color = tokens.muted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
fun SaigonCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 2.dp,
    containerColor: Color? = null,
    cornerRadius: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        shape = shape,
        color = containerColor ?: tokens.surface,
        border = BorderStroke(borderWidth, borderColor ?: tokens.line),
        modifier = modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun SaigonSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val tokens = LocalSaigonTokens.current
    val trackColor by animateColorAsState(if (checked) tokens.accent else tokens.line, label = "switchTrack")
    val thumbOffset by animateDpAsState(if (checked) 18.dp else 0.dp, label = "switchThumb")
    val switchControl: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 26.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(trackColor)
                .border(2.dp, tokens.text.copy(alpha = 0.15f), RoundedCornerShape(999.dp))
                .clickable { onCheckedChange(!checked) }
                .padding(3.dp),
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(20.dp)
                    .shadow(1.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
    if (label.isNullOrBlank()) {
        Box(modifier = modifier) { switchControl() }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onCheckedChange(!checked) }
                .padding(vertical = 2.dp),
        ) {
            switchControl()
            Text(
                text = label,
                color = tokens.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun SaigonSpinner(
    size: Dp = 28.dp,
    strokeWidth: Dp = 3.dp,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalSaigonTokens.current
    CircularProgressIndicator(
        color = tokens.accent,
        trackColor = tokens.line,
        strokeWidth = strokeWidth,
        modifier = modifier.size(size),
    )
}

@Composable
fun SaigonFab(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(56.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(tokens.fabBg)
            .clickable(onClick = onClick),
    ) {
        CompositionLocalProvider(LocalContentColor provides tokens.fabContent) {
            icon()
        }
    }
}

@Composable
fun SaigonDialog(
    onDismiss: () -> Unit,
    showCloseButton: Boolean = true,
    scrollable: Boolean = true,
    scrollEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(SaigonColors.Backdrop)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .systemBarsPadding()
                .imePadding()
                .padding(20.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = tokens.surface,
                shadowElevation = 20.dp,
                modifier = modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Box {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (scrollable) {
                                    Modifier.verticalScroll(rememberScrollState(), enabled = scrollEnabled)
                                } else {
                                    Modifier
                                },
                            )
                            .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 14.dp),
                        content = content,
                    )
                    if (showCloseButton) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 10.dp, end = 10.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(tokens.surface)
                                .border(2.dp, tokens.line, CircleShape)
                                .clickable(onClick = onDismiss),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = tokens.text,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaigonBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    trailingHeader: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var dismissing by remember { mutableStateOf(false) }
    val animatedDismiss: () -> Unit = {
        if (!dismissing) {
            dismissing = true
            scope.launch {
                try {
                    sheetState.hide()
                } finally {
                    onDismiss()
                }
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = tokens.surface,
        contentColor = tokens.text,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 44.dp, height = 5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(tokens.line),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.navigationBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 36.dp)
                    .padding(start = 48.dp, end = 48.dp, top = 4.dp, bottom = 8.dp),
            ) {
                Text(
                    text = title,
                    color = tokens.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center),
                )
                if (trailingHeader != null) {
                    Box(modifier = Modifier.align(Alignment.CenterStart)) {
                        trailingHeader()
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 38.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(tokens.surface)
                        .border(2.dp, tokens.line, CircleShape)
                        .clickable(onClick = animatedDismiss),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = tokens.text,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun UserAvatar(
    photoUrl: String?,
    name: String,
    size: Dp = 44.dp,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalSaigonTokens.current
    val resolvedBorder = borderColor ?: tokens.line.copy(alpha = 0.7f)
    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(SaigonColors.FaceBg)
                .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, resolvedBorder, CircleShape) else Modifier),
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(SaigonColors.FaceBg)
                .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, resolvedBorder, CircleShape) else Modifier),
        ) {
            val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString()
            if (initial != null) {
                Text(
                    text = initial,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = (size.value * 0.42f).sp,
                    color = SaigonColors.Ink,
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = name,
                    tint = SaigonColors.Ink,
                    modifier = Modifier.size(size * 0.55f),
                )
            }
        }
    }
}

@Composable
fun LinkifiedText(
    text: String,
    color: Color = LocalContentColor.current,
    linkColor: Color = SaigonColors.Accent,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val annotated: AnnotatedString = remember(text, linkColor) {
        buildAnnotatedString {
            TextLinks.pieces(text).forEach { piece ->
                if (piece.href != null) {
                    pushLink(
                        LinkAnnotation.Url(
                            url = piece.href!!,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                            linkInteractionListener = {
                                AndroidIntents.openExternalUrl(context, piece.href!!)
                            },
                        ),
                    )
                    append(piece.text)
                    pop()
                } else {
                    append(piece.text)
                }
            }
        }
    }
    Text(
        text = annotated,
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier,
    )
}

@Composable
fun NotificationBellButton(
    unreadCount: Long,
    label: String,
    onClick: () -> Unit,
    overlay: Boolean = true,
) {
    val tokens = LocalSaigonTokens.current
    Box(modifier = Modifier.size(44.dp)) {
        SaigonIconButton(
            onClick = onClick,
            overlay = overlay,
            size = 44.dp,
        ) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = label,
                tint = if (overlay) Color.White else tokens.text,
                modifier = Modifier.size(22.dp),
            )
        }
        if (unreadCount > 0L) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(if (unreadCount > 99L) 26.dp else 22.dp)
                    .clip(CircleShape)
                    .background(SaigonColors.NotifyRed)
                    .border(2.dp, Color.White, CircleShape),
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    color = Color.White,
                    fontSize = if (unreadCount > 99) 9.sp else 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
fun NotificationsSheet(
    store: NotificationStore,
    language: Language,
    onDismiss: () -> Unit,
    onNavigatePath: (String) -> Unit,
) {
    val state by store.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        store.loadInitial()
    }

    SaigonBottomSheet(
        title = Copy.text(language, "notifications"),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            if (state.items.any { !it.read }) {
                Text(
                    text = Copy.text(language, "markAllRead"),
                    color = tokens.accent,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { scope.launch { store.markAllRead() } }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            when {
                state.isLoading && state.items.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        SaigonSpinner()
                    }
                }
                state.items.isEmpty() -> {
                    Text(
                        Copy.text(language, "noNotifications"),
                        color = tokens.muted,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 420.dp),
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            NotificationRow(
                                item = item,
                                language = language,
                                onClick = {
                                    scope.launch { store.markRead(item.id) }
                                    onDismiss()
                                    onNavigatePath(NotificationCopy.path(item.type, item.postId, item.chatId, item.actorId))
                                },
                            )
                        }
                        if (state.nextCursor != null) {
                            item {
                                SaigonTextButton(
                                    text = Copy.text(language, "loadMore"),
                                    onClick = { store.loadMore() },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    item: NotificationDto,
    language: Language,
    onClick: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    val bg = if (item.read) tokens.surface else tokens.accent.copy(alpha = 0.12f)
    val border = if (item.read) tokens.line else tokens.accent
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(
            NotificationCopy.title(language, item.type, item.actorName, item.preview),
            color = tokens.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        val body = NotificationCopy.body(item.preview)
        if (body.isNotBlank()) {
            Text(body, fontSize = 13.sp, color = tokens.muted)
        }
        val age = PostAge.relative(item.createdAt, currentTimeMillis(), language)
        if (!age.isNullOrBlank()) {
            Text(age, fontSize = 12.sp, color = tokens.muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MiniProfileDialog(
    name: String,
    photoUrl: String?,
    location: String,
    intro: String,
    language: Language,
    onDismiss: () -> Unit,
    onOpenProfile: (() -> Unit)? = null,
    onMessage: (() -> Unit)? = null,
    communities: List<CommunityDto> = emptyList(),
    onOpenCommunity: ((String) -> Unit)? = null,
    wants: List<WantDto> = emptyList(),
    onToggleWant: ((String, Boolean) -> Unit)? = null,
    onOpenWant: ((String) -> Unit)? = null,
    coverPhotoUrl: String? = null,
    gradientKey: String = name,
) {
    val tokens = LocalSaigonTokens.current
    var pendingWantLeave by remember { mutableStateOf<WantDto?>(null) }
    SaigonDialog(onDismiss = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(176.dp)) {
                ProfileCoverPhoto(
                    photoUrl = coverPhotoUrl,
                    gradientKey = gradientKey,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(128.dp)
                        .align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(16.dp)),
                )
                UserAvatar(
                    photoUrl = photoUrl,
                    name = name,
                    size = 96.dp,
                    borderWidth = 0.dp,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            Text(
                text = name,
                color = tokens.text,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            if (location.isNotBlank()) {
                Text(
                    text = location,
                    color = tokens.muted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
            if (intro.isNotBlank()) {
                LinkifiedText(
                    text = intro,
                    color = tokens.text,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            if (communities.isNotEmpty()) {
                Text(
                    text = Copy.text(language, "communitiesJoined", mapOf("name" to name)),
                    color = tokens.text,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    communities.forEach { community ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(tokens.surface)
                                .border(1.dp, tokens.line, RoundedCornerShape(999.dp))
                                .clickable(enabled = onOpenCommunity != null) {
                                    onOpenCommunity?.invoke(community.id)
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = "${community.emoji} ${community.name}".trim(),
                                color = tokens.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }
            if (wants.isNotEmpty()) {
                Text(
                    text = Copy.text(language, "wantsTo", mapOf("name" to name)),
                    color = tokens.text,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    wants.forEach { want ->
                        WantNotebookCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = onOpenWant != null) { onOpenWant?.invoke(want.id) },
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = want.name,
                                    color = tokens.text,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic,
                                    fontFamily = LivvicFontFamily,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (want.memberCount >= 2L) {
                                    WantMemberFaceStack(members = want.members, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                            SaigonPillChip(
                                label = Copy.text(language, if (want.joined) "wantJoined" else "wantJoin"),
                                selected = want.joined,
                                enabled = onToggleWant != null,
                                onClick = {
                                    if (want.joined) pendingWantLeave = want else onToggleWant?.invoke(want.id, false)
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (onMessage != null) {
                    SaigonPrimaryButton(
                        text = Copy.text(language, "messageUser", mapOf("name" to name)),
                        onClick = {
                            onDismiss()
                            onMessage()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (onOpenProfile != null) {
                    SaigonOutlinedPillButton(
                        text = Copy.text(language, "profile"),
                        onClick = {
                            onDismiss()
                            onOpenProfile()
                        },
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
            onConfirm = {
                onToggleWant?.invoke(want.id, true)
                pendingWantLeave = null
            },
            onDismiss = { pendingWantLeave = null },
        )
    }
}

@Composable
fun ProfileCoverPhoto(
    photoUrl: String?,
    gradientKey: String,
    modifier: Modifier = Modifier,
) {
    val gradient = CalmGradients.forKey(gradientKey)
    Box(
        modifier = modifier.background(
            Brush.linearGradient(listOf(Color(gradient.startArgb), Color(gradient.endArgb))),
        ),
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun WantNotebookCard(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    val shape = RoundedCornerShape(14.dp)
    val paper = if (tokens.isDark) Color(0xFF2A2521) else Color(0xFFFFFDF7)
    val rule = if (tokens.isDark) Color.White.copy(alpha = 0.08f) else Color(0xFF658DAA).copy(alpha = 0.2f)
    val margin = if (tokens.isDark) Color(0xFFE9897C).copy(alpha = 0.6f) else Color(0xFFD66E72).copy(alpha = 0.55f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(shape)
            .background(paper)
            .drawBehind {
                val spacing = 24.dp.toPx()
                var y = spacing
                while (y < size.height) {
                    drawLine(rule, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    y += spacing
                }
                val marginX = 25.dp.toPx()
                drawLine(margin, Offset(marginX, 0f), Offset(marginX, size.height), 1.5.dp.toPx())
            }
            .border(1.dp, tokens.line, shape)
            .padding(start = 34.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        content = content,
    )
}

@Composable
fun WantMemberFaceStack(
    members: List<WantMemberDto>,
    modifier: Modifier = Modifier,
) {
    val faces = members.take(Limits.WANT_CARD_MEMBER_LIMIT)
    if (faces.isEmpty()) return
    val tokens = LocalSaigonTokens.current
    val faceSize = 26.dp
    val overlap = 18.dp
    Box(
        modifier = modifier
            .width(faceSize + overlap * (faces.size - 1).toFloat())
            .height(faceSize),
    ) {
        faces.forEachIndexed { index, member ->
            UserAvatar(
                photoUrl = member.photoUrl,
                name = member.name,
                size = faceSize,
                borderColor = tokens.surface,
                borderWidth = 2.dp,
                modifier = Modifier.offset(x = overlap * index.toFloat()).zIndex(index.toFloat()),
            )
        }
    }
}

@Composable
fun ViewerButton(
    me: MeDto?,
    language: Language,
    toComposer: Boolean,
    overlay: Boolean = true,
    showSignedOutBubble: Boolean = toComposer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalSaigonTokens.current
    val ringColor = if (overlay) Color.White else tokens.line
    val contentDesc = Copy.text(language, if (toComposer) "createPost" else "openProfile")

    Box(contentAlignment = Alignment.TopEnd, modifier = modifier) {
        if (me == null) {
            // Signed out: animated sparkles + '+' button when toComposer, or person button otherwise
            val pulse = rememberInfiniteTransition(label = "viewerSparkle")
            val sparkAlpha by pulse.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "sparkAlpha",
            )
            val bubbleBob by pulse.animateFloat(
                initialValue = 0f,
                targetValue = 4f,
                animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
                label = "bubbleBob",
            )
            Box(contentAlignment = Alignment.Center) {
                if (toComposer) {
                    Text(
                        "✦",
                        color = Color(0xFFFFF6C8),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .offset(x = (-22).dp, y = (-16).dp)
                            .alpha(sparkAlpha),
                    )
                    Text(
                        "✦",
                        color = Color(0xFFFFF6C8),
                        fontSize = 9.sp,
                        modifier = Modifier
                            .offset(x = 22.dp, y = 16.dp)
                            .alpha(1.15f - sparkAlpha * 0.7f),
                    )
                }
                SaigonIconButton(
                    onClick = onClick,
                    overlay = overlay,
                    size = 44.dp,
                ) {
                    if (toComposer) {
                        Text(
                            "+",
                            color = if (overlay) Color.White else tokens.text,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 26.sp,
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = contentDesc,
                            tint = if (overlay) Color.White else tokens.text,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            if (toComposer && showSignedOutBubble) {
                FirstPostBubble(
                    language = language,
                    bobOffsetDp = bubbleBob,
                    onClick = onClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = 50.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    ),
            ) {
                UserAvatar(
                    photoUrl = me.photoUrl,
                    name = me.name,
                    size = 44.dp,
                    borderColor = ringColor,
                    borderWidth = 2.dp,
                )
                if (toComposer) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(18.dp)
                            .shadow(2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.White),
                    ) {
                        Text(
                            "+",
                            color = SaigonColors.Ink,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 13.sp,
                        )
                    }
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(18.dp)
                            .shadow(2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(SaigonBrushes.primaryGradient),
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = contentDesc,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PostGridRows(
    posts: List<PostCardDto>,
    showAuthor: Boolean,
    onPostClick: (PostCardDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = posts.chunked(3)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        rows.forEach { rowPosts ->
            PostGridRow(
                rowPosts = rowPosts,
                showAuthor = showAuthor,
                onPostClick = onPostClick,
            )
        }
    }
}

@Composable
fun PostGridRow(
    rowPosts: List<PostCardDto>,
    showAuthor: Boolean,
    onPostClick: (PostCardDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalSaigonTokens.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        rowPosts.forEach { post ->
            val thumb = post.allPhotoUrls().firstOrNull()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.line)
                    .clickable { onPostClick(post) },
            ) {
                if (!thumb.isNullOrBlank()) {
                    AsyncImage(
                        model = thumb,
                        contentDescription = post.message,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.48f)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xB814100C)),
                            ),
                        ),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(8.dp),
                ) {
                    if (post.message.isNotBlank()) {
                        Text(
                            text = post.message,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 14.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (showAuthor && post.authorName.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            UserAvatar(
                                photoUrl = post.authorPhotoUrl,
                                name = post.authorName,
                                size = 20.dp,
                                borderColor = Color.White.copy(alpha = 0.85f),
                                borderWidth = 1.dp,
                            )
                            Text(
                                text = post.authorName,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        repeat((3 - rowPosts.size).coerceAtLeast(0)) {
            Spacer(Modifier.weight(1f))
        }
    }
}

data class PickedPhotoItem(
    val existingPath: String? = null,
    val existingUrl: String? = null,
    val bytes: ByteArray? = null,
)

@Composable
fun SaigonPhotoPicker(
    photos: List<PickedPhotoItem>,
    activeIndex: Int,
    onActiveIndexChange: (Int) -> Unit,
    onPhotosChange: (List<PickedPhotoItem>) -> Unit,
    onAddPhotosClick: () -> Unit,
    language: Language,
    square: Boolean = false,
    rotatePrompts: Boolean = !square,
    emptyPrompt: String = Copy.text(language, if (square) "addPhoto" else "uploadPrompt1"),
    isBusy: Boolean = false,
    maxPhotos: Int = Limits.PHOTOS_MAX,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalSaigonTokens.current
    val safeIdx = activeIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0))
    val activeItem = photos.getOrNull(safeIdx)
    val uploadPrompts = remember(language) { Copy.uploadPrompts(language) }
    var uploadPromptIdx by remember(language) { mutableIntStateOf(0) }
    LaunchedEffect(photos.isEmpty(), rotatePrompts, uploadPrompts) {
        if (!rotatePrompts || photos.isNotEmpty() || uploadPrompts.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(2600)
            uploadPromptIdx = (uploadPromptIdx + 1) % uploadPrompts.size
        }
    }

    val stageAspect = if (square) 1f else 9f / 16f
    val thumbWidthDp = 40.dp
    val thumbHeightDp = if (square) 40.dp else 71.dp
    val thumbGapDp = 8.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(220.dp)
                .aspectRatio(stageAspect)
                .clip(RoundedCornerShape(18.dp))
                .background(tokens.surface)
                .then(
                    if (activeItem == null) {
                        Modifier
                            .dashedBorder(tokens.line, strokeWidth = 2.dp, cornerRadius = 18.dp)
                            .clickable(enabled = !isBusy) { onAddPhotosClick() }
                    } else {
                        Modifier.border(2.dp, tokens.line, RoundedCornerShape(18.dp))
                    },
                ),
        ) {
            if (activeItem != null) {
                val activeBytes = activeItem.bytes
                if (activeBytes != null) {
                    val bitmap = remember(activeBytes) {
                        BitmapFactory.decodeByteArray(activeBytes, 0, activeBytes.size)
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else if (!activeItem.existingUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = activeItem.existingUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable {
                            onPhotosChange(photos.filterIndexed { i, _ -> i != safeIdx })
                            onActiveIndexChange((safeIdx - 1).coerceAtLeast(0))
                        },
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = Copy.text(language, "removePhoto"),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(
                        Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = tokens.muted,
                        modifier = Modifier.size(34.dp),
                    )
                    Text(
                        text = if (rotatePrompts && uploadPrompts.isNotEmpty()) {
                            uploadPrompts[uploadPromptIdx % uploadPrompts.size]
                        } else {
                            emptyPrompt
                        },
                        color = tokens.muted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (photos.isNotEmpty()) {
            val density = LocalDensity.current
            val stridePx = with(density) { (thumbWidthDp + thumbGapDp).toPx() }
            var draggingOrigin by remember { mutableStateOf<Int?>(null) }
            var dragOffsetX by remember { mutableFloatStateOf(0f) }
            var dragOffsetY by remember { mutableFloatStateOf(0f) }
            val dragTarget = draggingOrigin?.let { origin ->
                val shift = kotlin.math.round(dragOffsetX / stridePx).toInt()
                (origin + shift).coerceIn(0, photos.lastIndex)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(thumbGapDp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .width(220.dp)
                    .horizontalScroll(rememberScrollState(), enabled = draggingOrigin == null)
                    .padding(bottom = 4.dp),
            ) {
                photos.forEachIndexed { idx, item ->
                    val selected = idx == safeIdx
                    val isDraggingThis = draggingOrigin == idx
                    val siblingShiftPx = when {
                        draggingOrigin == null || dragTarget == null -> 0f
                        idx == draggingOrigin -> dragOffsetX
                        draggingOrigin!! < dragTarget && idx in (draggingOrigin!! + 1)..dragTarget -> -stridePx
                        dragTarget < draggingOrigin!! && idx in dragTarget until draggingOrigin!! -> stridePx
                        else -> 0f
                    }
                    Box(
                        modifier = Modifier
                            .zIndex(if (isDraggingThis) 40f else 0f)
                            .graphicsLayer {
                                translationX = siblingShiftPx
                                translationY = if (isDraggingThis) dragOffsetY else 0f
                                scaleX = if (isDraggingThis) 1.08f else 1f
                                scaleY = if (isDraggingThis) 1.08f else 1f
                            }
                            .size(width = thumbWidthDp, height = thumbHeightDp)
                            .then(if (isDraggingThis) Modifier.shadow(10.dp, RoundedCornerShape(8.dp)) else Modifier)
                            .clip(RoundedCornerShape(8.dp))
                            .background(tokens.surface)
                            .border(2.dp, if (selected) tokens.accent else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable(enabled = draggingOrigin == null) { onActiveIndexChange(idx) }
                            .pointerInput(photos.size, idx) {
                                if (photos.size <= 1) return@pointerInput
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingOrigin = idx
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetX += dragAmount.x
                                        dragOffsetY += dragAmount.y
                                    },
                                    onDragEnd = {
                                        val origin = draggingOrigin
                                        val target = origin?.let {
                                            (it + kotlin.math.round(dragOffsetX / stridePx).toInt())
                                                .coerceIn(0, photos.lastIndex)
                                        }
                                        if (origin != null && target != null && origin != target) {
                                            val nextList = photos.toMutableList()
                                            val moved = nextList.removeAt(origin)
                                            nextList.add(target, moved)
                                            onPhotosChange(nextList)
                                            onActiveIndexChange(target)
                                        }
                                        draggingOrigin = null
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggingOrigin = null
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f
                                    },
                                )
                            },
                    ) {
                        val itemBytes = item.bytes
                        if (itemBytes != null) {
                            val bitmap = remember(itemBytes) {
                                BitmapFactory.decodeByteArray(itemBytes, 0, itemBytes.size)
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        } else if (!item.existingUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = item.existingUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                if (photos.size < maxPhotos) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(width = thumbWidthDp, height = thumbHeightDp)
                            .clip(RoundedCornerShape(8.dp))
                            .dashedBorder(tokens.line, strokeWidth = 2.dp, cornerRadius = 8.dp)
                            .clickable(enabled = !isBusy) { onAddPhotosClick() },
                    ) {
                        Text("+", color = tokens.muted, fontSize = 22.sp, fontWeight = FontWeight.Light)
                    }
                }
            }
        }

        if (isBusy) {
            LinearProgressIndicator(
                color = tokens.accent,
                trackColor = tokens.line,
                modifier = Modifier.width(220.dp).clip(RoundedCornerShape(999.dp)),
            )
        }
    }
}

@Composable
fun FirstPostBubble(
    language: Language,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bobOffsetDp: Float? = null,
) {
    val resolvedBob = if (bobOffsetDp != null) {
        bobOffsetDp
    } else {
        val pulse = rememberInfiniteTransition(label = "firstPostBubble")
        val animated by pulse.animateFloat(
            initialValue = 0f,
            targetValue = 4f,
            animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
            label = "bubbleBob",
        )
        animated
    }
    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier
            .offset(y = resolvedBob.dp)
            .widthIn(max = 190.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .padding(end = 16.dp)
                .size(9.dp)
                .rotate(45f)
                .background(Color.White, RoundedCornerShape(2.dp)),
        )
        Box(
            modifier = Modifier
                .offset(y = (-5).dp)
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = Copy.text(language, "createFirstPost"),
                color = SaigonColors.LightText,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
fun SaigonConfirmDialog(
    title: String,
    hint: String? = null,
    confirmLabel: String,
    cancelLabel: String,
    danger: Boolean = true,
    loading: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    SaigonDialog(onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title,
                color = tokens.text,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 36.dp),
            )
            if (!hint.isNullOrBlank()) {
                Text(
                    text = hint,
                    color = tokens.muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            SaigonPrimaryButton(
                text = confirmLabel,
                danger = danger,
                loading = loading,
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
            )
            SaigonTextButton(
                text = cancelLabel,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun PhotoViewerDialog(
    photoUrl: String,
    onDismiss: () -> Unit,
) {
    PhotoViewerDialog(
        photos = listOf(UserPhotoDto(id = "", url = photoUrl, canDelete = false)),
        initialIndex = 0,
        language = Language.EN,
        onDeletePhoto = null,
        onDismiss = onDismiss,
    )
}

@Composable
fun PhotoViewerDialog(
    photos: List<UserPhotoDto>,
    initialIndex: Int = 0,
    language: Language,
    onDeletePhoto: (suspend (UserPhotoDto) -> Boolean)? = null,
    onDismiss: () -> Unit,
) {
    if (photos.isEmpty()) {
        onDismiss()
        return
    }
    val scope = rememberCoroutineScope()
    val startIndex = initialIndex.coerceIn(0, photos.lastIndex)
    val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { photos.size })
    var currentScale by remember { mutableFloatStateOf(1f) }
    var currentOffsetX by remember { mutableFloatStateOf(0f) }
    var currentOffsetY by remember { mutableFloatStateOf(0f) }
    var pendingDelete by remember { mutableStateOf<UserPhotoDto?>(null) }
    var deleting by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage, photos.size) {
        currentScale = 1f
        currentOffsetX = 0f
        currentOffsetY = 0f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SaigonColors.PhotoViewerBg),
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = currentScale <= 1.02f,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val photo = photos[page]
                val isCurrent = page == pagerState.currentPage
                val scale = if (isCurrent) currentScale else 1f
                val offsetX = if (isCurrent) currentOffsetX else 0f
                val offsetY = if (isCurrent) currentOffsetY else 0f

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 44.dp)
                        .pointerInput(page) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (currentScale > 1.05f) {
                                        currentScale = 1f
                                        currentOffsetX = 0f
                                        currentOffsetY = 0f
                                    } else {
                                        currentScale = 2.25f
                                    }
                                },
                            )
                        }
                        .pointerInput(page) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val pressedCount = event.changes.count { it.pressed }
                                    if (pressedCount >= 2 || currentScale > 1.02f) {
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()
                                        val nextScale = (currentScale * zoom).coerceIn(1f, 4f)
                                        currentScale = nextScale
                                        if (nextScale <= 1.02f) {
                                            currentScale = 1f
                                            currentOffsetX = 0f
                                            currentOffsetY = 0f
                                        } else {
                                            val maxPanX = (size.width * (nextScale - 1f)) / 2f
                                            val maxPanY = (size.height * (nextScale - 1f)) / 2f
                                            currentOffsetX = (currentOffsetX + pan.x).coerceIn(-maxPanX, maxPanX)
                                            currentOffsetY = (currentOffsetY + pan.y).coerceIn(-maxPanY, maxPanY)
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                ) {
                    AsyncImage(
                        model = photo.url,
                        contentDescription = Copy.text(language, "photo"),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY,
                            ),
                    )
                }
            }

            if (photos.size > 1) {
                val canPrev = pagerState.currentPage > 0
                val canNext = pagerState.currentPage < photos.lastIndex
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 10.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.36f), CircleShape)
                        .alpha(if (canPrev) 1f else 0.28f)
                        .clickable(enabled = canPrev) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = Copy.text(language, "previousPhoto"),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.36f), CircleShape)
                        .alpha(if (canNext) 1f else 0.28f)
                        .clickable(enabled = canNext) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = Copy.text(language, "nextPhoto"),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp),
            ) {
                val activePhoto = photos.getOrNull(pagerState.currentPage)
                if (onDeletePhoto != null && activePhoto != null && activePhoto.canDelete) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .border(1.dp, Color.White.copy(alpha = 0.36f), CircleShape)
                            .clickable { pendingDelete = activePhoto },
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = Copy.text(language, "deletePhotoAsk"),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.36f), CircleShape)
                        .clickable(onClick = onDismiss),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = Copy.text(language, "closePhotoViewer"),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            if (photos.size > 1 || onDeletePhoto != null) {
                val thumbListState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
                LaunchedEffect(pagerState.currentPage, photos.size) {
                    if (pagerState.currentPage in photos.indices) {
                        thumbListState.animateScrollToItem(pagerState.currentPage)
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.78f))
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    LazyRow(
                        state = thumbListState,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        itemsIndexed(photos, key = { idx, p -> "${p.id}_$idx" }) { idx, photo ->
                            val selected = idx == pagerState.currentPage
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = 2.dp,
                                        color = if (selected) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF222222))
                                    .clickable {
                                        scope.launch { pagerState.animateScrollToPage(idx) }
                                    },
                            ) {
                                AsyncImage(
                                    model = photo.url,
                                    contentDescription = Copy.text(language, "photo"),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(if (selected) 1f else 0.68f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        SaigonConfirmDialog(
            title = Copy.text(language, "deletePhotoAsk"),
            hint = Copy.text(language, "deletePhotoHint"),
            confirmLabel = Copy.text(language, "delete"),
            cancelLabel = Copy.text(language, "cancel"),
            loading = deleting,
            onConfirm = {
                val handler = onDeletePhoto ?: return@SaigonConfirmDialog
                scope.launch {
                    deleting = true
                    val ok = handler(target)
                    deleting = false
                    pendingDelete = null
                    if (ok && photos.size <= 1) {
                        onDismiss()
                    }
                }
            },
                onDismiss = { pendingDelete = null },
        )
    }
}

data class DirectMessageTarget(
    val userId: String,
    val name: String,
)

@Composable
fun rememberDirectMessageOpener(
    shared: TownSharedClient,
    onNavigate: (AppRoute) -> Unit,
    onRequireAuth: () -> Unit,
): (userId: String, name: String) -> Unit {
    val sessionState by shared.session.state.collectAsState()
    val lang = sessionState.language
    val scope = rememberCoroutineScope()
    var firstMessageTarget by remember { mutableStateOf<DirectMessageTarget?>(null) }

    firstMessageTarget?.let { target ->
        FirstDirectMessageDialog(
            name = target.name,
            language = lang,
            onSend = { text ->
                val chat = shared.messages.startDirectChat(target.userId, text)
                if (chat != null) {
                    firstMessageTarget = null
                    onNavigate(AppRoute.Chat(chat.id))
                }
            },
            onDismiss = { firstMessageTarget = null },
        )
    }

    return remember(shared, onNavigate, onRequireAuth, sessionState.me?.id) {
        { userId: String, name: String ->
            val viewerId = shared.session.state.value.me?.id
            when {
                userId.isBlank() || userId == viewerId -> Unit
                viewerId == null -> onRequireAuth()
                else -> {
                    scope.launch {
                        when (val result = shared.messages.checkDirectChat(userId)) {
                            is DirectChatCheckResult.Existing -> onNavigate(AppRoute.Chat(result.chat.id))
                            is DirectChatCheckResult.NeedsFirstMessage -> {
                                firstMessageTarget = DirectMessageTarget(userId, name)
                            }
                            is DirectChatCheckResult.Error -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FirstDirectMessageDialog(
    name: String,
    language: Language,
    onSend: suspend (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    SaigonDialog(onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = Copy.text(language, "messageUser", mapOf("name" to name)),
                color = tokens.text,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 36.dp),
            )
            Text(
                text = Copy.text(language, "firstDirectMessage", mapOf("name" to name)),
                color = tokens.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            SaigonTextField(
                value = text,
                onValueChange = { if (it.length <= Limits.CHAT_MESSAGE_MAX) text = it },
                singleLine = false,
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SaigonTextButton(
                    text = Copy.text(language, "cancel"),
                    onClick = onDismiss,
                    enabled = !sending,
                    modifier = Modifier.weight(1f),
                )
                SaigonPrimaryButton(
                    text = Copy.text(language, "send"),
                    enabled = text.isNotBlank() && !sending,
                    loading = sending,
                    onClick = {
                        val trimmed = text.trim()
                        if (trimmed.isEmpty()) return@SaigonPrimaryButton
                        scope.launch {
                            sending = true
                            onSend(trimmed)
                            sending = false
                        }
                    },
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}

fun checkInDurationLabel(minutes: Int, language: Language): String = when (minutes) {
    30 -> Copy.text(language, "checkIn30Minutes")
    60 -> Copy.text(language, "checkIn1Hour")
    90 -> Copy.text(language, "checkIn1AndHalfHours")
    120 -> Copy.text(language, "checkIn2Hours")
    180 -> Copy.text(language, "checkIn3Hours")
    240 -> Copy.text(language, "checkIn4Hours")
    300 -> Copy.text(language, "checkIn5Hours")
    360 -> Copy.text(language, "checkIn6Hours")
    else -> if (minutes < 60) "${minutes}m" else "${minutes / 60}h"
}

fun formatCheckInTime(epochMillis: Long, language: Language): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh")).apply {
        timeInMillis = epochMillis
    }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val minutes = minute.toString().padStart(2, '0')
    if (language == Language.VI) {
        return "${hour.toString().padStart(2, '0')}:$minutes"
    }
    val hour12 = (hour % 12).takeIf { it != 0 } ?: 12
    return "$hour12:$minutes ${if (hour < 12) "AM" else "PM"}"
}

fun formatCheckInBubbleSummary(checkIn: CheckInDto, language: Language): String =
    Copy.text(
        language,
        "checkInBubble",
        mapOf(
            "name" to checkIn.authorName,
            "location" to checkIn.location,
            "time" to formatCheckInTime(checkIn.untilAt, language),
        ),
    )

@Composable
fun CheckInBubbleItem(
    checkIn: CheckInDto,
    language: Language,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val summary = formatCheckInBubbleSummary(checkIn, language)
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val maxBubbleWidth = (screenWidth * 0.80f).coerceAtMost(460.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = modifier
            .widthIn(max = maxBubbleWidth)
            .clip(RoundedCornerShape(28.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        UserAvatar(
            photoUrl = checkIn.authorPhotoUrl,
            name = checkIn.authorName,
            size = 58.dp,
            borderColor = Color.White,
            borderWidth = 3.dp,
            modifier = Modifier.shadow(6.dp, CircleShape),
        )
        Surface(
            color = Color.White.copy(alpha = 0.96f),
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 6.dp,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
            ) {
                Text(
                    text = summary,
                    color = Color(0xFF28231F),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 18.sp,
                )
                if (checkIn.message.isNotBlank()) {
                    Text(
                        text = checkIn.message,
                        color = Color(0xFF28231F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun CheckInDetailDialog(
    checkIn: CheckInDto,
    shared: TownSharedClient,
    language: Language,
    onNavigate: (AppRoute) -> Unit,
    onRequireAuth: () -> Unit,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sessionState by shared.session.state.collectAsState()
    val discoverState by shared.discover.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentCheckIn by remember(checkIn.postId, checkIn.untilAt) { mutableStateOf(checkIn) }
    var selectedMinutes by remember(checkIn.postId) { mutableStateOf<Int?>(null) }
    var messageDraft by remember(checkIn.postId) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var mapTouched by remember { mutableStateOf(false) }

    val isSelf = sessionState.me?.id == currentCheckIn.authorId
    val summary = formatCheckInBubbleSummary(currentCheckIn, language)

    SaigonDialog(
        onDismiss = onDismiss,
        scrollEnabled = !mapTouched,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = Copy.text(language, "checkInDialogTitle"),
                color = tokens.text,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 36.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                UserAvatar(
                    photoUrl = currentCheckIn.authorPhotoUrl,
                    name = currentCheckIn.authorName,
                    size = 64.dp,
                    borderColor = Color.White,
                    borderWidth = 3.dp,
                    modifier = Modifier
                        .shadow(5.dp, CircleShape)
                        .clickable {
                            onDismiss()
                            onNavigate(AppRoute.User(currentCheckIn.authorId))
                        },
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(tokens.bg)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = summary,
                        color = tokens.text,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                    )
                }
            }

            if (currentCheckIn.message.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(tokens.surface)
                        .border(1.dp, tokens.line, RoundedCornerShape(14.dp))
                        .clickable {
                            onDismiss()
                            onNavigate(AppRoute.Post(currentCheckIn.postId))
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = currentCheckIn.message,
                        color = tokens.text,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }

            val checkInLat = currentCheckIn.lat
            val checkInLng = currentCheckIn.lng
            val locationToken = openLocationDestination(
                shared = shared,
                name = currentCheckIn.location,
                placeId = currentCheckIn.placeId,
                locationId = currentCheckIn.locationId,
                lat = checkInLat,
                lng = checkInLng,
            )
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (checkInLat != null && checkInLng != null) {
                    val matchedPlace = remember(currentCheckIn, discoverState.locations) {
                        discoverState.locations.firstOrNull { place ->
                            when {
                                !currentCheckIn.locationId.isNullOrBlank() -> place.id == currentCheckIn.locationId
                                !currentCheckIn.placeId.isNullOrBlank() -> place.placeId == currentCheckIn.placeId
                                else -> place.name == currentCheckIn.location && place.placeId.isNullOrBlank() && place.id.isNullOrBlank()
                            }
                        }
                    }
                    val pinPostCount = if (currentCheckIn.postCount > 0L) currentCheckIn.postCount else (matchedPlace?.postCount ?: 0L)
                    val pinActivityCount = if (currentCheckIn.activityCount > 0L) currentCheckIn.activityCount else (matchedPlace?.activityCount ?: 0L)
                    val pinActivityEmojis = currentCheckIn.activityEmojis.ifEmpty { matchedPlace?.activityEmojis.orEmpty() }
                    val pinHasActivities = currentCheckIn.hasActivities || pinActivityCount > 0L || (matchedPlace?.hasActivities == true)
                    val pinPhotoUrl = currentCheckIn.photoUrl ?: matchedPlace?.photoUrl
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(tokens.surface)
                            .border(1.dp, tokens.line, RoundedCornerShape(14.dp)),
                    ) {
                        SaigonMapView(
                            places = listOf(
                                SaigonMapPlacePin(
                                    key = locationToken,
                                    name = currentCheckIn.location,
                                    lat = checkInLat,
                                    lng = checkInLng,
                                    postCount = pinPostCount,
                                    activityCount = pinActivityCount,
                                    activityEmojis = pinActivityEmojis,
                                    hasActivities = pinHasActivities,
                                    photoUrl = pinPhotoUrl,
                                ),
                            ),
                            mapsApiKey = sessionState.mapsKey,
                            centerLat = checkInLat,
                            centerLng = checkInLng,
                            initialZoom = 15.5f,
                            alwaysShowBubbles = true,
                            language = language,
                            onPlaceClick = {
                                onDismiss()
                                onNavigate(AppRoute.Location(locationToken))
                            },
                            onMapTouchChange = { mapTouched = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = currentCheckIn.location,
                        color = tokens.text,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onDismiss()
                                onNavigate(AppRoute.Location(locationToken))
                            }
                            .padding(vertical = 2.dp),
                    )
                    SaigonPrimaryButton(
                        text = Copy.text(language, "directions"),
                        onClick = {
                            AndroidIntents.openDirections(
                                context = context,
                                name = currentCheckIn.location,
                                lat = checkInLat,
                                lng = checkInLng,
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }

            if (!errorText.isNullOrBlank()) {
                Text(
                    text = errorText.orEmpty(),
                    color = tokens.danger,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (isSelf) {
                Text(
                    text = Copy.text(language, "updateCheckIn"),
                    color = tokens.text,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CheckIns.durationOptionsMinutes.forEach { minutes ->
                        val selected = selectedMinutes == minutes
                        SaigonPillChip(
                            label = checkInDurationLabel(minutes, language),
                            selected = selected,
                            enabled = !busy,
                            onClick = { selectedMinutes = if (selected) null else minutes },
                        )
                    }
                }
                val untilAt = selectedMinutes?.let { CheckIns.endAt(currentTimeMillis(), it) } ?: currentCheckIn.untilAt
                Text(
                    text = Copy.text(language, "checkInUntil", mapOf("time" to formatCheckInTime(untilAt, language))),
                    color = tokens.text,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                )
                SaigonSecondaryButton(
                    text = Copy.text(language, if (selectedMinutes == null) "checkOut" else "updateCheckIn"),
                    enabled = !busy,
                    onClick = {
                        val minutes = selectedMinutes
                        scope.launch {
                            busy = true
                            errorText = null
                            if (minutes != null) {
                                val nextUntil = shared.postDetail.updateCheckIn(currentCheckIn.postId, minutes)
                                if (nextUntil != null) {
                                    currentCheckIn = currentCheckIn.copy(untilAt = nextUntil)
                                    selectedMinutes = null
                                    onChanged()
                                } else {
                                    errorText = shared.postDetail.state.value.error
                                }
                            } else {
                                if (shared.postDetail.removeCheckIn(currentCheckIn.postId)) {
                                    onChanged()
                                    onDismiss()
                                } else {
                                    errorText = shared.postDetail.state.value.error
                                }
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SaigonTextField(
                    value = messageDraft,
                    onValueChange = { if (it.length <= Limits.CHAT_MESSAGE_MAX) messageDraft = it },
                    placeholder = Copy.text(language, "checkInMessagePlaceholder", mapOf("name" to currentCheckIn.authorName)),
                    singleLine = false,
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                SaigonPrimaryButton(
                    text = Copy.text(language, "sendMessage"),
                    enabled = !busy,
                    loading = busy,
                    onClick = {
                        val text = messageDraft.trim()
                        if (text.isEmpty()) return@SaigonPrimaryButton
                        if (sessionState.me == null) {
                            onRequireAuth()
                            return@SaigonPrimaryButton
                        }
                        scope.launch {
                            busy = true
                            errorText = null
                            val chat = shared.messages.startDirectChat(currentCheckIn.authorId, text)
                            busy = false
                            if (chat != null) {
                                onDismiss()
                                onNavigate(AppRoute.Chat(chat.id))
                            } else {
                                errorText = shared.messages.state.value.error ?: Copy.text(language, "unavailable")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
