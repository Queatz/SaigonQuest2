package quest.saigon.android.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch
import quest.saigon.AppRoute
import quest.saigon.NotificationCopy
import quest.saigon.android.MainActivity
import quest.saigon.android.RoutePaths
import quest.saigon.android.SaigonApplication
import quest.saigon.matchAppRoute
import town.saigon.R

class SaigonFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val app = application as? SaigonApplication ?: return
        app.appScope.launch {
            app.shared.session.syncFcmToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = message.notification?.title ?: data["title"] ?: "Saigon.Town"
        val body = message.notification?.body ?: data["body"].orEmpty()
        val rawUrl = data["url"]?.takeIf { it.isNotBlank() } ?: NotificationCopy.path(
            type = data["type"].orEmpty(),
            postId = data["postId"].orEmpty(),
            chatId = data["chatId"].orEmpty(),
            actorId = data["actorId"].orEmpty(),
        )
        val parsedPath = RoutePaths.parsePathFromUriString(rawUrl)
        val url = if (parsedPath != null && matchAppRoute(parsedPath) !is AppRoute.NotFound) parsedPath else "/"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(RoutePaths.EXTRA_ROUTE_URL, url)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            url.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, SaigonApplication.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.saigon_notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(url.hashCode(), notification)
        }
    }
}
