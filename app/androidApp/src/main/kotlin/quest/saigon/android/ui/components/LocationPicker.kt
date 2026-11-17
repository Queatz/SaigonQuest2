package quest.saigon.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import quest.saigon.Copy
import quest.saigon.Language
import quest.saigon.Limits
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.shared.model.PlaceSuggestionDto

@Composable
fun LocationInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hasCoordinates: Boolean,
    language: Language,
    onOpenMapPicker: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = Copy.text(language, "locationHint"),
) {
    val tokens = LocalSaigonTokens.current
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        Text(
            text = label,
            color = tokens.text,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            SaigonTextField(
                value = value,
                onValueChange = { if (it.length <= Limits.LOCATION_MAX) onValueChange(it) },
                placeholder = placeholder,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxHeight()
                    .defaultMinSize(minHeight = 44.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (hasCoordinates) tokens.accent else tokens.surface)
                    .border(2.dp, if (hasCoordinates) tokens.accent else tokens.line, RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenMapPicker),
            ) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = Copy.text(language, "pickLocation"),
                    tint = if (hasCoordinates) Color.White else tokens.text,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun LocationPickerDialog(
    initialQuery: String,
    currentPlace: PlaceSuggestionDto?,
    recent: List<PlaceSuggestionDto>,
    results: List<PlaceSuggestionDto>,
    mapsApiKey: String?,
    language: Language,
    onSearch: (String) -> Unit,
    onResolvePlaceDetails: suspend (String) -> PlaceSuggestionDto?,
    onCreateCustomPlace: suspend (name: String, lat: Double, lng: Double) -> PlaceSuggestionDto?,
    onSelectPlace: (PlaceSuggestionDto) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery) }
    var pendingPlace by remember { mutableStateOf(currentPlace) }
    var placingLocation by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var hintText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        delay(200)
        onSearch(query)
    }

    SaigonDialog(onDismiss = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                Copy.text(language, "pickLocation"),
                color = tokens.text,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 36.dp),
            )
            SaigonTextField(
                value = query,
                onValueChange = {
                    query = it
                    placingLocation = false
                    hintText = null
                },
                placeholder = Copy.text(language, "searchPlaces"),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 170.dp),
            ) {
                if (query.isBlank() && recent.isNotEmpty()) {
                    item {
                        Text(Copy.text(language, "recentPlaces"), color = tokens.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    items(recent) { place ->
                        PlacePickerRow(
                            place = place,
                            language = language,
                            selected = !placingLocation && samePickerPlace(pendingPlace, place),
                            onClick = {
                                placingLocation = false
                                hintText = null
                                val pid = place.placeId
                                if (!pid.isNullOrBlank() && (place.lat == null || place.lng == null)) {
                                    scope.launch {
                                        resolving = true
                                        pendingPlace = onResolvePlaceDetails(pid) ?: place
                                        resolving = false
                                    }
                                } else {
                                    pendingPlace = place
                                }
                            },
                        )
                    }
                }
                items(results) { place ->
                    PlacePickerRow(
                        place = place,
                        language = language,
                        selected = !placingLocation && samePickerPlace(pendingPlace, place),
                        onClick = {
                            placingLocation = false
                            hintText = null
                            val pid = place.placeId
                            if (!pid.isNullOrBlank() && (place.lat == null || place.lng == null)) {
                                scope.launch {
                                    resolving = true
                                    pendingPlace = onResolvePlaceDetails(pid) ?: place
                                    resolving = false
                                }
                            } else {
                                pendingPlace = place
                            }
                        },
                    )
                }
                if (query.isNotBlank()) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (placingLocation) tokens.accent.copy(alpha = 0.12f) else tokens.surface,
                                )
                                .dashedBorder(
                                    color = if (placingLocation) tokens.accent else tokens.accent.copy(alpha = 0.48f),
                                    strokeWidth = 1.5.dp,
                                    cornerRadius = 14.dp,
                                )
                                .clickable {
                                    placingLocation = true
                                    pendingPlace = null
                                    hintText = Copy.text(language, "tapMapToPlace")
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                "+ ${Copy.text(language, "addThisLocation")}",
                                color = tokens.accent,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                            )
                            Text(
                                Copy.text(language, "addLocationHint"),
                                color = tokens.muted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            val pins = remember(pendingPlace) {
                val p = pendingPlace
                if (p != null && p.lat != null && p.lng != null) {
                    listOf(
                        SaigonMapPlacePin(
                            key = p.id ?: p.placeId ?: "picked",
                            name = p.name,
                            lat = p.lat!!,
                            lng = p.lng!!,
                            postCount = p.postCount,
                            activityCount = p.activityCount,
                            activityEmojis = p.activityEmojis,
                            hasActivities = p.hasActivities,
                            photoUrl = p.photoUrl,
                        ),
                    )
                } else {
                    emptyList()
                }
            }
            SaigonMapView(
                places = pins,
                mapsApiKey = mapsApiKey,
                centerLat = pendingPlace?.lat,
                centerLng = pendingPlace?.lng,
                initialZoom = if (pendingPlace?.lat != null) 16f else 12f,
                alwaysShowBubbles = true,
                onMapClick = { lat, lng ->
                    if (placingLocation) {
                        val cleanName = query.trim()
                        if (cleanName.isNotEmpty()) {
                            pendingPlace = PlaceSuggestionDto(name = cleanName, lat = lat, lng = lng, custom = true)
                            hintText = Copy.text(language, "locationPlaced")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, tokens.line, RoundedCornerShape(16.dp)),
            )

            hintText?.let {
                Text(it, color = tokens.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            val canConfirm = !resolving && !creating && pendingPlace != null &&
                (!placingLocation || (pendingPlace?.lat != null && pendingPlace?.lng != null))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SaigonTextButton(
                    text = Copy.text(language, "close"),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                SaigonPrimaryButton(
                    text = Copy.text(language, if (placingLocation) "createLocation" else "usePlace"),
                    enabled = canConfirm,
                    loading = creating || resolving,
                    dimWhenDisabled = false,
                    onClick = {
                        val place = pendingPlace ?: return@SaigonPrimaryButton
                        if (placingLocation) {
                            val lat = place.lat ?: return@SaigonPrimaryButton
                            val lng = place.lng ?: return@SaigonPrimaryButton
                            scope.launch {
                                creating = true
                                val created = onCreateCustomPlace(place.name, lat, lng)
                                creating = false
                                if (created != null) onSelectPlace(created)
                            }
                        } else {
                            onSelectPlace(place)
                        }
                    },
                    modifier = Modifier.weight(1.4f),
                )
            }
        }
    }
}

@Composable
private fun PlacePickerRow(
    place: PlaceSuggestionDto,
    language: Language,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.bg)
            .border(2.dp, if (selected) tokens.accent else tokens.line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        val thumb = place.photoUrl ?: place.googlePhotoUrl
        if (!thumb.isNullOrBlank()) {
            AsyncImage(
                model = thumb,
                contentDescription = place.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tokens.surface),
            ) {
                Text("📍", fontSize = 20.sp)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                place.name,
                color = if (selected) tokens.accent else tokens.text,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (place.address.isNotBlank() && place.address != place.name) {
                Text(place.address, color = tokens.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                Copy.text(language, "postCount", mapOf("count" to place.postCount.coerceAtLeast(0).toString())),
                color = tokens.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun samePickerPlace(first: PlaceSuggestionDto?, second: PlaceSuggestionDto?): Boolean {
    if (first == null || second == null) return false
    val fId = first.id?.takeIf { it.isNotBlank() }
    val sId = second.id?.takeIf { it.isNotBlank() }
    if (fId != null && sId != null && fId == sId) return true
    val fPid = first.placeId?.takeIf { it.isNotBlank() }
    val sPid = second.placeId?.takeIf { it.isNotBlank() }
    return fPid != null && sPid != null && fPid == sPid
}
