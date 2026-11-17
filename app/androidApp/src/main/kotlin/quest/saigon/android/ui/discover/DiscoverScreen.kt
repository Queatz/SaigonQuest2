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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import quest.saigon.AppRoute
import quest.saigon.Copy
import quest.saigon.Limits
import quest.saigon.android.BuildConfig
import quest.saigon.android.media.ImagePreparer
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.theme.SaigonColors
import quest.saigon.android.ui.components.AndroidIntents
import quest.saigon.android.ui.components.LinkifiedText
import quest.saigon.android.ui.components.PhotoViewerDialog
import quest.saigon.android.ui.components.SaigonCard
import quest.saigon.android.ui.components.SaigonDialog
import quest.saigon.android.ui.components.SaigonIconButton
import quest.saigon.android.ui.components.SaigonOutlinedPillButton
import quest.saigon.android.ui.components.SaigonPageShell
import quest.saigon.android.ui.components.SaigonPillChip
import quest.saigon.android.ui.components.SaigonPrimaryButton
import quest.saigon.android.ui.components.SaigonSpinner
import quest.saigon.android.ui.components.SaigonTextButton
import quest.saigon.android.ui.components.SaigonTextField
import quest.saigon.android.ui.components.SaigonTinyPill
import quest.saigon.android.ui.components.UserAvatar
import quest.saigon.android.ui.components.dashedBorder
import quest.saigon.decodeLocationToken
import quest.saigon.encodeLocationToken
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.CreateActivityRequest
import quest.saigon.shared.model.PlaceSuggestionDto
import quest.saigon.shared.store.ActivityStore
import quest.saigon.shared.store.LocationDetailState

