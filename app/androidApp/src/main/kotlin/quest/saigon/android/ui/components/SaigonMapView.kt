package quest.saigon.android.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.content.ContextCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import quest.saigon.Copy
import quest.saigon.Language
import town.saigon.BuildConfig
import quest.saigon.android.diagnostics.CrashReporter
import quest.saigon.android.theme.LivvicFontFamily
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.theme.SaigonColors
import quest.saigon.shared.model.EventPersonDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume

data class SaigonMapEventPin(
    val postId: String,
    val title: String,
    val location: String,
    val emoji: String = "📅",
    val lat: Double,
    val lng: Double,
    val attendeeCount: Int = 0,
    val eventStartAt: Long? = null,
    val eventPrice: Long? = null,
    val attendees: List<EventPersonDto> = emptyList(),
)

data class SaigonMapPlacePin(
    val key: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val postCount: Long = 0L,
    val activityCount: Long = 0L,
    val activityEmojis: List<String> = emptyList(),
    val hasActivities: Boolean = false,
    val photoUrl: String? = null,
)

@Serializable
private data class JsMapMarker(
    val kind: String,
    val id: String,
    val title: String,
    val subtitle: String = "",
    val emoji: String = "",
    val lat: Double,
    val lng: Double,
    val count: Long = 0L,
    val hasActivities: Boolean = false,
    val activityEmojis: String = "",
)

private val mapJson = Json { encodeDefaults = true }

private data class MapMarkerBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun overlaps(other: MapMarkerBox): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top

    fun overlapArea(other: MapMarkerBox): Float {
        val w = (minOf(right, other.right) - maxOf(left, other.left)).coerceAtLeast(0f)
        val h = (minOf(bottom, other.bottom) - maxOf(top, other.top)).coerceAtLeast(0f)
        return w * h
    }
}

private data class MapMarkerCandidate(
    val id: String,
    val isBubble: Boolean,
    val isLocation: Boolean,
    val postCount: Long,
    val attendeeCount: Int,
    val index: Int,
    val lat: Double,
    val lng: Double,
    val box: MapMarkerBox,
    val place: SaigonMapPlacePin? = null,
    val event: SaigonMapEventPin? = null,
    val labelWidthPx: Float = 0f,
    val labelHeightPx: Float = 0f,
)

private data class MapLayoutResult(
    val visibleEvents: List<Pair<SaigonMapEventPin, Float>> = emptyList(),
    val visiblePlaceBubbles: List<Pair<SaigonMapPlacePin, Float>> = emptyList(),
    val visiblePlaceDots: List<Triple<SaigonMapPlacePin, Boolean, Float>> = emptyList(), // place, labelOnLeft, zIndex
)

private data class MeasuredPlaceLabel(
    val titleWidthPx: Float,
    val emojiWidthPx: Float,
    val labelWidthPx: Float,
    val labelHeightPx: Float,
)

private sealed interface AnimatedMarkerPayload {
    val key: String
    val zIndex: Float

    data class Dot(
        override val key: String,
        val place: SaigonMapPlacePin,
        val labelOnLeft: Boolean,
        override val zIndex: Float,
    ) : AnimatedMarkerPayload

    data class PlaceBubble(
        override val key: String,
        val place: SaigonMapPlacePin,
        override val zIndex: Float,
    ) : AnimatedMarkerPayload

    data class EventBubble(
        override val key: String,
        val event: SaigonMapEventPin,
        override val zIndex: Float,
    ) : AnimatedMarkerPayload
}

private class AnimatedMarkerEntry(
    val key: String,
    initialPayload: AnimatedMarkerPayload,
    initialTargetVisible: Boolean,
) {
    var payload by mutableStateOf(initialPayload)
    var targetVisible by mutableStateOf(initialTargetVisible)
}

private fun isValidLatLng(lat: Double, lng: Double): Boolean =
    lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
private suspend fun getCurrentDeviceLocation(context: Context): Location? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
    val lastKnown = providers.mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.maxByOrNull { it.time }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return lastKnown

    val activeProviders = providers.filterNot { it == LocationManager.PASSIVE_PROVIDER }
    if (activeProviders.isEmpty()) return lastKnown

    return withTimeoutOrNull(10_000) {
        suspendCancellableCoroutine { continuation ->
            val waiting = activeProviders.toMutableSet()
            val cancellationSignals = mutableListOf<CancellationSignal>()
            continuation.invokeOnCancellation { cancellationSignals.forEach { it.cancel() } }
            activeProviders.forEach { provider ->
                val signal = CancellationSignal().also(cancellationSignals::add)
                runCatching {
                    manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                        if (!continuation.isActive) return@getCurrentLocation
                        if (location != null) {
                            continuation.resume(location)
                        } else {
                            waiting.remove(provider)
                            if (waiting.isEmpty() && continuation.isActive) continuation.resume(lastKnown)
                        }
                    }
                }.onFailure {
                    waiting.remove(provider)
                    if (waiting.isEmpty() && continuation.isActive) continuation.resume(lastKnown)
                }
            }
        }
    } ?: lastKnown
}

