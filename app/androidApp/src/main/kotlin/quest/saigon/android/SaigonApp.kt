package quest.saigon.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import quest.saigon.AppRoute
import quest.saigon.android.media.AudioPreviewPlayer
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.ui.activities.ActivityDetailScreen
import quest.saigon.android.ui.admin.AdminScreen
import quest.saigon.android.ui.auth.AuthDialog
import quest.saigon.android.ui.components.SaigonSpinner
import quest.saigon.android.ui.composer.ComposerScreen
import quest.saigon.android.ui.discover.DiscoverScreen
import quest.saigon.android.ui.discover.LocationDetailScreen
import quest.saigon.android.ui.feed.FeedScreen
import quest.saigon.android.ui.messages.ChatScreen
import quest.saigon.android.ui.messages.MessagesListScreen
import quest.saigon.android.ui.profile.*
import quest.saigon.android.ui.shop.ProductDetailScreen
import quest.saigon.android.ui.shop.ShopScreen
import quest.saigon.android.ui.stats.StatsScreen
import quest.saigon.matchAppRoute
import quest.saigon.shared.TownSharedClient

@Composable
fun SaigonApp(
    shared: TownSharedClient,
    audioPlayer: AudioPreviewPlayer,
    pendingRoutePath: String?,
    onConsumePendingRoutePath: () -> Unit,
) {
    val navController = rememberNavController()
    val sessionState by shared.session.state.collectAsState()

    var authPromptVisible by remember { mutableStateOf(false) }
    var pendingProtectedRoute by remember { mutableStateOf<AppRoute?>(null) }

    fun navigateTo(route: AppRoute) {
        if (RoutePaths.requiresAuth(route) && shared.session.state.value.me == null) {
            pendingProtectedRoute = route
            authPromptVisible = true
            return
        }
        val path = RoutePaths.toPath(route)
        navController.navigate(path) {
            launchSingleTop = true
        }
    }

    fun navigatePath(path: String) {
        navigateTo(matchAppRoute(path))
    }

    LaunchedEffect(pendingRoutePath, sessionState.meKnown) {
        val raw = pendingRoutePath ?: return@LaunchedEffect
        if (!sessionState.meKnown) return@LaunchedEffect
        onConsumePendingRoutePath()
        navigatePath(raw)
    }

    NavHost(
        navController = navController,
        startDestination = "/",
    ) {
        composable("/") {
            FeedScreen(
                shared = shared,
                audioPlayer = audioPlayer,
                onNavigate = ::navigateTo,
                onNavigatePath = ::navigatePath,
                onRequireAuth = { authPromptVisible = true },
            )
        }
        composable(
            route = "/p/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            FeedScreen(
                shared = shared,
                audioPlayer = audioPlayer,
                focusPostId = id,
                onNavigate = ::navigateTo,
                onNavigatePath = ::navigatePath,
                onRequireAuth = { authPromptVisible = true },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "/c/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            FeedScreen(
                shared = shared,
                audioPlayer = audioPlayer,
                communityId = id,
                onNavigate = ::navigateTo,
                onNavigatePath = ::navigatePath,
                onRequireAuth = { authPromptVisible = true },
                onBack = { navController.popBackStack() },
            )
        }
        composable("/new") {
            AuthGuarded(
                isSignedIn = sessionState.isSignedIn,
                onRequireAuth = {
                    pendingProtectedRoute = AppRoute.NewPost
                    authPromptVisible = true
                },
            ) {
                ComposerScreen(
                    shared = shared,
                    audioPlayer = audioPlayer,
                    onDone = {
                        navController.navigate("/") {
                            popUpTo("/") { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable("/profile") {
            AuthGuarded(
                isSignedIn = sessionState.isSignedIn,
                onRequireAuth = {
                    pendingProtectedRoute = AppRoute.Profile
                    authPromptVisible = true
                },
            ) {
                ProfileScreen(
                    shared = shared,
                    onNavigate = ::navigateTo,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable("/profile/posts") {
            AuthGuarded(
                isSignedIn = sessionState.isSignedIn,
                onRequireAuth = {
                    pendingProtectedRoute = AppRoute.MyPosts
                    authPromptVisible = true
                },
            ) {
                MyPostsScreen(shared = shared, onNavigate = ::navigateTo, onBack = { navController.popBackStack() })
            }
        }
        composable("/profile/communities") {
            AuthGuarded(
                isSignedIn = sessionState.isSignedIn,
                onRequireAuth = {
                    pendingProtectedRoute = AppRoute.MyCommunities
                    authPromptVisible = true
                },
            ) {
                MyCommunitiesScreen(shared = shared, onNavigate = ::navigateTo, onBack = { navController.popBackStack() })
            }
        }
        composable("/profile/music") {
            AuthGuarded(
                isSignedIn = sessionState.isSignedIn,
                onRequireAuth = {
                    pendingProtectedRoute = AppRoute.MyMusic
                    authPromptVisible = true
                },
            ) {
                MyMusicScreen(shared = shared, audioPlayer = audioPlayer, onBack = { navController.popBackStack() })
            }
        }
        composable("/profile/products") {
            AuthGuarded(
                isSignedIn = sessionState.isSignedIn,
                onRequireAuth = {
                    pendingProtectedRoute = AppRoute.MyProducts
                    authPromptVisible = true
                },
            ) {
                MyProductsScreen(shared = shared, onNavigate = ::navigateTo, onBack = { navController.popBackStack() })
            }
        }
        composable("/profile/activities") {
            AuthGuarded(
                isSignedIn = sessionState.isSignedIn,
                onRequireAuth = {
                    pendingProtectedRoute = AppRoute.MyActivities
                    authPromptVisible = true
                },
            ) {
                MyActivitiesScreen(shared = shared, onNavigate = ::navigateTo, onBack = { navController.popBackStack() })
            }
        }
        composable("/profile/orders") {
            AuthGuarded(
                isSignedIn = sessionState.isSignedIn,
                onRequireAuth = {
                    pendingProtectedRoute = AppRoute.MyOrders
                    authPromptVisible = true
                },
            ) {
                MyOrdersScreen(shared = shared, onNavigate = ::navigateTo, onBack = { navController.popBackStack() })
            }
        }
        composable("/profile/features") {
            AuthGuarded(
                isSignedIn = sessionState.isSignedIn,
                onRequireAuth = {
                    pendingProtectedRoute = AppRoute.FeatureSuggestions
                    authPromptVisible = true
                },
            ) {
                FeatureSuggestionsScreen(shared = shared, onBack = { navController.popBackStack() })
            }
        }
        composable(
            route = "/u/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            PublicUserProfileScreen(
                userId = entry.arguments?.getString("id").orEmpty(),
                shared = shared,
                onNavigate = ::navigateTo,
                onRequireAuth = { authPromptVisible = true },
                onBack = { navController.popBackStack() },
            )
        }
        composable("/discover") {
            DiscoverScreen(
                shared = shared,
                onNavigate = ::navigateTo,
                onRequireAuth = { authPromptVisible = true },
                onBack = { navController.popBackStack() },
            )
        }
        composable("/shop") {
            ShopScreen(
                shared = shared,
                onNavigate = ::navigateTo,
                onRequireAuth = { authPromptVisible = true },
                onBack = { navController.popBackStack() },
            )
        }
        composable("/messages") {
            AuthGuarded(
                isSignedIn = sessionState.isSignedIn,
                onRequireAuth = {
                    pendingProtectedRoute = AppRoute.Messages
                    authPromptVisible = true
                },
            ) {
                MessagesListScreen(
                    shared = shared,
                    onNavigate = ::navigateTo,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            route = "/messages/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            AuthGuarded(
                isSignedIn = sessionState.isSignedIn,
                onRequireAuth = {
                    pendingProtectedRoute = AppRoute.Chat(id)
                    authPromptVisible = true
                },
            ) {
                ChatScreen(
                    chatId = id,
                    shared = shared,
                    onNavigate = ::navigateTo,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            route = "/l/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            LocationDetailScreen(
                idOrToken = entry.arguments?.getString("id").orEmpty(),
                shared = shared,
                onNavigate = ::navigateTo,
                onRequireAuth = { authPromptVisible = true },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "/product/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            ProductDetailScreen(
                productId = entry.arguments?.getString("id").orEmpty(),
                shared = shared,
                onNavigate = ::navigateTo,
                onRequireAuth = { authPromptVisible = true },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "/activity/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            ActivityDetailScreen(
                activityId = entry.arguments?.getString("id").orEmpty(),
                shared = shared,
                onNavigate = ::navigateTo,
                onRequireAuth = { authPromptVisible = true },
                onBack = { navController.popBackStack() },
            )
        }
        composable("/stats") {
            StatsScreen(shared = shared, onBack = { navController.popBackStack() })
        }
        composable("/admin") {
            AuthGuarded(
                isSignedIn = sessionState.isSignedIn,
                onRequireAuth = {
                    pendingProtectedRoute = AppRoute.Admin
                    authPromptVisible = true
                },
            ) {
                AdminScreen(shared = shared, onBack = { navController.popBackStack() })
            }
        }
    }

    if (authPromptVisible) {
        AuthDialog(
            sessionStore = shared.session,
            onSuccess = {
                authPromptVisible = false
                shared.onForeground()
                shared.feed.refresh()
                shared.discover.loadSummary()
                shared.discover.refreshCheckIns()
                val target = pendingProtectedRoute
                pendingProtectedRoute = null
                if (target != null) navigateTo(target)
            },
            onDismiss = {
                authPromptVisible = false
                pendingProtectedRoute = null
            },
        )
    }
}

@Composable
private fun AuthGuarded(
    isSignedIn: Boolean,
    onRequireAuth: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (isSignedIn) {
        content()
    } else {
        val tokens = LocalSaigonTokens.current
        LaunchedEffect(Unit) { onRequireAuth() }
        Box(
            Modifier
                .fillMaxSize()
                .background(tokens.bg),
            contentAlignment = Alignment.Center,
        ) {
            SaigonSpinner()
        }
    }
}
