package quest.saigon.android.ui.activities

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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import quest.saigon.AppRoute
import quest.saigon.Copy
import quest.saigon.Limits
import quest.saigon.android.media.ImagePreparer
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.ui.components.ActivityEditorDialog
import quest.saigon.android.ui.components.AndroidIntents
import quest.saigon.android.ui.components.LinkifiedText
import quest.saigon.android.ui.components.PhotoViewerDialog
import quest.saigon.android.ui.components.SaigonCard
import quest.saigon.android.ui.components.SaigonConfirmDialog
import quest.saigon.android.ui.components.SaigonIconButton
import quest.saigon.android.ui.components.SaigonMapPlacePin
import quest.saigon.android.ui.components.SaigonMapView
import quest.saigon.android.ui.components.SaigonPageShell
import quest.saigon.android.ui.components.SaigonPrimaryButton
import quest.saigon.android.ui.components.SaigonSecondaryButton
import quest.saigon.android.ui.components.SaigonSpinner
import quest.saigon.android.ui.components.SaigonTinyPill
import quest.saigon.android.ui.components.UserAvatar
import quest.saigon.android.ui.components.dashedBorder
import quest.saigon.android.ui.components.formatScheduleMinutes
import quest.saigon.android.ui.components.openLocationDestination
import quest.saigon.android.ui.components.weekdayCopyKey
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.UserPhotoDto
import quest.saigon.shared.store.ActivityStore