private enum class DiscoverTab {
    EVENTS,
    PLACES,
    ACTIVITIES,
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
    val lang = sessionState.language
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(DiscoverTab.EVENTS) }
    var placeQuery by remember { mutableStateOf("") }
    var communityQuery by remember { mutableStateOf("") }
    var showCreateCommunity by remember { mutableStateOf(false) }
    var showAddPlace by remember { mutableStateOf(false) }
    var newPlaceLatLng by remember { mutableStateOf(LatLng(10.7769, 106.7009)) }

    LaunchedEffect(Unit) {
        shared.discover.loadAll()
        shared.activity.loadActivities()
    }

    SaigonPageShell(
        title = Copy.text(lang, "discover"),
        onBack = onBack,
        backContentDescription = Copy.text(lang, "back"),
        topActions = {
            if (selectedTab == DiscoverTab.COMMUNITIES) {
                SaigonIconButton(
                    onClick = { if (sessionState.me == null) onRequireAuth() else showCreateCommunity = true },
                    overlay = false,
                ) {
                    Icon(Icons.Default.Add, contentDescription = Copy.text(lang, "newCommunity"), modifier = Modifier.size(20.dp))
                }
            } else if (selectedTab == DiscoverTab.PLACES) {
                SaigonIconButton(
                    onClick = { if (sessionState.me == null) onRequireAuth() else showAddPlace = true },
                    overlay = false,
                ) {
                    Icon(Icons.Default.Add, contentDescription = Copy.text(lang, "createLocation"), modifier = Modifier.size(20.dp))
                }
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (BuildConfig.MAPS_CONFIGURED) {
                val cameraState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(LatLng(10.7769, 106.7009), 12.5f)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .height(230.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(tokens.surface)
                        .border(1.dp, tokens.line, RoundedCornerShape(24.dp)),
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraState,
                        onMapClick = { latLng ->
                            newPlaceLatLng = latLng
                        },
                    ) {
                        discoverState.locations.forEach { place ->
                            val lat = place.lat
                            val lng = place.lng
                            if (lat != null && lng != null) {
                                Marker(
                                    state = MarkerState(position = LatLng(lat, lng)),
                                    title = place.name,
                                    snippet = place.address,
                                    onInfoWindowClick = {
                                        val token = if (place.custom && !place.id.isNullOrBlank()) {
                                            place.id!!
                                        } else {
                                            encodeLocationToken(place.name)
                                        }
                                        onNavigate(AppRoute.Location(token))
                                    },
                                )
                            }
                        }
                        discoverState.events.forEach { ev ->
                            val lat = ev.lat
                            val lng = ev.lng
                            if (lat != null && lng != null) {
                                Marker(
                                    state = MarkerState(position = LatLng(lat, lng)),
                                    title = ev.authorName,
                                    snippet = ev.location,
                                    onInfoWindowClick = { onNavigate(AppRoute.Post(ev.id)) },
                                )
                            }
                        }
                    }
                }
            }

            // .events-tabs
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                val tabs = listOf(
                    DiscoverTab.EVENTS to Copy.text(lang, "events"),
                    DiscoverTab.PLACES to Copy.text(lang, "location"),
                    DiscoverTab.ACTIVITIES to Copy.text(lang, "activities"),
                    DiscoverTab.PEOPLE to Copy.text(lang, "people"),
                    DiscoverTab.COMMUNITIES to Copy.text(lang, "communities"),
                )
                tabs.forEach { (tab, label) ->
                    SaigonPillChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = label,
                    )
                }
            }

            if (discoverState.isLoading && discoverState.events.isEmpty() && discoverState.locations.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SaigonSpinner()
                }
            } else {
                when (selectedTab) {
                    DiscoverTab.EVENTS -> {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            item {
                                val summary = discoverState.summary
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SaigonTinyPill(label = "${Copy.text(lang, "today")}: ${summary.today}", selected = summary.today > 0)
                                    SaigonTinyPill(label = "${Copy.text(lang, "tomorrow")}: ${summary.tomorrow}")
                                    SaigonTinyPill(label = "7d: ${summary.week}")
                                }
                            }
                            if (discoverState.events.isEmpty()) {
                                item { Text(Copy.text(lang, "noEvents"), color = tokens.muted) }
                            } else {
                                items(discoverState.events, key = { it.id }) { ev ->
                                    SaigonCard(
                                        onClick = { onNavigate(AppRoute.Post(ev.id)) },
                                        borderWidth = 1.dp,
                                        cornerRadius = 14.dp,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            AsyncImage(
                                                model = ev.photoUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(tokens.bg),
                                            )
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                                                Text(ev.message, color = tokens.text, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                                Text(ev.location, fontSize = 12.sp, color = tokens.muted)
                                                Text("${ev.attendeeCount} ${Copy.text(lang, "going")}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = tokens.accent)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DiscoverTab.PLACES -> {
                        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
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
                            Spacer(Modifier.height(10.dp))
                            val shownPlaces = remember(placeQuery, discoverState.locations, discoverState.searchPlaces) {
                                if (placeQuery.isBlank()) discoverState.locations else discoverState.searchPlaces
                            }
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 24.dp),
                            ) {
                                items(shownPlaces) { place ->
                                    PlaceRowCard(place = place, onClick = {
                                        val token = if (place.custom && !place.id.isNullOrBlank()) {
                                            place.id!!
                                        } else {
                                            encodeLocationToken(place.name)
                                        }
                                        onNavigate(AppRoute.Location(token))
                                    })
                                }
                            }
                        }
                    }

                    DiscoverTab.ACTIVITIES -> {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (activityState.activities.isEmpty()) {
                                item { Text(Copy.text(lang, "noActivities"), color = tokens.muted) }
                            } else {
                                items(activityState.activities, key = { it.id }) { act ->
                                    SaigonCard(
                                        onClick = { onNavigate(AppRoute.Activity(act.id)) },
                                        borderWidth = 2.dp,
                                        cornerRadius = 16.dp,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text(act.emoji.ifBlank { "✨" }, fontSize = 28.sp)
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                                                Text(ActivityStore.localizedName(act, lang), color = tokens.text, fontWeight = FontWeight.ExtraBold)
                                                Text(act.locationName, fontSize = 13.sp, color = tokens.muted)
                                            }
                                            SaigonTinyPill(
                                                label = "${act.interestedCount}",
                                                selected = act.interested,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DiscoverTab.PEOPLE -> {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(discoverState.people, key = { it.id }) { person ->
                                SaigonCard(
                                    onClick = { onNavigate(AppRoute.User(person.id)) },
                                    borderWidth = 1.dp,
                                    cornerRadius = 14.dp,
                                    modifier = Modifier.fillMaxWidth(),
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
                                                Text(person.intro, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = tokens.text)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DiscoverTab.COMMUNITIES -> {
                        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                            SaigonTextField(
                                value = communityQuery,
                                onValueChange = {
                                    communityQuery = it
                                    shared.discover.searchCommunities(it)
                                },
                                placeholder = Copy.text(lang, "searchCommunities"),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(10.dp))
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 24.dp),
                            ) {
                                items(discoverState.communities, key = { it.id }) { comm ->
                                    SaigonCard(
                                        onClick = { onNavigate(AppRoute.Community(comm.id)) },
                                        borderWidth = 1.dp,
                                        cornerRadius = 14.dp,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text(comm.emoji, fontSize = 26.sp)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(comm.name, color = tokens.text, fontWeight = FontWeight.ExtraBold)
                                                Text(
                                                    "${comm.memberCount} ${Copy.text(lang, "communityMembers")}",
                                                    fontSize = 12.sp,
                                                    color = tokens.muted,
                                                )
                                            }
                                            SaigonOutlinedPillButton(
                                                text = Copy.text(lang, if (comm.joined) "leaveCommunity" else "joinCommunity"),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                onClick = {
                                                    if (sessionState.me == null) {
                                                        onRequireAuth()
                                                    } else {
                                                        scope.launch { shared.discover.toggleCommunityMembership(comm.id, comm.joined) }
                                                    }
                                                },
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

    if (showCreateCommunity) {
        var name by remember { mutableStateOf("") }
        var emoji by remember { mutableStateOf("✨") }
        SaigonDialog(onDismiss = { showCreateCommunity = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Copy.text(lang, "newCommunity"), color = tokens.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 36.dp))
                SaigonTextField(value = emoji, onValueChange = { emoji = it.take(4) }, label = Copy.text(lang, "communityEmoji"))
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
        SaigonDialog(onDismiss = { showAddPlace = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Copy.text(lang, "createLocation"), color = tokens.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 36.dp))
                Text("${newPlaceLatLng.latitude}, ${newPlaceLatLng.longitude}", fontSize = 12.sp, color = tokens.muted)
                SaigonTextField(value = placeName, onValueChange = { placeName = it }, label = Copy.text(lang, "location"))
                SaigonPrimaryButton(
                    text = Copy.text(lang, "save"),
                    enabled = placeName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val created = shared.discover.createCustomPlace(placeName.trim(), newPlaceLatLng.latitude, newPlaceLatLng.longitude)
                            if (created != null) showAddPlace = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SaigonTextButton(text = Copy.text(lang, "cancel"), onClick = { showAddPlace = false }, modifier = Modifier.fillMaxWidth())
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
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SaigonColors.Card),
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = tokens.accent)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(place.name, color = tokens.text, fontWeight = FontWeight.ExtraBold)
                if (place.address.isNotBlank()) {
                    Text(place.address, fontSize = 12.sp, color = tokens.muted)
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
    val lang = sessionState.language
    val tokens = LocalSaigonTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val detail: LocationDetailState? = discoverState.locationDetail

    var editingDesc by remember { mutableStateOf(false) }
    var descInput by remember { mutableStateOf("") }
    var showCreateActivity by remember { mutableStateOf(false) }
    var zoomPhoto by remember { mutableStateOf<String?>(null) }

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
        val decoded = decodeLocationToken(idOrToken)
        if (decoded != null) {
            shared.discover.openLocation(name = decoded)
        } else {
            shared.discover.openLocationByIdOrToken(idOrToken)
        }
    }

    SaigonPageShell(
        title = detail?.name?.ifBlank { null } ?: Copy.text(lang, "location"),
        onBack = {
            shared.discover.closeLocation()
            onBack()
        },
        backContentDescription = Copy.text(lang, "back"),
        topActions = {
            detail?.let { d ->
                SaigonIconButton(
                    onClick = { AndroidIntents.openDirections(context, d.name, d.place?.lat, d.place?.lng) },
                    overlay = false,
                ) {
                    Icon(Icons.Default.Directions, contentDescription = Copy.text(lang, "directions"), modifier = Modifier.size(20.dp))
                }
            }
        },
    ) {
        if (detail == null || (detail.isLoading && detail.posts.isEmpty() && detail.description.isBlank())) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SaigonSpinner()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                item {
                    SaigonCard(borderWidth = 1.dp, cornerRadius = 16.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(Copy.text(lang, "locationDescription"), color = tokens.text, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        if (sessionState.me == null) onRequireAuth()
                                        else {
                                            descInput = detail.description
                                            editingDesc = true
                                        }
                                    },
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = Copy.text(lang, "edit"), tint = tokens.accent)
                                }
                            }
                            if (detail.description.isNotBlank()) {
                                LinkifiedText(text = detail.description, color = tokens.muted)
                            } else {
                                Text(Copy.text(lang, "locationDescriptionHint"), fontSize = 14.sp, color = tokens.muted)
                            }
                        }
                    }
                }

                // .user-photos-row
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
                            detail.photos.forEach { photo ->
                                Box(modifier = Modifier.size(82.dp)) {
                                    AsyncImage(
                                        model = photo.url,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(14.dp))
                                            .border(1.dp, tokens.line, RoundedCornerShape(14.dp))
                                            .clickable { zoomPhoto = photo.url },
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
                                                .clickable { scope.launch { shared.discover.deleteLocationPhoto(photo.id) } },
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(Copy.text(lang, "activities"), color = tokens.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        SaigonTextButton(
                            text = "+ ${Copy.text(lang, "createActivity")}",
                            accent = true,
                            onClick = { if (sessionState.me == null) onRequireAuth() else showCreateActivity = true },
                        )
                    }
                }
                items(detail.activities, key = { it.id }) { act ->
                    SaigonCard(
                        onClick = { onNavigate(AppRoute.Activity(act.id)) },
                        borderWidth = 2.dp,
                        cornerRadius = 16.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(act.emoji, fontSize = 26.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ActivityStore.localizedName(act, lang), color = tokens.text, fontWeight = FontWeight.ExtraBold)
                                Text("${act.interestedCount}", fontSize = 12.sp, color = tokens.muted)
                            }
                        }
                    }
                }

                // .location-sheet-grid (3-column 9:16 photo tiles with bottom dark gradient)
                if (detail.posts.isNotEmpty()) {
                    item {
                        Text(
                            "${detail.posts.size} ${Copy.text(lang, "yourPosts")}",
                            color = tokens.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    val rows = detail.posts.chunked(3)
                    items(rows) { rowPosts ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            rowPosts.forEach { post ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(9f / 16f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(tokens.line)
                                        .clickable { onNavigate(AppRoute.Post(post.id)) },
                                ) {
                                    AsyncImage(
                                        model = post.photoUrl,
                                        contentDescription = post.message,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .fillMaxHeight(0.46f)
                                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB814100C)))),
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
                                                post.message,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = 14.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            UserAvatar(
                                                photoUrl = post.authorPhotoUrl,
                                                name = post.authorName,
                                                size = 20.dp,
                                                borderColor = Color.White.copy(alpha = 0.85f),
                                                borderWidth = 1.dp,
                                            )
                                            Text(
                                                post.authorName,
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
                            repeat(3 - rowPosts.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
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
        var name by remember { mutableStateOf("") }
        var emoji by remember { mutableStateOf("✨") }
        var description by remember { mutableStateOf("") }
        SaigonDialog(onDismiss = { showCreateActivity = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Copy.text(lang, "createActivity"), color = tokens.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 36.dp))
                SaigonTextField(value = emoji, onValueChange = { emoji = it.take(4) }, label = Copy.text(lang, "activityEmoji"))
                SaigonTextField(
                    value = name,
                    onValueChange = { if (it.length <= Limits.ACTIVITY_NAME_MAX) name = it },
                    label = Copy.text(lang, "activityName"),
                )
                SaigonTextField(
                    value = description,
                    onValueChange = { if (it.length <= Limits.ACTIVITY_DESCRIPTION_MAX) description = it },
                    label = Copy.text(lang, "activityDescription"),
                    singleLine = false,
                    minLines = 2,
                )
                SaigonPrimaryButton(
                    text = Copy.text(lang, "save"),
                    enabled = name.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val created = shared.activity.createActivity(
                                CreateActivityRequest(
                                    emoji = emoji.trim(),
                                    name = name.trim(),
                                    description = description.trim(),
                                    locationName = detail.name,
                                    locationId = detail.locationId,
                                    placeId = detail.placeId,
                                    lat = detail.place?.lat,
                                    lng = detail.place?.lng,
                                ),
                            )
                            if (created != null) {
                                showCreateActivity = false
                                shared.discover.openLocation(detail.name, detail.placeId, detail.locationId, detail.place)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SaigonTextButton(text = Copy.text(lang, "cancel"), onClick = { showCreateActivity = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    zoomPhoto?.let { PhotoViewerDialog(photoUrl = it, onDismiss = { zoomPhoto = null }) }
}
