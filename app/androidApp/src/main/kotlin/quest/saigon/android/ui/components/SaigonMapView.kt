package quest.saigon.android.ui.components

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import quest.saigon.android.BuildConfig
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.theme.SaigonColors

data class SaigonMapEventPin(
    val postId: String,
    val title: String,
    val location: String,
    val emoji: String = "📅",
    val lat: Double,
    val lng: Double,
    val attendeeCount: Int = 0,
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
)

private val mapJson = Json { encodeDefaults = true }

@Composable
fun SaigonMapView(
    events: List<SaigonMapEventPin> = emptyList(),
    places: List<SaigonMapPlacePin> = emptyList(),
    mapsApiKey: String? = null,
    centerLat: Double? = null,
    centerLng: Double? = null,
    initialZoom: Float = 13f,
    onEventClick: (String) -> Unit = {},
    onPlaceClick: (String) -> Unit = {},
    onMapClick: ((Double, Double) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalSaigonTokens.current
    if (BuildConfig.MAPS_CONFIGURED) {
        NativeGoogleMapView(
            events = events,
            places = places,
            centerLat = centerLat,
            centerLng = centerLng,
            initialZoom = initialZoom,
            onEventClick = onEventClick,
            onPlaceClick = onPlaceClick,
            onMapClick = onMapClick,
            modifier = modifier,
        )
    } else {
        WebSaigonMapView(
            events = events,
            places = places,
            mapsApiKey = mapsApiKey,
            centerLat = centerLat,
            centerLng = centerLng,
            initialZoom = initialZoom,
            darkTheme = tokens.isDark,
            onEventClick = onEventClick,
            onPlaceClick = onPlaceClick,
            onMapClick = onMapClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun NativeGoogleMapView(
    events: List<SaigonMapEventPin>,
    places: List<SaigonMapPlacePin>,
    centerLat: Double?,
    centerLng: Double?,
    initialZoom: Float,
    onEventClick: (String) -> Unit,
    onPlaceClick: (String) -> Unit,
    onMapClick: ((Double, Double) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalSaigonTokens.current
    val defaultCenter = remember(centerLat, centerLng) {
        LatLng(centerLat ?: 10.7769, centerLng ?: 106.7009)
    }
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultCenter, initialZoom)
    }

    LaunchedEffect(events, places, centerLat, centerLng) {
        if (centerLat != null && centerLng != null) {
            cameraState.move(CameraUpdateFactory.newLatLngZoom(LatLng(centerLat, centerLng), initialZoom))
            return@LaunchedEffect
        }
        val coords = events.map { LatLng(it.lat, it.lng) } + places.map { LatLng(it.lat, it.lng) }
        if (coords.size == 1) {
            cameraState.move(CameraUpdateFactory.newLatLngZoom(coords.first(), 15f))
        } else if (coords.size > 1) {
            val bounds = LatLngBounds.builder().apply { coords.forEach(::include) }.build()
            runCatching { cameraState.move(CameraUpdateFactory.newLatLngBounds(bounds, 72)) }
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraState,
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
        ),
        onMapClick = { latLng ->
            onMapClick?.invoke(latLng.latitude, latLng.longitude)
        },
    ) {
        places.forEach { place ->
            MarkerComposable(
                "place", place.key, place.name, place.postCount, place.hasActivities,
                state = MarkerState(position = LatLng(place.lat, place.lng)),
                onClick = {
                    onPlaceClick(place.key)
                    true
                },
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(tokens.surface)
                        .border(1.dp, tokens.line, RoundedCornerShape(14.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    val emojiPrefix = place.activityEmojis.firstOrNull()?.let { "$it " } ?: "📍 "
                    Text(
                        text = "$emojiPrefix${place.name}",
                        color = tokens.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        events.forEach { ev ->
            MarkerComposable(
                "event", ev.postId, ev.title,
                state = MarkerState(position = LatLng(ev.lat, ev.lng)),
                onClick = {
                    onEventClick(ev.postId)
                    true
                },
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(SaigonColors.Accent)
                        .border(1.dp, Color.White, RoundedCornerShape(16.dp))
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "${ev.emoji} ${ev.title}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebSaigonMapView(
    events: List<SaigonMapEventPin>,
    places: List<SaigonMapPlacePin>,
    mapsApiKey: String?,
    centerLat: Double?,
    centerLng: Double?,
    initialZoom: Float,
    darkTheme: Boolean,
    onEventClick: (String) -> Unit,
    onPlaceClick: (String) -> Unit,
    onMapClick: ((Double, Double) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val latestOnEvent by rememberUpdatedState(onEventClick)
    val latestOnPlace by rememberUpdatedState(onPlaceClick)
    val latestOnMapClick by rememberUpdatedState(onMapClick)

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
                        emoji = if (p.hasActivities) p.activityEmojis.firstOrNull().orEmpty().ifBlank { "📍" } else "📍",
                        lat = p.lat,
                        lng = p.lng,
                        count = p.postCount,
                        hasActivities = p.hasActivities,
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

    val html = remember(mapsApiKey, darkTheme, centerLat, centerLng, initialZoom) {
        buildMapHtml(
            mapsApiKey = mapsApiKey.orEmpty(),
            darkTheme = darkTheme,
            centerLat = centerLat ?: 10.7769,
            centerLng = centerLng ?: 106.7009,
            zoom = initialZoom.toInt(),
            hasFixedCenter = centerLat != null && centerLng != null,
        )
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(markersJson, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        wv.evaluateJavascript("if (window.__setSaigonMarkers) window.__setSaigonMarkers($markersJson);", null)
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webChromeClient = WebChromeClient()
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
    background: $bg; color: $fg; border: 1.5px solid $line; border-radius: 18px;
    padding: 6px 10px; font-size: 12px; font-weight: 800; line-height: 1.2;
    box-shadow: 0 6px 18px rgba(0,0,0,0.22); white-space: nowrap; max-width: 170px;
    overflow: hidden; text-overflow: ellipsis; cursor: pointer; position: relative;
  }
  .sq-bubble.event { background: #e9897c; color: #fff; border-color: #fff; }
  .sq-dot {
    width: 24px; height: 24px; border-radius: 50%; background: #e9897c; color: #fff;
    border: 2px solid #fff; font-size: 10px; font-weight: 800;
    display: flex; align-items: center; justify-content: center;
    box-shadow: 0 2px 8px rgba(0,0,0,0.35); cursor: pointer; position: relative;
  }
  .sq-dot .act-dot {
    position: absolute; top: -2px; right: -2px; width: 9px; height: 9px;
    border-radius: 50%; background: #f5c518; border: 1px solid #fff;
  }
  .sq-label {
    margin-top: 2px; color: #fff; font-size: 11px; font-weight: 800;
    text-shadow: 0 -1px 2px rgba(0,0,0,0.9), 0 1px 3px rgba(0,0,0,0.9);
    white-space: nowrap; max-width: 140px; overflow: hidden; text-overflow: ellipsis;
    text-align: center;
  }
  .sq-wrap { display: flex; flex-direction: column; align-items: center; }
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
  var gmap = null;
  var gOverlays = [];
  var fixedCenter = $hasFixedCenter;

  function makeEl(m) {
    var wrap = document.createElement('div');
    wrap.className = 'sq-wrap';
    if (m.kind === 'event') {
      var b = document.createElement('div');
      b.className = 'sq-bubble event';
      b.textContent = m.emoji + ' ' + m.title;
      b.onclick = function(e) { e.stopPropagation(); if (window.SaigonMapBridge) SaigonMapBridge.openEvent(m.id); };
      wrap.appendChild(b);
    } else if (fixedCenter || m.hasActivities || m.count >= 2) {
      var b2 = document.createElement('div');
      b2.className = 'sq-bubble';
      b2.textContent = (m.emoji ? m.emoji + ' ' : '') + m.title;
      b2.onclick = function(e) { e.stopPropagation(); if (window.SaigonMapBridge) SaigonMapBridge.openPlace(m.id); };
      wrap.appendChild(b2);
    } else {
      var d = document.createElement('div');
      d.className = 'sq-dot';
      d.textContent = m.count > 0 ? String(Math.min(99, m.count)) : '📍';
      if (m.hasActivities) {
        var ad = document.createElement('span');
        ad.className = 'act-dot';
        d.appendChild(ad);
      }
      var lbl = document.createElement('div');
      lbl.className = 'sq-label';
      lbl.textContent = m.title;
      wrap.appendChild(d);
      wrap.appendChild(lbl);
      wrap.onclick = function(e) { e.stopPropagation(); if (window.SaigonMapBridge) SaigonMapBridge.openPlace(m.id); };
    }
    return wrap;
  }

  function initLeaflet() {
    if (backend === 'leaflet') return;
    backend = 'leaflet';
    document.getElementById('map').innerHTML = '';
    leafletMap = L.map('map', { zoomControl: false, attributionControl: false }).setView([$centerLat, $centerLng], $zoom);
    L.tileLayer('$tileUrl', { maxZoom: 19, subdomains: 'abcd' }).addTo(leafletMap);
    leafletLayer = L.layerGroup().addTo(leafletMap);
    leafletMap.on('click', function(e) {
      if (window.SaigonMapBridge) SaigonMapBridge.pickLatLng(e.latlng.lat, e.latlng.lng);
    });
    renderMarkers();
  }

  function renderMarkers() {
    if (backend === 'leaflet' && leafletMap && leafletLayer) {
      leafletLayer.clearLayers();
      var bounds = [];
      pendingMarkers.forEach(function(m) {
        bounds.push([m.lat, m.lng]);
        var el = makeEl(m);
        var icon = L.divIcon({ className: '', html: '', iconSize: [0, 0] });
        var marker = L.marker([m.lat, m.lng], { icon: icon }).addTo(leafletLayer);
        var iconEl = marker.getElement();
        if (iconEl) {
          iconEl.style.width = 'auto';
          iconEl.style.height = 'auto';
          iconEl.style.transform += ' translate(-50%, -50%)';
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
      var bounds = new google.maps.LatLngBounds();
      pendingMarkers.forEach(function(m) {
        var pos = new google.maps.LatLng(m.lat, m.lng);
        bounds.extend(pos);
        var ov = new google.maps.OverlayView();
        var el = makeEl(m);
        el.style.position = 'absolute';
        el.style.transform = 'translate(-50%, -50%)';
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
        gmap.setCenter({ lat: pendingMarkers[0].lat, lng: pendingMarkers[0].lng });
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

  window.__initGoogleMap = function() {
    try {
      if (!window.google || !google.maps) { initLeaflet(); return; }
      backend = 'google';
      gmap = new google.maps.Map(document.getElementById('map'), {
        center: { lat: $centerLat, lng: $centerLng },
        zoom: $zoom,
        disableDefaultUI: true,
        gestureHandling: 'greedy'
      });
      gmap.addListener('click', function(e) {
        if (e.latLng && window.SaigonMapBridge) SaigonMapBridge.pickLatLng(e.latLng.lat(), e.latLng.lng());
      });
      renderMarkers();
    } catch (e) {
      initLeaflet();
    }
  };

  if ('$safeKey') {
    var s = document.createElement('script');
    s.src = 'https://maps.googleapis.com/maps/api/js?key=$safeKey&callback=__initGoogleMap';
    s.async = true;
    s.onerror = initLeaflet;
    document.head.appendChild(s);
    setTimeout(function() { if (!backend) initLeaflet(); }, 3500);
  } else {
    initLeaflet();
  }
</script>
</body>
</html>
""".trimIndent()
}
