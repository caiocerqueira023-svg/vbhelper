package com.github.nacabaro.vbhelper.world

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

/**
 * Resolves a coarse world biome from nearby public OpenStreetMap features.
 *
 * Results are kept only in memory, by ~220 m cells, for 15 minutes. This keeps
 * the World screen from repeatedly sending the same GPS position to Overpass
 * while it refreshes nearby spawns. Failures intentionally degrade to Null so
 * map-data availability never blocks normal spawning or biases spawns.
 */
internal class OpenStreetMapBiomeDetector {
    private data class Cell(val latitude: Int, val longitude: Int)
    private data class CachedBiome(val biome: WorldBiome, val expiresAt: Long)

    private val cache = ConcurrentHashMap<Cell, CachedBiome>()

    fun biomeAt(latitude: Double, longitude: Double): WorldBiome {
        val now = System.currentTimeMillis()
        val cell = Cell(
            latitude = floor(latitude * CELL_SCALE).toInt(),
            longitude = floor(longitude * CELL_SCALE).toInt()
        )
        cache[cell]?.takeIf { it.expiresAt > now }?.let { return it.biome }

        val query = runCatching { queryBiome(latitude, longitude) }
        val biome = query.getOrDefault(WorldBiome.NULL)
        // A transient network failure should not make this location stay Null for
        // the full successful-query cache window.
        val cacheDuration = if (query.isSuccess) {
            CACHE_DURATION_MILLIS
        } else {
            FAILURE_CACHE_DURATION_MILLIS
        }
        cache[cell] = CachedBiome(biome, now + cacheDuration)
        return biome
    }

    private fun queryBiome(latitude: Double, longitude: Double): WorldBiome {
        val query = """
            [out:json][timeout:8];
            (
              nwr(around:$SEARCH_RADIUS_METERS,$latitude,$longitude)["natural"="water"];
              nwr(around:$SEARCH_RADIUS_METERS,$latitude,$longitude)["water"];
              nwr(around:$SEARCH_RADIUS_METERS,$latitude,$longitude)["waterway"];
              nwr(around:$SEARCH_RADIUS_METERS,$latitude,$longitude)["leisure"="park"];
              nwr(around:$SEARCH_RADIUS_METERS,$latitude,$longitude)["landuse"="recreation_ground"];
              nwr(around:$SEARCH_RADIUS_METERS,$latitude,$longitude)["boundary"="national_park"];
              nwr(around:$SEARCH_RADIUS_METERS,$latitude,$longitude)["natural"="grassland"];
              nwr(around:$SEARCH_RADIUS_METERS,$latitude,$longitude)["landuse"="meadow"];
              nwr(around:$SEARCH_RADIUS_METERS,$latitude,$longitude)["landuse"="grass"];
              nwr(around:$SEARCH_RADIUS_METERS,$latitude,$longitude)["landuse"="farmland"];
              nwr(around:$SEARCH_RADIUS_METERS,$latitude,$longitude)["landuse"="orchard"];
              nwr(around:$SEARCH_RADIUS_METERS,$latitude,$longitude)["landuse"="vineyard"];
              nwr(around:$SEARCH_RADIUS_METERS,$latitude,$longitude)["landuse"="farmyard"];
              nwr(around:$SEARCH_RADIUS_METERS,$latitude,$longitude)["landuse"="industrial"];
              nwr(around:$SEARCH_RADIUS_METERS,$latitude,$longitude)["man_made"="works"];
              nwr(around:$SEARCH_RADIUS_METERS,$latitude,$longitude)["leisure"~"^(amusement_arcade|theme_park|stadium|sports_centre|zoo)$"];
              nwr(around:$SEARCH_RADIUS_METERS,$latitude,$longitude)["amenity"="cinema"];
              nwr(around:$SEARCH_RADIUS_METERS,$latitude,$longitude)["tourism"="attraction"];
              nwr(around:$SEARCH_RADIUS_METERS,$latitude,$longitude)["landuse"~"^(residential|commercial|retail)$"];
            );
            out tags;
        """.trimIndent()
        val biome = biomeFromResponse(executeQuery(query))
        if (biome != WorldBiome.NULL) return biome

        // Residential land-use polygons are not consistently mapped in every
        // neighbourhood, while building footprints are. Use one small,
        // single-result fallback so homes still classify as Urban without
        // treating unmapped countryside as urban.
        val buildingQuery = """
            [out:json][timeout:8];
            way(around:$SEARCH_RADIUS_METERS,$latitude,$longitude)["building"];
            out tags 1;
        """.trimIndent()
        val buildings = executeQuery(buildingQuery).optJSONArray("elements")
        return if (buildings?.length() ?: 0 > 0) WorldBiome.URBAN else WorldBiome.NULL
    }