@Composable
fun SaigonMapView(
    events: List<SaigonMapEventPin> = emptyList(),
    places: List<SaigonMapPlacePin> = emptyList(),
    mapsApiKey: String? = null,
    centerLat: Double? = null,
    centerLng: Double? = null,
    initialZoom: Float = 13f,
    restoredCenterLat: Double? = null,
    restoredCenterLng: Double? = null,
    restoredZoom: Float? = null,
    alwaysShowBubbles: Boolean = false,
    language: Language = Language.EN,
    onEventClick: (String) -> Unit = {},
    onPlaceClick: (String) -> Unit = {},
    onMapClick: ((Double, Double) -> Unit)? = null,
    onCameraChanged: ((Double, Double, Float) -> Unit)? = null,
    onMapTouchChange: ((Boolean) -> Unit)? = null,
    showMapControls: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalSaigonTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var satelliteView by rememberSaveable { mutableStateOf(false) }
    var locationPermissionGranted by remember { mutableStateOf(context.hasLocationPermission()) }
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var locateRequestCount by remember { mutableIntStateOf(0) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        locationPermissionGranted = permissions.values.any { it }
        if (locationPermissionGranted) {
            scope.launch {
                val location = getCurrentDeviceLocation(context)
                if (location != null) {
                    currentLocation = LatLng(location.latitude, location.longitude)
                    locateRequestCount += 1
                } else {
                    Toast.makeText(context, Copy.text(language, "locationUnavailable"), Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, Copy.text(language, "locationPermissionDenied"), Toast.LENGTH_SHORT).show()
        }
    }
    fun locateUser() {
        if (!locationPermissionGranted) {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
            return
        }
        scope.launch {
            val location = getCurrentDeviceLocation(context)
            if (location != null) {
                currentLocation = LatLng(location.latitude, location.longitude)
                locateRequestCount += 1
            } else {
                Toast.makeText(context, Copy.text(language, "locationUnavailable"), Toast.LENGTH_SHORT).show()
            }
        }
    }
    LaunchedEffect(BuildConfig.MAPS_CONFIGURED) {
        CrashReporter.log("SaigonMapView mode=${if (BuildConfig.MAPS_CONFIGURED) "native" else "web"}")
    }
    Box(modifier = modifier) {
        if (BuildConfig.MAPS_CONFIGURED) {
            NativeGoogleMapView(
                events = events,
                places = places,
                centerLat = centerLat,
                centerLng = centerLng,
                initialZoom = initialZoom,
                restoredCenterLat = restoredCenterLat,
                restoredCenterLng = restoredCenterLng,
                restoredZoom = restoredZoom,
                alwaysShowBubbles = alwaysShowBubbles,
                language = language,
                satelliteView = satelliteView,
                currentLocation = currentLocation,
                locateRequestCount = locateRequestCount,
                onEventClick = onEventClick,
                onPlaceClick = onPlaceClick,
                onMapClick = onMapClick,
                onCameraChanged = onCameraChanged,
                onMapTouchChange = onMapTouchChange,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            WebSaigonMapView(
                events = events,
                places = places,
                mapsApiKey = mapsApiKey,
                centerLat = centerLat,
                centerLng = centerLng,
                initialZoom = initialZoom,
                alwaysShowBubbles = alwaysShowBubbles,
                darkTheme = tokens.isDark,
                satelliteView = satelliteView,
                currentLocation = currentLocation,
                locateRequestCount = locateRequestCount,
                onEventClick = onEventClick,
                onPlaceClick = onPlaceClick,
                onMapClick = onMapClick,
                onMapTouchChange = onMapTouchChange,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showMapControls) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                SaigonMapControlButton(
                    icon = Icons.Default.Layers,
                    description = Copy.text(language, if (satelliteView) "standardMap" else "satelliteView"),
                    onClick = { satelliteView = !satelliteView },
                )
                SaigonMapControlButton(
                    icon = Icons.Default.MyLocation,
                    description = Copy.text(language, "locateMe"),
                    onClick = ::locateUser,
                )
            }
        }
    }
}

@Composable
private fun SaigonMapControlButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .shadow(6.dp, RoundedCornerShape(15.dp))
            .clip(RoundedCornerShape(15.dp))
            .background(tokens.surface)
            .border(1.dp, tokens.line, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = description, tint = tokens.text, modifier = Modifier.size(22.dp))
    }
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
private fun NativeGoogleMapView(
    events: List<SaigonMapEventPin>,
    places: List<SaigonMapPlacePin>,
    centerLat: Double?,
    centerLng: Double?,
    initialZoom: Float,
    restoredCenterLat: Double?,
    restoredCenterLng: Double?,
    restoredZoom: Float?,
    alwaysShowBubbles: Boolean,
    language: Language,
    satelliteView: Boolean,
    currentLocation: LatLng?,
    locateRequestCount: Int,
    onEventClick: (String) -> Unit,
    onPlaceClick: (String) -> Unit,
    onMapClick: ((Double, Double) -> Unit)?,
    onCameraChanged: ((Double, Double, Float) -> Unit)?,
    onMapTouchChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalSaigonTokens.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val latestOnCameraChanged by rememberUpdatedState(onCameraChanged)
    val latestOnMapTouchChange by rememberUpdatedState(onMapTouchChange)

    val validEvents = remember(events) {
        events.filter { isValidLatLng(it.lat, it.lng) }.distinctBy { it.postId }
    }
    val validPlaces = remember(places) {
        places.filter { isValidLatLng(it.lat, it.lng) }.distinctBy { it.key }
    }

    val safeCenterLat = centerLat?.takeIf { isValidLatLng(it, 0.0) }
    val safeCenterLng = centerLng?.takeIf { isValidLatLng(0.0, it) }
    val safeRestoredLat = restoredCenterLat?.takeIf { isValidLatLng(it, 0.0) }
    val safeRestoredLng = restoredCenterLng?.takeIf { isValidLatLng(0.0, it) }
    val safeRestoredZoom = restoredZoom?.takeIf { it.isFinite() && it in 2f..22f }
    val hasRestored = safeRestoredLat != null && safeRestoredLng != null && safeRestoredZoom != null
    val startLat = safeRestoredLat ?: safeCenterLat ?: 10.7769
    val startLng = safeRestoredLng ?: safeCenterLng ?: 106.7009
    val startZoom = safeRestoredZoom ?: initialZoom
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(startLat, startLng), startZoom)
    }
    LaunchedEffect(locateRequestCount, currentLocation) {
        currentLocation?.let { cameraState.animate(CameraUpdateFactory.newLatLngZoom(it, 16f)) }
    }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    var mapReady by remember { mutableStateOf(false) }
    var hasFittedBounds by remember { mutableStateOf(hasRestored) }

    val placeBitmaps = remember { mutableStateMapOf<String, ImageBitmap>() }
    val thumbSizePx = with(density) { 40.dp.roundToPx().coerceAtLeast(32) }
    val placePhotoUrls = remember(validPlaces) {
        validPlaces.mapNotNull { it.photoUrl?.takeIf(String::isNotBlank) }.distinct()
    }
    LaunchedEffect(placePhotoUrls, thumbSizePx) {
        val loader = SingletonImageLoader.get(context)
        placePhotoUrls.forEach { url ->
            if (placeBitmaps.containsKey(url)) return@forEach
            launch(Dispatchers.IO) {
                val bmp = runCatching {
                    val req = ImageRequest.Builder(context)
                        .data(url)
                        .size(thumbSizePx, thumbSizePx)
                        .allowHardware(false)
                        .build()
                    val res = loader.execute(req)
                    (res as? SuccessResult)?.image?.toBitmap()?.asImageBitmap()
                }.getOrNull()
                if (bmp != null) {
                    withContext(Dispatchers.Main.immediate) {
                        placeBitmaps[url] = bmp
                    }
                }
            }
        }
    }

    val labelTitleStyle = remember {
        TextStyle(
            fontFamily = LivvicFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 15.sp,
        )
    }
    val labelEmojiStyle = remember {
        TextStyle(
            fontFamily = LivvicFontFamily,
            fontSize = 11.sp,
            lineHeight = 13.sp,
        )
    }

    val maxLabelW = with(density) { 144.dp.roundToPx() }
    val measuredLabels = remember(validPlaces, maxLabelW, labelTitleStyle, labelEmojiStyle) {
        validPlaces.associate { place ->
            val titleRes = if (place.name.isNotBlank()) {
                textMeasurer.measure(
                    text = place.name,
                    style = labelTitleStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = maxLabelW),
                )
            } else {
                null
            }
            val emojiText = place.activityEmojis.joinToString(" ")
            val emojiRes = if (emojiText.isNotBlank()) {
                textMeasurer.measure(
                    text = emojiText,
                    style = labelEmojiStyle,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = maxLabelW),
                )
            } else {
                null
            }
            val tw = titleRes?.size?.width?.toFloat() ?: 0f
            val ew = emojiRes?.size?.width?.toFloat() ?: 0f
            val lw = maxOf(tw, ew)
            val lh = ((titleRes?.size?.height ?: 0) + (emojiRes?.size?.height ?: 0)).toFloat()
            place.key to MeasuredPlaceLabel(tw, ew, lw, lh)
        }
    }

    var layoutResult by remember { mutableStateOf(MapLayoutResult()) }

    fun recomputeMarkerLayout() {
        if (!mapReady || mapSize.width <= 0 || mapSize.height <= 0) return
        val zoom = cameraState.position.zoom
        val zoomedIn = alwaysShowBubbles || zoom >= 18f
        val projection = runCatching { cameraState.projection }.getOrNull()
        if (projection == null) {
            layoutResult = MapLayoutResult(
                visibleEvents = validEvents.mapIndexed { idx, ev -> ev to (200f - idx) },
                visiblePlaceBubbles = if (zoomedIn) validPlaces.mapIndexed { idx, p -> p to (100f - idx) } else emptyList(),
                visiblePlaceDots = if (!zoomedIn) validPlaces.mapIndexed { idx, p -> Triple(p, false, 100f - idx) } else emptyList(),
            )
            return
        }

        val eventBubbleW = with(density) { 168.dp.toPx() }
        val eventBubbleH = with(density) { 86.dp.toPx() }
        val placeBubbleW = with(density) { 164.dp.toPx() }
        val placeBubbleH = with(density) { 64.dp.toPx() }
        val dotDiameterPx = with(density) { 26.dp.toPx() }
        val pointerExtraPx = with(density) { 6.dp.toPx() }
        val labelGapPx = with(density) { 6.dp.toPx() }

        val candidates = mutableListOf<MapMarkerCandidate>()
        var idx = 0

        validEvents.forEach { ev ->
            val pt = runCatching { projection.toScreenLocation(LatLng(ev.lat, ev.lng)) }.getOrNull() ?: return@forEach
            val x = pt.x.toFloat()
            val y = pt.y.toFloat()
            candidates += MapMarkerCandidate(
                id = "ev_${ev.postId}",
                isBubble = true,
                isLocation = false,
                postCount = 1L,
                attendeeCount = ev.attendeeCount,
                index = idx++,
                lat = ev.lat,
                lng = ev.lng,
                box = MapMarkerBox(
                    left = x - eventBubbleW / 2f,
                    top = y - eventBubbleH - pointerExtraPx,
                    right = x + eventBubbleW / 2f,
                    bottom = y,
                ),
                event = ev,
            )
        }

        validPlaces.forEach { place ->
            val pt = runCatching { projection.toScreenLocation(LatLng(place.lat, place.lng)) }.getOrNull() ?: return@forEach
            val x = pt.x.toFloat()
            val y = pt.y.toFloat()
            if (zoomedIn) {
                candidates += MapMarkerCandidate(
                    id = "pb_${place.key}",
                    isBubble = true,
                    isLocation = true,
                    postCount = place.postCount.coerceAtLeast(0L),
                    attendeeCount = 0,
                    index = idx++,
                    lat = place.lat,
                    lng = place.lng,
                    box = MapMarkerBox(
                        left = x - placeBubbleW / 2f,
                        top = y - placeBubbleH - pointerExtraPx,
                        right = x + placeBubbleW / 2f,
                        bottom = y,
                    ),
                    place = place,
                )
            } else {
                val measured = measuredLabels[place.key]
                candidates += MapMarkerCandidate(
                    id = "pd_${place.key}",
                    isBubble = false,
                    isLocation = true,
                    postCount = place.postCount.coerceAtLeast(0L),
                    attendeeCount = 0,
                    index = idx++,
                    lat = place.lat,
                    lng = place.lng,
                    box = MapMarkerBox(
                        left = x - dotDiameterPx / 2f,
                        top = y - dotDiameterPx / 2f,
                        right = x + dotDiameterPx / 2f,
                        bottom = y + dotDiameterPx / 2f,
                    ),
                    place = place,
                    labelWidthPx = measured?.labelWidthPx ?: 0f,
                    labelHeightPx = measured?.labelHeightPx ?: 0f,
                )
            }
        }

        // Priority pack matching EventsUi.kt packMapMarkers
        val prioritized = candidates.sortedWith(
            compareByDescending<MapMarkerCandidate> { it.isBubble }
                .thenByDescending { it.postCount }
                .thenByDescending { it.attendeeCount }
                .thenBy { it.index },
        )
        val accepted = mutableListOf<MapMarkerCandidate>()
        prioritized.forEach { candidate ->
            if (accepted.none { it.box.overlaps(candidate.box) }) {
                accepted += candidate
            }
        }

        // Label auto-placement matching EventsUi.kt updateMapMarkers
        val mapW = mapSize.width.toFloat()
        val mapH = mapSize.height.toFloat()
        val mapViewport = MapMarkerBox(0f, 0f, mapW, mapH)
        val placedLabels = mutableListOf<MapMarkerBox>()
        val labelSideMap = mutableMapOf<String, Boolean>()

        accepted.forEach { item ->
            if (!item.isLocation || item.isBubble || item.labelWidthPx <= 0f || item.labelHeightPx <= 0f) return@forEach
            val centerX = (item.box.left + item.box.right) / 2f
            val centerY = (item.box.top + item.box.bottom) / 2f
            val preferredLeft = centerX > mapW / 2f
            fun labelBox(left: Boolean): MapMarkerBox {
                val l = if (left) item.box.left - labelGapPx - item.labelWidthPx else item.box.right + labelGapPx
                return MapMarkerBox(
                    left = l,
                    top = centerY - item.labelHeightPx / 2f,
                    right = l + item.labelWidthPx,
                    bottom = centerY + item.labelHeightPx / 2f,
                )
            }
            fun overlapArea(box: MapMarkerBox): Float =
                accepted.asSequence()
                    .filter { it.id != item.id }
                    .sumOf { box.overlapArea(it.box).toDouble() }.toFloat() +
                    placedLabels.sumOf { box.overlapArea(it).toDouble() }.toFloat()
            fun overflowArea(box: MapMarkerBox): Float {
                val area = (box.right - box.left) * (box.bottom - box.top)
                return (area - box.overlapArea(mapViewport)).coerceAtLeast(0f)
            }
            val leftBox = labelBox(true)
            val rightBox = labelBox(false)
            val leftOverlap = overlapArea(leftBox)
            val rightOverlap = overlapArea(rightBox)
            val leftOverflow = overflowArea(leftBox)
            val rightOverflow = overflowArea(rightBox)
            val useLeft = when {
                leftOverlap < rightOverlap -> true
                rightOverlap < leftOverlap -> false
                leftOverflow < rightOverflow -> true
                rightOverflow < leftOverflow -> false
                else -> preferredLeft
            }
            labelSideMap[item.id] = useLeft
            placedLabels += if (useLeft) leftBox else rightBox
        }

        val count = accepted.size
        val visEvents = mutableListOf<Pair<SaigonMapEventPin, Float>>()
        val visPlaceBubbles = mutableListOf<Pair<SaigonMapPlacePin, Float>>()
        val visPlaceDots = mutableListOf<Triple<SaigonMapPlacePin, Boolean, Float>>()

        accepted.forEachIndexed { posIndex, item ->
            val z = (if (item.isBubble) 2 * count - posIndex else count - posIndex).toFloat()
            when {
                item.event != null -> visEvents += item.event to z
                item.place != null && item.isBubble -> visPlaceBubbles += item.place to z
                item.place != null -> visPlaceDots += Triple(item.place, labelSideMap[item.id] == true, z)
            }
        }

        layoutResult = MapLayoutResult(
            visibleEvents = visEvents,
            visiblePlaceBubbles = visPlaceBubbles,
            visiblePlaceDots = visPlaceDots,
        )
    }

    val hasMapSize = mapSize.width > 0 && mapSize.height > 0
    val coordsKey = remember(validEvents, validPlaces) {
        validEvents.map { it.lat to it.lng } + validPlaces.map { it.lat to it.lng }
    }
    LaunchedEffect(mapReady, hasMapSize, coordsKey, safeCenterLat, safeCenterLng, initialZoom) {
        if (!mapReady || !hasMapSize) return@LaunchedEffect
        if (safeCenterLat != null && safeCenterLng != null) {
            runCatching {
                cameraState.move(CameraUpdateFactory.newLatLngZoom(LatLng(safeCenterLat, safeCenterLng), initialZoom))
            }
            hasFittedBounds = true
        } else if (!hasFittedBounds) {
            val coords = coordsKey.map { LatLng(it.first, it.second) }
            if (coords.size == 1) {
                runCatching {
                    cameraState.move(CameraUpdateFactory.newLatLngZoom(coords.first(), 15f))
                }
                hasFittedBounds = true
            } else if (coords.size > 1) {
                val bounds = LatLngBounds.builder().apply { coords.forEach(::include) }.build()
                val pad = (minOf(mapSize.width, mapSize.height) / 6).coerceIn(16, 72)
                runCatching { cameraState.move(CameraUpdateFactory.newLatLngBounds(bounds, pad)) }
                hasFittedBounds = true
            }
        }
        delay(30)
        recomputeMarkerLayout()
    }

    LaunchedEffect(
        mapReady,
        mapSize,
        validEvents,
        validPlaces,
        alwaysShowBubbles,
        measuredLabels,
    ) {
        if (mapReady && mapSize.width > 0 && mapSize.height > 0) {
            recomputeMarkerLayout()
        }
    }

    LaunchedEffect(mapReady, cameraState.isMoving, alwaysShowBubbles, validEvents, validPlaces) {
        if (!mapReady) return@LaunchedEffect
        if (!cameraState.isMoving) {
            val pos = cameraState.position
            latestOnCameraChanged?.invoke(pos.target.latitude, pos.target.longitude, pos.zoom)
            delay(60)
            recomputeMarkerLayout()
        } else {
            snapshotFlow { cameraState.position.zoom >= 18f }
                .distinctUntilChanged()
                .collect {
                    recomputeMarkerLayout()
                }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (mapReady) {
                val pos = cameraState.position
                latestOnCameraChanged?.invoke(pos.target.latitude, pos.target.longitude, pos.zoom)
            }
            latestOnMapTouchChange?.invoke(false)
        }
    }

    val animatedEntries = remember { mutableStateMapOf<String, AnimatedMarkerEntry>() }
    LaunchedEffect(layoutResult) {
        val nextPayloads = LinkedHashMap<String, AnimatedMarkerPayload>()
        layoutResult.visiblePlaceDots.forEach { (place, labelOnLeft, z) ->
            val k = "dot_${place.key}"
            nextPayloads[k] = AnimatedMarkerPayload.Dot(k, place, labelOnLeft, z)
        }
        layoutResult.visiblePlaceBubbles.forEach { (place, z) ->
            val k = "pb_${place.key}"
            nextPayloads[k] = AnimatedMarkerPayload.PlaceBubble(k, place, z)
        }
        layoutResult.visibleEvents.forEach { (ev, z) ->
            val k = "ev_${ev.postId}"
            nextPayloads[k] = AnimatedMarkerPayload.EventBubble(k, ev, z)
        }

        val existingKeys = animatedEntries.keys.toList()
        existingKeys.forEach { k ->
            val entry = animatedEntries[k] ?: return@forEach
            val next = nextPayloads[k]
            if (next != null) {
                entry.payload = next
                entry.targetVisible = true
            } else {
                entry.targetVisible = false
            }
        }
        nextPayloads.forEach { (k, payload) ->
            if (!animatedEntries.containsKey(k)) {
                animatedEntries[k] = AnimatedMarkerEntry(
                    key = k,
                    initialPayload = payload,
                    initialTargetVisible = true,
                )
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { mapSize = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        latestOnMapTouchChange?.invoke(event.changes.any { it.pressed })
                    }
                }
            },
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(
                mapType = if (satelliteView) MapType.HYBRID else MapType.NORMAL,
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = false,
            ),
            onMapLoaded = { mapReady = true },
            onMapClick = { latLng ->
                onMapClick?.invoke(latLng.latitude, latLng.longitude)
            },
        ) {
            MapEffect(Unit) {
                mapReady = true
            }
            currentLocation?.let { location ->
                val locationBitmap = rememberComposeBitmap("currentLocation") {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .shadow(4.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color(0xFF4285F4))
                            .border(3.dp, Color.White, CircleShape),
                    )
                }
                val locationIcon = remember(locationBitmap) { BitmapDescriptorFactory.fromBitmap(locationBitmap) }
                Marker(
                    state = rememberUpdatedMarkerState(position = location),
                    icon = locationIcon,
                    anchor = Offset(0.5f, 0.5f),
                    zIndex = 1000f,
                )
            }
            animatedEntries.values.toList().forEach { entry ->
                key(entry.key) {
                    val scaleAnim = remember { Animatable(0f) }
                    LaunchedEffect(entry.targetVisible) {
                        scaleAnim.animateTo(
                            targetValue = if (entry.targetVisible) 1f else 0f,
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        )
                        if (!entry.targetVisible && scaleAnim.value <= 0.01f) {
                            animatedEntries.remove(entry.key)
                        }
                    }
                    val rawScale = scaleAnim.value
                    val step = (rawScale * 24f).roundToInt().coerceIn(0, 24)

                    when (val payload = entry.payload) {
                        is AnimatedMarkerPayload.Dot -> {
                            val place = payload.place
                            val labelOnLeft = payload.labelOnLeft
                            val z = payload.zIndex
                            val dotBoxSize = 28.dp
                            val gap = 6.dp
                            val labelText = place.name
                            val emojiText = place.activityEmojis.joinToString(" ")
                            val labelW = measuredLabels[place.key]?.labelWidthPx ?: 0f
                            val dotPx = with(density) { dotBoxSize.toPx() }
                            val gapPx = with(density) { gap.toPx() }
                            val hasLabel = labelW > 0f
                            val totalW = if (hasLabel) labelW + gapPx + dotPx else dotPx
                            val anchorX = when {
                                !hasLabel -> 0.5f
                                labelOnLeft -> (labelW + gapPx + dotPx / 2f) / totalW
                                else -> (dotPx / 2f) / totalW
                            }.coerceIn(0.05f, 0.95f)
                            val anchor = Offset(anchorX, 0.5f)
                            val placeBitmap = place.photoUrl?.let { placeBitmaps[it] }
                            val markerState = rememberUpdatedMarkerState(position = LatLng(place.lat, place.lng))
                            val baseBitmap = rememberComposeBitmap(
                                "dot",
                                place.key,
                                place.name,
                                place.postCount,
                                place.hasActivities,
                                labelOnLeft,
                                emojiText,
                                placeBitmap != null,
                                tokens.isDark,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(gap),
                                    modifier = Modifier.defaultMinSize(minWidth = dotBoxSize, minHeight = dotBoxSize),
                                ) {
                                    if (hasLabel && labelOnLeft) {
                                        PlaceDotLabel(
                                            title = labelText,
                                            emojiText = emojiText,
                                            alignEnd = true,
                                            titleStyle = labelTitleStyle,
                                            emojiStyle = labelEmojiStyle,
                                        )
                                    }
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.size(dotBoxSize),
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .shadow(4.dp, CircleShape)
                                                .clip(CircleShape)
                                                .background(SaigonColors.Accent)
                                                .border(2.dp, Color.White, CircleShape),
                                        ) {
                                            if (placeBitmap != null) {
                                                Image(
                                                    bitmap = placeBitmap,
                                                    contentDescription = place.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            }
                                            Text(
                                                text = place.postCount.coerceIn(0, 99).toString(),
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                style = TextStyle(
                                                    shadow = Shadow(
                                                        color = Color.Black,
                                                        offset = Offset(0f, 0f),
                                                        blurRadius = 3f,
                                                    ),
                                                ),
                                            )
                                        }
                                        if (place.hasActivities) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(9.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFF5C518))
                                                    .border(1.dp, Color.White, CircleShape),
                                            )
                                        }
                                    }
                                    if (hasLabel && !labelOnLeft) {
                                        PlaceDotLabel(
                                            title = labelText,
                                            emojiText = emojiText,
                                            alignEnd = false,
                                            titleStyle = labelTitleStyle,
                                            emojiStyle = labelEmojiStyle,
                                        )
                                    }
                                }
                            }
                            val icon = rememberScaledBitmapDescriptor(baseBitmap, step, anchor.x, anchor.y)
                            Marker(
                                state = markerState,
                                icon = icon,
                                alpha = rawScale.coerceIn(0f, 1f),
                                visible = step > 0,
                                anchor = anchor,
                                zIndex = z,
                                onClick = {
                                    if (entry.targetVisible) {
                                        onPlaceClick(place.key)
                                    }
                                    true
                                },
                            )
                        }

                        is AnimatedMarkerPayload.PlaceBubble -> {
                            val place = payload.place
                            val z = payload.zIndex
                            val anchor = Offset(0.5f, 1.0f)
                            val placeBitmap = place.photoUrl?.let { placeBitmaps[it] }
                            val markerState = rememberUpdatedMarkerState(position = LatLng(place.lat, place.lng))
                            val baseBitmap = rememberComposeBitmap(
                                "placeBubble",
                                place.key,
                                place.name,
                                place.postCount,
                                place.activityCount,
                                place.hasActivities,
                                place.activityEmojis,
                                placeBitmap != null,
                                tokens.isDark,
                                language,
                            ) {
                                Box(modifier = Modifier.defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)) {
                                    MapBubbleContainer(
                                        fillColor = tokens.surface,
                                        borderColor = tokens.line,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.widthIn(max = 168.dp),
                                        ) {
                                            if (placeBitmap != null) {
                                                Image(
                                                    bitmap = placeBitmap,
                                                    contentDescription = place.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(RoundedCornerShape(10.dp)),
                                                )
                                            }
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = "📍 ${place.name}",
                                                    color = tokens.text,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                val emojiPart = place.activityEmojis.joinToString(" ")
                                                val counts = buildList {
                                                    if (place.activityCount > 0) {
                                                        add(Copy.text(language, "activityCountShort", mapOf("count" to place.activityCount.toString())))
                                                    }
                                                    if (place.postCount > 0 || place.activityCount == 0L) {
                                                        add(Copy.text(language, "postCount", mapOf("count" to place.postCount.coerceAtLeast(0).toString())))
                                                    }
                                                }.joinToString(", ")
                                                if (emojiPart.isNotBlank() || counts.isNotBlank()) {
                                                    Text(
                                                        text = listOfNotNull(emojiPart.takeIf { it.isNotBlank() }, counts.takeIf { it.isNotBlank() }).joinToString(" "),
                                                        color = tokens.muted,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (place.hasActivities) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(11.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFF5C518))
                                                .border(1.dp, Color.White, CircleShape),
                                        )
                                    }
                                }
                            }
                            val icon = rememberScaledBitmapDescriptor(baseBitmap, step, anchor.x, anchor.y)
                            Marker(
                                state = markerState,
                                icon = icon,
                                alpha = rawScale.coerceIn(0f, 1f),
                                visible = step > 0,
                                anchor = anchor,
                                zIndex = z,
                                onClick = {
                                    if (entry.targetVisible) {
                                        onPlaceClick(place.key)
                                    }
                                    true
                                },
                            )
                        }

                        is AnimatedMarkerPayload.EventBubble -> {
                            val ev = payload.event
                            val z = payload.zIndex
                            val anchor = Offset(0.5f, 1.0f)
                            val markerState = rememberUpdatedMarkerState(position = LatLng(ev.lat, ev.lng))
                            val baseBitmap = rememberComposeBitmap(
                                "event",
                                ev.postId,
                                ev.title,
                                ev.emoji,
                                ev.location,
                                ev.eventPrice ?: -1L,
                                ev.eventStartAt ?: -1L,
                                ev.attendeeCount,
                                ev.attendees.size,
                                tokens.isDark,
                                language,
                            ) {
                                MapBubbleContainer(
                                    fillColor = tokens.surface,
                                    borderColor = tokens.line,
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                                            .widthIn(max = 168.dp),
                                    ) {
                                        Text(
                                            text = "${ev.emoji} ${ev.title}",
                                            color = tokens.text,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            lineHeight = 15.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val priceText = ev.eventPrice?.takeIf { it > 0L }?.let(::formatVnd) ?: Copy.text(language, "free")
                                        val clock = ev.eventStartAt?.let(::formatBubbleClock)
                                        val metaLine = listOfNotNull(clock, "💰 $priceText").joinToString(" · ")
                                        Text(
                                            text = metaLine,
                                            color = tokens.muted,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (ev.location.isNotBlank()) {
                                            Text(
                                                text = "📍 ${ev.location}",
                                                color = tokens.muted,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        val shownFaces = ev.attendees.take(4)
                                        val count = ev.attendeeCount.coerceAtLeast(shownFaces.size)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            if (shownFaces.isNotEmpty()) {
                                                Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                                                    shownFaces.forEach { person ->
                                                        Box(
                                                            contentAlignment = Alignment.Center,
                                                            modifier = Modifier
                                                                .size(16.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(0xFFD9D0C6))
                                                                .border(1.dp, tokens.surface, CircleShape),
                                                        ) {
                                                            Text(
                                                                text = person.name.take(1).uppercase(),
                                                                color = SaigonColors.Ink,
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.ExtraBold,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            Text(
                                                text = if (count > 0) "${Copy.text(language, "attendees")} · $count" else Copy.text(language, "attendees"),
                                                color = tokens.muted,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                            val icon = rememberScaledBitmapDescriptor(baseBitmap, step, anchor.x, anchor.y)
                            Marker(
                                state = markerState,
                                icon = icon,
                                alpha = rawScale.coerceIn(0f, 1f),
                                visible = step > 0,
                                anchor = anchor,
                                zIndex = z,
                                onClick = {
                                    if (entry.targetVisible) {
                                        onEventClick(ev.postId)
                                    }
                                    true
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private val markerMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

@Composable
private fun rememberComposeBitmap(
    vararg keys: Any,
    content: @Composable @UiComposable () -> Unit,
): Bitmap {
    val parent = LocalView.current as ViewGroup
    val compositionContext = rememberCompositionContext()
    val latestContent = rememberUpdatedState(content)
    return remember(parent, compositionContext, *keys) {
        val host = parent.rootView as? ViewGroup ?: parent
        val canvasOfHolding = Canvas()
        val composeView = androidx.compose.ui.platform.ComposeView(host.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setParentCompositionContext(compositionContext)
            setContent { latestContent.value() }
        }.also(host::addView)
        try {
            composeView.draw(canvasOfHolding)
            composeView.measure(markerMeasureSpec, markerMeasureSpec)
            val w = composeView.measuredWidth.coerceAtLeast(1)
            val h = composeView.measuredHeight.coerceAtLeast(1)
            composeView.layout(0, 0, w, h)
            createBitmap(w, h).applyCanvas { composeView.draw(this) }
        } finally {
            host.removeView(composeView)
        }
    }
}

@Composable
private fun rememberScaledBitmapDescriptor(
    base: Bitmap,
    step: Int,
    pivotFractionX: Float,
    pivotFractionY: Float,
): BitmapDescriptor = remember(base, step, pivotFractionX, pivotFractionY) {
    if (step <= 0 || step >= 24) {
        BitmapDescriptorFactory.fromBitmap(base)
    } else {
        val s = step / 24f
        val out = createBitmap(base.width, base.height)
        out.applyCanvas {
            save()
            scale(s, s, base.width * pivotFractionX, base.height * pivotFractionY)
            drawBitmap(base, 0f, 0f, null)
            restore()
        }
        BitmapDescriptorFactory.fromBitmap(out)
    }
}

@Composable
private fun PlaceDotLabel(
    title: String,
    emojiText: String,
    alignEnd: Boolean,
    titleStyle: TextStyle,
    emojiStyle: TextStyle,
) {
    val labelShadow = Shadow(
        color = Color.Black.copy(alpha = 0.92f),
        offset = Offset(0f, 1f),
        blurRadius = 4f,
    )
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        modifier = Modifier.widthIn(max = 144.dp),
    ) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
                style = titleStyle.copy(color = Color.White, shadow = labelShadow),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (emojiText.isNotBlank()) {
            Text(
                text = emojiText,
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
                style = emojiStyle.copy(color = Color.White, shadow = labelShadow),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MapBubbleContainer(
    fillColor: Color,
    borderColor: Color,
    content: @Composable BoxScope.() -> Unit,
) {
    val pointerHeight = 7.dp
    val pointerHalfWidth = 7.dp
    val cornerRadius = 20.dp

    Box(
        modifier = Modifier
            .padding(2.dp)
            .padding(bottom = pointerHeight)
            .drawBehind {
                val ptrH = pointerHeight.toPx()
                val ptrHalf = pointerHalfWidth.toPx().coerceAtMost(size.width / 2f)
                val bodyH = size.height.coerceAtLeast(2f)
                val w = size.width.coerceAtLeast(2f)
                val corner = cornerRadius.toPx().coerceAtMost(minOf(w, bodyH) / 2f).coerceAtLeast(1f)
                val d = corner * 2f

                val path = Path().apply {
                    moveTo(corner, 0f)
                    lineTo(w - corner, 0f)
                    arcTo(Rect(w - d, 0f, w, d), startAngleDegrees = -90f, sweepAngleDegrees = 90f, forceMoveTo = false)
                    lineTo(w, bodyH - corner)
                    arcTo(Rect(w - d, bodyH - d, w, bodyH), startAngleDegrees = 0f, sweepAngleDegrees = 90f, forceMoveTo = false)
                    lineTo((w / 2f + ptrHalf).coerceAtMost(w - corner), bodyH)
                    lineTo(w / 2f, bodyH + ptrH)
                    lineTo((w / 2f - ptrHalf).coerceAtLeast(corner), bodyH)
                    lineTo(corner, bodyH)
                    arcTo(Rect(0f, bodyH - d, d, bodyH), startAngleDegrees = 90f, sweepAngleDegrees = 90f, forceMoveTo = false)
                    lineTo(0f, corner)
                    arcTo(Rect(0f, 0f, d, d), startAngleDegrees = 180f, sweepAngleDegrees = 90f, forceMoveTo = false)
                    close()
                }
                drawPath(path, color = fillColor)
                drawPath(path, color = borderColor, style = Stroke(width = 1.dp.toPx()))
            }
            .padding(horizontal = 11.dp, vertical = 8.dp),
        content = content,
    )
}

private fun formatBubbleClock(epochMillis: Long): String {
    val fmt = SimpleDateFormat("HH:mm", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }
    return fmt.format(Date(epochMillis))
}

private class SaigonMapJsBridge(
    private val onEvent: (String) -> Unit,
    private val onPlace: (String) -> Unit,
    private val onPick: ((Double, Double) -> Unit)?,
) {
    @JavascriptInterface
    fun openEvent(postId: String) {
        onEvent(postId)
    }

    @JavascriptInterface
    fun openPlace(key: String) {
        onPlace(key)
    }

    @JavascriptInterface
    fun pickLatLng(lat: Double, lng: Double) {
        onPick?.invoke(lat, lng)
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun WebSaigonMapView(
    events: List<SaigonMapEventPin>,
    places: List<SaigonMapPlacePin>,
    mapsApiKey: String?,
    centerLat: Double?,
    centerLng: Double?,
    initialZoom: Float,
    alwaysShowBubbles: Boolean,
    darkTheme: Boolean,
    satelliteView: Boolean,
    currentLocation: LatLng?,
    locateRequestCount: Int,
    onEventClick: (String) -> Unit,
    onPlaceClick: (String) -> Unit,
    onMapClick: ((Double, Double) -> Unit)?,
    onMapTouchChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val latestOnEvent by rememberUpdatedState(onEventClick)
    val latestOnPlace by rememberUpdatedState(onPlaceClick)
    val latestOnMapClick by rememberUpdatedState(onMapClick)
    val latestOnMapTouchChange by rememberUpdatedState(onMapTouchChange)
    val latestSatelliteView by rememberUpdatedState(satelliteView)
    val latestCurrentLocation by rememberUpdatedState(currentLocation)
    val latestLocateRequestCount by rememberUpdatedState(locateRequestCount)

    fun applyMapControls(webView: WebView?) {
        webView ?: return
        webView.evaluateJavascript("if(window.__setSaigonMapType)window.__setSaigonMapType(${latestSatelliteView});", null)
        val location = latestCurrentLocation
        if (latestLocateRequestCount > 0 && location != null) {
            webView.evaluateJavascript(
                "if(window.__locateSaigonMap)window.__locateSaigonMap(${location.latitude},${location.longitude});",
                null,
            )
        }
    }

    val markersJson = remember(events, places) {
        val list = buildList {
            places.forEach { p ->
                val emojis = p.activityEmojis.joinToString(" ")
                val subtitle = listOfNotNull(
                    emojis.takeIf { it.isNotBlank() },
                    if (p.postCount > 0) "${p.postCount}" else null,
                ).joinToString(" · ")
                add(
                    JsMapMarker(
                        kind = "place",
                        id = p.key,
                        title = p.name,
                        subtitle = subtitle,
                        emoji = "📍",
                        lat = p.lat,
                        lng = p.lng,
                        count = p.postCount,
                        hasActivities = p.hasActivities,
                        activityEmojis = emojis,
                    ),
                )
            }
            events.forEach { ev ->
                add(
                    JsMapMarker(
                        kind = "event",
                        id = ev.postId,
                        title = ev.title,
                        subtitle = ev.location,
                        emoji = ev.emoji.ifBlank { "📅" },
                        lat = ev.lat,
                        lng = ev.lng,
                        count = ev.attendeeCount.toLong(),
                    ),
                )
            }
        }
        mapJson.encodeToString(ListSerializer(JsMapMarker.serializer()), list)
    }

    val html = remember(mapsApiKey, darkTheme, centerLat, centerLng, initialZoom, alwaysShowBubbles) {
        buildMapHtml(
            mapsApiKey = mapsApiKey.orEmpty(),
            darkTheme = darkTheme,
            centerLat = centerLat ?: 10.7769,
            centerLng = centerLng ?: 106.7009,
            zoom = initialZoom.toInt(),
            hasFixedCenter = centerLat != null && centerLng != null,
            alwaysShowBubbles = alwaysShowBubbles,
        )
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(markersJson, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        wv.evaluateJavascript("if (window.__setSaigonMarkers) window.__setSaigonMarkers($markersJson);", null)
    }

    LaunchedEffect(satelliteView, currentLocation, locateRequestCount, webViewRef) {
        applyMapControls(webViewRef)
    }

    DisposableEffect(Unit) {
        onDispose { latestOnMapTouchChange?.invoke(false) }
    }

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    latestOnMapTouchChange?.invoke(event.changes.any { it.pressed })
                }
            }
        },
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webChromeClient = WebChromeClient()
                    setOnTouchListener { v, _ ->
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        false
                    }
                    addJavascriptInterface(
                        SaigonMapJsBridge(
                            onEvent = { id -> post { latestOnEvent(id) } },
                            onPlace = { key -> post { latestOnPlace(key) } },
                            onPick = { lat, lng -> post { latestOnMapClick?.invoke(lat, lng) } },
                        ),
                        "SaigonMapBridge",
                    )
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            view?.evaluateJavascript("if (window.__setSaigonMarkers) window.__setSaigonMarkers($markersJson);", null)
                            applyMapControls(view)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            if (request?.isForMainFrame == true) {
                                CrashReporter.log("WebSaigonMapView error: ${error?.errorCode} ${error?.description}")
                            }
                        }

                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: RenderProcessGoneDetail?,
                        ): Boolean {
                            val didCrash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) detail?.didCrash() else null
                            CrashReporter.recordException(
                                IllegalStateException("WebView render process gone (didCrash=$didCrash)"),
                                "WebSaigonMapView render process gone",
                            )
                            return false
                        }
                    }
                    loadDataWithBaseURL(
                        BuildConfig.SERVER_ORIGIN.trimEnd('/') + "/",
                        html,
                        "text/html",
                        "UTF-8",
                        null,
                    )
                    webViewRef = this
                }
            },
            update = { wv ->
                webViewRef = wv
                wv.evaluateJavascript("if (window.__setSaigonMarkers) window.__setSaigonMarkers($markersJson);", null)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun buildMapHtml(
    mapsApiKey: String,
    darkTheme: Boolean,
    centerLat: Double,
    centerLng: Double,
    zoom: Int,
    hasFixedCenter: Boolean,
    alwaysShowBubbles: Boolean,
): String {
    val bg = if (darkTheme) "#241f1b" else "#fffdf8"
    val fg = if (darkTheme) "#f6f1ea" else "#241f1a"
    val line = if (darkTheme) "#3c342e" else "#e6ddd2"
    val tileUrl = if (darkTheme) {
        "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
    } else {
        "https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png"
    }
    val safeKey = mapsApiKey.replace("'", "").replace("\"", "")

    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<style>
  html, body, #map { margin:0; padding:0; width:100%; height:100%; background:$bg; overflow:hidden; font-family: sans-serif; }
  .sq-bubble {
    background: $bg; color: $fg; border: 1px solid $line; border-radius: 20px;
    padding: 8px 11px; font-size: 12px; font-weight: 800; line-height: 1.25;
    box-shadow: 0 10px 24px rgba(36,31,26,0.18); white-space: nowrap; max-width: 168px;
    cursor: pointer; position: relative;
    transform: scale(0); transform-origin: center bottom; transition: transform 220ms ease;
  }
  .sq-bubble.is-shown { transform: scale(1); }
  .sq-bubble::after {
    content: ""; position: absolute; left: 50%; bottom: -5px; width: 10px; height: 10px;
    background: $bg; border-right: 1px solid $line; border-bottom: 1px solid $line;
    transform: translateX(-50%) rotate(45deg);
  }
  .sq-dot {
    width: 24px; height: 24px; border-radius: 50%; background: #e9897c; color: #fff;
    border: 2px solid #fff; font-size: 9px; font-weight: 800;
    display: flex; align-items: center; justify-content: center;
    box-shadow: 0 2px 8px rgba(0,0,0,0.35); cursor: pointer; position: relative;
    transform: scale(0); transform-origin: center; transition: transform 220ms ease;
  }
  .sq-dot.is-shown { transform: scale(1); }
  .sq-dot .act-dot, .sq-bubble .act-dot {
    position: absolute; top: -2px; right: -2px; width: 9px; height: 9px;
    border-radius: 50%; background: #f5c518; border: 1px solid #fff;
  }
  .sq-label {
    margin-left: 6px; color: #fff; font-size: 12px; font-weight: 700;
    text-shadow: 0 -1px 1px rgba(0,0,0,0.9), 0 1px 3px rgba(0,0,0,0.9);
    white-space: nowrap; max-width: 140px; overflow: hidden; text-overflow: ellipsis;
  }
  .sq-wrap { display: inline-flex; align-items: center; }
</style>
</head>
<body>
<div id="map"></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
  var pendingMarkers = [];
  var backend = null;
  var leafletMap = null;
  var leafletLayer = null;
  var leafletStandardLayer = null;
  var leafletSatelliteLayer = null;
  var gmap = null;
  var gOverlays = [];
  var googleLocationMarker = null;
  var leafletLocationMarker = null;
  var pendingUserLocation = null;
  var satelliteViewEnabled = false;
  var fixedCenter = $hasFixedCenter;
  var alwaysBubbles = $alwaysShowBubbles;

  function applyMapType() {
    if (backend === 'google' && gmap) {
      gmap.setMapTypeId(satelliteViewEnabled ? 'hybrid' : 'roadmap');
    } else if (backend === 'leaflet' && leafletMap) {
      if (satelliteViewEnabled) {
        if (!leafletSatelliteLayer) {
          leafletSatelliteLayer = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
            maxZoom: 19,
            attribution: 'Tiles &copy; Esri'
          });
        }
        if (leafletMap.hasLayer(leafletStandardLayer)) leafletMap.removeLayer(leafletStandardLayer);
        if (!leafletMap.hasLayer(leafletSatelliteLayer)) leafletSatelliteLayer.addTo(leafletMap);
      } else {
        if (leafletSatelliteLayer && leafletMap.hasLayer(leafletSatelliteLayer)) leafletMap.removeLayer(leafletSatelliteLayer);
        if (leafletStandardLayer && !leafletMap.hasLayer(leafletStandardLayer)) leafletStandardLayer.addTo(leafletMap);
      }
    }
  }

  function applyUserLocation() {
    if (!pendingUserLocation) return;
    if (backend === 'google' && gmap && window.google && google.maps) {
      gmap.panTo(pendingUserLocation);
      gmap.setZoom(16);
      if (!googleLocationMarker) {
        googleLocationMarker = new google.maps.Marker({
          map: gmap,
          position: pendingUserLocation,
          clickable: false,
          icon: { path: google.maps.SymbolPath.CIRCLE, scale: 8, fillColor: '#4285f4', fillOpacity: 1, strokeColor: '#fff', strokeWeight: 2 }
        });
      } else {
        googleLocationMarker.setPosition(pendingUserLocation);
      }
    } else if (backend === 'leaflet' && leafletMap) {
      leafletMap.setView([pendingUserLocation.lat, pendingUserLocation.lng], 16);
      if (leafletLocationMarker) leafletLocationMarker.setLatLng([pendingUserLocation.lat, pendingUserLocation.lng]);
      else leafletLocationMarker = L.circleMarker([pendingUserLocation.lat, pendingUserLocation.lng], {
        radius: 8, color: '#fff', weight: 2, fillColor: '#4285f4', fillOpacity: 1
      }).addTo(leafletMap);
    }
  }

  window.__setSaigonMapType = function(satellite) {
    satelliteViewEnabled = !!satellite;
    applyMapType();
  };

  window.__locateSaigonMap = function(lat, lng) {
    pendingUserLocation = { lat: lat, lng: lng };
    applyUserLocation();
  };

  function makeEl(m, currentZoom) {
    var wrap = document.createElement('div');
    wrap.className = 'sq-wrap';
    var showBubble = m.kind === 'event' || alwaysBubbles || currentZoom >= 18;
    if (showBubble) {
      var b = document.createElement('div');
      b.className = 'sq-bubble';
      b.textContent = (m.emoji ? m.emoji + ' ' : '') + m.title;
      if (m.hasActivities) {
        var ad = document.createElement('span');
        ad.className = 'act-dot';
        b.appendChild(ad);
      }
      b.onclick = function(e) {
        e.stopPropagation();
        if (window.SaigonMapBridge) {
          if (m.kind === 'event') SaigonMapBridge.openEvent(m.id);
          else SaigonMapBridge.openPlace(m.id);
        }
      };
      wrap.appendChild(b);
      requestAnimationFrame(function() { requestAnimationFrame(function() { b.classList.add('is-shown'); }); });
    } else {
      var d = document.createElement('div');
      d.className = 'sq-dot';
      d.textContent = String(Math.min(99, Math.max(0, m.count || 0)));
      if (m.hasActivities) {
        var ad2 = document.createElement('span');
        ad2.className = 'act-dot';
        d.appendChild(ad2);
      }
      var lbl = document.createElement('div');
      lbl.className = 'sq-label';
      lbl.textContent = m.title + (m.activityEmojis ? ' ' + m.activityEmojis : '');
      wrap.appendChild(d);
      wrap.appendChild(lbl);
      requestAnimationFrame(function() { requestAnimationFrame(function() { d.classList.add('is-shown'); }); });
      wrap.onclick = function(e) { e.stopPropagation(); if (window.SaigonMapBridge) SaigonMapBridge.openPlace(m.id); };
    }
    return wrap;
  }

  function initLeaflet() {
    if (backend === 'leaflet') return;
    backend = 'leaflet';
    document.getElementById('map').innerHTML = '';
    leafletMap = L.map('map', { zoomControl: false, attributionControl: true }).setView([$centerLat, $centerLng], $zoom);
    leafletStandardLayer = L.tileLayer('$tileUrl', {
      maxZoom: 19,
      subdomains: 'abcd',
      attribution: '&copy; OpenStreetMap contributors &copy; CARTO'
    }).addTo(leafletMap);
    leafletLayer = L.layerGroup().addTo(leafletMap);
    applyMapType();
    applyUserLocation();
    leafletMap.on('click', function(e) {
      if (window.SaigonMapBridge) SaigonMapBridge.pickLatLng(e.latlng.lat, e.latlng.lng);
    });
    leafletMap.on('zoomend', renderMarkers);
    renderMarkers();
  }

  function renderMarkers() {
    if (backend === 'leaflet' && leafletMap && leafletLayer) {
      leafletLayer.clearLayers();
      var z = leafletMap.getZoom();
      var bounds = [];
      pendingMarkers.forEach(function(m) {
        bounds.push([m.lat, m.lng]);
        var el = makeEl(m, z);
        var isBubble = m.kind === 'event' || alwaysBubbles || z >= 18;
        var icon = L.divIcon({ className: '', html: '', iconSize: [0, 0] });
        var marker = L.marker([m.lat, m.lng], { icon: icon }).addTo(leafletLayer);
        var iconEl = marker.getElement();
        if (iconEl) {
          iconEl.style.width = 'auto';
          iconEl.style.height = 'auto';
          iconEl.style.transform += isBubble ? ' translate(-50%, -100%)' : ' translate(-12px, -50%)';
          iconEl.appendChild(el);
        }
      });
      if (!fixedCenter && bounds.length === 1) {
        leafletMap.setView(bounds[0], 15);
      } else if (!fixedCenter && bounds.length > 1) {
        leafletMap.fitBounds(bounds, { padding: [36, 36], maxZoom: 15 });
      }
    } else if (backend === 'google' && gmap && window.google && google.maps) {
      gOverlays.forEach(function(o) { o.setMap(null); });
      gOverlays = [];
      var z = gmap.getZoom() || $zoom;
      var bounds = new google.maps.LatLngBounds();
      pendingMarkers.forEach(function(m) {
        var pos = new google.maps.LatLng(m.lat, m.lng);
        bounds.extend(pos);
        var ov = new google.maps.OverlayView();
        var isBubble = m.kind === 'event' || alwaysBubbles || z >= 18;
        var el = makeEl(m, z);
        el.style.position = 'absolute';
        el.style.transform = isBubble ? 'translate(-50%, -100%)' : 'translate(-12px, -50%)';
        ov.onAdd = function() {
          var panes = ov.getPanes();
          if (panes && panes.overlayMouseTarget) panes.overlayMouseTarget.appendChild(el);
          if (google.maps.OverlayView.preventMapHitsAndGesturesFrom) google.maps.OverlayView.preventMapHitsAndGesturesFrom(el);
        };
        ov.draw = function() {
          var proj = ov.getProjection();
          if (!proj) return;
          var pt = proj.fromLatLngToDivPixel(pos);
          if (pt) { el.style.left = pt.x + 'px'; el.style.top = pt.y + 'px'; }
        };
        ov.onRemove = function() { if (el.parentNode) el.parentNode.removeChild(el); };
        ov.setMap(gmap);
        gOverlays.push(ov);
      });
      if (!fixedCenter && pendingMarkers.length === 1) {
        gmap.setCenter(bounds.getCenter());
        gmap.setZoom(15);
      } else if (!fixedCenter && pendingMarkers.length > 1) {
        gmap.fitBounds(bounds);
      }
    }
  }

  window.__setSaigonMarkers = function(markers) {
    pendingMarkers = Array.isArray(markers) ? markers : [];
    renderMarkers();
  };

  window.gm_authFailure = function() {
    initLeaflet();
  };

  function boot() {
    var key = '$safeKey';
    if (!key) {
      initLeaflet();
      return;
    }
    var timer = setTimeout(function() {
      if (!backend) initLeaflet();
    }, 3500);
    window.__gmapReady = function() {
      clearTimeout(timer);
      if (backend) return;
      try {
        backend = 'google';
        gmap = new google.maps.Map(document.getElementById('map'), {
          center: { lat: $centerLat, lng: $centerLng },
          zoom: $zoom,
          disableDefaultUI: true,
          zoomControl: true,
          mapTypeId: satelliteViewEnabled ? 'hybrid' : 'roadmap'
        });
        applyMapType();
        applyUserLocation();
        gmap.addListener('click', function(e) {
          if (window.SaigonMapBridge && e.latLng) SaigonMapBridge.pickLatLng(e.latLng.lat(), e.latLng.lng());
        });
        gmap.addListener('zoom_changed', renderMarkers);
        renderMarkers();
      } catch (err) {
        backend = null;
        initLeaflet();
      }
    };
    var s = document.createElement('script');
    s.src = 'https://maps.googleapis.com/maps/api/js?key=' + encodeURIComponent(key) + '&callback=__gmapReady';
    s.async = true;
    s.onerror = function() { clearTimeout(timer); initLeaflet(); };
    document.head.appendChild(s);
  }
  boot();
</script>
</body>
</html>
    """.trimIndent()
}
