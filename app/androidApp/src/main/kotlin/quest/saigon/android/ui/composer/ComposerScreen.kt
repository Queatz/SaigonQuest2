package quest.saigon.android.ui.composer

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import quest.saigon.CallToAction
import quest.saigon.CheckIns
import quest.saigon.Copy
import quest.saigon.Language
import quest.saigon.Limits
import quest.saigon.android.media.AudioPreviewPlayer
import quest.saigon.android.media.ImagePreparer
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.ui.components.SaigonBottomSheet
import quest.saigon.android.ui.components.SaigonCard
import quest.saigon.android.ui.components.SaigonOutlinedPillButton
import quest.saigon.android.ui.components.SaigonPageShell
import quest.saigon.android.ui.components.SaigonPillChip
import quest.saigon.android.ui.components.SaigonPrimaryButton
import quest.saigon.android.ui.components.SaigonSpinner
import quest.saigon.android.ui.components.SaigonSwitch
import quest.saigon.android.ui.components.SaigonTextButton
import quest.saigon.android.ui.components.SaigonTextField
import quest.saigon.android.ui.components.dashedBorder
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.AvatarDto
import quest.saigon.shared.model.CommunityDto
import quest.saigon.shared.model.MusicDto
import quest.saigon.shared.model.PlaceSuggestionDto
import quest.saigon.shared.platform.currentTimeMillis

