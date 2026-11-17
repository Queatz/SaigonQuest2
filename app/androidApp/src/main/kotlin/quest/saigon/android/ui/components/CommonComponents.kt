package quest.saigon.android.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
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
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import quest.saigon.AppRoute
import quest.saigon.Copy
import quest.saigon.Language
import quest.saigon.Limits
import quest.saigon.NotificationCopy
import quest.saigon.PostAge
import quest.saigon.TextLinks
import quest.saigon.android.BuildConfig
import quest.saigon.android.theme.LivvicFontFamily
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.theme.SaigonBrushes
import quest.saigon.android.theme.SaigonColors
import quest.saigon.encodeLocationToken
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.MeDto
import quest.saigon.shared.model.NotificationDto
import quest.saigon.shared.model.PlaceSuggestionDto
import quest.saigon.shared.model.PostCardDto
import quest.saigon.shared.model.UserPhotoDto
import quest.saigon.shared.platform.currentTimeMillis
import quest.saigon.shared.store.DirectChatCheckResult
import quest.saigon.shared.store.NotificationStore

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
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
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
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
) {
    val tokens = LocalSaigonTokens.current
    val shape = RoundedCornerShape(999.dp)
    val bgBrush = if (danger) SolidColor(tokens.danger) else SaigonBrushes.primaryGradient
    val textColor = if (danger && tokens.isDark) tokens.bg else Color.White
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(bgBrush, shape)
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
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(SaigonBrushes.secondaryGradient, shape)
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
) {
    val tokens = LocalSaigonTokens.current
    val shape = RoundedCornerShape(999.dp)
    val borderColor = if (selected) tokens.text else tokens.line
    val bgBrush: Brush = if (selected) SaigonBrushes.primaryGradient else SolidColor(tokens.surface)
    val textColor = if (selected) Color.White else tokens.text
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(bgBrush, shape)
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
) {
    val tokens = LocalSaigonTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val resolvedBorderColor by animateColorAsState(
        if (focused) tokens.accent else (borderColor ?: tokens.line),
        label = "fieldBorder",
    )
    val shape = RoundedCornerShape(cornerRadius)
    val bg = containerColor ?: tokens.surface

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
                    modifier = Modifier.fillMaxWidth(),
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
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(SaigonColors.NotifyRed)
                    .border(2.dp, Color.White, RoundedCornerShape(999.dp))
                    .padding(horizontal = 5.dp),
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
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
                                    onNavigatePath(NotificationCopy.path(item.type, item.postId, item.chatId))
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
) {
    val tokens = LocalSaigonTokens.current
    SaigonDialog(onDismiss = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            UserAvatar(photoUrl = photoUrl, name = name, size = 96.dp, borderWidth = 0.dp)
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

    Box(modifier = modifier) {
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
    val tokens = LocalSaigonTokens.current
    val rows = posts.chunked(3)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        rows.forEach { rowPosts ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
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
                repeat(3 - rowPosts.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
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
