package quest.saigon.maps

import quest.saigon.http.Jsons
import quest.saigon.town.PlaceSuggestion
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

interface PlaceLookup {
    fun search(query: String): List<PlaceSuggestion>
    fun details(placeId: String): PlaceSuggestion?
    fun photo(photoName: String): ByteArray? = null
}

object NoPlaces : PlaceLookup {
    override fun search(query: String): List<PlaceSuggestion> = emptyList()
    override fun details(placeId: String): PlaceSuggestion? = null
}

class GooglePlaces(private val apiKey: String) : PlaceLookup {
    override fun search(query: String): List<PlaceSuggestion> {
        val needle = query.trim()
        if (needle.length < 2) return emptyList()
        val body = Jsons.write(
            mapOf(
                "input" to needle,
                "includedRegionCodes" to listOf("vn"),
                "languageCode" to "vi",
                "locationBias" to mapOf(
                    "circle" to mapOf(
                        "center" to mapOf(
                            "latitude" to SAIGON_LAT,
                            "longitude" to SAIGON_LNG,
                        ),
                        "radius" to 40_000.0,
                    ),
                ),
            ),
        )
        val json = post(
            "https://places.googleapis.com/v1/places:autocomplete",
            body,
            "suggestions.placePrediction.placeId,suggestions.placePrediction.text,suggestions.placePrediction.structuredFormat",
        ) ?: return emptyList()
        val suggestions = json.path("suggestions")
        if (!suggestions.isArray) return emptyList()
        return (0 until suggestions.size()).mapNotNull { index ->
            val prediction = suggestions.get(index).path("placePrediction")
            val placeId = prediction.path("placeId").asText("").ifBlank { null } ?: return@mapNotNull null
            val main = prediction.path("structuredFormat").path("mainText").path("text").asText("")
            val secondary = prediction.path("structuredFormat").path("secondaryText").path("text").asText("")
            val text = prediction.path("text").path("text").asText("")
            PlaceSuggestion(
                name = main.ifBlank { text.substringBefore(",") }.ifBlank { text },
                address = secondary.ifBlank { text },
                placeId = placeId,
            )
        }
    }

    override fun details(placeId: String): PlaceSuggestion? {
        val id = placeId.trim()
        if (id.isBlank()) return null
        val encoded = URLEncoder.encode(id, StandardCharsets.UTF_8)
        val json = get(
            "https://places.googleapis.com/v1/places/$encoded",
            "id,displayName,formattedAddress,location,photos.name,primaryType",
        ) ?: return null
        val name = json.path("displayName").path("text").asText("").ifBlank { return null }
        val location = json.path("location")
        val lat = location.path("latitude").takeIf { it.isNumber }?.asDouble()
        val lng = location.path("longitude").takeIf { it.isNumber }?.asDouble()
        return PlaceSuggestion(
            name = name,
            address = json.path("formattedAddress").asText(""),
            placeId = json.path("id").asText(id).ifBlank { id },
            lat = lat,
            lng = lng,
            googlePhotoName = json.path("photos").path(0).path("name").asText("").ifBlank { null },
            category = json.path("primaryType").asText("").ifBlank { null },
        )
    }

    override fun photo(photoName: String): ByteArray? {
        val name = photoName.trim()
        if (!isGooglePlacePhotoName(name)) return null
        val encodedName = name.split('/').joinToString("/") { URLEncoder.encode(it, StandardCharsets.UTF_8) }
        return try {
            val connection = URI("https://places.googleapis.com/v1/$encodedName/media?maxWidthPx=480")
                .toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("X-Goog-Api-Key", apiKey)
            if (connection.responseCode !in 200..299 || !connection.contentType.orEmpty().startsWith("image/")) {
                null
            } else {
                connection.inputStream.use { it.readBytes() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun post(url: String, body: String, fieldMask: String) = request(url, "POST", body, fieldMask)

    private fun get(url: String, fieldMask: String) = request(url, "GET", null, fieldMask)

    private fun request(url: String, method: String, body: String?, fieldMask: String) = try {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("X-Goog-Api-Key", apiKey)
        connection.setRequestProperty("X-Goog-FieldMask", fieldMask)
        connection.setRequestProperty("Content-Type", "application/json")
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (connection.responseCode in 200..299) Jsons.parse(text) else null
    } catch (_: Exception) {
        null
    }

    companion object {
        const val SAIGON_LAT = 10.7769
        const val SAIGON_LNG = 106.7009
    }
}

internal fun isGooglePlacePhotoName(name: String): Boolean =
    Regex("places/[A-Za-z0-9_-]+/photos/[A-Za-z0-9_=-]+").matches(name)
