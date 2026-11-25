package quest.saigon.android.ui.composer

import android.content.ActivityNotFoundException
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import quest.saigon.CallToAction
import quest.saigon.CheckIns
import quest.saigon.Copy
import quest.saigon.Language
import quest.saigon.Limits
import quest.saigon.android.media.AudioPreviewPlayer
import quest.saigon.android.media.ImagePreparer
import quest.saigon.android.theme.LivvicFontFamily
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.ui.components.LocationInputRow
import quest.saigon.android.ui.components.LocationPickerDialog
import quest.saigon.android.ui.components.PickedPhotoItem
import quest.saigon.android.ui.components.SaigonBottomSheet
import quest.saigon.android.ui.components.SaigonCard
import quest.saigon.android.ui.components.SaigonConfirmDialog
import quest.saigon.android.ui.components.SaigonDialog
import quest.saigon.android.ui.components.SaigonOutlinedPillButton
import quest.saigon.android.ui.components.SaigonPageShell
import quest.saigon.android.ui.components.SaigonPhotoPicker
import quest.saigon.android.ui.components.SaigonPillChip
import quest.saigon.android.ui.components.SaigonPrimaryButton
import quest.saigon.android.ui.components.SaigonSpinner
import quest.saigon.android.ui.components.SaigonSwitch
import quest.saigon.android.ui.components.SaigonTextButton
import quest.saigon.android.ui.components.SaigonTextField
import quest.saigon.android.ui.components.SaigonTinyPill
import quest.saigon.android.ui.components.ViewerButton
import quest.saigon.android.ui.components.checkInDurationLabel
import quest.saigon.android.ui.components.dashedBorder
import quest.saigon.android.ui.components.formatVnd
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.AvatarDto
import quest.saigon.shared.model.ChatDto
import quest.saigon.shared.model.CommunityDto
import quest.saigon.shared.model.MusicDto
import quest.saigon.shared.model.PlaceSuggestionDto
import quest.saigon.shared.model.ProductDto
import quest.saigon.shared.network.ApiResult
import quest.saigon.shared.platform.currentTimeMillis
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private enum class EventDateTimeTarget {
    START_DATE,
    START_TIME,
    END_DATE,
    END_TIME,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerScreen(
    shared: TownSharedClient,
    audioPlayer: AudioPreviewPlayer,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit = {},
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
    var firstComment by remember { mutableStateOf("") }
    var firstCommentEnabled by remember { mutableStateOf(false) }
    var locationText by remember { mutableStateOf(sessionState.me?.location.orEmpty()) }
    var selectedPlace by remember(sessionState.me?.id) {
        val me = sessionState.me
        mutableStateOf(
            if (me != null && me.location.isNotBlank() && me.lat != null && me.lng != null) {
                PlaceSuggestionDto(
                    id = me.locationId,
                    name = me.location,
                    placeId = me.placeId,
                    lat = me.lat,
                    lng = me.lng,
                    custom = me.locationId != null,
                )
            } else {
                null
            },
        )
    }
    var selectedCommunity by remember { mutableStateOf<CommunityDto?>(null) }
    var communityOnly by remember { mutableStateOf(false) }
    var creatingSuggestedCommunity by remember { mutableStateOf(false) }

    var showLocationDialog by remember { mutableStateOf(false) }
    var showCommunitySheet by remember { mutableStateOf(false) }
    var showAvatarSheet by remember { mutableStateOf(false) }
    var showMusicSheet by remember { mutableStateOf(false) }
    var showProductPicker by remember { mutableStateOf(false) }
    var showGroupPicker by remember { mutableStateOf(false) }

    var enableEvent by remember { mutableStateOf(false) }
    var eventStartAt by remember { mutableStateOf<Long?>(null) }
    var eventEndAt by remember { mutableStateOf<Long?>(null) }
    var eventPriceText by remember { mutableStateOf("") }
    var eventDateTimeTarget by remember { mutableStateOf<EventDateTimeTarget?>(null) }

    var enablePoll by remember { mutableStateOf(false) }
    var pollOptions by remember { mutableStateOf(listOf("", "")) }

    var enableQuestion by remember { mutableStateOf(false) }
    var questionTitle by remember { mutableStateOf("") }

    var enableCta by remember { mutableStateOf(false) }
    var ctaDestination by remember { mutableStateOf(CallToAction.MESSAGE) }
    var ctaTitle by remember { mutableStateOf("") }
    var ctaText by remember { mutableStateOf("") }
    var ctaUrl by remember { mutableStateOf("") }
    var ctaSelectedProduct by remember { mutableStateOf<ProductDto?>(null) }

    var enableOpenChat by remember { mutableStateOf(false) }
    var openChatCreateNew by remember { mutableStateOf(false) }
    var selectedOpenChatGroup by remember { mutableStateOf<ChatDto?>(null) }
    var openChatName by remember { mutableStateOf("") }
    var openChatHours by remember { mutableIntStateOf(Limits.OPEN_CHAT_HOURS_DEFAULT) }
    var openChatHoursText by remember { mutableStateOf(Limits.OPEN_CHAT_HOURS_DEFAULT.toString()) }
    var openChatRemaining by remember { mutableStateOf<Int?>(null) }
    var openChatRemainingText by remember { mutableStateOf("") }

    var checkInMinutes by remember { mutableStateOf<Int?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    var preparingPhotos by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        shared.composer.loadInitial()
    }

    LaunchedEffect(message) {
        val trimmed = message.trim()
        if (trimmed.length < 8) {
            shared.composer.clearCommunitySuggestion()
            return@LaunchedEffect
        }
        delay(700L)
        shared.composer.suggestCommunity(trimmed)
    }

    val hasCoordinates = selectedPlace?.lat != null &&
        selectedPlace?.lng != null &&
        (selectedPlace?.name ?: locationText).isNotBlank()
    LaunchedEffect(hasCoordinates) {
        if (!hasCoordinates) checkInMinutes = null
    }

    fun processPickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            preparingPhotos = true
            localError = null
            try {
                val initialCount = preparedPhotos.size
                val list = preparedPhotos.toMutableList()
                var failedCount = 0
                for (uri in uris) {
                    if (list.size >= Limits.PHOTOS_MAX) break
                    val prepared = ImagePreparer.preparePostPhoto(context, uri)
                    if (prepared != null && prepared.isNotEmpty()) {
                        list += prepared
                    } else {
                        failedCount += 1
                    }
                }
                preparedPhotos = list
                if (list.isNotEmpty()) activePhotoIdx = list.lastIndex
                if (failedCount > 0 && list.size == initialCount) {
                    localError = Copy.text(lang, "invalidPhoto")
                }
            } finally {
                preparingPhotos = false
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(Limits.PHOTOS_MAX),
    ) { uris: List<Uri> ->
        processPickedUris(uris)
    }
    val fallbackPhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris: List<Uri> ->
        processPickedUris(uris)
    }

    fun launchPhotoPicker() {
        try {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: ActivityNotFoundException) {
            runCatching { fallbackPhotoPicker.launch("image/*") }
        }
    }

    fun submitCurrentPost() {
        localError = null
        if (preparedPhotos.isEmpty()) {
            localError = Copy.text(lang, "photoRequired")
            return
        }
        if (message.isBlank()) {
            localError = Copy.text(lang, "invalidMessage")
            return
        }
        val locName = (selectedPlace?.name ?: locationText).trim()
        if (locName.isBlank()) {
            localError = Copy.text(lang, "invalidLocation")
            return
        }
        if (enableEvent) {
            val start = eventStartAt
            if (start == null) {
                localError = Copy.text(lang, "invalidEvent")
                return
            }
            val end = eventEndAt
            if (end != null && end < start) {
                localError = Copy.text(lang, "invalidEventEnd")
                return
            }
        }
        val cleanPollOptions = pollOptions.map { it.trim() }.filter { it.isNotBlank() }
        if (enablePoll && cleanPollOptions.size < 2) {
            localError = Copy.text(lang, "invalidPoll")
            return
        }
        if (enableCta) {
            if (ctaText.isBlank()) {
                localError = Copy.text(lang, "invalidCallToAction")
                return
            }
            if (ctaDestination == CallToAction.WEBSITE && ctaUrl.isBlank()) {
                localError = Copy.text(lang, "invalidCallToActionUrl")
                return
            }
            if (ctaDestination == CallToAction.PRODUCT && ctaSelectedProduct == null) {
                localError = Copy.text(lang, "invalidCallToAction")
                return
            }
        }
        if (enableOpenChat && !openChatCreateNew && selectedOpenChatGroup == null) {
            localError = Copy.text(lang, "invalidOpenChat")
            return
        }

        val fields = buildMap {
            put("message", message.trim())
            put("location", locName)
            if (firstCommentEnabled && firstComment.isNotBlank()) put("firstComment", firstComment.trim())
            val placeId = selectedPlace?.placeId?.takeIf { it.isNotBlank() }
            val locationId = selectedPlace?.id?.takeIf { selectedPlace?.custom == true && placeId == null }
            placeId?.let { put("placeId", it) }
            locationId?.let { put("locationId", it) }
            selectedPlace?.lat?.let { put("lat", it.toString()) }
            selectedPlace?.lng?.let { put("lng", it.toString()) }
            composerState.selectedAvatarId?.let { put("avatarId", it) }
            composerState.selectedMusic?.id?.let { put("musicId", it) }
            selectedCommunity?.id?.let {
                put("communityId", it)
                if (communityOnly) put("communityOnly", "true")
            }
            if (hasCoordinates) {
                checkInMinutes?.let { put("checkInMinutes", it.toString()) }
            }
            if (enableEvent) {
                eventStartAt?.let { put("eventStartAt", it.toString()) }
                eventEndAt?.let { put("eventEndAt", it.toString()) }
                eventPriceText.trim().toLongOrNull()?.takeIf { it > 0L }?.let {
                    put("eventPrice", it.toString())
                }
            }
            if (enablePoll) {
                cleanPollOptions.take(Limits.POLL_OPTIONS_MAX).forEachIndexed { idx, opt ->
                    put("pollOption$idx", opt)
                }
            }
            if (enableQuestion) {
                put("askQuestion", "true")
                if (questionTitle.isNotBlank()) put("questionTitle", questionTitle.trim())
            }
            if (enableCta) {
                put("addCta", "true")
                put("ctaDestination", ctaDestination)
                if (ctaTitle.isNotBlank()) put("ctaTitle", ctaTitle.trim())
                put("ctaText", ctaText.trim())
                when (ctaDestination) {
                    CallToAction.WEBSITE -> put("ctaUrl", ctaUrl.trim())
                    CallToAction.PRODUCT -> ctaSelectedProduct?.id?.let { put("ctaUrl", it) }
                    else -> Unit
                }
            }
            if (enableOpenChat) {
                val resolvedHours = openChatHoursText.trim().toIntOrNull()
                    ?.coerceIn(Limits.OPEN_CHAT_HOURS_MIN, Limits.OPEN_CHAT_HOURS_MAX)
                    ?: openChatHours.coerceIn(Limits.OPEN_CHAT_HOURS_MIN, Limits.OPEN_CHAT_HOURS_MAX)
                val resolvedRemaining = openChatRemainingText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
                    ?.coerceIn(Limits.OPEN_CHAT_REMAINING_MIN, Limits.OPEN_CHAT_REMAINING_MAX)
                    ?: openChatRemaining
                put("addOpenChat", "true")
                put("openChatNew", if (openChatCreateNew) "true" else "false")
                if (openChatCreateNew) {
                    if (openChatName.isNotBlank()) put("openChatName", openChatName.trim())
                } else {
                    selectedOpenChatGroup?.id?.let { put("openChatId", it) }
                }
                put("openChatHours", resolvedHours.toString())
                resolvedRemaining?.let { put("openChatRemaining", it.toString()) }
            }
        }
        scope.launch {
            val created = shared.composer.submitPost(fields, preparedPhotos)
            if (created != null) {
                shared.composer.clearCreatedPost()
                onDone()
            }
        }
    }

    val err = localError ?: composerState.error

    SaigonPageShell(
        title = Copy.text(lang, "newPost"),
        onBack = onBack,
        backContentDescription = Copy.text(lang, "back"),
        topActions = {
            ViewerButton(
                me = sessionState.me,
                language = lang,
                toComposer = false,
                overlay = false,
                showSignedOutBubble = false,
                onClick = onOpenProfile,
            )
        },
        bottomBar = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.bg)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                if (!err.isNullOrBlank()) {
                    Text(
                        err,
                        color = tokens.danger,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
                SaigonPrimaryButton(
                    text = Copy.text(lang, "postNow"),
                    enabled = !composerState.isSubmittingPost && !preparingPhotos,
                    loading = composerState.isSubmittingPost,
                    onClick = { submitCurrentPost() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            // .composer-photo-stage (220.dp wide, 9:16, 18.dp radius, 2.dp dashed line) + .photo-thumbs
            SaigonPhotoPicker(
                photos = preparedPhotos.map { PickedPhotoItem(bytes = it) },
                activeIndex = activePhotoIdx,
                onActiveIndexChange = { activePhotoIdx = it },
                onPhotosChange = { updated -> preparedPhotos = updated.mapNotNull { it.bytes } },
                onAddPhotosClick = { launchPhotoPicker() },
                language = lang,
                square = false,
                rotatePrompts = true,
                isBusy = preparingPhotos,
            )

            SaigonTextField(
                value = message,
                onValueChange = { if (it.length <= Limits.MESSAGE_MAX) { message = it; localError = null } },
                label = "💬 ${Copy.text(lang, "message")}",
                singleLine = false,
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            // Location input + map picker button (.location-input-row)
            LocationInputRow(
                label = "📍 ${Copy.text(lang, "location")}",
                value = locationText,
                onValueChange = { typed ->
                    locationText = typed
                    if (selectedPlace != null && !typed.trim().equals(selectedPlace?.name?.trim(), ignoreCase = false)) {
                        selectedPlace = null
                    }
                    localError = null
                },
                hasCoordinates = hasCoordinates,
                language = lang,
                onOpenMapPicker = { showLocationDialog = true },
                modifier = Modifier.fillMaxWidth(),
            )

            // Check-in duration selector: revealed ONLY when selected location has lat/lng (same as web)
            if (hasCoordinates) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            Copy.text(lang, "checkInDialogTitle"),
                            color = tokens.text,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                        )
                        checkInMinutes?.let { mins ->
                            val quantizedEnd = CheckIns.endAt(currentTimeMillis(), mins) ?: (currentTimeMillis() + mins * 60_000L)
                            val untilClock = formatSaigonClock(quantizedEnd)
                            Text(
                                Copy.text(lang, "checkInUntil", mapOf("time" to untilClock)),
                                color = tokens.accent,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        CheckIns.durationOptionsMinutes.forEach { mins ->
                            val selected = checkInMinutes == mins
                            SaigonPillChip(
                                selected = selected,
                                onClick = { checkInMinutes = if (selected) null else mins },
                                label = checkInDurationLabel(mins, lang),
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                SaigonOutlinedPillButton(
                    text = selectedCommunity?.let { "${it.emoji} ${it.name}" } ?: Copy.text(lang, "community"),
                    onClick = { showCommunitySheet = true },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                )
                SaigonOutlinedPillButton(
                    text = "🙆 ${Copy.text(lang, "selectAvatar")}",
                    onClick = { showAvatarSheet = true },
                    contentColor = if (composerState.selectedAvatarId != null) tokens.accent else tokens.text,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                )
                SaigonOutlinedPillButton(
                    text = "🎵 ${composerState.selectedMusic?.name?.takeIf { it.isNotBlank() } ?: Copy.text(lang, "selectMusic")}",
                    onClick = { showMusicSheet = true },
                    contentColor = if (composerState.selectedMusic != null) tokens.accent else tokens.text,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                )
                SaigonPillChip(
                    selected = firstCommentEnabled,
                    onClick = {
                        firstCommentEnabled = !firstCommentEnabled
                        if (!firstCommentEnabled) firstComment = ""
                    },
                    label = "💭 ${Copy.text(lang, "firstComment")}",
                )
                if (selectedCommunity != null) {
                    SaigonPillChip(
                        selected = communityOnly,
                        onClick = { communityOnly = !communityOnly },
                        label = Copy.text(lang, "communityOnly"),
                    )
                }
                SaigonPillChip(
                    selected = enableEvent,
                    onClick = {
                        enableEvent = !enableEvent
                        if (enableEvent && eventStartAt == null) {
                            eventStartAt = defaultEventStartMillis()
                        }
                    },
                    label = "📅 ${Copy.text(lang, "addEvent")}",
                )
                SaigonPillChip(selected = enablePoll, onClick = { enablePoll = !enablePoll }, label = "📊 ${Copy.text(lang, "addPoll")}")
                SaigonPillChip(selected = enableQuestion, onClick = { enableQuestion = !enableQuestion }, label = "❓ ${Copy.text(lang, "askQuestion")}")
                SaigonPillChip(selected = enableCta, onClick = { enableCta = !enableCta }, label = "📣 ${Copy.text(lang, "addCallToAction")}")
                SaigonPillChip(selected = enableOpenChat, onClick = { enableOpenChat = !enableOpenChat }, label = "💬 ${Copy.text(lang, "addOpenChat")}")
            }

            if (firstCommentEnabled) {
                SaigonTextField(
                    value = firstComment,
                    onValueChange = { if (it.length <= Limits.COMMENT_MAX) firstComment = it },
                    label = "💭 ${Copy.text(lang, "firstComment")}",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val suggestion = composerState.communitySuggestion
            if (message.trim().length >= 8 &&
                suggestion != null &&
                suggestion.name.isNotBlank() &&
                (selectedCommunity == null || selectedCommunity?.id != suggestion.communityId)
            ) {
                val suggestedEmoji = suggestion.emoji.ifBlank { "✨" }
                val label = if (creatingSuggestedCommunity) {
                    Copy.text(lang, "creatingCommunity")
                } else {
                    "${Copy.text(lang, "suggestCommunity")}: $suggestedEmoji ${suggestion.name}"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .dashedBorder(
                            color = tokens.accent,
                            strokeWidth = 1.5.dp,
                            cornerRadius = 999.dp,
                        )
                        .clickable(enabled = !creatingSuggestedCommunity) {
                            val existingId = suggestion.communityId?.takeIf { it.isNotBlank() }
                            if (existingId != null) {
                                selectedCommunity = composerState.communities.firstOrNull { it.id == existingId }
                                    ?: CommunityDto(
                                        id = existingId,
                                        name = suggestion.name,
                                        emoji = suggestedEmoji,
                                        joined = true,
                                    )
                            } else {
                                scope.launch {
                                    creatingSuggestedCommunity = true
                                    val created = shared.composer.createCommunity(suggestion.name, suggestedEmoji)
                                    creatingSuggestedCommunity = false
                                    if (created != null) {
                                        selectedCommunity = created
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = label,
                        color = tokens.text,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            HorizontalDivider(color = tokens.line, thickness = 1.dp)


            if (enableEvent) {
                val startMs = eventStartAt ?: defaultEventStartMillis()
                SaigonCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("📅 ${Copy.text(lang, "addEvent")}", color = tokens.text, fontWeight = FontWeight.ExtraBold)

                        // Start date & time
                        Text(Copy.text(lang, "eventStart"), color = tokens.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            DateTimeChipButton(
                                text = formatSaigonDate(startMs, lang),
                                onClick = { eventDateTimeTarget = EventDateTimeTarget.START_DATE },
                                modifier = Modifier.weight(1f),
                            )
                            DateTimeChipButton(
                                text = formatSaigonClock(startMs),
                                onClick = { eventDateTimeTarget = EventDateTimeTarget.START_TIME },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // End date & time (optional, with clear button)
                        Text(Copy.text(lang, "eventEnd"), color = tokens.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        val endMs = eventEndAt
                        if (endMs == null) {
                            SaigonOutlinedPillButton(
                                text = "+ ${Copy.text(lang, "eventEnd")}",
                                onClick = { eventEndAt = startMs + 2 * 3_600_000L },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                DateTimeChipButton(
                                    text = formatSaigonDate(endMs, lang),
                                    onClick = { eventDateTimeTarget = EventDateTimeTarget.END_DATE },
                                    modifier = Modifier.weight(1f),
                                )
                                DateTimeChipButton(
                                    text = formatSaigonClock(endMs),
                                    onClick = { eventDateTimeTarget = EventDateTimeTarget.END_TIME },
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(tokens.surface)
                                        .border(1.dp, tokens.line, RoundedCornerShape(14.dp))
                                        .clickable { eventEndAt = null },
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = Copy.text(lang, "removeEventEnd"),
                                        tint = tokens.text,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }

                        SaigonTextField(
                            value = eventPriceText,
                            onValueChange = { next ->
                                val digits = next.filter(Char::isDigit)
                                val clamped = digits.toLongOrNull()?.coerceAtMost(Limits.EVENT_PRICE_MAX.toLong())?.toString().orEmpty()
                                eventPriceText = if (digits.isEmpty()) "" else clamped
                            },
                            label = Copy.text(lang, "eventPrice"),
                            placeholder = Copy.text(lang, "eventPriceHint"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                SaigonTextField(
                                    value = opt,
                                    onValueChange = { next ->
                                        if (next.length <= Limits.POLL_OPTION_MAX) {
                                            pollOptions = pollOptions.mapIndexed { i, v -> if (i == idx) next else v }
                                        }
                                    },
                                    placeholder = Copy.text(lang, "pollOption", mapOf("n" to (idx + 1).toString())),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                if (pollOptions.size > 2) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(tokens.surface)
                                            .border(1.dp, tokens.line, RoundedCornerShape(14.dp))
                                            .clickable {
                                                pollOptions = pollOptions.filterIndexed { i, _ -> i != idx }
                                            },
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = Copy.text(lang, "delete"), tint = tokens.text, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
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
                SaigonCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("❓ ${Copy.text(lang, "askQuestion")}", color = tokens.text, fontWeight = FontWeight.ExtraBold)
                        SaigonTextField(
                            value = questionTitle,
                            onValueChange = { if (it.length <= Limits.QUESTION_TITLE_MAX) questionTitle = it },
                            label = Copy.text(lang, "questionTitle"),
                            placeholder = Copy.text(lang, "questionTitleHint"),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (enableCta) {
                SaigonCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📣 ${Copy.text(lang, "addCallToAction")}", color = tokens.text, fontWeight = FontWeight.ExtraBold)
                        SaigonTextField(
                            value = ctaTitle,
                            onValueChange = { if (it.length <= Limits.CTA_TITLE_MAX) ctaTitle = it },
                            label = Copy.text(lang, "callToActionTitle"),
                            placeholder = Copy.text(lang, "callToActionTitleHint"),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SaigonTextField(
                            value = ctaText,
                            onValueChange = { if (it.length <= Limits.CTA_TEXT_MAX) ctaText = it },
                            label = Copy.text(lang, "callToActionText"),
                            placeholder = Copy.text(lang, "callToActionTextHint"),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(Copy.text(lang, "callToActionDestination"), color = tokens.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            CallToAction.destinations.forEach { dest ->
                                SaigonPillChip(
                                    selected = ctaDestination == dest,
                                    onClick = { ctaDestination = dest },
                                    label = ctaDestinationLabel(dest, lang),
                                )
                            }
                        }
                        ctaDestinationHint(ctaDestination, lang)?.let { hint ->
                            Text(hint, color = tokens.muted, fontSize = 13.sp)
                        }
                        when (ctaDestination) {
                            CallToAction.WEBSITE -> {
                                SaigonTextField(
                                    value = ctaUrl,
                                    onValueChange = { if (it.length <= Limits.CTA_URL_MAX) ctaUrl = it },
                                    label = "URL",
                                    placeholder = Copy.text(lang, "callToActionUrlHint"),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            CallToAction.PRODUCT -> {
                                SelectorRowButton(
                                    label = ctaSelectedProduct?.name ?: Copy.text(lang, "chooseProduct"),
                                    isPlaceholder = ctaSelectedProduct == null,
                                    onClick = {
                                        shared.composer.loadMyProducts()
                                        showProductPicker = true
                                    },
                                )
                            }
                            else -> Unit
                        }
                    }
                }
            }

            if (enableOpenChat) {
                SaigonCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("💬 ${Copy.text(lang, "addOpenChat")}", color = tokens.text, fontWeight = FontWeight.ExtraBold)
                        if (!openChatCreateNew) {
                            Text(Copy.text(lang, "groupChat"), color = tokens.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            SelectorRowButton(
                                label = selectedOpenChatGroup?.name?.ifBlank { Copy.text(lang, "unnamedGroupChat") }
                                    ?: Copy.text(lang, "selectGroupChat"),
                                isPlaceholder = selectedOpenChatGroup == null,
                                onClick = {
                                    shared.composer.loadGroupChats()
                                    showGroupPicker = true
                                },
                            )
                        }
                        SaigonSwitch(
                            checked = openChatCreateNew,
                            onCheckedChange = { openChatCreateNew = it },
                            label = Copy.text(lang, "createGroupChat"),
                        )
                        if (openChatCreateNew) {
                            SaigonTextField(
                                value = openChatName,
                                onValueChange = { if (it.length <= Limits.CHAT_NAME_MAX) openChatName = it },
                                label = Copy.text(lang, "groupName"),
                                placeholder = Copy.text(lang, "groupNameHint"),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        // Available for (editable numeric input + stepper + suffix)
                        OpenChatNumberField(
                            label = Copy.text(lang, "openChatExpiration"),
                            text = openChatHoursText,
                            min = Limits.OPEN_CHAT_HOURS_MIN,
                            max = Limits.OPEN_CHAT_HOURS_MAX,
                            optional = false,
                            suffix = Copy.text(lang, "hoursUnit"),
                            onTextChange = { raw ->
                                val digits = raw.filter(Char::isDigit)
                                openChatHoursText = digits
                                digits.toIntOrNull()?.takeIf { it in Limits.OPEN_CHAT_HOURS_MIN..Limits.OPEN_CHAT_HOURS_MAX }?.let {
                                    openChatHours = it
                                }
                            },
                            onNormalize = {
                                val bounded = (openChatHoursText.toIntOrNull() ?: Limits.OPEN_CHAT_HOURS_MIN)
                                    .coerceIn(Limits.OPEN_CHAT_HOURS_MIN, Limits.OPEN_CHAT_HOURS_MAX)
                                openChatHours = bounded
                                openChatHoursText = bounded.toString()
                            },
                            onStep = { delta ->
                                val base = openChatHoursText.toIntOrNull() ?: openChatHours
                                val bounded = (base + delta).coerceIn(Limits.OPEN_CHAT_HOURS_MIN, Limits.OPEN_CHAT_HOURS_MAX)
                                openChatHours = bounded
                                openChatHoursText = bounded.toString()
                            },
                            decreaseEnabled = (openChatHoursText.toIntOrNull() ?: openChatHours) > Limits.OPEN_CHAT_HOURS_MIN,
                            increaseEnabled = (openChatHoursText.toIntOrNull() ?: openChatHours) < Limits.OPEN_CHAT_HOURS_MAX,
                        )

                        // Total join slots (optional editable numeric input + stepper)
                        OpenChatNumberField(
                            label = Copy.text(lang, "openChatRemaining"),
                            text = openChatRemainingText,
                            min = Limits.OPEN_CHAT_REMAINING_MIN,
                            max = Limits.OPEN_CHAT_REMAINING_MAX,
                            optional = true,
                            placeholder = Copy.text(lang, "unlimitedOpenChatJoins"),
                            onTextChange = { raw ->
                                val digits = raw.filter(Char::isDigit)
                                openChatRemainingText = digits
                                if (digits.isEmpty()) {
                                    openChatRemaining = null
                                } else {
                                    digits.toIntOrNull()?.takeIf { it in Limits.OPEN_CHAT_REMAINING_MIN..Limits.OPEN_CHAT_REMAINING_MAX }?.let {
                                        openChatRemaining = it
                                    }
                                }
                            },
                            onNormalize = {
                                if (openChatRemainingText.isBlank()) {
                                    openChatRemaining = null
                                    openChatRemainingText = ""
                                } else {
                                    val bounded = (openChatRemainingText.toIntOrNull() ?: Limits.OPEN_CHAT_REMAINING_MIN)
                                        .coerceIn(Limits.OPEN_CHAT_REMAINING_MIN, Limits.OPEN_CHAT_REMAINING_MAX)
                                    openChatRemaining = bounded
                                    openChatRemainingText = bounded.toString()
                                }
                            },
                            onStep = { delta ->
                                val cur = if (openChatRemainingText.isBlank()) null else (openChatRemainingText.toIntOrNull() ?: openChatRemaining)
                                if (cur == null) {
                                    if (delta > 0) {
                                        openChatRemaining = Limits.OPEN_CHAT_REMAINING_MIN
                                        openChatRemainingText = Limits.OPEN_CHAT_REMAINING_MIN.toString()
                                    }
                                } else if (delta < 0 && cur <= Limits.OPEN_CHAT_REMAINING_MIN) {
                                    openChatRemaining = null
                                    openChatRemainingText = ""
                                } else {
                                    val bounded = (cur + delta).coerceIn(Limits.OPEN_CHAT_REMAINING_MIN, Limits.OPEN_CHAT_REMAINING_MAX)
                                    openChatRemaining = bounded
                                    openChatRemainingText = bounded.toString()
                                }
                            },
                            decreaseEnabled = openChatRemainingText.isNotBlank() || openChatRemaining != null,
                            increaseEnabled = (openChatRemainingText.toIntOrNull() ?: openChatRemaining ?: 0) < Limits.OPEN_CHAT_REMAINING_MAX,
                        )
                    }
                }
            }
        }
    }

    // Event Date/Time Pickers
    eventDateTimeTarget?.let { target ->
        val baseMillis = when (target) {
            EventDateTimeTarget.START_DATE, EventDateTimeTarget.START_TIME -> eventStartAt ?: defaultEventStartMillis()
            EventDateTimeTarget.END_DATE, EventDateTimeTarget.END_TIME -> eventEndAt ?: (eventStartAt ?: defaultEventStartMillis()) + 2 * 3_600_000L
        }
        when (target) {
            EventDateTimeTarget.START_DATE, EventDateTimeTarget.END_DATE -> {
                val cal = Calendar.getInstance(saigonTimeZone).apply { timeInMillis = baseMillis }
                val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    clear()
                    set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                }
                val dateState = rememberDatePickerState(initialSelectedDateMillis = utcCal.timeInMillis)
                DatePickerDialog(
                    onDismissRequest = { eventDateTimeTarget = null },
                    confirmButton = {
                        SaigonTextButton(
                            text = Copy.text(lang, "okay"),
                            accent = true,
                            onClick = {
                                val pickedUtc = dateState.selectedDateMillis
                                if (pickedUtc != null) {
                                    val chosen = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickedUtc }
                                    val updated = Calendar.getInstance(saigonTimeZone).apply {
                                        timeInMillis = baseMillis
                                        set(Calendar.YEAR, chosen.get(Calendar.YEAR))
                                        set(Calendar.MONTH, chosen.get(Calendar.MONTH))
                                        set(Calendar.DAY_OF_MONTH, chosen.get(Calendar.DAY_OF_MONTH))
                                    }.timeInMillis
                                    if (target == EventDateTimeTarget.START_DATE) eventStartAt = updated else eventEndAt = updated
                                }
                                eventDateTimeTarget = null
                            },
                        )
                    },
                    dismissButton = {
                        SaigonTextButton(text = Copy.text(lang, "cancel"), onClick = { eventDateTimeTarget = null })
                    },
                ) {
                    DatePicker(state = dateState)
                }
            }
            EventDateTimeTarget.START_TIME, EventDateTimeTarget.END_TIME -> {
                val cal = Calendar.getInstance(saigonTimeZone).apply { timeInMillis = baseMillis }
                val timeState = rememberTimePickerState(
                    initialHour = cal.get(Calendar.HOUR_OF_DAY),
                    initialMinute = cal.get(Calendar.MINUTE),
                    is24Hour = true,
                )
                SaigonDialog(onDismiss = { eventDateTimeTarget = null }) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            Copy.text(lang, if (target == EventDateTimeTarget.START_TIME) "eventStart" else "eventEnd"),
                            color = tokens.text,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.fillMaxWidth().padding(end = 36.dp),
                        )
                        TimePicker(state = timeState)
                        SaigonPrimaryButton(
                            text = Copy.text(lang, "okay"),
                            onClick = {
                                val updated = Calendar.getInstance(saigonTimeZone).apply {
                                    timeInMillis = baseMillis
                                    set(Calendar.HOUR_OF_DAY, timeState.hour)
                                    set(Calendar.MINUTE, timeState.minute)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                                if (target == EventDateTimeTarget.START_TIME) eventStartAt = updated else eventEndAt = updated
                                eventDateTimeTarget = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SaigonTextButton(text = Copy.text(lang, "cancel"), onClick = { eventDateTimeTarget = null }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    if (showLocationDialog) {
        LocationPickerDialog(
            initialQuery = "",
            currentPlace = selectedPlace,
            recent = composerState.recentPlaces,
            results = composerState.placeResults,
            mapsApiKey = sessionState.mapsKey,
            language = lang,
            onSearch = { q -> shared.composer.searchPlaces(q, selectedCommunity?.id) },
            onResolvePlaceDetails = { placeId -> shared.composer.resolvePlaceDetails(placeId) },
            onCreateCustomPlace = { name, lat, lng -> shared.composer.createCustomPlace(name, lat, lng) },
            onSelectPlace = { place ->
                selectedPlace = place
                locationText = place.name
                showLocationDialog = false
            },
            onDismiss = { showLocationDialog = false },
        )
    }

    if (showCommunitySheet) {
        CommunityPickerSheet(
            communities = composerState.communities,
            selected = selectedCommunity,
            language = lang,
            onSearch = { q -> shared.composer.loadCommunities(q) },
            onSuggestEmoji = { name ->
                when (val res = shared.api.suggestCommunityEmoji(name)) {
                    is ApiResult.Success -> res.value.emoji
                    is ApiResult.Failure -> null
                }
            },
            onCreate = { name, emoji ->
                shared.composer.createCommunity(name, emoji)?.also { created ->
                    selectedCommunity = created
                    showCommunitySheet = false
                }
            },
            onSelect = {
                selectedCommunity = it
                if (it == null) communityOnly = false
                showCommunitySheet = false
            },
            onDismiss = { showCommunitySheet = false },
        )
    }

    if (showProductPicker) {
        ProductChooserDialog(
            products = composerState.myProducts,
            isLoading = composerState.isLoadingProducts,
            selectedId = ctaSelectedProduct?.id,
            language = lang,
            onSelect = { product ->
                ctaSelectedProduct = product
                showProductPicker = false
            },
            onDismiss = { showProductPicker = false },
        )
    }

    if (showGroupPicker) {
        GroupChatChooserDialog(
            groups = composerState.myGroupChats,
            isLoading = composerState.isLoadingGroupChats,
            selectedId = selectedOpenChatGroup?.id,
            language = lang,
            onSelect = { group ->
                selectedOpenChatGroup = group
                showGroupPicker = false
            },
            onDismiss = { showGroupPicker = false },
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
private fun SelectorRowButton(
    label: String,
    isPlaceholder: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.surface)
            .border(2.dp, tokens.line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            color = if (isPlaceholder) tokens.muted else tokens.text,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = tokens.muted)
    }
}

@Composable
private fun OpenChatNumberField(
    label: String,
    text: String,
    min: Int,
    max: Int,
    optional: Boolean,
    placeholder: String = "",
    suffix: String? = null,
    onTextChange: (String) -> Unit,
    onNormalize: () -> Unit,
    onStep: (Int) -> Unit,
    decreaseEnabled: Boolean,
    increaseEnabled: Boolean,
) {
    val tokens = LocalSaigonTokens.current
    var hadFocus by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(label, color = tokens.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            StepperButton("−", enabled = decreaseEnabled) { onStep(-1) }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.surface)
                    .border(1.dp, tokens.line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                if (text.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        color = tokens.muted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = LivvicFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.text,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(tokens.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                hadFocus = true
                            } else if (hadFocus) {
                                hadFocus = false
                                val parsed = text.toIntOrNull()
                                if ((!optional && (parsed == null || parsed !in min..max)) ||
                                    (optional && text.isNotBlank() && (parsed == null || parsed !in min..max))
                                ) {
                                    onNormalize()
                                } else if (!optional && parsed != null) {
                                    onNormalize()
                                }
                            }
                        },
                )
            }
            StepperButton("+", enabled = increaseEnabled) { onStep(1) }
            if (!suffix.isNullOrBlank()) {
                Text(
                    text = suffix,
                    color = tokens.muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StepperButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.surface)
            .border(1.dp, tokens.line, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            symbol,
            color = if (enabled) tokens.text else tokens.muted.copy(alpha = 0.45f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DateTimeChipButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalSaigonTokens.current
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.surface)
            .border(2.dp, tokens.line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(text, color = tokens.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun ctaDestinationLabel(destination: String, language: Language): String = when (destination) {
    CallToAction.MESSAGE -> Copy.text(language, "ctaSendMessage")
    CallToAction.ZALO -> Copy.text(language, "ctaZaloMessage")
    CallToAction.WEBSITE -> Copy.text(language, "ctaOpenWebsite")
    CallToAction.PROFILE -> Copy.text(language, "ctaShowProfile")
    CallToAction.PRODUCT -> Copy.text(language, "ctaShowProduct")
    else -> destination.replaceFirstChar { it.uppercase() }
}

private fun ctaDestinationHint(destination: String, language: Language): String? = when (destination) {
    CallToAction.MESSAGE -> Copy.text(language, "ctaSendMessageHint")
    CallToAction.ZALO -> Copy.text(language, "ctaZaloMessageHint")
    CallToAction.PROFILE -> Copy.text(language, "ctaShowProfileHint")
    CallToAction.PRODUCT -> Copy.text(language, "ctaShowProductHint")
    else -> null
}

private val saigonTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")

private fun defaultEventStartMillis(): Long {
    val cal = Calendar.getInstance(saigonTimeZone).apply {
        timeInMillis = currentTimeMillis() + 4 * 3_600_000L
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

private fun formatSaigonDate(epochMs: Long, language: Language): String {
    val locale = if (language == Language.VI) Locale.forLanguageTag("vi-VN") else Locale.ENGLISH
    return SimpleDateFormat("EEE, MMM d", locale).apply { timeZone = saigonTimeZone }.format(Date(epochMs))
}

private fun formatSaigonClock(epochMs: Long): String {
    return SimpleDateFormat("HH:mm", Locale.ENGLISH).apply { timeZone = saigonTimeZone }.format(Date(epochMs))
}

@Composable
private fun ProductChooserDialog(
    products: List<ProductDto>,
    isLoading: Boolean,
    selectedId: String?,
    language: Language,
    onSelect: (ProductDto) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(products, query) {
        if (query.isBlank()) products else products.filter { it.name.contains(query, ignoreCase = true) }
    }
    SaigonDialog(onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                Copy.text(language, "chooseProduct"),
                color = tokens.text,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 36.dp),
            )
            SaigonTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = Copy.text(language, "searchProducts"),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                isLoading && products.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        SaigonSpinner(size = 24.dp)
                    }
                }
                filtered.isEmpty() -> {
                    Text(
                        Copy.text(language, if (products.isEmpty()) "noProducts" else "noProductMatches"),
                        color = tokens.muted,
                        fontSize = 13.sp,
                    )
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 300.dp),
                    ) {
                        items(filtered, key = { it.id }) { product ->
                            val isSel = product.id == selectedId
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(tokens.bg)
                                    .border(2.dp, if (isSel) tokens.accent else tokens.line, RoundedCornerShape(14.dp))
                                    .clickable { onSelect(product) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    product.name,
                                    color = if (isSel) tokens.accent else tokens.text,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                )
                                Text(formatVnd(product.price), color = tokens.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            SaigonTextButton(text = Copy.text(language, "cancel"), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun GroupChatChooserDialog(
    groups: List<ChatDto>,
    isLoading: Boolean,
    selectedId: String?,
    language: Language,
    onSelect: (ChatDto) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(groups, query) {
        if (query.isBlank()) {
            groups
        } else {
            groups.filter {
                val label = it.name.ifBlank { Copy.text(language, "unnamedGroupChat") }
                label.contains(query, ignoreCase = true)
            }
        }
    }
    SaigonDialog(onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                Copy.text(language, "selectGroupChat"),
                color = tokens.text,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 36.dp),
            )
            SaigonTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = Copy.text(language, "searchGroups"),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                isLoading && groups.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        SaigonSpinner(size = 24.dp)
                    }
                }
                filtered.isEmpty() -> {
                    Text(
                        Copy.text(language, if (groups.isEmpty()) "noGroups" else "noGroupMatches"),
                        color = tokens.muted,
                        fontSize = 13.sp,
                    )
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 300.dp),
                    ) {
                        items(filtered, key = { it.id }) { group ->
                            val isSel = group.id == selectedId
                            val label = group.name.ifBlank { Copy.text(language, "unnamedGroupChat") }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(tokens.bg)
                                    .border(2.dp, if (isSel) tokens.accent else tokens.line, RoundedCornerShape(14.dp))
                                    .clickable { onSelect(group) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    label,
                                    color = if (isSel) tokens.accent else tokens.text,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                )
                                if (group.memberCount > 0) {
                                    Text(
                                        "${group.memberCount} ${Copy.text(language, "chatMembers")}",
                                        color = tokens.muted,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            SaigonTextButton(text = Copy.text(language, "cancel"), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CommunityPickerSheet(
    communities: List<CommunityDto>,
    selected: CommunityDto?,
    language: Language,
    onSearch: (String) -> Unit,
    onSuggestEmoji: suspend (String) -> String?,
    onCreate: suspend (String, String) -> CommunityDto?,
    onSelect: (CommunityDto?) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newEmoji by remember { mutableStateOf("") }
    var emojiManual by remember { mutableStateOf(false) }
    var suggestingEmoji by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        delay(200)
        onSearch(query)
    }

    LaunchedEffect(newName, emojiManual) {
        if (emojiManual) return@LaunchedEffect
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            suggestingEmoji = false
            return@LaunchedEffect
        }
        delay(2000)
        suggestingEmoji = true
        val suggested = onSuggestEmoji(trimmed)
        if (!emojiManual && newName.trim() == trimmed && !suggested.isNullOrBlank()) {
            newEmoji = suggested
        }
        suggestingEmoji = false
    }

    val filtered = remember(communities, query) {
        if (query.isBlank()) communities else communities.filter { it.name.contains(query, ignoreCase = true) }
    }

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
                    onValueChange = {
                        newEmoji = it.take(4)
                        emojiManual = newEmoji.isNotBlank()
                    },
                    placeholder = if (suggestingEmoji) "…" else "✨",
                    singleLine = true,
                    modifier = Modifier.width(68.dp),
                )
                SaigonTextField(
                    value = newName,
                    onValueChange = {
                        newName = it
                        createError = null
                    },
                    placeholder = Copy.text(language, "createCommunity"),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                SaigonPrimaryButton(
                    text = "+",
                    enabled = !creating,
                    loading = creating,
                    onClick = {
                        val cleanName = newName.trim()
                        if (cleanName.isEmpty()) {
                            createError = Copy.text(language, "invalidCommunity")
                            return@SaigonPrimaryButton
                        }
                        scope.launch {
                            creating = true
                            createError = null
                            val created = onCreate(cleanName, newEmoji.trim())
                            creating = false
                            if (created != null) {
                                newName = ""
                                newEmoji = ""
                                emojiManual = false
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            createError?.let { err ->
                Text(err, color = tokens.danger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            SaigonTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = Copy.text(language, "searchCommunities"),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (selected != null && query.isBlank()) {
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
                items(filtered, key = { it.id }) { comm ->
                    val isSelected = comm.id == selected?.id
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(tokens.bg)
                            .border(2.dp, if (isSelected) tokens.accent else tokens.line, RoundedCornerShape(14.dp))
                            .clickable { onSelect(comm) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "${comm.emoji} ${comm.name}",
                                color = if (isSelected) tokens.accent else tokens.text,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${comm.memberCount}", color = tokens.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (comm.description.isNotBlank()) {
                            Text(
                                comm.description,
                                color = tokens.muted,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
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
                    dimWhenDisabled = false,
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
                            if (avatar.status != "ready") {
                                val statusText = when (avatar.status) {
                                    "generating" -> Copy.text(language, "generating")
                                    "failed" -> avatar.error?.takeIf { it.isNotBlank() }?.let {
                                        Copy.text(language, "avatarFailedDetail", mapOf("reason" to it))
                                    } ?: Copy.text(language, "avatarFailed")
                                    else -> avatar.status
                                }
                                Text(
                                    statusText,
                                    color = if (avatar.status == "failed") tokens.danger else tokens.muted,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        if (avatar.status == "failed") {
                            IconButton(onClick = { onRetry(avatar.id) }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = tokens.accent)
                            }
                        }
                        IconButton(onClick = { pendingDeleteId = avatar.id }) {
                            Icon(Icons.Default.Delete, contentDescription = Copy.text(language, "delete"), tint = tokens.danger)
                        }
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { targetId ->
        SaigonConfirmDialog(
            title = Copy.text(language, "deleteAvatarAsk"),
            hint = Copy.text(language, "deleteAvatarHint"),
            confirmLabel = Copy.text(language, "delete"),
            cancelLabel = Copy.text(language, "cancel"),
            onConfirm = {
                onDelete(targetId)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
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
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

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
                    dimWhenDisabled = false,
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
                            if (track.status != "ready") {
                                val statusText = when (track.status) {
                                    "generating" -> Copy.text(language, "generatingMusic")
                                    "failed" -> track.error?.takeIf { it.isNotBlank() }?.let {
                                        Copy.text(language, "musicFailedDetail", mapOf("reason" to it))
                                    } ?: Copy.text(language, "musicFailed")
                                    else -> track.status
                                }
                                Text(
                                    statusText,
                                    color = if (track.status == "failed") tokens.danger else tokens.muted,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        if (track.status == "failed") {
                            IconButton(onClick = { onRetry(track.id) }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = tokens.accent)
                            }
                        }
                        IconButton(onClick = { pendingDeleteId = track.id }) {
                            Icon(Icons.Default.Delete, contentDescription = Copy.text(language, "delete"), tint = tokens.danger)
                        }
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { targetId ->
        SaigonConfirmDialog(
            title = Copy.text(language, "dismissMusicAsk"),
            hint = Copy.text(language, "dismissMusicHint"),
            confirmLabel = Copy.text(language, "delete"),
            cancelLabel = Copy.text(language, "cancel"),
            onConfirm = {
                onDelete(targetId)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}
