package quest.saigon.android.ui.discover

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import quest.saigon.AppRoute
import quest.saigon.Copy
import quest.saigon.Language
import quest.saigon.Limits
import quest.saigon.android.media.ImagePreparer
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.theme.SaigonColors
import quest.saigon.android.ui.components.ActivityCard
import quest.saigon.android.ui.components.ActivityEditorDialog
import quest.saigon.android.ui.components.AndroidIntents
import quest.saigon.android.ui.components.CheckInBubbleItem
import quest.saigon.android.ui.components.CheckInDetailDialog
import quest.saigon.android.ui.components.LinkifiedText
import quest.saigon.android.ui.components.PhotoViewerDialog
import quest.saigon.android.ui.components.PostGridRow
import quest.saigon.android.ui.components.PostGridRows
import quest.saigon.android.ui.components.SaigonBottomSheet
import quest.saigon.android.ui.components.SaigonCard
import quest.saigon.android.ui.components.SaigonConfirmDialog
import quest.saigon.android.ui.components.SaigonDialog
import quest.saigon.android.ui.components.SaigonIconButton
import quest.saigon.android.ui.components.SaigonMapEventPin
import quest.saigon.android.ui.components.SaigonMapPlacePin
import quest.saigon.android.ui.components.SaigonMapView
import quest.saigon.android.ui.components.SaigonOutlinedPillButton
import quest.saigon.android.ui.components.SaigonPageShell
import quest.saigon.android.ui.components.SaigonPillChip
import quest.saigon.android.ui.components.SaigonPrimaryButton
import quest.saigon.android.ui.components.SaigonSpinner
import quest.saigon.android.ui.components.SaigonTextButton
import quest.saigon.android.ui.components.SaigonTextField
import quest.saigon.android.ui.components.SaigonTinyPill
import quest.saigon.android.ui.components.UserAvatar
import quest.saigon.android.ui.components.ViewerButton
import quest.saigon.android.ui.components.dashedBorder
import quest.saigon.android.ui.components.formatVnd
import quest.saigon.android.ui.components.openLocationDestination
import quest.saigon.android.ui.shop.LearnProductCard
import quest.saigon.encodeLocationToken
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.CheckInDto
import quest.saigon.shared.model.PlaceSuggestionDto
import quest.saigon.shared.model.PostCardDto
import quest.saigon.shared.model.UserPhotoDto
import quest.saigon.shared.network.ApiResult
import quest.saigon.shared.platform.currentTimeMillis
import quest.saigon.shared.store.ActivityStore
import quest.saigon.shared.store.LocationDetailState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private enum class DiscoverTab {
    EVENTS,
    ACTIVITIES,
    LEARN,
    PEOPLE,
    COMMUNITIES,
}

