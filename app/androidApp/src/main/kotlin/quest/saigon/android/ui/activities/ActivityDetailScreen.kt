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
import quest.saigon.android.ui.components.LinkifiedText
import quest.saigon.android.ui.components.PhotoViewerDialog
import quest.saigon.android.ui.components.SaigonCard
import quest.saigon.android.ui.components.SaigonDialog
import quest.saigon.android.ui.components.SaigonIconButton
import quest.saigon.android.ui.components.SaigonPageShell
import quest.saigon.android.ui.components.SaigonPrimaryButton
import quest.saigon.android.ui.components.SaigonSecondaryButton
import quest.saigon.android.ui.components.SaigonSpinner
import quest.saigon.android.ui.components.SaigonTextButton
import quest.saigon.android.ui.components.SaigonTextField
import quest.saigon.android.ui.components.SaigonTinyPill
import quest.saigon.android.ui.components.UserAvatar
import quest.saigon.android.ui.components.dashedBorder
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.UpdateActivityRequest
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
    var zoomUrl by remember { mutableStateOf<String?>(null) }
    var selectedChatIds by remember { mutableStateOf<Set<String>>(emptySet()) }

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

    SaigonPageShell(
        title = act?.let { "${it.emoji} ${ActivityStore.localizedName(it, lang)}" } ?: Copy.text(lang, "activityAttachment"),
        onBack = {
            shared.activity.closeActivity()
            onBack()
        },
        backContentDescription = Copy.text(lang, "back"),
        topActions = {
            if (isCreator) {
                SaigonIconButton(onClick = { showEdit = true }, overlay = false) {
                    Icon(Icons.Default.Edit, contentDescription = Copy.text(lang, "edit"), modifier = Modifier.size(18.dp))
                }
                SaigonIconButton(
                    onClick = {
                        scope.launch {
                            if (shared.activity.deleteActivity(act.id)) onBack()
                        }
                    },
                    overlay = false,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = Copy.text(lang, "delete"), tint = tokens.danger, modifier = Modifier.size(18.dp))
                }
            }
        },
    ) {
        if (act == null) {
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
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${act.emoji} ${ActivityStore.localizedName(act, lang)}", color = tokens.text, style = MaterialTheme.typography.headlineSmall)
                        Text(act.locationName, fontSize = 14.sp, fontWeight = FontWeight.Black, color = tokens.text)
                        val desc = ActivityStore.localizedDescription(act, lang)
                        if (desc.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            LinkifiedText(text = desc, color = tokens.muted, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                item {
                    val label = "${Copy.text(lang, "imInterested")} (${act.interestedCount})"
                    val onInterestClick = {
                        if (sessionState.me == null) onRequireAuth()
                        else {
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

                // .activity-schedule-table
                item {
                    SaigonCard(borderWidth = 1.dp, cornerRadius = 14.dp, modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(Copy.text(lang, "activitySchedule"), color = tokens.muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            if (act.schedule.isEmpty()) {
                                Text(Copy.text(lang, "alwaysAvailable"), color = tokens.muted, fontSize = 14.sp)
                            } else {
                                act.schedule.forEachIndexed { idx, slot ->
                                    val dayKey = when (slot.dayOfWeek) {
                                        1 -> "weekdayMon"
                                        2 -> "weekdayTue"
                                        3 -> "weekdayWed"
                                        4 -> "weekdayThu"
                                        5 -> "weekdayFri"
                                        6 -> "weekdaySat"
                                        else -> "weekdaySun"
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    ) {
                                        Text(Copy.text(lang, dayKey), color = tokens.text, fontWeight = FontWeight.Bold)
                                        Text("${formatMinutes(slot.startMinutes)} - ${formatMinutes(slot.endMinutes)}", color = tokens.muted)
                                    }
                                    if (idx < act.schedule.lastIndex) {
                                        HorizontalDivider(color = tokens.line, thickness = 1.dp)
                                    }
                                }
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
                            activityState.photos.forEach { photo ->
                                Box(modifier = Modifier.size(82.dp)) {
                                    AsyncImage(
                                        model = photo.url,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(14.dp))
                                            .border(1.dp, tokens.line, RoundedCornerShape(14.dp))
                                            .clickable { zoomUrl = photo.url },
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
                                                .clickable { scope.launch { shared.activity.deletePhoto(act.id, photo.id) } },
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
                        Text(Copy.text(lang, "peopleInterested"), color = tokens.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (selectedChatIds.isNotEmpty()) {
                            SaigonPrimaryButton(
                                text = Copy.text(lang, "startGroupChat"),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                onClick = {
                                    if (sessionState.me == null) onRequireAuth()
                                    else scope.launch {
                                        shared.activity.startActivityChat(act.id, selectedChatIds.toList())?.let { chat ->
                                            onNavigate(AppRoute.Chat(chat.id))
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                // .activity-people .attendee-row
                items(act.interestedPeople, key = { it.id }) { person ->
                    val checked = person.id in selectedChatIds
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
                            .clickable { onNavigate(AppRoute.User(person.id)) }
                            .padding(8.dp),
                    ) {
                        UserAvatar(photoUrl = person.photoUrl, name = person.name, size = 40.dp, borderWidth = 0.dp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(person.name, color = tokens.text, fontWeight = FontWeight.Bold)
                            if (person.host) SaigonTinyPill(label = Copy.text(lang, "host"), selected = true)
                        }
                        if (sessionState.me != null && person.id != sessionState.me?.id) {
                            Checkbox(
                                checked = checked,
                                colors = CheckboxDefaults.colors(checkedColor = tokens.accent),
                                onCheckedChange = {
                                    selectedChatIds = if (it) selectedChatIds + person.id else selectedChatIds - person.id
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEdit && act != null) {
        var name by remember { mutableStateOf(act.name) }
        var emoji by remember { mutableStateOf(act.emoji) }
        var description by remember { mutableStateOf(act.description) }
        SaigonDialog(onDismiss = { showEdit = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Copy.text(lang, "editActivity"), color = tokens.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 36.dp))
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
                            val updated = shared.activity.updateActivity(
                                act.id,
                                UpdateActivityRequest(
                                    emoji = emoji.trim(),
                                    name = name.trim(),
                                    description = description.trim(),
                                    hostUserId = act.hostUserId,
                                    schedule = act.schedule,
                                ),
                            )
                            if (updated != null) showEdit = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SaigonTextButton(text = Copy.text(lang, "cancel"), onClick = { showEdit = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    zoomUrl?.let { PhotoViewerDialog(photoUrl = it, onDismiss = { zoomUrl = null }) }
}

private fun formatMinutes(minutes: Int): String {
    val h = (minutes / 60).toString().padStart(2, '0')
    val m = (minutes % 60).toString().padStart(2, '0')
    return "$h:$m"
}