@Composable
fun ActivityDetailScreen(
    activityId: String,
    shared: TownSharedClient,
    onNavigate: (AppRoute) -> Unit,
    onRequireAuth: () -> Unit,
    onBack: () -> Unit,
) {
    val sessionState by shared.session.state.collectAsState()
    val activityState by shared.activity.state.collectAsState()
    val lang = sessionState.language
    val tokens = LocalSaigonTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showEdit by remember { mutableStateOf(false) }
    var confirmDeleteActivity by remember { mutableStateOf(false) }
    var pendingDeletePhoto by remember { mutableStateOf<UserPhotoDto?>(null) }
    var viewerInitialIndex by remember { mutableStateOf<Int?>(null) }
    var selectedChatIds by remember(activityId) { mutableStateOf<Set<String>>(emptySet()) }
    var mapTouched by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(Limits.PHOTOS_MAX),
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = uris.mapNotNull { ImagePreparer.prepareSquareOrOriginal(context, it) }
            if (bytes.isNotEmpty()) shared.activity.uploadPhotos(activityId, bytes)
        }
    }

    LaunchedEffect(activityId) {
        shared.activity.openActivity(activityId)
    }

    val act = activityState.selectedActivity?.takeIf { it.id == activityId }
    val isCreator = act != null && sessionState.me?.id == act.createdBy

    // Pre-select host if present and not self (matches web ActivitiesUi.kt)
    LaunchedEffect(act?.id, act?.hostUserId, act?.interestedPeople, sessionState.me?.id) {
        val a = act ?: return@LaunchedEffect
        val viewer = sessionState.me?.id
        val hostId = a.hostUserId
        if (!hostId.isNullOrBlank() && hostId != viewer && a.interestedPeople.any { it.id == hostId || it.host }) {
            selectedChatIds = selectedChatIds + hostId
        }
    }

    SaigonPageShell(
        title = act?.let { "${it.emoji} ${ActivityStore.localizedName(it, lang)}" } ?: Copy.text(lang, "activityAttachment"),
        onBack = {
            shared.activity.closeActivity()
            onBack()
        },
        backContentDescription = Copy.text(lang, "back"),
        topActions = {
            if (act != null) {
                SaigonIconButton(
                    onClick = { AndroidIntents.copyPathLink(context, "/activity/${act.id}", lang) },
                    overlay = false,
                ) {
                    Icon(Icons.Default.Link, contentDescription = Copy.text(lang, "copyLink"), modifier = Modifier.size(18.dp))
                }
            }
            if (isCreator) {
                SaigonIconButton(onClick = { showEdit = true }, overlay = false) {
                    Icon(Icons.Default.Edit, contentDescription = Copy.text(lang, "edit"), modifier = Modifier.size(18.dp))
                }
                SaigonIconButton(
                    onClick = { confirmDeleteActivity = true },
                    overlay = false,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = Copy.text(lang, "delete"), tint = tokens.danger, modifier = Modifier.size(18.dp))
                }
            }
        },
        bottomBar = if (act != null) {
            {
                val viewer = sessionState.me?.id
                val hostIds = act.interestedPeople
                    .filter { it.host || (!act.hostUserId.isNullOrBlank() && it.id == act.hostUserId) }
                    .map { it.id }
                    .toSet()
                val effectiveIds = (selectedChatIds + hostIds).filter { it.isNotBlank() && it != viewer }
                val chatCount = effectiveIds.size
                val chatLabel = if (chatCount <= 1) {
                    Copy.text(lang, "openChat")
                } else {
                    Copy.text(lang, "startGroupChat")
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tokens.surface),
                ) {
                    HorizontalDivider(color = tokens.line, thickness = 1.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        SaigonPrimaryButton(
                            text = chatLabel,
                            enabled = effectiveIds.isNotEmpty() && !activityState.isMutating,
                            loading = activityState.isMutating,
                            onClick = {
                                if (sessionState.me == null) {
                                    onRequireAuth()
                                } else {
                                    scope.launch {
                                        shared.activity.startActivityChat(act.id, effectiveIds)?.let { chat ->
                                            onNavigate(AppRoute.Chat(chat.id))
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        } else {
            null
        },
    ) {
        if (act == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SaigonSpinner()
            }
        } else {
            LazyColumn(
                userScrollEnabled = !mapTouched,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Header + Description
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "${act.emoji} ${ActivityStore.localizedName(act, lang)}",
                            color = tokens.text,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        val desc = ActivityStore.localizedDescription(act, lang)
                        if (desc.isNotBlank()) {
                            LinkifiedText(text = desc, color = tokens.muted, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                // Photos gallery (.user-photos-row)
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
                            activityState.photos.forEachIndexed { idx, photo ->
                                Box(modifier = Modifier.size(82.dp)) {
                                    AsyncImage(
                                        model = photo.url,
                                        contentDescription = Copy.text(lang, "photo"),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(14.dp))
                                            .border(1.dp, tokens.line, RoundedCornerShape(14.dp))
                                            .clickable { viewerInitialIndex = idx },
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

                // Location + Map + Directions (.activity-location-section)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(Copy.text(lang, "location"), color = tokens.text, style = MaterialTheme.typography.titleMedium)
                        val lat = act.lat
                        val lng = act.lng
                        fun openLocation() {
                            val token = openLocationDestination(
                                shared = shared,
                                name = act.locationName,
                                placeId = act.placeId,
                                locationId = act.locationId,
                                lat = lat,
                                lng = lng,
                            )
                            onNavigate(AppRoute.Location(token))
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (lat != null && lng != null) {
                                Box(
                                    modifier = Modifier
                                        .size(160.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(tokens.surface)
                                        .border(1.dp, tokens.line, RoundedCornerShape(14.dp)),
                                ) {
                                    SaigonMapView(
                                        places = listOf(
                                            SaigonMapPlacePin(
                                                key = act.locationId ?: act.placeId ?: act.locationName,
                                                name = act.locationName,
                                                lat = lat,
                                                lng = lng,
                                                activityEmojis = listOf(act.emoji),
                                                hasActivities = true,
                                            ),
                                        ),
                                        mapsApiKey = sessionState.mapsKey,
                                        centerLat = lat,
                                        centerLng = lng,
                                        initialZoom = 15.5f,
                                        alwaysShowBubbles = true,
                                        language = lang,
                                        onPlaceClick = { openLocation() },
                                        onMapTouchChange = { mapTouched = it },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = "📍 ${act.locationName}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = tokens.text,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(onClick = ::openLocation)
                                        .padding(vertical = 2.dp),
                                )
                                SaigonPrimaryButton(
                                    text = Copy.text(lang, "directions"),
                                    onClick = { AndroidIntents.openDirections(context, act.locationName, lat, lng) },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                // Schedule (.activity-schedule-table)
                item {
                    SaigonCard(borderWidth = 1.dp, cornerRadius = 14.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(Copy.text(lang, "activitySchedule"), color = tokens.muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            if (act.schedule.isEmpty()) {
                                Text(Copy.text(lang, "alwaysAvailable"), color = tokens.muted, fontSize = 14.sp)
                            } else {
                                act.schedule.forEachIndexed { idx, slot ->
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    ) {
                                        Text(Copy.text(lang, weekdayCopyKey(slot.dayOfWeek)), color = tokens.text, fontWeight = FontWeight.Bold)
                                        Text("${formatScheduleMinutes(slot.startMinutes)} – ${formatScheduleMinutes(slot.endMinutes)}", color = tokens.muted)
                                    }
                                    if (idx < act.schedule.lastIndex) {
                                        HorizontalDivider(color = tokens.line, thickness = 1.dp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Interested CTA
                item {
                    val label = Copy.text(lang, "interestedCount", mapOf("count" to act.interestedCount.toString()))
                    val onInterestClick = {
                        if (sessionState.me == null) {
                            onRequireAuth()
                        } else {
                            scope.launch { shared.activity.toggleInterest(act.id, act.interested) }
                            Unit
                        }
                    }
                    if (act.interested) {
                        SaigonPrimaryButton(
                            text = label,
                            enabled = !activityState.isMutating,
                            loading = activityState.isMutating,
                            onClick = onInterestClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        SaigonSecondaryButton(
                            text = label,
                            enabled = !activityState.isMutating,
                            onClick = onInterestClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // Interested People (.activity-people)
                item {
                    Text(Copy.text(lang, "peopleInterested"), color = tokens.text, style = MaterialTheme.typography.titleMedium)
                }

                if (act.interestedPeople.isEmpty()) {
                    item {
                        Text(Copy.text(lang, "noPeople"), color = tokens.muted, fontSize = 14.sp)
                    }
                } else {
                    items(act.interestedPeople, key = { it.id }) { person ->
                        val isSelf = person.id == sessionState.me?.id
                        val isHost = person.host || (!act.hostUserId.isNullOrBlank() && person.id == act.hostUserId)
                        val checked = !isSelf && (isHost || person.id in selectedChatIds)
                        val canToggle = !isSelf && !isHost
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (checked) tokens.accent.copy(alpha = 0.14f) else Color.Transparent)
                                .then(
                                    if (checked) Modifier.border(2.dp, tokens.accent.copy(alpha = 0.24f), RoundedCornerShape(999.dp))
                                    else Modifier,
                                )
                                .clickable {
                                    when {
                                        canToggle -> selectedChatIds = if (checked) selectedChatIds - person.id else selectedChatIds + person.id
                                        isSelf -> onNavigate(AppRoute.User(person.id))
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            if (!isSelf) {
                                Checkbox(
                                    checked = checked,
                                    enabled = canToggle,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = tokens.accent,
                                        disabledCheckedColor = tokens.accent,
                                    ),
                                    onCheckedChange = {
                                        if (canToggle) {
                                            selectedChatIds = if (it) selectedChatIds + person.id else selectedChatIds - person.id
                                        }
                                    },
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                            UserAvatar(photoUrl = person.photoUrl, name = person.name, size = 40.dp, borderWidth = 0.dp)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(person.name, color = tokens.text, fontWeight = FontWeight.Bold)
                                if (isHost) SaigonTinyPill(label = Copy.text(lang, "host"), selected = true)
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(tokens.bg)
                                    .clickable { onNavigate(AppRoute.User(person.id)) },
                            ) {
                                Icon(Icons.Default.Person, contentDescription = Copy.text(lang, "profile"), tint = tokens.muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEdit && act != null) {
        ActivityEditorDialog(
            existing = act,
            locationName = act.locationName,
            locationId = act.locationId,
            placeId = act.placeId,
            lat = act.lat,
            lng = act.lng,
            shared = shared,
            language = lang,
            onSaved = { showEdit = false },
            onDismiss = { showEdit = false },
        )
    }

    if (confirmDeleteActivity && act != null) {
        SaigonConfirmDialog(
            title = Copy.text(lang, "deleteActivityAsk"),
            hint = Copy.text(lang, "deleteActivityHint"),
            confirmLabel = Copy.text(lang, "delete"),
            cancelLabel = Copy.text(lang, "cancel"),
            loading = activityState.isMutating,
            onConfirm = {
                scope.launch {
                    if (shared.activity.deleteActivity(act.id)) {
                        confirmDeleteActivity = false
                        onBack()
                    }
                }
            },
            onDismiss = { confirmDeleteActivity = false },
        )
    }

    pendingDeletePhoto?.let { photo ->
        if (act != null) {
            SaigonConfirmDialog(
                title = Copy.text(lang, "deletePhotoAsk"),
                hint = Copy.text(lang, "deletePhotoHint"),
                confirmLabel = Copy.text(lang, "delete"),
                cancelLabel = Copy.text(lang, "cancel"),
                loading = activityState.isMutating,
                onConfirm = {
                    scope.launch {
                        shared.activity.deletePhoto(act.id, photo.id)
                        pendingDeletePhoto = null
                    }
                },
                onDismiss = { pendingDeletePhoto = null },
            )
        }
    }

    viewerInitialIndex?.let { idx ->
        if (act != null && activityState.photos.isNotEmpty()) {
            PhotoViewerDialog(
                photos = activityState.photos,
                initialIndex = idx,
                language = lang,
                onDeletePhoto = { photo -> shared.activity.deletePhoto(act.id, photo.id) },
                onDismiss = { viewerInitialIndex = null },
            )
        }
    }
}
