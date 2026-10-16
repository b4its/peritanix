package id.peritanix.dape.android.core.hardware

import kotlin.math.*

/**
 * Tahap 1.1 High-Precision GPS Polygon Tracker for Micro-Farming Land Boundary.
 *
 * Tracks GPS coordinates while a smallholder farmer walks around their field perimeter.
 * Provides:
 *   1. Strict accuracy filtering (accuracy <= 10.0m).
 *   2. Real-time perimeter calculation via Haversine distance summation.
 *   3. Smallholder planar projection & Shoelace area computation (in m² and Hectares).
 *   4. Boundary closure validation (distance from last to first point <= 15m).
 *   5. PostGIS-compatible WKT export (`POLYGON((lon lat, ...))`).
 */
class GpsPolygonTracker {

    companion object {
        const val MAX_ACCURACY_METERS = 10.0f
        const val EARTH_RADIUS_METERS = 6371000.0
        const val MIN_POINTS_FOR_POLYGON = 3
    }

    data class GpsPoint(
        val latitude: Double,
        val longitude: Double,
        val altitudeM: Double,
        val accuracyM: Float,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val points = mutableListOf<GpsPoint>()

    /** Clears all recorded points. */
    fun clear() {
        points.clear()
    }

    /** Returns an immutable copy of recorded points. */
    fun getPoints(): List<GpsPoint> = points.toList()

    /**
     * Adds a GPS point if accuracy satisfies the precision threshold.
     * @return true if accepted, false if discarded due to high error.
     */
    fun addPoint(latitude: Double, longitude: Double, altitudeM: Double = 0.0, accuracyM: Float = 5.0f): Boolean {
        if (accuracyM > MAX_ACCURACY_METERS) {
            return false
        }
        points.add(GpsPoint(latitude, longitude, altitudeM, accuracyM))
        return true
    }

    /**
     * Computes distance between two coordinates in meters using Haversine formula.
     */
    fun haversineDistanceMeters(p1: GpsPoint, p2: GpsPoint): Double {
        val lat1Rad = Math.toRadians(p1.latitude)
        val lat2Rad = Math.toRadians(p2.latitude)
        val deltaLat = Math.toRadians(p2.latitude - p1.latitude)
        val deltaLon = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(deltaLat / 2.0).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2.0).pow(2)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Calculates the total perimeter length in meters along recorded points.
     */
    fun calculatePerimeterMeters(): Double {
        if (points.size < 2) return 0.0
        var totalDist = 0.0
        for (i in 0 until points.size - 1) {
            totalDist += haversineDistanceMeters(points[i], points[i + 1])
        }
        // Include closing edge if points >= 3
        if (points.size >= MIN_POINTS_FOR_POLYGON) {
            totalDist += haversineDistanceMeters(points.last(), points.first())
        }
        return Math.round(totalDist * 100.0) / 100.0
    }

    /**
     * Calculates the enclosed surface area in square meters using Shoelace formula
     * projected onto a local equirectangular plane (ideal for 0.1 - 2.0 ha plots).
     */
    fun calculateAreaSquareMeters(): Double {
        if (points.size < MIN_POINTS_FOR_POLYGON) return 0.0

        val refLat = Math.toRadians(points.map { it.latitude }.average())
        val cosRef = cos(refLat)

        // Convert (lat, lon) to local planar coordinates (x, y) in meters
        val planarCoords = points.map { pt ->
            val x = Math.toRadians(pt.longitude) * EARTH_RADIUS_METERS * cosRef
            val y = Math.toRadians(pt.latitude) * EARTH_RADIUS_METERS
            Pair(x, y)
        }

        var shoelaceSum = 0.0
        val n = planarCoords.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            shoelaceSum += (planarCoords[i].first * planarCoords[j].second) -
                    (planarCoords[j].first * planarCoords[i].second)
        }

        val area = abs(shoelaceSum) / 2.0
        return Math.round(area * 100.0) / 100.0
    }

    /**
     * Calculates area in Hectares (1 ha = 10,000 m²).
     */
    fun calculateAreaHectares(): Double {
        val m2 = calculateAreaSquareMeters()
        return Math.round((m2 / 10000.0) * 1000.0) / 1000.0
    }

    /**
     * Evaluates if the polygon area falls within smallholder target (0.1 to 2.0 Hectares).
     */
    fun isWithinSmallholderRange(): Boolean {
        val ha = calculateAreaHectares()
        return ha in 0.1..2.0
    }

    /**
     * Checks if the user has returned close to the starting point to form a closed loop.
     */
    fun isPolygonClosed(maxGapMeters: Double = 15.0): Boolean {
        if (points.size < MIN_POINTS_FOR_POLYGON) return false
        val gap = haversineDistanceMeters(points.last(), points.first())
        return gap <= maxGapMeters
    }

    /**
     * Exports polygon as PostGIS WKT `POLYGON((lon lat, ...))` SRID 4326.
     */
    fun toWktPolygon(): String {
        if (points.size < MIN_POINTS_FOR_POLYGON) return ""
        val coordsList = points.toMutableList()
        // Ensure closed ring (last point == first point)
        if (coordsList.last().latitude != coordsList.first().latitude ||
            coordsList.last().longitude != coordsList.first().longitude) {
            coordsList.add(coordsList.first())
        }

        val coordStrings = coordsList.joinToString(", ") { pt ->
            "${"%.7f".format(java.util.Locale.US, pt.longitude)} ${"%.7f".format(java.util.Locale.US, pt.latitude)}"
        }
        return "POLYGON(($coordStrings))"
    }

    /**
     * Exports polygon as GeoJSON geometry string.
     */
    fun toGeoJson(): String {
        if (points.size < MIN_POINTS_FOR_POLYGON) return "{}"
        val coordsList = points.toMutableList()
        if (coordsList.last().latitude != coordsList.first().latitude ||
            coordsList.last().longitude != coordsList.first().longitude) {
            coordsList.add(coordsList.first())
        }

        val jsonPairs = coordsList.joinToString(",") { pt ->
            "[${"%.7f".format(java.util.Locale.US, pt.longitude)},${"%.7f".format(java.util.Locale.US, pt.latitude)}]"
        }
        return "{\"type\":\"Polygon\",\"coordinates\":[[$jsonPairs]]}"
    }
}
