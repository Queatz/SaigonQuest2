package quest.saigon.android.diagnostics

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.WebView
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CancellationException
import town.saigon.BuildConfig

data class InstallOriginInfo(
    val installingPackage: String = "",
    val initiatingPackage: String = "",
)

data class DiagnosticSnapshot(
    val appVersion: String,
    val buildType: String,
    val serverOrigin: String,
    val mapsConfigured: Boolean,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val hardware: String,
    val display: String,
    val fingerprint: String,
    val androidRelease: String,
    val androidSdkInt: Int,
    val supportedAbis: String,
    val installerPackage: String,
    val initiatingPackage: String,
    val harmonyPlatformVersion: String,
    val emuiVersion: String,
    val ohosVersion: String,
    val hasOhosRuntime: Boolean,
    val containerHint: String,
    val gmsStatusCode: Int,
    val gmsStatusName: String,
    val gmsAvailable: Boolean,
    val webViewProvider: String,
)

object CrashReporter {
    @Volatile
    private var crashlyticsEnabled: Boolean = false

    fun init(context: Context) {
        val appContext = context.applicationContext ?: context
        val firebaseReady = runCatching {
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                FirebaseApp.initializeApp(appContext) != null
            } else {
                true
            }
        }.getOrDefault(false)

        if (!firebaseReady) {
            crashlyticsEnabled = false
            return
        }