@Composable
fun ComposerScreen(
    shared: TownSharedClient,
    audioPlayer: AudioPreviewPlayer,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val sessionState by shared.session.state.collectAsState()
    val composerState by shared.composer.state.collectAsState()
    val audioState by audioPlayer.state.collectAsState()
    val lang = sessionState.language
    val tokens = LocalSaigonTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var preparedPhotos by remember { mutableStateOf<List<ByteArray>>(emptyList()) }
    var activePhotoIdx by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var locationText by remember { mutableStateOf(sessionState.me?.location.orEmpty()) }
    var selectedPlace by remember { mutableStateOf<PlaceSuggestionDto?>(null) }
    var selectedCommunity by remember { mutableStateOf<CommunityDto?>(null) }
    var communityOnly by remember { mutableStateOf(false) }

    var showLocationSheet by remember { mutableStateOf(false) }
    var showCommunitySheet by remember { mutableStateOf(false) }
    var showAvatarSheet by remember { mutableStateOf(false) }
    var showMusicSheet by remember { mutableStateOf(false) }

    var enableEvent by remember { mutableStateOf(false) }
    var eventHoursFromNow by remember { mutableIntStateOf(4) }
    var eventPriceText by remember { mutableStateOf("") }

    var enablePoll by remember { mutableStateOf(false) }
    var pollOptions by remember { mutableStateOf(listOf("", "")) }

    var enableQuestion by remember { mutableStateOf(false) }
    var questionTitle by remember { mutableStateOf("") }

    var enableCta by remember { mutableStateOf(false) }
    var ctaDestination by remember { mutableStateOf(CallToAction.MESSAGE) }
    var ctaTitle by remember { mutableStateOf("") }
    var ctaText by remember { mutableStateOf("") }
    var ctaUrl by remember { mutableStateOf("") }

    var enableOpenChat by remember { mutableStateOf(false) }
    var openChatName by remember { mutableStateOf("") }

    var checkInMinutes by remember { mutableStateOf<Int?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    var preparingPhotos by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        shared.composer.loadInitial()
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(Limits.PHOTOS_MAX),
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            preparingPhotos = true
            val list = preparedPhotos.toMutableList()
            for (uri in uris) {
                if (list.size >= Limits.PHOTOS_MAX) break
                ImagePreparer.preparePostPhoto(context, uri)?.let { list += it }
            }
            preparedPhotos = list
            if (list.isNotEmpty()) activePhotoIdx = list.lastIndex
            preparingPhotos = false
        }
    }

    SaigonPageShell(
        title = Copy.text(lang, "newPost"),
        onBack = onBack,
        backContentDescription = Copy.text(lang, "back"),
        topActions = {
            SaigonPrimaryButton(
                text = Copy.text(lang, "postNow"),
                enabled = !composerState.isSubmittingPost && !preparingPhotos,
                loading = composerState.isSubmittingPost,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                onClick = {
                    localError = null
                    if (preparedPhotos.isEmpty()) {
                        localError = Copy.text(lang, "photoRequired")
                        return@SaigonPrimaryButton
                    }
                    if (message.isBlank()) {
                        localError = Copy.text(lang, "invalidMessage")
                        return@SaigonPrimaryButton
                    }
                    val locName = selectedPlace?.name ?: locationText.trim()
                    if (locName.isBlank()) {
                        localError = Copy.text(lang, "invalidLocation")
                        return@SaigonPrimaryButton
                    }
                    val fields = buildMap {
                        put("message", message.trim())
                        put("location", locName)
                        selectedPlace?.placeId?.let { put("placeId", it) }
                        selectedPlace?.id?.takeIf { selectedPlace?.custom == true }?.let { put("locationId", it) }
                        selectedPlace?.lat?.let { put("lat", it.toString()) }
                        selectedPlace?.lng?.let { put("lng", it.toString()) }
                        composerState.selectedAvatarId?.let { put("avatarId", it) }
                        composerState.selectedMusic?.id?.let { put("musicId", it) }
                        selectedCommunity?.id?.let {
                            put("communityId", it)
                            if (communityOnly) put("communityOnly", "true")
                        }
                        if (enableEvent) {
                            val startAt = currentTimeMillis() + eventHoursFromNow * 3_600_000L
                            put("eventStartAt", startAt.toString())
                            eventPriceText.trim().toLongOrNull()?.takeIf { it > 0 }?.let {
                                put("eventPrice", it.toString())
                            }
                        }
                        if (enablePoll) {
                            val cleanOptions = pollOptions.map { it.trim() }.filter { it.isNotBlank() }
                            if (cleanOptions.size >= 2) {
                                put(
                                    "pollOptions",
                                    shared.api.json.encodeToString(
                                        kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()),
                                        cleanOptions,
                                    ),
                                )
                            }
                        }
                        if (enableQuestion && questionTitle.isNotBlank()) {
                            put("questionTitle", questionTitle.trim())
                        }
                        if (enableCta && ctaText.isNotBlank()) {
                            put("ctaDestination", ctaDestination)
                            put("ctaTitle", ctaTitle.trim().ifBlank { ctaText.trim() })
                            put("ctaText", ctaText.trim())
                            if (ctaUrl.isNotBlank()) put("ctaUrl", ctaUrl.trim())
                        }
                        if (enableOpenChat) {
                            put("openChat", "true")
                            if (openChatName.isNotBlank()) put("openChatName", openChatName.trim())
                        }
                        checkInMinutes?.let { put("checkInMinutes", it.toString()) }
                    }
                    scope.launch {
                        val created = shared.composer.submitPost(fields, preparedPhotos)
                        if (created != null) {
                            shared.composer.clearCreatedPost()
                            onDone()
                        }
                    }
                },
            )
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            // .composer-photo-stage (220.dp wide, 9:16, 18.dp radius, 2.dp dashed line) + .photo-thumbs
            val safeIdx = activePhotoIdx.coerceIn(0, (preparedPhotos.size - 1).coerceAtLeast(0))
            val activeBytes = preparedPhotos.getOrNull(safeIdx)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(220.dp)
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(tokens.surface)
                        .then(
                            if (activeBytes == null) {
                                Modifier.dashedBorder(tokens.line, strokeWidth = 2.dp, cornerRadius = 18.dp)
                            } else {
                                Modifier.border(2.dp, tokens.line, RoundedCornerShape(18.dp))
                            },
                        )
                        .clickable {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                ) {
                    if (activeBytes != null) {
                        val bitmap = remember(activeBytes) { BitmapFactory.decodeByteArray(activeBytes, 0, activeBytes.size) }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(36.dp)
                                .shadow(4.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f))
                                .clickable {
                                    preparedPhotos = preparedPhotos.filterIndexed { i, _ -> i != safeIdx }
                                    activePhotoIdx = (safeIdx - 1).coerceAtLeast(0)
                                },
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = tokens.muted, modifier = Modifier.size(34.dp))
                            Text(
                                "${preparedPhotos.size}/${Limits.PHOTOS_MAX}",
                                color = tokens.muted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                if (preparedPhotos.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        preparedPhotos.forEachIndexed { idx, bytes ->
                            val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                            val selected = idx == safeIdx
                            Box(
                                modifier = Modifier
                                    .size(width = 40.dp, height = 71.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(tokens.surface)
                                    .border(2.dp, if (selected) tokens.accent else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { activePhotoIdx = idx },
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                        if (preparedPhotos.size < Limits.PHOTOS_MAX) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(width = 40.dp, height = 71.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .dashedBorder(tokens.line, strokeWidth = 2.dp, cornerRadius = 8.dp)
                                    .clickable {
                                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                            ) {
                                Text("+", color = tokens.muted, fontSize = 22.sp, fontWeight = FontWeight.Light)
                            }
                        }
                    }
                }
            }

            if (preparingPhotos) {
                LinearProgressIndicator(
                    color = tokens.accent,
                    trackColor = tokens.line,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)),
                )
            }

            SaigonTextField(
                value = message,
                onValueChange = { if (it.length <= Limits.MESSAGE_MAX) message = it },
                label = Copy.text(lang, "message"),
                singleLine = false,
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            // Location selector (.community-open style)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    Copy.text(lang, "location"),
                    color = tokens.text,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(tokens.surface)
                        .border(2.dp, tokens.line, RoundedCornerShape(14.dp))
                        .clickable { showLocationSheet = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(20.dp))
                    Text(
                        (selectedPlace?.name ?: locationText).ifBlank { Copy.text(lang, "location") },
                        color = if ((selectedPlace?.name ?: locationText).isBlank()) tokens.muted else tokens.text,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = tokens.muted)
                }
            }

            // Community, Avatar, Music quick pickers
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SaigonOutlinedPillButton(
                    text = selectedCommunity?.let { "${it.emoji} ${it.name}" } ?: Copy.text(lang, "community"),
                    onClick = { showCommunitySheet = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                )
                SaigonOutlinedPillButton(
                    text = if (composerState.selectedAvatarId != null) Copy.text(lang, "selectAvatar") else Copy.text(lang, "createAvatar"),
                    onClick = { showAvatarSheet = true },
                    contentColor = if (composerState.selectedAvatarId != null) tokens.accent else tokens.text,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                )
                SaigonOutlinedPillButton(
                    text = composerState.selectedMusic?.name?.takeIf { it.isNotBlank() } ?: Copy.text(lang, "selectMusic"),
                    onClick = { showMusicSheet = true },
                    contentColor = if (composerState.selectedMusic != null) tokens.accent else tokens.text,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                )
            }

            if (selectedCommunity != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    SaigonSwitch(checked = communityOnly, onCheckedChange = { communityOnly = it })
                    Text(
                        Copy.text(lang, "communityOnly"),
                        color = tokens.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Check-in duration selector (.chip-row)
            Text(
                Copy.text(lang, "checkInDialogTitle"),
                color = tokens.text,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                SaigonPillChip(
                    selected = checkInMinutes == null,
                    onClick = { checkInMinutes = null },
                    label = Copy.text(lang, "noAvatar"),
                )
                CheckIns.durationOptionsMinutes.forEach { mins ->
                    val label = if (mins < 60) "${mins}m" else "${mins / 60}h"
                    SaigonPillChip(
                        selected = checkInMinutes == mins,
                        onClick = { checkInMinutes = mins },
                        label = label,
                    )
                }
            }

            HorizontalDivider(color = tokens.line, thickness = 1.dp)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                SaigonPillChip(selected = enableEvent, onClick = { enableEvent = !enableEvent }, label = Copy.text(lang, "events"))
                SaigonPillChip(selected = enablePoll, onClick = { enablePoll = !enablePoll }, label = Copy.text(lang, "poll"))
                SaigonPillChip(selected = enableQuestion, onClick = { enableQuestion = !enableQuestion }, label = Copy.text(lang, "askQuestion"))
                SaigonPillChip(selected = enableCta, onClick = { enableCta = !enableCta }, label = Copy.text(lang, "callToActionText"))
                SaigonPillChip(selected = enableOpenChat, onClick = { enableOpenChat = !enableOpenChat }, label = Copy.text(lang, "openChat"))
            }

            if (enableEvent) {
                SaigonCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(Copy.text(lang, "events"), color = tokens.text, fontWeight = FontWeight.ExtraBold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            listOf(1, 2, 4, 24, 48).forEach { hrs ->
                                SaigonPillChip(
                                    selected = eventHoursFromNow == hrs,
                                    onClick = { eventHoursFromNow = hrs },
                                    label = "+${hrs}h",
                                )
                            }
                        }
                        SaigonTextField(
                            value = eventPriceText,
                            onValueChange = { eventPriceText = it.filter(Char::isDigit) },
                            label = Copy.text(lang, "eventPrice"),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (enablePoll) {
                SaigonCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(Copy.text(lang, "poll"), color = tokens.text, fontWeight = FontWeight.ExtraBold)
                        pollOptions.forEachIndexed { idx, opt ->
                            SaigonTextField(
                                value = opt,
                                onValueChange = { next ->
                                    if (next.length <= Limits.POLL_OPTION_MAX) {
                                        pollOptions = pollOptions.mapIndexed { i, v -> if (i == idx) next else v }
                                    }
                                },
                                placeholder = Copy.text(lang, "pollOption", mapOf("n" to (idx + 1).toString())),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (pollOptions.size < Limits.POLL_OPTIONS_MAX) {
                            SaigonTextButton(
                                text = "+ ${Copy.text(lang, "addPollOption")}",
                                accent = true,
                                onClick = { pollOptions = pollOptions + "" },
                            )
                        }
                    }
                }
            }

            if (enableQuestion) {
                SaigonTextField(
                    value = questionTitle,
                    onValueChange = { if (it.length <= Limits.QUESTION_TITLE_MAX) questionTitle = it },
                    label = Copy.text(lang, "questionTitle"),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (enableCta) {
                SaigonCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(Copy.text(lang, "callToActionText"), color = tokens.text, fontWeight = FontWeight.ExtraBold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            CallToAction.destinations.forEach { dest ->
                                SaigonPillChip(
                                    selected = ctaDestination == dest,
                                    onClick = { ctaDestination = dest },
                                    label = dest,
                                )
                            }
                        }
                        SaigonTextField(
                            value = ctaTitle,
                            onValueChange = { if (it.length <= Limits.CTA_TITLE_MAX) ctaTitle = it },
                            label = Copy.text(lang, "callToActionTitle"),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SaigonTextField(
                            value = ctaText,
                            onValueChange = { if (it.length <= Limits.CTA_TEXT_MAX) ctaText = it },
                            label = Copy.text(lang, "callToActionText"),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (ctaDestination == CallToAction.WEBSITE || ctaDestination == CallToAction.PRODUCT) {
                            SaigonTextField(
                                value = ctaUrl,
                                onValueChange = { if (it.length <= Limits.CTA_URL_MAX) ctaUrl = it },
                                label = "URL / ID",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (enableOpenChat) {
                SaigonTextField(
                    value = openChatName,
                    onValueChange = { openChatName = it },
                    label = Copy.text(lang, "groupName"),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val err = localError ?: composerState.error
            if (!err.isNullOrBlank()) {
                Text(err, color = tokens.danger, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }

    if (showLocationSheet) {
        LocationPickerSheet(
            initialQuery = locationText,
            recent = composerState.recentPlaces,
            results = composerState.placeResults,
            language = lang,
            onSearch = { q -> shared.composer.searchPlaces(q, selectedCommunity?.id) },
            onSelectPlace = { place ->
                selectedPlace = place
                locationText = place.name
                showLocationSheet = false
            },
            onSelectCustomText = { text ->
                selectedPlace = null
                locationText = text
                showLocationSheet = false
            },
            onDismiss = { showLocationSheet = false },
        )
    }

    if (showCommunitySheet) {
        CommunityPickerSheet(
            communities = composerState.communities,
            selected = selectedCommunity,
            language = lang,
            onCreate = { name, emoji ->
                scope.launch {
                    shared.composer.createCommunity(name, emoji)?.let { created ->
                        selectedCommunity = created
                        showCommunitySheet = false
                    }
                }
            },
            onSelect = {
                selectedCommunity = it
                showCommunitySheet = false
            },
            onDismiss = { showCommunitySheet = false },
        )
    }

    if (showAvatarSheet) {
        AvatarStudioSheet(
            avatars = composerState.avatars,
            selectedAvatarId = composerState.selectedAvatarId,
            language = lang,
            onCreate = { prompt -> scope.launch { shared.composer.createAvatar(prompt) } },
            onRetry = { id -> scope.launch { shared.composer.retryAvatar(id) } },
            onDelete = { id -> scope.launch { shared.composer.deleteAvatar(id) } },
            onSelect = { id ->
                shared.composer.selectAvatar(id)
                showAvatarSheet = false
            },
            onDismiss = { showAvatarSheet = false },
        )
    }

    if (showMusicSheet) {
        MusicStudioSheet(
            myMusic = composerState.myMusic,
            searchedMusic = composerState.searchedMusic,
            selectedMusic = composerState.selectedMusic,
            playingUrl = if (audioState.isPlaying) audioState.currentUrl else null,
            language = lang,
            onSearch = { q -> shared.composer.searchMusic(q) },
            onCreate = { prompt, instrumental -> scope.launch { shared.composer.createMusic(prompt, instrumental = instrumental) } },
            onRetry = { id -> scope.launch { shared.composer.retryMusic(id) } },
            onDelete = { id -> scope.launch { shared.composer.deleteMusic(id) } },
            onTogglePlay = { url -> audioPlayer.toggle(url) },
            onSelect = { track ->
                shared.composer.selectMusic(track)
                showMusicSheet = false
            },
            onDismiss = { showMusicSheet = false },
        )
    }
}

@Composable
private fun LocationPickerSheet(
    initialQuery: String,
    recent: List<PlaceSuggestionDto>,
    results: List<PlaceSuggestionDto>,
    language: Language,
    onSearch: (String) -> Unit,
    onSelectPlace: (PlaceSuggestionDto) -> Unit,
    onSelectCustomText: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    var query by remember { mutableStateOf(initialQuery) }
    SaigonBottomSheet(
        title = Copy.text(language, "location"),
        onDismiss = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SaigonTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onSearch(it)
                    },
                    placeholder = Copy.text(language, "searchPlaces"),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (query.isNotBlank()) {
                    SaigonPrimaryButton(
                        text = Copy.text(language, "usePlace"),
                        onClick = { onSelectCustomText(query.trim()) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 360.dp),
            ) {
                items(recent + results) { place ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(tokens.bg)
                            .border(2.dp, tokens.line, RoundedCornerShape(14.dp))
                            .clickable { onSelectPlace(place) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(place.name, color = tokens.text, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        if (place.address.isNotBlank()) {
                            Text(place.address, color = tokens.muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityPickerSheet(
    communities: List<CommunityDto>,
    selected: CommunityDto?,
    language: Language,
    onCreate: (String, String) -> Unit,
    onSelect: (CommunityDto?) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    var newName by remember { mutableStateOf("") }
    var newEmoji by remember { mutableStateOf("✨") }
    SaigonBottomSheet(
        title = Copy.text(language, "community"),
        onDismiss = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SaigonTextField(
                    value = newEmoji,
                    onValueChange = { newEmoji = it.take(4) },
                    singleLine = true,
                    modifier = Modifier.width(68.dp),
                )
                SaigonTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = Copy.text(language, "createCommunity"),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                SaigonPrimaryButton(
                    text = "+",
                    enabled = newName.isNotBlank(),
                    onClick = { onCreate(newName.trim(), newEmoji.trim()) },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            if (selected != null) {
                SaigonTextButton(
                    text = Copy.text(language, "noCommunity"),
                    accent = true,
                    onClick = { onSelect(null) },
                )
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 320.dp),
            ) {
                items(communities, key = { it.id }) { comm ->
                    val isSelected = comm.id == selected?.id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(tokens.bg)
                            .border(2.dp, if (isSelected) tokens.accent else tokens.line, RoundedCornerShape(14.dp))
                            .clickable { onSelect(comm) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(
                            "${comm.emoji} ${comm.name}",
                            color = if (isSelected) tokens.accent else tokens.text,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                        )
                        Text("${comm.memberCount}", color = tokens.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarStudioSheet(
    avatars: List<AvatarDto>,
    selectedAvatarId: String?,
    language: Language,
    onCreate: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    var prompt by remember { mutableStateOf("") }
    SaigonBottomSheet(
        title = Copy.text(language, "selectAvatar"),
        onDismiss = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SaigonTextField(
                    value = prompt,
                    onValueChange = { if (it.length <= Limits.PROMPT_MAX) prompt = it },
                    placeholder = Copy.text(language, "avatarPrompt"),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                SaigonPrimaryButton(
                    text = Copy.text(language, "createAvatar"),
                    enabled = prompt.isNotBlank(),
                    onClick = {
                        onCreate(prompt.trim())
                        prompt = ""
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            if (selectedAvatarId != null) {
                SaigonTextButton(text = Copy.text(language, "noAvatar"), accent = true, onClick = { onSelect(null) })
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 320.dp),
            ) {
                items(avatars, key = { it.id }) { avatar ->
                    val selected = avatar.id == selectedAvatarId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(tokens.surface)
                            .border(2.dp, if (selected) tokens.accent else tokens.line, RoundedCornerShape(16.dp))
                            .clickable(enabled = avatar.status == "ready") { onSelect(avatar.id) }
                            .padding(10.dp),
                    ) {
                        if (!avatar.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = avatar.imageUrl,
                                contentDescription = avatar.prompt,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                            )
                        } else {
                            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                                if (avatar.status == "generating") SaigonSpinner(size = 24.dp)
                                else Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = tokens.muted)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(avatar.prompt, color = tokens.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(avatar.status, color = tokens.muted, fontSize = 12.sp)
                        }
                        if (avatar.status == "failed") {
                            IconButton(onClick = { onRetry(avatar.id) }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = tokens.accent)
                            }
                        }
                        IconButton(onClick = { onDelete(avatar.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = tokens.danger)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicStudioSheet(
    myMusic: List<MusicDto>,
    searchedMusic: List<MusicDto>,
    selectedMusic: MusicDto?,
    playingUrl: String?,
    language: Language,
    onSearch: (String) -> Unit,
    onCreate: (String, Boolean) -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    onTogglePlay: (String?) -> Unit,
    onSelect: (MusicDto?) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    var prompt by remember { mutableStateOf("") }
    var instrumental by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }

    SaigonBottomSheet(
        title = Copy.text(language, "selectMusic"),
        onDismiss = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SaigonTextField(
                    value = prompt,
                    onValueChange = { if (it.length <= Limits.MUSIC_PROMPT_MAX) prompt = it },
                    placeholder = Copy.text(language, "musicPrompt"),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                SaigonPrimaryButton(
                    text = Copy.text(language, "createMusic"),
                    enabled = prompt.isNotBlank(),
                    onClick = {
                        onCreate(prompt.trim(), instrumental)
                        prompt = ""
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SaigonSwitch(checked = instrumental, onCheckedChange = { instrumental = it })
                Text(Copy.text(language, "instrumental"), color = tokens.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            SaigonTextField(
                value = search,
                onValueChange = {
                    search = it
                    onSearch(it)
                },
                placeholder = Copy.text(language, "searchMusic"),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (selectedMusic != null) {
                SaigonTextButton(text = Copy.text(language, "noMusic"), accent = true, onClick = { onSelect(null) })
            }
            val combined = remember(myMusic, searchedMusic, search) {
                if (search.isBlank()) myMusic else searchedMusic
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 300.dp),
            ) {
                items(combined, key = { it.id }) { track ->
                    val selected = track.id == selectedMusic?.id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(tokens.surface)
                            .border(2.dp, if (selected) tokens.accent else tokens.line, RoundedCornerShape(16.dp))
                            .clickable(enabled = track.status == "ready") { onSelect(track) }
                            .padding(12.dp),
                    ) {
                        if (!track.audioUrl.isNullOrBlank()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(tokens.bg)
                                    .clickable { onTogglePlay(track.audioUrl) },
                            ) {
                                Icon(
                                    if (playingUrl == track.audioUrl) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = tokens.text,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else if (track.status == "generating") {
                            SaigonSpinner(size = 22.dp)
                        } else {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = tokens.muted)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.name.ifBlank { track.prompt }, color = tokens.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.status, color = tokens.muted, fontSize = 12.sp)
                        }
                        if (track.status == "failed") {
                            IconButton(onClick = { onRetry(track.id) }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = tokens.accent)
                            }
                        }
                        IconButton(onClick = { onDelete(track.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = tokens.danger)
                        }
                    }
                }
            }
        }
    }
}
