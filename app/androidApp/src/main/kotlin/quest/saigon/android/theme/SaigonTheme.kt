package quest.saigon.android.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quest.saigon.android.R

val LivvicFontFamily = FontFamily(
    Font(R.font.livvic_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.livvic_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.livvic_medium, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.livvic_semibold, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.livvic_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.livvic_bold, FontWeight.ExtraBold, FontStyle.Normal),
    Font(R.font.livvic_black, FontWeight.Black, FontStyle.Normal),
)

object SaigonColors {
    val LightBg = Color(0xFFF6F1EA)
    val LightSurface = Color(0xFFFFFDF8)
    val LightCard = Color(0xFFF6F1E8)
    val LightText = Color(0xFF241F1A)
    val LightMuted = Color(0xFF746B62)
    val LightLine = Color(0xFFE6DDD2)
    val LightDanger = Color(0xFFB42318)

    val DarkBg = Color(0xFF161311)
    val DarkSurface = Color(0xFF241F1B)
    val DarkText = Color(0xFFF6F1EA)
    val DarkMuted = Color(0xFFB7AEA4)
    val DarkLine = Color(0xFF3C342E)
    val DarkDanger = Color(0xFFFFB4AB)

    val Accent = Color(0xFFE9897C)
    val AccentSoft = Color(0xFFF1B2AA) // color-mix(in srgb, white 35%, #e9897c)
    val Tertiary = Color(0xFF7F7CE9)
    val SecondaryStart = Color(0xFFF9F6F1) // color-mix(in srgb, white 35%, #f6f1ea)
    val SecondaryEnd = Color(0xFFD2D1F7) // color-mix(in srgb, white 65%, #7f7ce9)
    val Ink = Color(0xFF2A241E)
    val Card = Color(0xFFF6F1E8)
    val FaceBg = Color(0xFFD9D0C6)
    val NotifyRed = Color(0xFFE23D2E)
    val StatsGreen = Color(0xFF3CB371)
    val ActivityMapGold = Color(0xFFF5C518)
    val Backdrop = Color(0x7514100C) // rgba(20, 16, 12, .46)
    val PlaceholderInk = Color(0xFFB0A89E)
    val EmbeddedCardBg = Color(0xB8FFFFFF) // rgba(255, 255, 255, .72)
    val PollFill = Color(0x47E9897C) // rgba(233, 137, 124, .28)
    val PhotoViewerBg = Color(0xFF080808)
    val StageBg = Color(0xFF111111)
}

object SaigonBrushes {
    val primaryGradient = Brush.linearGradient(
        colors = listOf(SaigonColors.AccentSoft, SaigonColors.Tertiary),
    )

    val secondaryGradient = Brush.linearGradient(
        colors = listOf(SaigonColors.SecondaryStart, SaigonColors.SecondaryEnd),
    )

    fun pageBackground(dark: Boolean): Brush = Brush.verticalGradient(
        colors = if (dark) {
            listOf(SaigonColors.DarkBg, SaigonColors.DarkSurface)
        } else {
            listOf(SaigonColors.LightBg, SaigonColors.LightSurface)
        },
    )
}

@Immutable
data class SaigonTokens(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val tertiary: Color,
    val ink: Color,
    val card: Color,
    val danger: Color,
    val fabBg: Color,
    val fabContent: Color,
)

private val LightTokens = SaigonTokens(
    isDark = false,
    bg = SaigonColors.LightBg,
    surface = SaigonColors.LightSurface,
    text = SaigonColors.LightText,
    muted = SaigonColors.LightMuted,
    line = SaigonColors.LightLine,
    accent = SaigonColors.Accent,
    tertiary = SaigonColors.Tertiary,
    ink = SaigonColors.Ink,
    card = SaigonColors.Card,
    danger = SaigonColors.LightDanger,
    fabBg = SaigonColors.Ink,
    fabContent = Color.White,
)

private val DarkTokens = SaigonTokens(
    isDark = true,
    bg = SaigonColors.DarkBg,
    surface = SaigonColors.DarkSurface,
    text = SaigonColors.DarkText,
    muted = SaigonColors.DarkMuted,
    line = SaigonColors.DarkLine,
    accent = SaigonColors.Accent,
    tertiary = SaigonColors.Tertiary,
    ink = SaigonColors.Ink,
    card = SaigonColors.Card,
    danger = SaigonColors.DarkDanger,
    fabBg = SaigonColors.DarkText,
    fabContent = Color(0xFF1C1916),
)

val LocalSaigonTokens = staticCompositionLocalOf { LightTokens }

private val LightColors = lightColorScheme(
    primary = SaigonColors.Accent,
    onPrimary = Color.White,
    secondary = SaigonColors.Tertiary,
    onSecondary = Color.White,
    tertiary = SaigonColors.Tertiary,
    onTertiary = Color.White,
    background = SaigonColors.LightBg,
    onBackground = SaigonColors.LightText,
    surface = SaigonColors.LightSurface,
    onSurface = SaigonColors.LightText,
    surfaceVariant = SaigonColors.LightCard,
    onSurfaceVariant = SaigonColors.LightMuted,
    outline = SaigonColors.LightLine,
    error = SaigonColors.LightDanger,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = SaigonColors.Accent,
    onPrimary = Color.White,
    secondary = SaigonColors.Tertiary,
    onSecondary = Color.White,
    tertiary = SaigonColors.Tertiary,
    onTertiary = Color.White,
    background = SaigonColors.DarkBg,
    onBackground = SaigonColors.DarkText,
    surface = SaigonColors.DarkSurface,
    onSurface = SaigonColors.DarkText,
    surfaceVariant = SaigonColors.DarkSurface,
    onSurfaceVariant = SaigonColors.DarkMuted,
    outline = SaigonColors.DarkLine,
    error = SaigonColors.DarkDanger,
    onError = SaigonColors.DarkBg,
)

private val SaigonShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val SaigonTypography = Typography(
    displayLarge = TextStyle(fontFamily = LivvicFontFamily, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
    displayMedium = TextStyle(fontFamily = LivvicFontFamily, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
    displaySmall = TextStyle(fontFamily = LivvicFontFamily, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = LivvicFontFamily, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = LivvicFontFamily, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontFamily = LivvicFontFamily, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
    titleLarge = TextStyle(fontFamily = LivvicFontFamily, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontFamily = LivvicFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontFamily = LivvicFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontFamily = LivvicFontFamily, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = LivvicFontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = LivvicFontFamily, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = LivvicFontFamily, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold),
    labelMedium = TextStyle(fontFamily = LivvicFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold),
    labelSmall = TextStyle(fontFamily = LivvicFontFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun SaigonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) DarkTokens else LightTokens
    CompositionLocalProvider(
        LocalSaigonTokens provides tokens,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = SaigonShapes,
            typography = SaigonTypography,
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = LivvicFontFamily),
                content = content,
            )
        }
    }
}