@Composable
fun DiscoverScreen(
    shared: TownSharedClient,
    onNavigate: (AppRoute) -> Unit,
    onRequireAuth: () -> Unit,
    onBack: () -> Unit,
) {
    val sessionState by shared.session.state.collectAsState()
    val discoverState by shared.discover.state.collectAsState()
    val activityState by shared.activity.state.collectAsState()
    val shopState by shared.shop.state.collectAsState()
    val lang = sessionState.language
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()

    var selectedTabName by rememberSaveable { mutableStateOf(DiscoverTab.EVENTS.name) }
    val selectedTab = DiscoverTab.entries.firstOrNull { it.name == selectedTabName } ?: DiscoverTab.EVENTS
    var autoSwitchedEmptyEvents by rememberSaveable { mutableStateOf(false) }
    var startedInitialLoad by remember { mutableStateOf(false) }
    var mapTouched by remember { mutableStateOf(false) }
    var activeCheckIn by remember { mutableStateOf<CheckInDto?>(null) }
    var communityQuery by remember { mutableStateOf("") }
    var learnQuery by remember { mutableStateOf("") }
    var showCreateCommunity by remember { mutableStateOf(false) }
    var showPlaceSearch by remember { mutableStateOf(false) }
    var showPostSearch by remember { mutableStateOf(false) }
    var showAddPlace by remember { mutableStateOf(false) }
    var newPlaceLat by remember { mutableDoubleStateOf(10.7769) }
    var newPlaceLng by remember { mutableDoubleStateOf(106.7009) }
    var zoomPhoto by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sessionState.me?.id) {
        shared.discover.loadAll()
        shared.activity.loadActivities()
        shared.shop.loadInitial()
    }

    LaunchedEffect(discoverState.isLoading, discoverState.events.size, discoverState.error) {
        if (discoverState.isLoading) {
            startedInitialLoad = true
        } else {
            if (discoverState.events.isNotEmpty()) {
                autoSwitchedEmptyEvents = true
            } else if (startedInitialLoad && !autoSwitchedEmptyEvents && discoverState.error == null) {
                autoSwitchedEmptyEvents = true
                if (selectedTab == DiscoverTab.EVENTS) {
                    selectedTabName = DiscoverTab.ACTIVITIES.name
                }
            }
        }
    }

    val eventPins = remember(discoverState.events) {
        discoverState.events.mapNotNull { ev ->
            val lat = ev.lat ?: return@mapNotNull null
            val lng = ev.lng ?: return@mapNotNull null
            SaigonMapEventPin(
                postId = ev.id,
                title = ev.message.ifBlank { ev.location },
                location = ev.location,
                emoji = ev.communityEmoji ?: "📅",
                lat = lat,
                lng = lng,
                attendeeCount = ev.attendeeCount,
                eventStartAt = ev.eventStartAt,
                eventPrice = ev.eventPrice,
                attendees = ev.attendees,
            )
        }
    }

    val placePins = remember(discoverState.locations) {
        discoverState.locations.mapIndexedNotNull { index, place ->
            val lat = place.lat ?: return@mapIndexedNotNull null
            val lng = place.lng ?: return@mapIndexedNotNull null
            val key = place.id?.takeIf { it.isNotBlank() }
                ?: place.placeId?.takeIf { it.isNotBlank() }
                ?: "idx_$index"
            SaigonMapPlacePin(
                key = key,
                name = place.name,
                lat = lat,
                lng = lng,
                postCount = place.postCount,
                activityCount = place.activityCount,
                activityEmojis = place.activityEmojis,
                hasActivities = place.hasActivities,
                photoUrl = place.photoUrl,
            )
        }
    }

    val placeByKey = remember(discoverState.locations) {
        discoverState.locations.mapIndexed { index, place ->
            val key = place.id?.takeIf { it.isNotBlank() }
                ?: place.placeId?.takeIf { it.isNotBlank() }
                ?: "idx_$index"
            key to place
        }.toMap()
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    SaigonPageShell(
        title = Copy.text(lang, "discover"),
        onBack = onBack,
        backContentDescription = Copy.text(lang, "back"),
        topActions = {
            SaigonIconButton(
                onClick = { showPostSearch = true },
                overlay = false,
            ) {
                Icon(Icons.Default.Search, contentDescription = Copy.text(lang, "searchPosts"), modifier = Modifier.size(20.dp))
            }
            ViewerButton(
                me = sessionState.me,
                language = lang,
                toComposer = false,
                overlay = false,
                showSignedOutBubble = false,
                onClick = { onNavigate(AppRoute.Profile) },
            )
        },
    ) {
        LazyColumn(
            userScrollEnabled = !mapTouched,
            contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (discoverState.checkIns.isNotEmpty()) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                    ) {
                        discoverState.checkIns.forEach { checkIn ->
                            CheckInBubbleItem(
                                checkIn = checkIn,
                                language = lang,
                                onClick = { activeCheckIn = checkIn },
                            )
                        }
                    }
                }
            }

            // Interactive map (.events-map) with search toggle
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .height(260.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(tokens.surface)
                        .border(1.dp, tokens.line, RoundedCornerShape(24.dp)),
                ) {
                    SaigonMapView(
                        events = eventPins,
                        places = placePins,
                        mapsApiKey = sessionState.mapsKey,
                        restoredCenterLat = discoverState.mapCameraLat,
                        restoredCenterLng = discoverState.mapCameraLng,
                        restoredZoom = discoverState.mapCameraZoom,
                        language = lang,
                        onEventClick = { postId -> onNavigate(AppRoute.Post(postId)) },
                        onPlaceClick = { key ->
                            val place = placeByKey[key]
                            if (place != null) {
                                val token = openLocationDestination(
                                    shared = shared,
                                    name = place.name,
                                    placeId = place.placeId,
                                    locationId = place.id?.takeIf { place.custom },
                                    lat = place.lat,
                                    lng = place.lng,
                                    initialPlace = place,
                                )
                                onNavigate(AppRoute.Location(token))
                            }
                        },
                        onMapClick = { lat, lng ->
                            newPlaceLat = lat
                            newPlaceLng = lng
                        },
                        onCameraChanged = { lat, lng, zoom ->
                            shared.discover.updateMapCamera(lat, lng, zoom)
                        },
                        onMapTouchChange = { mapTouched = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // .events-map-search-toggle
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .size(46.dp)
                            .shadow(6.dp, RoundedCornerShape(15.dp))
                            .clip(RoundedCornerShape(15.dp))
                            .background(tokens.surface)
                            .border(1.dp, tokens.line, RoundedCornerShape(15.dp))
                            .clickable {
                                shared.discover.searchPlaces("")
                                showPlaceSearch = true
                            },
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = Copy.text(lang, "searchPlaces"),
                            tint = tokens.text,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // .events-tabs (Events, Activities, Learn, People, Communities)
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp),
                ) {
                    val tabs = listOf(
                        DiscoverTab.EVENTS to Copy.text(lang, "events"),
                        DiscoverTab.ACTIVITIES to Copy.text(lang, "activities"),
                        DiscoverTab.LEARN to Copy.text(lang, "products"),
                        DiscoverTab.PEOPLE to Copy.text(lang, "people"),
                        DiscoverTab.COMMUNITIES to Copy.text(lang, "communities"),
                    )
                    tabs.forEach { (tab, label) ->
                        SaigonPillChip(
                            selected = selectedTab == tab,
                            onClick = { selectedTabName = tab.name },
                            label = label,
                        )
                    }
                }
            }

            if (selectedTab != DiscoverTab.LEARN && discoverState.isLoading && discoverState.events.isEmpty() && discoverState.locations.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        SaigonSpinner()
                    }
                }
            } else {
                when (selectedTab) {
                    DiscoverTab.EVENTS -> {
                        if (discoverState.events.isEmpty()) {
                            item {
                                Text(
                                    Copy.text(lang, "noEvents"),
                                    color = tokens.muted,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                )
                            }
                        } else {
                            val grouped = discoverState.events.groupBy { ev ->
                                ev.eventStartAt?.let { discoverEventDayHeading(it, lang) }.orEmpty()
                            }
                            grouped.forEach { (heading, dayEvents) ->
                                if (heading.isNotBlank()) {
                                    item(key = "day_$heading") {
                                        Text(
                                            text = heading,
                                            color = tokens.muted,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 4.dp),
                                        )
                                    }
                                }
                                items(dayEvents, key = { it.id }) { ev ->
                                    DiscoverEventRow(
                                        event = ev,
                                        language = lang,
                                        onClick = { onNavigate(AppRoute.Post(ev.id)) },
                                        modifier = Modifier.padding(horizontal = 18.dp),
                                    )
                                }
                            }
                        }
                    }

                    DiscoverTab.ACTIVITIES -> {
                        if (activityState.activities.isEmpty()) {
                            item {
                                Text(
                                    Copy.text(lang, "noActivities"),
                                    color = tokens.muted,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                )
                            }
                        } else {
                            items(activityState.activities, key = { it.id }) { act ->
                                ActivityCard(
                                    activity = act,
                                    language = lang,
                                    viewerId = sessionState.me?.id,
                                    showLocation = true,
                                    isMutating = activityState.isMutating,
                                    onOpen = { onNavigate(AppRoute.Activity(act.id)) },
                                    onToggleInterest = {
                                        if (sessionState.me == null) {
                                            onRequireAuth()
                                        } else {
                                            scope.launch { shared.activity.toggleInterest(act.id, act.interested) }
                                        }
                                    },
                                    onOpenLocation = {
                                        val token = openLocationDestination(
                                            shared = shared,
                                            name = act.locationName,
                                            placeId = act.placeId,
                                            locationId = act.locationId,
                                            lat = act.lat,
                                            lng = act.lng,
                                        )
                                        onNavigate(AppRoute.Location(token))
                                    },
                                    onOpenPhoto = { url -> zoomPhoto = url },
                                    modifier = Modifier.padding(horizontal = 18.dp),
                                )
                            }
                        }
                    }

                    DiscoverTab.LEARN -> {
                        item {
                            SaigonTextField(
                                value = learnQuery,
                                onValueChange = {
                                    learnQuery = it
                                    shared.shop.loadInitial(it)
                                },
                                placeholder = Copy.text(lang, "searchShopProducts"),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                            )
                        }
                        when {
                            shopState.isLoading && shopState.products.isEmpty() -> item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    SaigonSpinner()
                                }
                            }
                            shopState.products.isEmpty() -> item {
                                Text(
                                    Copy.text(lang, "noProducts"),
                                    color = tokens.muted,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                )
                            }
                            else -> {
                                items(shopState.products.chunked(2), key = { row -> row.first().id }) { rowProducts ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                                    ) {
                                        rowProducts.forEach { product ->
                                            LearnProductCard(
                                                product = product,
                                                onClick = { onNavigate(AppRoute.Product(product.id)) },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        if (rowProducts.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                                if (shopState.nextCursor != null) {
                                    item {
                                        SaigonTextButton(
                                            text = Copy.text(lang, "loadMore"),
                                            onClick = { shared.shop.loadMore() },
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    DiscoverTab.PEOPLE -> {
                        if (discoverState.people.isEmpty()) {
                            item {
                                Text(
                                    Copy.text(lang, "noMembers"),
                                    color = tokens.muted,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                )
                            }
                        } else {
                            items(discoverState.people, key = { it.id }) { person ->
                                SaigonCard(
                                    onClick = { onNavigate(AppRoute.User(person.id)) },
                                    borderWidth = 1.dp,
                                    cornerRadius = 14.dp,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        UserAvatar(photoUrl = person.photoUrl, name = person.name, size = 44.dp, borderWidth = 0.dp)
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                                            Text(person.name, color = tokens.text, fontWeight = FontWeight.ExtraBold)
                                            if (person.location.isNotBlank()) {
                                                Text(person.location, fontSize = 12.sp, color = tokens.muted)
                                            }
                                            if (person.intro.isNotBlank()) {
                                                Text(
                                                    person.intro,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontSize = 13.sp,
                                                    color = tokens.text,
                                                )
                                            }
                                        }
                                        val latestPhoto = person.latestPostPhotoUrl
                                        val latestId = person.latestPostId
                                        if (!latestPhoto.isNullOrBlank() && !latestId.isNullOrBlank()) {
                                            AsyncImage(
                                                model = latestPhoto,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .width(48.dp)
                                                    .aspectRatio(9f / 16f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(tokens.bg)
                                                    .clickable { onNavigate(AppRoute.Post(latestId)) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DiscoverTab.COMMUNITIES -> {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp),
                            ) {
                                SaigonTextField(
                                    value = communityQuery,
                                    onValueChange = {
                                        communityQuery = it
                                        shared.discover.searchCommunities(it)
                                    },
                                    placeholder = Copy.text(lang, "searchCommunities"),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                SaigonIconButton(
                                    onClick = { if (sessionState.me == null) onRequireAuth() else showCreateCommunity = true },
                                    overlay = false,
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = Copy.text(lang, "newCommunity"), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        if (discoverState.communities.isEmpty()) {
                            item {
                                Text(
                                    Copy.text(lang, "noCommunities"),
                                    color = tokens.muted,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                                )
                            }
                        } else {
                            items(discoverState.communities, key = { it.id }) { comm ->
                                SaigonCard(
                                    onClick = { onNavigate(AppRoute.Community(comm.id)) },
                                    borderWidth = 1.dp,
                                    cornerRadius = 14.dp,
                                    contentPadding = PaddingValues(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 18.dp),
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text(comm.emoji, fontSize = 24.sp)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(comm.name, color = tokens.text, fontWeight = FontWeight.ExtraBold)
                                                Text(
                                                    "${comm.memberCount} ${Copy.text(lang, "communityMembers")}",
                                                    fontSize = 12.sp,
                                                    color = tokens.muted,
                                                )
                                            }
                                            SaigonTinyPill(
                                                label = Copy.text(lang, if (comm.joined) "leaveCommunity" else "joinCommunity"),
                                                danger = comm.joined,
                                                onClick = {
                                                    if (sessionState.me == null) {
                                                        onRequireAuth()
                                                    } else {
                                                        scope.launch { shared.discover.toggleCommunityMembership(comm.id, comm.joined) }
                                                    }
                                                },
                                            )
                                        }
                                        if (comm.description.isNotBlank()) {
                                            LinkifiedText(
                                                text = comm.description,
                                                color = tokens.muted,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                        if (comm.latestPosts.isNotEmpty()) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            ) {
                                                comm.latestPosts.take(Limits.COMMUNITY_CARD_PHOTO_LIMIT).forEach { postPhoto ->
                                                    AsyncImage(
                                                        model = postPhoto.photoUrl,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .size(56.dp)
                                                            .clip(RoundedCornerShape(9.dp))
                                                            .background(tokens.surface)
                                                            .clickable { onNavigate(AppRoute.Post(postPhoto.id)) },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPlaceSearch) {
        var placeQuery by remember { mutableStateOf("") }
        val shownPlaces = remember(placeQuery, discoverState.searchRecentPlaces, discoverState.searchPlaces, discoverState.locations) {
            if (placeQuery.isBlank()) {
                (discoverState.searchRecentPlaces + discoverState.locations).distinctBy { it.id ?: it.placeId ?: it.name }
            } else {
                discoverState.searchPlaces
            }
        }
        SaigonDialog(onDismiss = { showPlaceSearch = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    Copy.text(lang, "searchPlaces"),
                    color = tokens.text,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(end = 36.dp),
                )
                SaigonTextField(
                    value = placeQuery,
                    onValueChange = {
                        placeQuery = it
                        shared.discover.searchPlaces(it)
                    },
                    placeholder = Copy.text(lang, "searchPlaces"),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 320.dp),
                ) {
                    if (shownPlaces.isEmpty() && placeQuery.isNotBlank()) {
                        item {
                            Text(Copy.text(lang, "noPlaceMatches"), color = tokens.muted, fontSize = 13.sp)
                        }
                    }
                    items(shownPlaces) { place ->
                        PlaceRowCard(
                            place = place,
                            onClick = {
                                showPlaceSearch = false
                                val token = openLocationDestination(
                                    shared = shared,
                                    name = place.name,
                                    placeId = place.placeId,
                                    locationId = place.id?.takeIf { place.custom },
                                    lat = place.lat,
                                    lng = place.lng,
                                    initialPlace = place,
                                )
                                onNavigate(AppRoute.Location(token))
                            },
                        )
                    }
                }
                SaigonOutlinedPillButton(
                    text = "+ ${Copy.text(lang, "createLocation")}",
                    onClick = {
                        showPlaceSearch = false
                        if (sessionState.me == null) onRequireAuth() else showAddPlace = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showPostSearch) {
        PostSearchSheet(
            shared = shared,
            language = lang,
            onOpenPost = { postId ->
                showPostSearch = false
                onNavigate(AppRoute.Post(postId))
            },
            onDismiss = { showPostSearch = false },
        )
    }

    if (showCreateCommunity) {
        var name by remember { mutableStateOf("") }
        var emoji by remember { mutableStateOf("") }
        var emojiManual by remember { mutableStateOf(false) }
        var suggestingEmoji by remember { mutableStateOf(false) }
        LaunchedEffect(name, emojiManual) {
            if (emojiManual) return@LaunchedEffect
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                suggestingEmoji = false
                return@LaunchedEffect
            }
            delay(2000)
            suggestingEmoji = true
            when (val res = shared.api.suggestCommunityEmoji(trimmed)) {
                is ApiResult.Success -> {
                    if (!emojiManual && name.trim() == trimmed && res.value.emoji.isNotBlank()) {
                        emoji = res.value.emoji
                    }
                }
                is ApiResult.Failure -> Unit
            }
            suggestingEmoji = false
        }
        SaigonDialog(onDismiss = { showCreateCommunity = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Copy.text(lang, "newCommunity"), color = tokens.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 36.dp))
                SaigonTextField(
                    value = emoji,
                    onValueChange = {
                        emoji = it.take(4)
                        emojiManual = emoji.isNotBlank()
                    },
                    label = Copy.text(lang, "communityEmoji"),
                    placeholder = if (suggestingEmoji) "…" else "✨",
                    singleLine = true,
                )
                SaigonTextField(value = name, onValueChange = { name = it }, label = Copy.text(lang, "communityName"))
                SaigonPrimaryButton(
                    text = Copy.text(lang, "createCommunity"),
                    enabled = name.isNotBlank(),
                    onClick = {
                        scope.launch {
                            if (shared.discover.createCommunity(name.trim(), emoji.trim()) != null) {
                                showCreateCommunity = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SaigonTextButton(text = Copy.text(lang, "cancel"), onClick = { showCreateCommunity = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (showAddPlace) {
        var placeName by remember { mutableStateOf("") }
        var addPlaceMapTouched by remember { mutableStateOf(false) }
        SaigonDialog(
            onDismiss = { showAddPlace = false },
            scrollEnabled = !addPlaceMapTouched,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Copy.text(lang, "createLocation"), color = tokens.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 36.dp))
                Text(Copy.text(lang, "tapMapToPlace"), fontSize = 12.sp, color = tokens.muted)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, tokens.line, RoundedCornerShape(16.dp)),
                ) {
                    SaigonMapView(
                        places = listOf(
                            SaigonMapPlacePin(
                                key = "draft",
                                name = placeName.ifBlank { Copy.text(lang, "location") },
                                lat = newPlaceLat,
                                lng = newPlaceLng,
                            ),
                        ),
                        mapsApiKey = sessionState.mapsKey,
                        centerLat = newPlaceLat,
                        centerLng = newPlaceLng,
                        initialZoom = 15f,
                        alwaysShowBubbles = true,
                        language = lang,
                        onMapClick = { lat, lng ->
                            newPlaceLat = lat
                            newPlaceLng = lng
                        },
                        onMapTouchChange = { addPlaceMapTouched = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                SaigonTextField(value = placeName, onValueChange = { placeName = it }, label = Copy.text(lang, "location"))
                SaigonPrimaryButton(
                    text = Copy.text(lang, "save"),
                    enabled = placeName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val created = shared.discover.createCustomPlace(placeName.trim(), newPlaceLat, newPlaceLng)
                            if (created != null) showAddPlace = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SaigonTextButton(text = Copy.text(lang, "cancel"), onClick = { showAddPlace = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    zoomPhoto?.let { PhotoViewerDialog(photoUrl = it, onDismiss = { zoomPhoto = null }) }

    activeCheckIn?.let { checkIn ->
        CheckInDetailDialog(
            checkIn = checkIn,
            shared = shared,
            language = lang,
            onNavigate = onNavigate,
            onRequireAuth = onRequireAuth,
            onChanged = { shared.discover.refreshCheckIns() },
            onDismiss = { activeCheckIn = null },
        )
    }
}

@Composable
private fun DiscoverEventRow(
    event: PostCardDto,
    language: Language,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalSaigonTokens.current
    SaigonCard(
        onClick = onClick,
        borderWidth = 1.dp,
        cornerRadius = 14.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = event.communityEmoji ?: "📅",
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                Text(
                    event.message.ifBlank { event.location },
                    color = tokens.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.ExtraBold,
                )
                val timePart = event.eventStartAt?.let { discoverEventClock(it) }
                val meta = listOfNotNull(timePart, event.location.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(meta, fontSize = 12.sp, color = tokens.muted)
                }
                event.eventPrice?.takeIf { it > 0L }?.let { price ->
                    Text(formatVnd(price), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tokens.accent)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    val shown = event.attendees.take(5)
                    if (shown.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
                            shown.forEach { person ->
                                UserAvatar(
                                    photoUrl = person.photoUrl,
                                    name = person.name,
                                    size = 20.dp,
                                    borderColor = tokens.surface,
                                    borderWidth = 1.dp,
                                )
                            }
                        }
                    }
                    val count = maxOf(event.attendeeCount, shown.size)
                    Text(
                        text = if (count > 0) "${Copy.text(language, "attendees")} · $count" else Copy.text(language, "attendees"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun PostSearchSheet(
    shared: TownSharedClient,
    language: Language,
    onOpenPost: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PostCardDto>>(emptyList()) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var requestId by remember { mutableIntStateOf(0) }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) delay(220)
        val token = ++requestId
        loading = true
        when (val res = shared.api.searchPosts(trimmed, limit = 24)) {
            is ApiResult.Success -> {
                if (token == requestId) {
                    results = res.value.posts
                    nextCursor = res.value.nextCursor
                }
            }
            is ApiResult.Failure -> Unit
        }
        if (token == requestId) loading = false
    }

    SaigonBottomSheet(
        title = Copy.text(language, "searchPosts"),
        onDismiss = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            SaigonTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = Copy.text(language, "searchPosts"),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                loading && results.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        SaigonSpinner(size = 24.dp)
                    }
                }
                results.isEmpty() -> {
                    Text(Copy.text(language, "noSearchResults"), color = tokens.muted, fontSize = 13.sp)
                }
                else -> {
                    val rows = remember(results) { results.chunked(3) }
                    fun triggerLoadMore() {
                        val c = nextCursor ?: return
                        if (loadingMore) return
                        val activeQuery = query.trim()
                        scope.launch {
                            loadingMore = true
                            when (val res = shared.api.searchPosts(activeQuery, cursor = c, limit = 24)) {
                                is ApiResult.Success -> {
                                    val seen = results.mapTo(HashSet()) { it.id }
                                    results = results + res.value.posts.filter { seen.add(it.id) }
                                    nextCursor = res.value.nextCursor
                                }
                                is ApiResult.Failure -> Unit
                            }
                            loadingMore = false
                        }
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp),
                    ) {
                        items(rows) { rowPosts ->
                            PostGridRow(
                                rowPosts = rowPosts,
                                showAuthor = true,
                                onPostClick = { onOpenPost(it.id) },
                            )
                        }
                        if (nextCursor != null) {
                            item {
                                LaunchedEffect(nextCursor, results.size) {
                                    triggerLoadMore()
                                }
                                if (loadingMore) {
                                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                        SaigonSpinner(size = 20.dp)
                                    }
                                } else {
                                    SaigonTextButton(
                                        text = Copy.text(language, "loadMore"),
                                        onClick = { triggerLoadMore() },
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
}

@Composable
private fun PlaceRowCard(place: PlaceSuggestionDto, onClick: () -> Unit) {
    val tokens = LocalSaigonTokens.current
    SaigonCard(
        onClick = onClick,
        borderWidth = 1.dp,
        cornerRadius = 14.dp,
        contentPadding = PaddingValues(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsyncImage(
                model = place.photoUrl ?: place.googlePhotoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SaigonColors.Card),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(place.name, color = tokens.text, fontWeight = FontWeight.ExtraBold)
                if (place.address.isNotBlank()) {
                    Text(place.address, fontSize = 12.sp, color = tokens.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (place.activityEmojis.isNotEmpty()) {
                Text(place.activityEmojis.joinToString(" "))
            }
        }
    }
}

@Composable
fun LocationDetailScreen(
    idOrToken: String,
    shared: TownSharedClient,
    onNavigate: (AppRoute) -> Unit,
    onRequireAuth: () -> Unit,
    onBack: () -> Unit,
) {
    val sessionState by shared.session.state.collectAsState()
    val discoverState by shared.discover.state.collectAsState()
    val activityState by shared.activity.state.collectAsState()
    val lang = sessionState.language
    val tokens = LocalSaigonTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val detail: LocationDetailState? = discoverState.locationDetail

    var editingDesc by remember { mutableStateOf(false) }
    var descInput by remember { mutableStateOf("") }
    var quickActivityDraft by remember { mutableStateOf("") }
    var showCreateActivity by remember { mutableStateOf(false) }
    var viewerPhotos by remember { mutableStateOf<List<UserPhotoDto>>(emptyList()) }
    var viewerInitialIndex by remember { mutableStateOf<Int?>(null) }
    var pendingDeletePhoto by remember { mutableStateOf<UserPhotoDto?>(null) }
    var deletingPhoto by remember { mutableStateOf(false) }
    var mapTouched by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(Limits.PHOTOS_MAX),
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = uris.mapNotNull { ImagePreparer.prepareSquareOrOriginal(context, it) }
            if (bytes.isNotEmpty()) shared.discover.uploadLocationPhotos(bytes)
        }
    }

    LaunchedEffect(idOrToken) {
        shared.discover.ensureLocationOpen(idOrToken)
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    SaigonPageShell(
        title = detail?.name?.ifBlank { null } ?: Copy.text(lang, "location"),
        onBack = {
            shared.discover.closeLocation()
            onBack()
        },
        backContentDescription = Copy.text(lang, "back"),
    ) {
        if (detail == null || (detail.isLoading && detail.name.isBlank())) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SaigonSpinner()
            }
        } else {
            val lat = detail.place?.lat
            val lng = detail.place?.lng
            val linkToken = detail.locationId?.takeIf { it.isNotBlank() }
                ?: detail.placeId?.takeIf { it.isNotBlank() }
                ?: encodeLocationToken(detail.name)

            LazyColumn(
                userScrollEnabled = !mapTouched,
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 28.dp + bottomInset),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // 1. Map (.location-sheet-map) with vertical margin
                if (lat != null && lng != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .height(200.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(tokens.surface)
                                .border(1.dp, tokens.line, RoundedCornerShape(18.dp)),
                        ) {
                            SaigonMapView(
                                places = listOf(
                                    SaigonMapPlacePin(
                                        key = linkToken,
                                        name = detail.name,
                                        lat = lat,
                                        lng = lng,
                                        postCount = detail.postsCount,
                                        activityCount = detail.activities.size.toLong(),
                                        activityEmojis = detail.activities.map { it.emoji }.distinct(),
                                        hasActivities = detail.activities.isNotEmpty(),
                                    ),
                                ),
                                mapsApiKey = sessionState.mapsKey,
                                centerLat = lat,
                                centerLng = lng,
                                initialZoom = 15.5f,
                                alwaysShowBubbles = true,
                                language = lang,
                                onMapTouchChange = { mapTouched = it },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                // 2. Directions + Copy Link action row (.location-sheet-actions)
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SaigonPrimaryButton(
                            text = Copy.text(lang, "directions"),
                            onClick = { AndroidIntents.openDirections(context, detail.name, lat, lng) },
                            modifier = Modifier.weight(1f),
                        )
                        SaigonIconButton(
                            onClick = { AndroidIntents.copyPathLink(context, "/l/$linkToken", lang) },
                            overlay = false,
                        ) {
                            Icon(Icons.Default.Link, contentDescription = Copy.text(lang, "copyLink"), modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // 3. Details / Description section
                item {
                    SaigonCard(borderWidth = 1.dp, cornerRadius = 16.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    Copy.text(lang, "details"),
                                    color = tokens.text,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.weight(1f),
                                )
                                SaigonTextButton(
                                    text = Copy.text(lang, if (detail.description.isBlank()) "addDescription" else "edit"),
                                    accent = true,
                                    onClick = {
                                        if (sessionState.me == null) onRequireAuth()
                                        else {
                                            descInput = detail.description
                                            editingDesc = true
                                        }
                                    },
                                )
                            }
                            if (detail.description.isNotBlank()) {
                                LinkifiedText(text = detail.description, color = tokens.muted)
                            } else {
                                Text(Copy.text(lang, "locationDescriptionHint"), fontSize = 14.sp, color = tokens.muted)
                            }
                        }
                    }
                }

                // 4. Photos (.user-photos-row)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(Copy.text(lang, "photos"), color = tokens.text, style = MaterialTheme.typography.titleMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(82.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .dashedBorder(tokens.line, strokeWidth = 2.dp, cornerRadius = 14.dp)
                                    .clickable {
                                        if (sessionState.me == null) onRequireAuth()
                                        else photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = Copy.text(lang, "addPhoto"), tint = tokens.muted, modifier = Modifier.size(28.dp))
                            }
                            detail.photos.forEachIndexed { idx, photo ->
                                Box(modifier = Modifier.size(82.dp)) {
                                    AsyncImage(
                                        model = photo.url,
                                        contentDescription = Copy.text(lang, "photo"),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(14.dp))
                                            .border(1.dp, tokens.line, RoundedCornerShape(14.dp))
                                            .clickable {
                                                viewerPhotos = detail.photos
                                                viewerInitialIndex = idx
                                            },
                                    )
                                    if (photo.canDelete) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.68f))
                                                .clickable { pendingDeletePhoto = photo },
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = Copy.text(lang, "deletePhotoAsk"), tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Activities (.activity-list + .activity-create-composer)
                item {
                    Text(Copy.text(lang, "activities"), color = tokens.text, style = MaterialTheme.typography.titleMedium)
                }
                items(detail.activities, key = { it.id }) { act ->
                    ActivityCard(
                        activity = act,
                        language = lang,
                        viewerId = sessionState.me?.id,
                        showLocation = false,
                        isMutating = activityState.isMutating,
                        onOpen = { onNavigate(AppRoute.Activity(act.id)) },
                        onToggleInterest = {
                            if (sessionState.me == null) {
                                onRequireAuth()
                            } else {
                                scope.launch {
                                    if (shared.activity.toggleInterest(act.id, act.interested) != null) {
                                        shared.discover.openLocation(detail.name, detail.placeId, detail.locationId, detail.place)
                                    }
                                }
                            }
                        },
                        onOpenPhoto = { url ->
                            viewerPhotos = act.photoUrls.mapIndexed { i, u -> UserPhotoDto(id = "${act.id}_$i", url = u, canDelete = false) }
                            viewerInitialIndex = act.photoUrls.indexOf(url).coerceAtLeast(0)
                        },
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SaigonTextField(
                            value = quickActivityDraft,
                            onValueChange = { if (it.length <= Limits.ACTIVITY_NAME_MAX) quickActivityDraft = it },
                            placeholder = Copy.text(lang, "whatDoPeopleDoHere"),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (quickActivityDraft.isNotBlank()) {
                            SaigonPrimaryButton(
                                text = Copy.text(lang, "createActivity"),
                                onClick = {
                                    if (sessionState.me == null) onRequireAuth() else showCreateActivity = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // 6. Posts (.location-sheet-count + .location-sheet-grid)
                if (detail.posts.isNotEmpty()) {
                    item {
                        val count = maxOf(detail.postsCount, detail.posts.size.toLong())
                        Text(
                            Copy.text(lang, "postCount", mapOf("count" to count.toString())),
                            color = tokens.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    item {
                        PostGridRows(
                            posts = detail.posts,
                            showAuthor = true,
                            onPostClick = { onNavigate(AppRoute.Post(it.id)) },
                        )
                    }
                    if (detail.postsNextCursor != null) {
                        item {
                            SaigonTextButton(
                                text = Copy.text(lang, "loadMore"),
                                onClick = { shared.discover.loadMoreLocationPosts() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (editingDesc) {
        SaigonDialog(onDismiss = { editingDesc = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Copy.text(lang, "locationDescription"), color = tokens.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 36.dp))
                SaigonTextField(
                    value = descInput,
                    onValueChange = { if (it.length <= Limits.LOCATION_DESCRIPTION_MAX) descInput = it },
                    singleLine = false,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                SaigonPrimaryButton(
                    text = Copy.text(lang, "save"),
                    onClick = {
                        scope.launch {
                            if (shared.discover.updateLocationDescription(descInput.trim())) editingDesc = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SaigonTextButton(text = Copy.text(lang, "cancel"), onClick = { editingDesc = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (showCreateActivity && detail != null) {
        ActivityEditorDialog(
            existing = null,
            locationName = detail.name,
            initialName = quickActivityDraft.trim(),
            locationId = detail.locationId,
            placeId = detail.placeId,
            lat = detail.place?.lat,
            lng = detail.place?.lng,
            shared = shared,
            language = lang,
            onSaved = {
                quickActivityDraft = ""
                showCreateActivity = false
                shared.discover.openLocation(detail.name, detail.placeId, detail.locationId, detail.place)
            },
            onDismiss = { showCreateActivity = false },
        )
    }

    pendingDeletePhoto?.let { photo ->
        SaigonConfirmDialog(
            title = Copy.text(lang, "deletePhotoAsk"),
            hint = Copy.text(lang, "deletePhotoHint"),
            confirmLabel = Copy.text(lang, "delete"),
            cancelLabel = Copy.text(lang, "cancel"),
            loading = deletingPhoto,
            onConfirm = {
                scope.launch {
                    deletingPhoto = true
                    shared.discover.deleteLocationPhoto(photo.id)
                    deletingPhoto = false
                    pendingDeletePhoto = null
                }
            },
            onDismiss = { pendingDeletePhoto = null },
        )
    }

    viewerInitialIndex?.let { idx ->
        val activePhotos = detail?.photos?.takeIf { viewerPhotos.isNotEmpty() && viewerPhotos.firstOrNull()?.canDelete == true } ?: viewerPhotos
        if (activePhotos.isNotEmpty()) {
            PhotoViewerDialog(
                photos = activePhotos,
                initialIndex = idx,
                language = lang,
                onDeletePhoto = if (activePhotos.any { it.canDelete }) {
                    { photo -> shared.discover.deleteLocationPhoto(photo.id) }
                } else {
                    null
                },
                onDismiss = {
                    viewerInitialIndex = null
                    viewerPhotos = emptyList()
                },
            )
        }
    }
}

private val discoverTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")

private fun discoverEventDayHeading(millis: Long, language: Language): String {
    val locale = if (language == Language.VI) Locale.forLanguageTag("vi-VN") else Locale.ENGLISH
    val dayFmt = SimpleDateFormat("yyyy-MM-dd", locale).apply { timeZone = discoverTimeZone }
    val dateFmt = SimpleDateFormat("EEE, MMM d", locale).apply { timeZone = discoverTimeZone }
    val now = currentTimeMillis()
    val oneDay = 24 * 60 * 60 * 1000L
    return when (dayFmt.format(Date(millis))) {
        dayFmt.format(Date(now)) -> Copy.text(language, "today")
        dayFmt.format(Date(now + oneDay)) -> Copy.text(language, "tomorrow")
        else -> dateFmt.format(Date(millis))
    }
}

private fun discoverEventClock(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.ENGLISH).apply { timeZone = discoverTimeZone }.format(Date(millis))
