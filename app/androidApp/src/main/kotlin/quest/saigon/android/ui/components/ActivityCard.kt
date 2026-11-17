package quest.saigon.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import quest.saigon.Copy
import quest.saigon.Language
import quest.saigon.Limits
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.theme.SaigonColors
import quest.saigon.shared.model.ActivityDto
import quest.saigon.shared.model.ActivityScheduleSlotDto
import quest.saigon.shared.store.ActivityStore

@Composable
fun ActivityCard(
    activity: ActivityDto,
    language: Language,
    viewerId: String?,
    showLocation: Boolean = true,
    isMutating: Boolean = false,
    onOpen: () -> Unit,
    onToggleInterest: () -> Unit,
    onOpenLocation: (() -> Unit)? = null,
    onOpenPhoto: ((String) -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalSaigonTokens.current
    val name = ActivityStore.localizedName(activity, language)
    val description = ActivityStore.localizedDescription(activity, language)
    val scheduleSummary = activityScheduleSummary(activity.schedule, language)

    SaigonCard(
        onClick = onOpen,
        borderWidth = 2.dp,
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = activity.emoji.ifBlank { "✨" },
                    fontSize = 28.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = name,
                        color = tokens.text,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                    )
                    if (showLocation && activity.locationName.isNotBlank()) {
                        Text(
                            text = "📍 ${activity.locationName}",
                            color = tokens.text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (onOpenLocation != null) {
                                Modifier.clickable(onClick = onOpenLocation)
                            } else {
                                Modifier
                            },
                        )
                    }
                    activity.host?.let { host ->
                        val hostDisplay = if (viewerId != null && host.id == viewerId) {
                            Copy.text(language, "me")
                        } else {
                            host.name
                        }
                        if (hostDisplay.isNotBlank()) {
                            Text(
                                text = "${Copy.text(language, "activityHost")}: $hostDisplay",
                                color = tokens.muted,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    if (description.isNotBlank()) {
                        LinkifiedText(
                            text = description,
                            color = tokens.muted,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                        )
                    }
                    if (!scheduleSummary.isNullOrBlank()) {
                        Text(
                            text = scheduleSummary,
                            color = tokens.muted,
                            fontSize = 13.sp,
                        )
                    }
                }
                val interestLabel = Copy.text(
                    language,
                    "interestedCount",
                    mapOf("count" to activity.interestedCount.toString()),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (activity.interested) tokens.accent else tokens.bg)
                        .border(
                            1.dp,
                            if (activity.interested) tokens.accent else tokens.line,
                            RoundedCornerShape(999.dp),
                        )
                        .clickable(enabled = !isMutating, onClick = onToggleInterest)
                        .alpha(if (isMutating) 0.55f else 1f)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = interestLabel,
                        color = if (activity.interested) Color.White else tokens.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                }
            }

            if (activity.interestedPeople.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val shown = activity.interestedPeople.take(Limits.ACTIVITY_CARD_INTERESTED_LIMIT)
                    Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
                        shown.forEach { person ->
                            UserAvatar(
                                photoUrl = person.photoUrl,
                                name = person.name,
                                size = 26.dp,
                                borderColor = tokens.surface,
                                borderWidth = 2.dp,
                            )
                        }
                    }
                    val names = activity.interestedPeople.take(2).map { it.name }.filter { it.isNotBlank() }
                    val total = maxOf(activity.interestedCount.toInt(), activity.interestedPeople.size)
                    val summary = when {
                        names.size == 2 && total > 2 -> Copy.text(
                            language,
                            "peopleNamesAndMore",
                            mapOf(
                                "first" to names[0],
                                "second" to names[1],
                                "count" to (total - 2).toString(),
                            ),
                        )
                        else -> names.joinToString(", ")
                    }
                    if (summary.isNotBlank()) {
                        Text(
                            text = summary,
                            color = tokens.text,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (activity.photoUrls.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    activity.photoUrls.take(Limits.ACTIVITY_CARD_PHOTO_LIMIT).forEach { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(SaigonColors.Card)
                                .then(
                                    if (onOpenPhoto != null) Modifier.clickable { onOpenPhoto(url) } else Modifier,
                                ),
                        )
                    }
                }
            }

            if (footer != null) {
                footer()
            }
        }
    }
}

fun weekdayCopyKey(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "weekdayMon"
    2 -> "weekdayTue"
    3 -> "weekdayWed"
    4 -> "weekdayThu"
    5 -> "weekdayFri"
    6 -> "weekdaySat"
    else -> "weekdaySun"
}

fun formatScheduleMinutes(minutes: Int): String {
    val safe = minutes.coerceIn(0, 24 * 60)
    val h = (safe / 60).toString().padStart(2, '0')
    val m = (safe % 60).toString().padStart(2, '0')
    return "$h:$m"
}

fun activityScheduleSummary(schedule: List<ActivityScheduleSlotDto>, language: Language): String? {
    if (schedule.isEmpty()) return null
    return schedule.groupBy { it.startMinutes to it.endMinutes }.entries.joinToString(" · ") { (time, days) ->
        val dayNames = days.joinToString(", ") { Copy.text(language, weekdayCopyKey(it.dayOfWeek)) }
        "$dayNames ${formatScheduleMinutes(time.first)}–${formatScheduleMinutes(time.second)}"
    }
}
