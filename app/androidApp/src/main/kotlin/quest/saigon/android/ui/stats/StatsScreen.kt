package quest.saigon.android.ui.stats

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import quest.saigon.Copy
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.theme.SaigonColors
import quest.saigon.android.ui.components.SaigonCard
import quest.saigon.android.ui.components.SaigonPageShell
import quest.saigon.android.ui.components.SaigonSpinner
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.StatsDayDto

@Composable
fun StatsScreen(
    shared: TownSharedClient,
    onBack: () -> Unit,
) {
    val lang = shared.session.state.collectAsState().value.language
    val statsState by shared.stats.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    var rangeDays by remember { mutableIntStateOf(7) }

    LaunchedEffect(Unit) { shared.stats.load() }

    val pulse = rememberInfiniteTransition(label = "statsLive")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha",
    )

    SaigonPageShell(
        title = Copy.text(lang, "stats"),
        onBack = onBack,
        backContentDescription = Copy.text(lang, "back"),
        topActions = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SaigonColors.StatsGreen.copy(alpha = pulseAlpha)),
                )
                Text("LIVE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tokens.muted)
            }
        },
    ) {
        val stats = statsState.stats
        if (statsState.isLoading && stats == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SaigonSpinner()
            }
        } else if (stats != null) {
            val chartDays = stats.days.takeLast(rangeDays)
            val days = chartDays.reversed()
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(Copy.text(lang, "statsHint"), fontSize = 13.sp, color = tokens.muted)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            SaigonCard(
                                borderWidth = 1.dp,
                                cornerRadius = 14.dp,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(Copy.text(lang, "totalUsers").uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.muted)
                                Text("${stats.totalUsers}", color = tokens.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
                            }
                            SaigonCard(
                                borderWidth = 1.dp,
                                cornerRadius = 14.dp,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(Copy.text(lang, "totalPosts").uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.muted)
                                Text("${stats.totalPosts}", color = tokens.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
                            }
                        }

                        // .stats-ranges
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7 to Copy.text(lang, "statsRange7"), 30 to Copy.text(lang, "statsRange30")).forEach { (d, label) ->
                                val active = rangeDays == d
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(if (active) tokens.text else tokens.surface)
                                        .border(1.dp, if (active) tokens.text else tokens.line, RoundedCornerShape(999.dp))
                                        .clickable { rangeDays = d }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        label,
                                        color = if (active) tokens.bg else tokens.text,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        if (chartDays.size >= 2) {
                            StatsLineChart(days = chartDays, lang = lang)
                        }
                    }
                }
                items(days, key = { it.day }) { row ->
                    SaigonCard(
                        borderWidth = 1.dp,
                        cornerRadius = 14.dp,
                        contentPadding = PaddingValues(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(row.day, color = tokens.text, fontWeight = FontWeight.ExtraBold)
                            Text(
                                "${Copy.text(lang, "visitorVisits")}: ${row.visitorVisits} · ${Copy.text(lang, "userVisits")}: ${row.userVisits} · ${Copy.text(lang, "uniqueUserVisits")}: ${row.uniqueUserVisits}",
                                fontSize = 12.sp,
                                color = tokens.text,
                            )
                            Text(
                                "${Copy.text(lang, "postsPerDay")}: ${row.posts} · ${Copy.text(lang, "newUsersPerDay")}: ${row.newUsers} · ${Copy.text(lang, "postViews")}: ${row.postViews}",
                                fontSize = 12.sp,
                                color = tokens.muted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsLineChart(days: List<StatsDayDto>, lang: quest.saigon.Language) {
    val tokens = LocalSaigonTokens.current
    val maxVal = days.maxOfOrNull { maxOf(it.visitorVisits, it.userVisits, it.posts) }?.coerceAtLeast(1L) ?: 1L

    SaigonCard(
        borderWidth = 1.dp,
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            ) {
                val stepX = size.width / (days.size - 1).coerceAtLeast(1)
                for (i in 0..3) {
                    val y = size.height * (i / 3f)
                    drawLine(
                        color = tokens.line,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                fun drawSeries(values: List<Long>, color: androidx.compose.ui.graphics.Color) {
                    val path = Path()
                    values.forEachIndexed { idx, v ->
                        val x = idx * stepX
                        val y = size.height - (v.toFloat() / maxVal.toFloat()) * (size.height - 8.dp.toPx()) - 4.dp.toPx()
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }

                drawSeries(days.map { it.visitorVisits }, tokens.accent)
                drawSeries(days.map { it.userVisits }, tokens.tertiary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(tokens.accent))
                    Text(Copy.text(lang, "visitorVisits"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tokens.muted)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(tokens.tertiary))
                    Text(Copy.text(lang, "userVisits"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tokens.muted)
                }
            }
        }
    }
}
