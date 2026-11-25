package quest.saigon.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import quest.saigon.AppRoute
import quest.saigon.Copy
import quest.saigon.Language
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.WantDto
import quest.saigon.shared.network.ApiResult

@Composable
fun WantSheet(
    wantId: String,
    shared: TownSharedClient,
    language: Language,
    onNavigate: (AppRoute) -> Unit,
    onRequireAuth: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    val sessionState by shared.session.state.collectAsState()
    val profileState by shared.profile.state.collectAsState()
    val scope = rememberCoroutineScope()
    var want by remember(wantId) { mutableStateOf<WantDto?>(null) }
    var loading by remember(wantId) { mutableStateOf(true) }
    var loadFailed by remember(wantId) { mutableStateOf(false) }
    var selectedIds by remember(wantId) { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(wantId) {
        when (val result = shared.api.getWant(wantId)) {
            is ApiResult.Success -> want = result.value
            is ApiResult.Failure -> loadFailed = true
        }
        loading = false
    }

    val title = want?.name ?: Copy.text(language, "wantAttachment")
    SaigonBottomSheet(title = title, onDismiss = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Text(Copy.text(language, "peopleWantThis"), color = tokens.text, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            when {
                loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SaigonSpinner() }
                loadFailed -> Text(Copy.text(language, "unavailable"), color = tokens.muted, fontSize = 14.sp)
                want?.members.isNullOrEmpty() -> Text(Copy.text(language, "noPeople"), color = tokens.muted, fontSize = 14.sp)
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    ) {
                        items(want?.members.orEmpty(), key = { it.id }) { person ->
                            val isSelf = person.id == sessionState.me?.id
                            val checked = person.id in selectedIds
                            Surface(
                                color = if (checked) tokens.accent.copy(alpha = 0.14f) else tokens.surface,
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, if (checked) tokens.accent.copy(alpha = 0.3f) else tokens.line),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable(enabled = !isSelf) {
                                        selectedIds = if (checked) selectedIds - person.id else selectedIds + person.id
                                    },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        enabled = !isSelf,
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = tokens.accent,
                                            disabledCheckedColor = tokens.accent,
                                        ),
                                        onCheckedChange = {
                                            if (!isSelf) selectedIds = if (it) selectedIds + person.id else selectedIds - person.id
                                        },
                                    )
                                    UserAvatar(photoUrl = person.photoUrl, name = person.name, size = 40.dp, borderWidth = 0.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(person.name, color = tokens.text, fontWeight = FontWeight.Bold)
                                        if (person.location.isNotBlank()) Text(person.location, color = tokens.muted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = tokens.line)
            val selectedOthers = selectedIds.filter { it != sessionState.me?.id }
            SaigonPrimaryButton(
                text = Copy.text(language, if (selectedOthers.size <= 1) "openChat" else "startGroupChat"),
                enabled = selectedOthers.isNotEmpty() && !profileState.isMutating && !loading,
                loading = profileState.isMutating,
                onClick = {
                    if (sessionState.me == null) {
                        onRequireAuth()
                    } else {
                        scope.launch {
                            shared.profile.startWantChat(wantId, selectedOthers)?.let { chat ->
                                onDismiss()
                                onNavigate(AppRoute.Chat(chat.id))
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                contentPadding = PaddingValues(vertical = 13.dp),
            )
        }
    }
}