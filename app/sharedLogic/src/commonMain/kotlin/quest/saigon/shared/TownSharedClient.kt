package quest.saigon.shared

import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import quest.saigon.shared.network.TownApiClient
import quest.saigon.shared.platform.TokenStorage
import quest.saigon.shared.platform.createHttpClientEngine
import quest.saigon.shared.platform.createPlatformTokenStorage
import quest.saigon.shared.platform.defaultServerOrigin
import quest.saigon.shared.store.ActivityStore
import quest.saigon.shared.store.AdminStore
import quest.saigon.shared.store.ComposerStore
import quest.saigon.shared.store.DiscoverStore
import quest.saigon.shared.store.FeedStore
import quest.saigon.shared.store.MessagesStore
import quest.saigon.shared.store.NotificationStore
import quest.saigon.shared.store.PostDetailStore
import quest.saigon.shared.store.ProfileStore
import quest.saigon.shared.store.ShopStore
import quest.saigon.shared.store.StatsStore
import quest.saigon.shared.store.TownSessionStore

class TownSharedClient(
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    engine: HttpClientEngine = createHttpClientEngine(),
    tokenStorage: TokenStorage = createPlatformTokenStorage(),
    serverOriginProvider: () -> String = { defaultServerOrigin() },
) {
    private var sessionRef: TownSessionStore? = null

    val api: TownApiClient = TownApiClient(
        engine = engine,
        tokenStorage = tokenStorage,
        serverOriginProvider = serverOriginProvider,
        languageProvider = {
            sessionRef?.currentLanguage ?: quest.saigon.Language.EN
        },
        onUnauthorized = {
            sessionRef?.handleUnauthorized()
            onSignedOut()
        },
    )

    val session: TownSessionStore = TownSessionStore(api, scope).also { sessionRef = it }

    val feed: FeedStore = FeedStore(api, scope)
    val postDetail: PostDetailStore = PostDetailStore(api, feed, scope)
    val notifications: NotificationStore = NotificationStore(api, scope)
    val discover: DiscoverStore = DiscoverStore(api, scope)
    val shop: ShopStore = ShopStore(api, scope)
    val activity: ActivityStore = ActivityStore(api, scope)
    val composer: ComposerStore = ComposerStore(api, feed, scope)
    val messages: MessagesStore = MessagesStore(api, scope)
    val profile: ProfileStore = ProfileStore(api, scope)
    val stats: StatsStore = StatsStore(api, scope)
    val admin: AdminStore = AdminStore(api, scope)

    suspend fun bootstrap() {
        session.bootstrap()
        if (session.state.value.isSignedIn) {
            onForeground()
        }
    }

    fun onForeground() {
        if (session.state.value.isSignedIn) {
            notifications.connect()
            messages.connectStream()
        }
    }

    fun onBackground() {
        notifications.disconnect()
        messages.disconnectStream()
        composer.stopPolling()
    }

    fun onSignedOut() {
        notifications.disconnect()
        messages.disconnectStream()
        composer.stopPolling()
    }

    fun close() {
        onBackground()
        scope.cancel()
        api.httpClient.close()
    }
}