    private fun executeQuery(query: String): JSONObject {
        val body = "data=" + URLEncoder.encode(query, Charsets.UTF_8.name())
        val connection = (URL(OVERPASS_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = NETWORK_TIMEOUT_MILLIS
            readTimeout = NETWORK_TIMEOUT_MILLIS
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty(
                "User-Agent",
                "VBHelper/1.0 (+https://github.com/nacabaro/vbhelper)"
            )
        }

        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            check(connection.responseCode in 200..299) { "Overpass returned ${connection.responseCode}" }
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    private fun biomeFromResponse(response: JSONObject): WorldBiome {
        val elements = response.optJSONArray("elements") ?: return WorldBiome.NULL
        var foundPark = false
        var foundGrassland = false
        var foundRural = false
        var foundIndustrial = false
        var foundEntertainment = false
        var foundUrban = false

        for (index in 0 until elements.length()) {
            val tags = elements.optJSONObject(index)?.optJSONObject("tags") ?: continue
            if (
                tags.optString("natural") == "water" ||
                tags.has("water") ||
                tags.has("waterway")
            ) {
                // Water takes precedence when a park contains a lake or river.
                return WorldBiome.WATER
            }
            if (
                tags.optString("leisure") == "park" ||
                tags.optString("landuse") == "recreation_ground" ||
                tags.optString("boundary") == "national_park"
            ) {
                foundPark = true
            }
            if (
                tags.optString("natural") == "grassland" ||
                tags.optString("landuse") in GRASSLAND_LAND_USES
            ) {
                foundGrassland = true
            }
            if (tags.optString("landuse") in RURAL_LAND_USES) {
                foundRural = true
            }
            if (
                tags.optString("landuse") == "industrial" ||
                tags.optString("man_made") == "works"
            ) {
                foundIndustrial = true
            }
            if (
                tags.optString("leisure") in ENTERTAINMENT_LEISURE ||
                tags.optString("amenity") == "cinema" ||
                tags.optString("tourism") == "attraction"
            ) {
                foundEntertainment = true
            }
            if (tags.optString("landuse") in URBAN_LAND_USES) {
                foundUrban = true
            }
        }
        return when {
            foundIndustrial -> WorldBiome.INDUSTRIAL
            foundEntertainment -> WorldBiome.ENTERTAINMENT
            foundPark -> WorldBiome.PARK
            foundGrassland -> WorldBiome.GRASSLAND
            foundRural -> WorldBiome.RURAL
            foundUrban -> WorldBiome.URBAN
            else -> WorldBiome.NULL
        }
    }

    private companion object {
        const val OVERPASS_ENDPOINT = "https://overpass-api.de/api/interpreter"
        const val SEARCH_RADIUS_METERS = 250
        const val CELL_SCALE = 500.0
        const val CACHE_DURATION_MILLIS = 15 * 60 * 1000L
        const val FAILURE_CACHE_DURATION_MILLIS = 30 * 1000L
        // Biome data is enrichment, not a prerequisite for showing the World map.
        // Keep the first GPS refresh responsive when Overpass is slow or unavailable.
        const val NETWORK_TIMEOUT_MILLIS = 1_500
        val GRASSLAND_LAND_USES = setOf("meadow", "grass")
        val RURAL_LAND_USES = setOf("farmland", "orchard", "vineyard", "farmyard")
        val URBAN_LAND_USES = setOf("residential", "commercial", "retail")
        val ENTERTAINMENT_LEISURE = setOf(
            "amusement_arcade", "theme_park", "stadium", "sports_centre", "zoo"
        )
    }
}
