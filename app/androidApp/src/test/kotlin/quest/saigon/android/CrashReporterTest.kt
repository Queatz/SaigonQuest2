package quest.saigon.android

import kotlinx.coroutines.CancellationException
import quest.saigon.android.diagnostics.CrashReporter
import kotlin.test.Test
import kotlin.test.assertEquals

class CrashReporterTest {
    @Test
    fun detectsEasyAbroadAndDroiTongAndHarmonyContainers() {
        assertEquals(
            "easyabroad",
            CrashReporter.detectContainerHint(
                installerPackage = "com.easyabroad.app",
                initiatingPackage = "",
                harmonyPlatformVersion = "5.0.0",
                emuiVersion = "",
                ohosVersion = "",
                hasOhosRuntime = true,
                manufacturer = "HUAWEI",
                brand = "HUAWEI",
                display = "",
                fingerprint = "",
                hardware = "",
                filesPath = "",
            ),
        )

        assertEquals(
            "droitong",
            CrashReporter.detectContainerHint(
                installerPackage = "com.droi.tong",
                initiatingPackage = "",
                harmonyPlatformVersion = "",
                emuiVersion = "",
                ohosVersion = "",
                hasOhosRuntime = false,
                manufacturer = "HUAWEI",
                brand = "HUAWEI",
                display = "",
                fingerprint = "",
                hardware = "",
                filesPath = "",
            ),
        )

        assertEquals(
            "harmonyos",
            CrashReporter.detectContainerHint(
                installerPackage = "com.huawei.appmarket",
                initiatingPackage = "",
                harmonyPlatformVersion = "4.2.0",
                emuiVersion = "",
                ohosVersion = "",
                hasOhosRuntime = false,
                manufacturer = "HUAWEI",
                brand = "HUAWEI",
                display = "",
                fingerprint = "",
                hardware = "",
                filesPath = "",
            ),
        )

        assertEquals(
            "huawei_emui",
            CrashReporter.detectContainerHint(
                installerPackage = "",
                initiatingPackage = "",
                harmonyPlatformVersion = "",
                emuiVersion = "EmotionUI_13.0.0",
                ohosVersion = "",
                hasOhosRuntime = false,
                manufacturer = "HUAWEI",
                brand = "HUAWEI",
                display = "",
                fingerprint = "",
                hardware = "",
                filesPath = "",
            ),
        )

        assertEquals(
            "standard_android",
            CrashReporter.detectContainerHint(
                installerPackage = "com.android.vending",
                initiatingPackage = "com.android.vending",
                harmonyPlatformVersion = "",
                emuiVersion = "",
                ohosVersion = "",
                hasOhosRuntime = false,
                manufacturer = "Google",
                brand = "google",
                display = "AP3A.241005.015",
                fingerprint = "google/husky/husky:15/AP3A",
                hardware = "husky",
                filesPath = "/data/user/0/town.saigon/files",
            ),
        )
    }

    @Test
    fun crashReporterMethodsNoOpSafelyWhenFirebaseNotInitialized() {
        CrashReporter.log("test breadcrumb")
        CrashReporter.setUser("user_123", isSignedIn = true)
        CrashReporter.recordException(IllegalStateException("boom"), "test context")
        CrashReporter.recordException(CancellationException("cancelled"), "should be ignored")
    }
}
