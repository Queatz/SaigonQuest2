package quest.saigon.android

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import quest.saigon.android.theme.SaigonColors

class SaigonThemeTest {
    @Test
    fun colorTokensMatchWebStylesCss() {
        assertEquals(Color(0xFFF6F1EA), SaigonColors.LightBg)
        assertEquals(Color(0xFFFFFDF8), SaigonColors.LightSurface)
        assertEquals(Color(0xFF241F1A), SaigonColors.LightText)
        assertEquals(Color(0xFF746B62), SaigonColors.LightMuted)
        assertEquals(Color(0xFFE6DDD2), SaigonColors.LightLine)
        assertEquals(Color(0xFFB42318), SaigonColors.LightDanger)

        assertEquals(Color(0xFF161311), SaigonColors.DarkBg)
        assertEquals(Color(0xFF241F1B), SaigonColors.DarkSurface)
        assertEquals(Color(0xFFF6F1EA), SaigonColors.DarkText)
        assertEquals(Color(0xFFB7AEA4), SaigonColors.DarkMuted)
        assertEquals(Color(0xFF3C342E), SaigonColors.DarkLine)
        assertEquals(Color(0xFFFFB4AB), SaigonColors.DarkDanger)

        assertEquals(Color(0xFFE9897C), SaigonColors.Accent)
        assertEquals(Color(0xFF7F7CE9), SaigonColors.Tertiary)
        assertEquals(Color(0xFF2A241E), SaigonColors.Ink)
        assertEquals(Color(0xFFF6F1E8), SaigonColors.Card)
        assertEquals(Color(0xFFD9D0C6), SaigonColors.FaceBg)
        assertEquals(Color(0xFFE23D2E), SaigonColors.NotifyRed)
    }
}
