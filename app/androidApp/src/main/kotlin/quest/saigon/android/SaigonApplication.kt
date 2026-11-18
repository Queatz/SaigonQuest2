package quest.saigon.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import quest.saigon.android.diagnostics.CrashReporter
import quest.saigon.android.media.AudioPreviewPlayer
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.platform.AndroidPlatformContext
import town.saigon.BuildConfig

class SaigonApplication : Application(), SingletonImageLoader.Factory {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var shared: TownSharedClient
        private set

    lateinit var audioPlayer: AudioPreviewPlayer
        private set

    override fun onCreate() {
        super.onCreate()
        AndroidPlatformContext.init(this)
        CrashReporter.init(this)
        createNotificationChannel()
        shared = TownSharedClient(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            serverOriginProvider = { BuildConfig.SERVER_ORIGIN },
        )
        audioPlayer = AudioPreviewPlayer(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    CrashReporter.log("Lifecycle: foreground")
                    shared.onForeground()
                }

                override fun onStop(owner: LifecycleOwner) {
                    CrashReporter.log("Lifecycle: background")
                    shared.onBackground()
                    audioPlayer.pause()
                }
            },
        )

        appScope.launch {
            shared.session.state
                .map { it.me?.id to it.isSignedIn }
                .distinctUntilChanged()
                .collect { (userId, isSignedIn) ->
                    CrashReporter.setUser(userId = userId, isSignedIn = isSignedIn)
                }
        }

        appScope.launch {
            try {
                CrashReporter.log("App bootstrap started")
                shared.bootstrap()
                shared.api.recordVisit()
                CrashReporter.log("App bootstrap complete")
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                CrashReporter.recordException(error, "App bootstrap failed")
                throw error
            }
            refreshFcmTokenSafely()
        }
    }

    fun refreshFcmTokenSafely() {
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        appScope.launch { shared.session.syncFcmToken(token) }
                    }
                }
                .addOnFailureListener { error ->
                    CrashReporter.recordException(error, "FCM token fetch failed")
                }
        }.onFailure { error ->
            CrashReporter.recordException(error, "FirebaseMessaging unavailable")
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val okHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = runBlocking { shared.api.tokenStorage.readSessionToken() }
                val req = if (!token.isNullOrBlank()) {
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .header("Cookie", "sq_session=$token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(req)
            }
            .build()
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttp }))
            }
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Saigon.Town",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Comments, events, orders, and chat messages"
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "saigon_town_updates"
    }
}
