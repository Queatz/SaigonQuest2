package quest.saigon.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import quest.saigon.Copy
import quest.saigon.Language
import quest.saigon.Limits
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.ActivityDto
import quest.saigon.shared.model.ActivityScheduleSlotDto
import quest.saigon.shared.model.CreateActivityRequest
import quest.saigon.shared.model.EventPersonDto
import quest.saigon.shared.model.UpdateActivityRequest
import quest.saigon.shared.network.ApiResult

private data class DayScheduleState(
    val dayOfWeek: Int,
    val enabled: Boolean,
    val startMinutes: Int,
    val endMinutes: Int,
)

private data class TimePickTarget(
    val dayOfWeek: Int?, // null = master
    val isEnd: Boolean,
    val initialMinutes: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityEditorDialog(
    existing: ActivityDto?,
    locationName: String,
    initialName: String = "",
    locationId: String? = null,
    placeId: String? = null,
    lat: Double? = null,
    lng: Double? = null,
    shared: TownSharedClient,
    language: Language,
    onSaved: (ActivityDto) -> Unit,
    onDismiss: () -> Unit,
) {
    val sessionState by shared.session.state.collectAsState()
    val actState by shared.activity.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()

    var name by remember(existing?.id, initialName) { mutableStateOf(existing?.name ?: initialName) }
    var emoji by remember(existing?.id) { mutableStateOf(existing?.emoji.orEmpty()) }
    var emojiManuallyEdited by remember(existing?.id) { mutableStateOf(existing != null) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }

    var scheduled by remember(existing?.id) { mutableStateOf(existing?.schedule?.isNotEmpty() == true) }
    var masterStart by remember(existing?.id) { mutableIntStateOf(existing?.schedule?.firstOrNull()?.startMinutes ?: 9 * 60) }
    var masterEnd by remember(existing?.id) { mutableIntStateOf(existing?.schedule?.firstOrNull()?.endMinutes ?: 17 * 60) }
    var days by remember(existing?.id) {
        val byDay = existing?.schedule?.associateBy { it.dayOfWeek }.orEmpty()
        mutableStateOf(
            (1..7).map { d ->
                val slot = byDay[d]
                DayScheduleState(
                    dayOfWeek = d,
                    enabled = slot != null,
                    startMinutes = slot?.startMinutes ?: 9 * 60,
                    endMinutes = slot?.endMinutes ?: 17 * 60,
                )
            },
        )
    }

    var hostUserId by remember(existing?.id) { mutableStateOf(existing?.hostUserId) }
    var hostName by remember(existing?.id) { mutableStateOf(existing?.host?.name) }
    var interested by remember(existing?.id) { mutableStateOf(true) }

    var enName by remember(existing?.id) { mutableStateOf(existing?.translations?.en?.get("name").orEmpty()) }
    var enDescription by remember(existing?.id) { mutableStateOf(existing?.translations?.en?.get("description").orEmpty()) }
    var viName by remember(existing?.id) { mutableStateOf(existing?.translations?.vi?.get("name").orEmpty()) }
    var viDescription by remember(existing?.id) { mutableStateOf(existing?.translations?.vi?.get("description").orEmpty()) }
    var translationStatus by remember(existing?.id) { mutableStateOf(existing?.translations?.status.orEmpty()) }

    var showHostPicker by remember { mutableStateOf(false) }
    var timePickTarget by remember { mutableStateOf<TimePickTarget?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(name, emojiManuallyEdited, existing?.id) {
        if (existing != null || emojiManuallyEdited || name.trim().length < 2) return@LaunchedEffect
        delay(260)
        val suggested = shared.activity.suggestEmoji(name.trim())
        if (!emojiManuallyEdited && !suggested.isNullOrBlank()) {
            emoji = suggested
        }
    }

    LaunchedEffect(existing?.id, translationStatus) {
        val actId = existing?.id ?: return@LaunchedEffect
        if (translationStatus != "pending") return@LaunchedEffect
        repeat(10) {
            delay(1_200)
            val res = shared.api.getActivity(actId)
            if (res is ApiResult.Success) {
                val t = res.value.translations
                translationStatus = t?.status.orEmpty()
                if (t != null && t.status != "pending") {
                    enName = t.en["name"].orEmpty()
                    enDescription = t.en["description"].orEmpty()
                    viName = t.vi["name"].orEmpty()
                    viDescription = t.vi["description"].orEmpty()
                    return@LaunchedEffect
                }
            }
        }
    }

    val errorText = localError ?: actState.error

    SaigonDialog(onDismiss = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                Copy.text(language, if (existing == null) "createActivity" else "editActivity"),
                color = tokens.text,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 36.dp),
            )
            if (locationName.isNotBlank()) {
                Text("📍 $locationName", color = tokens.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                SaigonTextField(
                    value = emoji,
                    onValueChange = {
                        emojiManuallyEdited = true
                        emoji = it
                    },
                    label = Copy.text(language, "activityEmoji"),
                    placeholder = "✨",
                    singleLine = true,
                    modifier = Modifier.width(84.dp),
                )
                SaigonTextField(
                    value = name,
                    onValueChange = { if (it.length <= Limits.ACTIVITY_NAME_MAX) { name = it; localError = null } },
                    label = Copy.text(language, "activityName"),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            SaigonTextField(
                value = description,
                onValueChange = { if (it.length <= Limits.ACTIVITY_DESCRIPTION_MAX) { description = it; localError = null } },
                label = Copy.text(language, "activityDescription"),
                placeholder = Copy.text(language, "whatDoPeopleDoHere"),
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            // Translations review when editing
            if (existing != null) {
                when (translationStatus) {
                    "pending" -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SaigonSpinner(size = 16.dp)
                            Text(Copy.text(language, "translating"), color = tokens.muted, fontSize = 13.sp)
                        }
                    }
                    "failed", "ready" -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(tokens.surface)
                                .border(1.dp, tokens.line, RoundedCornerShape(14.dp))
                                .padding(10.dp),
                        ) {
                            if (translationStatus == "failed") {
                                Text(Copy.text(language, "translationFailed"), color = tokens.muted, fontSize = 12.sp)
                            }
                            Text(Copy.text(language, "languageEn"), color = tokens.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            SaigonTextField(
                                value = enName,
                                onValueChange = { if (it.length <= Limits.ACTIVITY_NAME_MAX) enName = it },
                                label = Copy.text(language, "activityName"),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            SaigonTextField(
                                value = enDescription,
                                onValueChange = { if (it.length <= Limits.ACTIVITY_DESCRIPTION_MAX) enDescription = it },
                                label = Copy.text(language, "activityDescription"),
                                singleLine = false,
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(Copy.text(language, "languageVi"), color = tokens.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            SaigonTextField(
                                value = viName,
                                onValueChange = { if (it.length <= Limits.ACTIVITY_NAME_MAX) viName = it },
                                label = Copy.text(language, "activityName"),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            SaigonTextField(
                                value = viDescription,
                                onValueChange = { if (it.length <= Limits.ACTIVITY_DESCRIPTION_MAX) viDescription = it },
                                label = Copy.text(language, "activityDescription"),
                                singleLine = false,
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // Schedule section
            Text(Copy.text(language, "activitySchedule"), color = tokens.text, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SaigonPillChip(
                    selected = !scheduled,
                    onClick = { scheduled = false; localError = null },
                    label = Copy.text(language, "alwaysAvailable"),
                )
                SaigonPillChip(
                    selected = scheduled,
                    onClick = { scheduled = true; localError = null },
                    label = Copy.text(language, "scheduled"),
                )
            }

            if (scheduled) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, tokens.line, RoundedCornerShape(14.dp))
                        .padding(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ScheduleTimeChip(
                            text = formatScheduleMinutes(masterStart),
                            onClick = { timePickTarget = TimePickTarget(null, false, masterStart) },
                        )
                        Text("–", color = tokens.muted, fontWeight = FontWeight.Bold)
                        ScheduleTimeChip(
                            text = formatScheduleMinutes(masterEnd),
                            onClick = { timePickTarget = TimePickTarget(null, true, masterEnd) },
                        )
                        Spacer(Modifier.weight(1f))
                        SaigonTinyPill(
                            label = Copy.text(language, "applyTimesToAllDays"),
                            selected = true,
                            onClick = {
                                days = days.map { it.copy(startMinutes = masterStart, endMinutes = masterEnd) }
                            },
                        )
                    }
                    days.forEach { d ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Checkbox(
                                checked = d.enabled,
                                onCheckedChange = { checked ->
                                    days = days.map { if (it.dayOfWeek == d.dayOfWeek) it.copy(enabled = checked) else it }
                                    localError = null
                                },
                                colors = CheckboxDefaults.colors(checkedColor = tokens.accent),
                                modifier = Modifier.size(32.dp),
                            )
                            Text(
                                Copy.text(language, weekdayCopyKey(d.dayOfWeek)),
                                color = tokens.text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.width(34.dp),
                            )
                            if (d.enabled) {
                                ScheduleTimeChip(
                                    text = formatScheduleMinutes(d.startMinutes),
                                    onClick = { timePickTarget = TimePickTarget(d.dayOfWeek, false, d.startMinutes) },
                                )
                                Text("–", color = tokens.muted)
                                ScheduleTimeChip(
                                    text = formatScheduleMinutes(d.endMinutes),
                                    onClick = { timePickTarget = TimePickTarget(d.dayOfWeek, true, d.endMinutes) },
                                )
                            } else {
                                Text("—", color = tokens.muted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Host picker
            Text(Copy.text(language, "activityHostOptional"), color = tokens.text, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val resolvedHostLabel = when {
                    hostUserId == null -> Copy.text(language, "chooseHost")
                    hostUserId == sessionState.me?.id -> Copy.text(language, "me")
                    !hostName.isNullOrBlank() -> hostName!!
                    else -> Copy.text(language, "chooseHost")
                }
                SaigonOutlinedPillButton(
                    text = resolvedHostLabel,
                    onClick = { showHostPicker = true },
                    modifier = Modifier.weight(1f),
                )
                if (hostUserId != null) {
                    SaigonTinyPill(
                        label = Copy.text(language, "clearHost"),
                        onClick = {
                            hostUserId = null
                            hostName = null
                        },
                    )
                }
            }

            if (existing == null) {
                SaigonSwitch(
                    checked = interested,
                    onCheckedChange = { interested = it },
                    label = Copy.text(language, "imInterestedInActivity"),
                )
            }

            errorText?.let {
                Text(it, color = tokens.danger, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            SaigonPrimaryButton(
                text = Copy.text(language, if (existing == null) "createActivity" else "save"),
                enabled = !actState.isMutating,
                loading = actState.isMutating,
                onClick = {
                    val cleanName = name.trim()
                    val cleanDesc = description.trim()
                    if (cleanName.length !in 1..Limits.ACTIVITY_NAME_MAX) {
                        localError = Copy.text(language, "invalidActivityName")
                        return@SaigonPrimaryButton
                    }
                    if (cleanDesc.length > Limits.ACTIVITY_DESCRIPTION_MAX) {
                        localError = Copy.text(language, "invalidActivityDescription")
                        return@SaigonPrimaryButton
                    }
                    val scheduleSlots = if (!scheduled) {
                        emptyList()
                    } else {
                        val enabledDays = days.filter { it.enabled }
                        if (enabledDays.any { it.endMinutes <= it.startMinutes }) {
                            localError = Copy.text(language, "invalidActivitySchedule")
                            return@SaigonPrimaryButton
                        }
                        enabledDays.map {
                            ActivityScheduleSlotDto(
                                dayOfWeek = it.dayOfWeek,
                                startMinutes = it.startMinutes,
                                endMinutes = it.endMinutes,
                            )
                        }
                    }
                    scope.launch {
                        val saved = if (existing == null) {
                            shared.activity.createActivity(
                                CreateActivityRequest(
                                    emoji = emoji.trim(),
                                    name = cleanName,
                                    description = cleanDesc,
                                    locationName = locationName,
                                    locationId = locationId,
                                    placeId = placeId,
                                    lat = lat,
                                    lng = lng,
                                    hostUserId = hostUserId,
                                    schedule = scheduleSlots,
                                    interested = interested,
                                ),
                            )
                        } else {
                            val translationsPayload = if (translationStatus == "ready" || translationStatus == "failed") {
                                mapOf(
                                    "en" to mapOf("name" to enName.trim(), "description" to enDescription.trim()),
                                    "vi" to mapOf("name" to viName.trim(), "description" to viDescription.trim()),
                                )
                            } else {
                                null
                            }
                            shared.activity.updateActivity(
                                existing.id,
                                UpdateActivityRequest(
                                    emoji = emoji.trim(),
                                    name = cleanName,
                                    description = cleanDesc,
                                    hostUserId = hostUserId,
                                    schedule = scheduleSlots,
                                    translations = translationsPayload,
                                ),
                            )
                        }
                        if (saved != null) onSaved(saved)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            SaigonTextButton(text = Copy.text(language, "cancel"), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }

    if (showHostPicker) {
        HostPickerDialog(
            shared = shared,
            language = language,
            currentHostId = hostUserId,
            onSelect = { id, label ->
                hostUserId = id
                hostName = label
                showHostPicker = false
            },
            onDismiss = { showHostPicker = false },
        )
    }

    timePickTarget?.let { target ->
        val timeState = rememberTimePickerState(
            initialHour = (target.initialMinutes / 60).coerceIn(0, 23),
            initialMinute = (target.initialMinutes % 60).coerceIn(0, 59),
            is24Hour = true,
        )
        SaigonDialog(onDismiss = { timePickTarget = null }) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    Copy.text(language, "scheduleTime"),
                    color = tokens.text,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth().padding(end = 36.dp),
                )
                TimePicker(state = timeState)
                SaigonPrimaryButton(
                    text = Copy.text(language, "okay"),
                    onClick = {
                        val mins = timeState.hour * 60 + timeState.minute
                        if (target.dayOfWeek == null) {
                            if (target.isEnd) masterEnd = mins else masterStart = mins
                        } else {
                            days = days.map {
                                if (it.dayOfWeek == target.dayOfWeek) {
                                    if (target.isEnd) it.copy(endMinutes = mins) else it.copy(startMinutes = mins)
                                } else {
                                    it
                                }
                            }
                        }
                        timePickTarget = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SaigonTextButton(text = Copy.text(language, "cancel"), onClick = { timePickTarget = null }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ScheduleTimeChip(
    text: String,
    onClick: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.surface)
            .border(1.dp, tokens.line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text, color = tokens.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HostPickerDialog(
    shared: TownSharedClient,
    language: Language,
    currentHostId: String?,
    onSelect: (String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sessionState by shared.session.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    var query by remember { mutableStateOf("") }
    var people by remember { mutableStateOf<List<EventPersonDto>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        delay(180)
        loading = true
        when (val res = shared.api.searchPeople(query.trim())) {
            is ApiResult.Success -> people = res.value.people
            is ApiResult.Failure -> Unit
        }
        loading = false
    }

    SaigonDialog(onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                Copy.text(language, "chooseHost"),
                color = tokens.text,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 36.dp),
            )
            SaigonTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = Copy.text(language, "searchPeople"),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                sessionState.me?.let { me ->
                    if (query.isBlank() || me.name.contains(query, ignoreCase = true)) {
                        val selected = currentHostId == me.id
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(tokens.bg)
                                .border(2.dp, if (selected) tokens.accent else tokens.line, RoundedCornerShape(14.dp))
                                .clickable { onSelect(me.id, Copy.text(language, "me")) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            UserAvatar(photoUrl = me.photoUrl, name = me.name, size = 34.dp, borderWidth = 0.dp)
                            Text(
                                "${Copy.text(language, "me")} (${me.name})",
                                color = tokens.text,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (loading && people.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        SaigonSpinner(size = 22.dp)
                    }
                } else {
                    val filtered = people.filter { it.id != sessionState.me?.id }
                    if (filtered.isEmpty() && sessionState.me == null) {
                        Text(Copy.text(language, "noPeople"), color = tokens.muted, fontSize = 13.sp)
                    } else {
                        filtered.forEach { person ->
                            val selected = currentHostId == person.id
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(tokens.bg)
                                    .border(2.dp, if (selected) tokens.accent else tokens.line, RoundedCornerShape(14.dp))
                                    .clickable { onSelect(person.id, person.name) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                UserAvatar(photoUrl = person.photoUrl, name = person.name, size = 34.dp, borderWidth = 0.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(person.name, color = tokens.text, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (person.location.isNotBlank()) {
                                        Text(person.location, color = tokens.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
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