        val snapshot = collectDiagnosticSnapshot(appContext)
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.isCrashlyticsCollectionEnabled = true
            crashlytics.setCustomKey("app_version", snapshot.appVersion)
            crashlytics.setCustomKey("build_type", snapshot.buildType)
            crashlytics.setCustomKey("server_origin", snapshot.serverOrigin)
            crashlytics.setCustomKey("maps_configured", snapshot.mapsConfigured)
            crashlytics.setCustomKey("manufacturer", snapshot.manufacturer)
            crashlytics.setCustomKey("brand", snapshot.brand)
            crashlytics.setCustomKey("model", snapshot.model)
            crashlytics.setCustomKey("device", snapshot.device)
            crashlytics.setCustomKey("hardware", snapshot.hardware)
            crashlytics.setCustomKey("display", snapshot.display)
            crashlytics.setCustomKey("fingerprint", snapshot.fingerprint)
            crashlytics.setCustomKey("android_release", snapshot.androidRelease)
            crashlytics.setCustomKey("android_sdk_int", snapshot.androidSdkInt)
            crashlytics.setCustomKey("supported_abis", snapshot.supportedAbis)
            crashlytics.setCustomKey("installer_package", snapshot.installerPackage.ifBlank { "unknown" })
            crashlytics.setCustomKey("initiating_package", snapshot.initiatingPackage.ifBlank { "unknown" })
            crashlytics.setCustomKey("harmony_platform_version", snapshot.harmonyPlatformVersion.ifBlank { "none" })
            crashlytics.setCustomKey("emui_version", snapshot.emuiVersion.ifBlank { "none" })
            crashlytics.setCustomKey("ohos_version", snapshot.ohosVersion.ifBlank { "none" })
            crashlytics.setCustomKey("has_ohos_runtime", snapshot.hasOhosRuntime)
            crashlytics.setCustomKey("container_hint", snapshot.containerHint)
            crashlytics.setCustomKey("gms_status_code", snapshot.gmsStatusCode)
            crashlytics.setCustomKey("gms_status_name", snapshot.gmsStatusName)
            crashlytics.setCustomKey("gms_available", snapshot.gmsAvailable)
            crashlytics.setCustomKey("webview_provider", snapshot.webViewProvider.ifBlank { "unknown" })
            crashlytics.setCustomKey("is_signed_in", false)
            crashlytics.log(
                "CrashReporter init: container=${snapshot.containerHint}, " +
                    "installer=${snapshot.installerPackage.ifBlank { "unknown" }}, " +
                    "gms=${snapshot.gmsStatusName}(${snapshot.gmsStatusCode}), " +
                    "webview=${snapshot.webViewProvider.ifBlank { "unknown" }}",
            )
            crashlyticsEnabled = true
        }.onFailure {
            crashlyticsEnabled = false
        }
    }

    fun setUser(userId: String?, isSignedIn: Boolean) {
        if (!crashlyticsEnabled) return
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setUserId(userId?.takeIf { it.isNotBlank() }.orEmpty())
            crashlytics.setCustomKey("is_signed_in", isSignedIn)
        }
    }

    fun log(message: String) {
        if (!crashlyticsEnabled || message.isBlank()) return
        runCatching {
            FirebaseCrashlytics.getInstance().log(message)
        }
    }

    fun recordException(throwable: Throwable, contextMessage: String? = null) {
        if (throwable is CancellationException) return
        if (!crashlyticsEnabled) return
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            if (!contextMessage.isNullOrBlank()) {
                crashlytics.log(contextMessage)
            }
            crashlytics.recordException(throwable)
        }
    }

    internal fun collectDiagnosticSnapshot(context: Context): DiagnosticSnapshot {
        val installOrigin = resolveInstallOrigin(context)
        val harmonyPlatformVersion = readSystemProperty("hw_sc.build.platform.version")
        val emuiVersion = readSystemProperty("ro.build.version.emui")
        val ohosVersion = readSystemProperty("ro.build.ohos.version")
            .ifBlank { readSystemProperty("ro.build.version.magic") }
        val hasOhosRuntime = hasClass("ohos.system.version.SystemVersion") ||
            hasClass("com.huawei.system.BuildEx")
        val filesPath = runCatching { context.filesDir?.absolutePath.orEmpty() }.getOrDefault("")
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val display = Build.DISPLAY.orEmpty()
        val fingerprint = Build.FINGERPRINT.orEmpty()
        val hardware = Build.HARDWARE.orEmpty()

        val containerHint = detectContainerHint(
            installerPackage = installOrigin.installingPackage,
            initiatingPackage = installOrigin.initiatingPackage,
            harmonyPlatformVersion = harmonyPlatformVersion,
            emuiVersion = emuiVersion,
            ohosVersion = ohosVersion,
            hasOhosRuntime = hasOhosRuntime,
            manufacturer = manufacturer,
            brand = brand,
            display = display,
            fingerprint = fingerprint,
            hardware = hardware,
            filesPath = filesPath,
        )

        val (gmsCode, gmsName) = checkGmsAvailability(context)

        return DiagnosticSnapshot(
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            buildType = BuildConfig.BUILD_TYPE,
            serverOrigin = BuildConfig.SERVER_ORIGIN,
            mapsConfigured = BuildConfig.MAPS_CONFIGURED,
            manufacturer = manufacturer,
            brand = brand,
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            hardware = hardware,
            display = display,
            fingerprint = fingerprint,
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            androidSdkInt = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(","),
            installerPackage = installOrigin.installingPackage,
            initiatingPackage = installOrigin.initiatingPackage,
            harmonyPlatformVersion = harmonyPlatformVersion,
            emuiVersion = emuiVersion,
            ohosVersion = ohosVersion,
            hasOhosRuntime = hasOhosRuntime,
            containerHint = containerHint,
            gmsStatusCode = gmsCode,
            gmsStatusName = gmsName,
            gmsAvailable = gmsCode == ConnectionResult.SUCCESS,
            webViewProvider = resolveWebViewProvider(),
        )
    }

    internal fun detectContainerHint(
        installerPackage: String,
        initiatingPackage: String,
        harmonyPlatformVersion: String,
        emuiVersion: String,
        ohosVersion: String,
        hasOhosRuntime: Boolean,
        manufacturer: String,
        brand: String,
        display: String,
        fingerprint: String,
        hardware: String,
        filesPath: String,
    ): String {
        val combined = listOf(
            installerPackage,
            initiatingPackage,
            display,
            fingerprint,
            hardware,
            filesPath,
        ).joinToString(" ").lowercase()

        return when {
            "easyabroad" in combined || "chujingyi" in combined -> "easyabroad"
            "droitong" in combined || "zhuoyitong" in combined || "com.droi" in combined -> "droitong"
            harmonyPlatformVersion.isNotBlank() || ohosVersion.isNotBlank() || hasOhosRuntime -> "harmonyos"
            emuiVersion.isNotBlank() ||
                manufacturer.equals("huawei", ignoreCase = true) ||
                brand.equals("huawei", ignoreCase = true) -> "huawei_emui"
            else -> "standard_android"
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveInstallOrigin(context: Context): InstallOriginInfo = runCatching {
        val pm = context.packageManager
        val pkg = context.packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val info = pm.getInstallSourceInfo(pkg)
            InstallOriginInfo(
                installingPackage = info.installingPackageName.orEmpty(),
                initiatingPackage = info.initiatingPackageName.orEmpty(),
            )
        } else {
            InstallOriginInfo(
                installingPackage = pm.getInstallerPackageName(pkg).orEmpty(),
                initiatingPackage = "",
            )
        }
    }.getOrDefault(InstallOriginInfo())

    private fun checkGmsAvailability(context: Context): Pair<Int, String> = runCatching {
        val api = GoogleApiAvailability.getInstance()
        val code = api.isGooglePlayServicesAvailable(context)
        code to api.getErrorString(code)
    }.getOrElse { -1 to "check_failed:${it::class.java.simpleName}" }

    private fun resolveWebViewProvider(): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val info = WebView.getCurrentWebViewPackage()
            if (info != null) "${info.packageName}:${info.versionName.orEmpty()}" else "none"
        } else {
            "pre_o"
        }
    }.getOrElse { "error:${it::class.java.simpleName}" }

    @SuppressLint("PrivateApi")
    private fun readSystemProperty(key: String): String = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java, String::class.java)
        (method.invoke(null, key, "") as? String).orEmpty().trim()
    }.getOrDefault("")

    private fun hasClass(name: String): Boolean = runCatching {
        Class.forName(name, false, CrashReporter::class.java.classLoader)
        true
    }.getOrDefault(false)
}
