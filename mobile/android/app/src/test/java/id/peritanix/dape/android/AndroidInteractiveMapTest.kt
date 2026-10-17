package id.peritanix.dape.android

import id.peritanix.dape.android.core.hardware.GpsPolygonTracker
import id.peritanix.dape.android.core.physics.TwiLocalCompute
import id.peritanix.dape.android.ui.components.BasemapStyle
import id.peritanix.dape.android.ui.components.FarmMapMarker
import id.peritanix.dape.android.ui.components.GeoBounds
import id.peritanix.dape.android.ui.components.MapGeoProjection
import id.peritanix.dape.android.ui.components.SlippyTile
import id.peritanix.dape.android.ui.components.SlippyTileMath
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for the offline GIS farm map projection engine, coordinate transformations,
 * bounding box math, layer models, and spatial agronomic integration.
 */
class AndroidInteractiveMapTest {

    @Test
    fun testComputeBoundsEmptyFallback() {
        val bounds = MapGeoProjection.computeBounds(emptyList())
        assertTrue("Bounds spanLat should be positive", bounds.spanLat > 0)
        assertTrue("Bounds spanLon should be positive", bounds.spanLon > 0)
        assertEquals(-7.123456, bounds.centerLat, 0.001)
        assertEquals(110.456789, bounds.centerLon, 0.001)
    }

    @Test
    fun testComputeBoundsFromFarmPoints() {
        val points = listOf(
            Pair(-7.1230, 110.4560),
            Pair(-7.1240, 110.4560),
            Pair(-7.1240, 110.4570),
            Pair(-7.1230, 110.4570)
        )
        val bounds = MapGeoProjection.computeBounds(points, paddingMeters = 10.0)

        assertTrue(bounds.minLat < -7.1240)
        assertTrue(bounds.maxLat > -7.1230)
        assertTrue(bounds.minLon < 110.4560)
        assertTrue(bounds.maxLon > 110.4570)
        assertEquals(-7.1235, bounds.centerLat, 0.0001)
        assertEquals(110.4565, bounds.centerLon, 0.0001)
    }

    @Test
    fun testCalculateFitScale() {
        val bounds = GeoBounds(
            minLat = -7.1240,
            maxLat = -7.1230,
            minLon = 110.4560,
            maxLon = 110.4570
        )
        val scale = MapGeoProjection.calculateFitScale(
            bounds = bounds,
            screenWidthPx = 400f,
            screenHeightPx = 400f,
            paddingPx = 20f
        )
        assertTrue("Scale should be positive and reasonable for smallholder plot", scale in 0.5f..15.0f)
    }

    @Test
    fun testScreenProjectionCenterAndCardinalDirections() {
        val centerLat = -7.123456
        val centerLon = 110.456789
        val screenWidth = 400f
        val screenHeight = 600f
        val pixelsPerMeter = 2.5f

        val projection = MapGeoProjection(
            centerLat = centerLat,
            centerLon = centerLon,
            pixelsPerMeter = pixelsPerMeter,
            screenWidthPx = screenWidth,
            screenHeightPx = screenHeight
        )

        // Center point projects exactly to screen center
        val centerScreen = projection.toScreen(centerLat, centerLon)
        assertEquals(200f, centerScreen.x, 0.001f)
        assertEquals(300f, centerScreen.y, 0.001f)

        // North point (higher latitude) must have smaller Y (higher on screen)
        val northScreen = projection.toScreen(centerLat + 0.0005, centerLon)
        assertEquals(200f, northScreen.x, 0.001f)
        assertTrue("North must move UP on screen (smaller Y)", northScreen.y < 300f)

        // South point (lower latitude) must have larger Y (lower on screen)
        val southScreen = projection.toScreen(centerLat - 0.0005, centerLon)
        assertTrue("South must move DOWN on screen (larger Y)", southScreen.y > 300f)

        // East point (higher longitude) must move RIGHT (larger X)
        val eastScreen = projection.toScreen(centerLat, centerLon + 0.0005)
        assertTrue("East must move RIGHT on screen (larger X)", eastScreen.x > 200f)

        // West point (lower longitude) must move LEFT (smaller X)
        val westScreen = projection.toScreen(centerLat, centerLon - 0.0005)
        assertTrue("West must move LEFT on screen (smaller X)", westScreen.x < 200f)
    }

    @Test
    fun testPanOffsetShiftsProjection() {
        val centerLat = -7.123456
        val centerLon = 110.456789
        val projectionWithPan = MapGeoProjection(
            centerLat = centerLat,
            centerLon = centerLon,
            pixelsPerMeter = 3.0f,
            screenWidthPx = 400f,
            screenHeightPx = 400f,
            panOffsetX = 50f,
            panOffsetY = -30f
        )

        val pt = projectionWithPan.toScreen(centerLat, centerLon)
        assertEquals(250f, pt.x, 0.001f) // 200 + 50
        assertEquals(170f, pt.y, 0.001f) // 200 - 30
    }

    @Test
    fun testInverseGeoProjectionRoundtrip() {
        val centerLat = -7.123456
        val centerLon = 110.456789
        val projection = MapGeoProjection(
            centerLat = centerLat,
            centerLon = centerLon,
            pixelsPerMeter = 4.0f,
            screenWidthPx = 500f,
            screenHeightPx = 500f,
            panOffsetX = 15f,
            panOffsetY = -25f
        )

        val testCoordinates = listOf(
            Pair(-7.123000, 110.456500),
            Pair(-7.123800, 110.457100),
            Pair(-7.122900, 110.457400),
            Pair(centerLat, centerLon)
        )

        for ((origLat, origLon) in testCoordinates) {
            val screen = projection.toScreen(origLat, origLon)
            val (invLat, invLon) = projection.toGeo(screen.x, screen.y)

            assertEquals("Lat roundtrip must match", origLat, invLat, 1e-7)
            assertEquals("Lon roundtrip must match", origLon, invLon, 1e-7)
        }
    }

    @Test
    fun testMetersAndPixelsConversion() {
        val projection = MapGeoProjection(
            centerLat = -7.123456,
            centerLon = 110.456789,
            pixelsPerMeter = 5.0f,
            screenWidthPx = 400f,
            screenHeightPx = 400f
        )

        assertEquals(50.0, projection.metersFromPixels(250f), 0.001)
        assertEquals(100f, projection.pixelsFromMeters(20.0), 0.001f)
    }

    @Test
    fun testDiseaseMarkerDataContract() {
        val marker = FarmMapMarker(
            id = "test-1",
            lat = -7.123456,
            lon = 110.456789,
            title = "Antraknosa Akut",
            diseaseType = "ANTHRACNOSE",
            severityPct = 45.0,
            localTwi = 7.5,
            protocolCode = "P-CU-01",
            actionNote = "Semprot tembaga"
        )

        assertEquals("test-1", marker.id)
        assertEquals("ANTHRACNOSE", marker.diseaseType)
        assertEquals(45.0, marker.severityPct, 0.001)
        assertEquals(7.5, marker.localTwi, 0.001)
    }

    @Test
    fun testGpsPolygonIntegrationWithMapProjection() {
        val tracker = GpsPolygonTracker()
        val baseLat = -7.123456
        val baseLon = 110.456789

        tracker.addPoint(baseLat, baseLon, 850.0, 3.5f)
        tracker.addPoint(baseLat + 0.00045, baseLon, 852.0, 4.0f)
        tracker.addPoint(baseLat + 0.00045, baseLon + 0.00055, 851.0, 3.8f)
        tracker.addPoint(baseLat, baseLon + 0.00055, 849.0, 4.2f)

        val points = tracker.getPoints()
        assertEquals(4, points.size)

        val bounds = MapGeoProjection.computeBounds(points.map { Pair(it.latitude, it.longitude) })
        val projection = MapGeoProjection(
            centerLat = bounds.centerLat,
            centerLon = bounds.centerLon,
            pixelsPerMeter = 2.0f,
            screenWidthPx = 300f,
            screenHeightPx = 300f
        )

        val screenPts = points.map { projection.toScreen(it.latitude, it.longitude) }
        assertEquals(4, screenPts.size)

        // All 4 vertices should be distinct on screen
        for (i in 0 until 3) {
            val dist = (screenPts[i] - screenPts[i + 1]).getDistance()
            assertTrue("Distance between adjacent vertices on screen must be non-zero", dist > 10f)
        }
    }

    @Test
    fun testTwiGridOverlayIntegration() {
        val grid = TwiLocalCompute.computeLocalGrid(baseUpslope = 180.0, baseSlope = 3.5, rows = 3, cols = 4)
        assertEquals(3, grid.size)
        assertEquals(4, grid[0].size)

        val criticalCells = grid.flatten().filter { it.riskClass == "CRITICAL" || it.riskClass == "HIGH" }
        assertTrue("High upslope and gentle slope should produce elevated TWI risk cells", criticalCells.isNotEmpty())
    }

    @Test
    fun testSlippyTileCoordinatesEquatorAndMeridian() {
        val zoom = 16
        val n = 1 shl zoom
        val x = SlippyTileMath.lonToTileX(0.0, zoom)
        val y = SlippyTileMath.latToTileY(0.0, zoom)

        assertEquals("Equator & Prime Meridian at zoom 16 must map to center tile", n / 2, x)
        assertEquals("Equator & Prime Meridian at zoom 16 must map to center tile", n / 2, y)
    }

    @Test
    fun testSlippyTileCoordinatesChiliFarm() {
        val lat = -7.123456
        val lon = 110.456789
        val zoom = 17
        val n = 1 shl zoom

        val tileX = SlippyTileMath.lonToTileX(lon, zoom)
        val tileY = SlippyTileMath.latToTileY(lat, zoom)

        assertTrue("TileX must be within 0 until $n", tileX in 0 until n)
        assertTrue("TileY must be within 0 until $n", tileY in 0 until n)

        val bounds = SlippyTileMath.getTileBounds(tileX, tileY, zoom)
        assertTrue("Tile bounds must contain latitude", lat in bounds.minLat..bounds.maxLat)
        assertTrue("Tile bounds must contain longitude", lon in bounds.minLon..bounds.maxLon)
    }

    @Test
    fun testSlippyTileToLonLatRoundtrip() {
        val zoom = 16
        for (tx in listOf(1000, 32768, 52875)) {
            for (ty in listOf(1000, 32768, 34068)) {
                val bounds = SlippyTileMath.getTileBounds(tx, ty, zoom)
                val centerLat = (bounds.minLat + bounds.maxLat) / 2.0
                val centerLon = (bounds.minLon + bounds.maxLon) / 2.0

                val recoveredX = SlippyTileMath.lonToTileX(centerLon, zoom)
                val recoveredY = SlippyTileMath.latToTileY(centerLat, zoom)

                assertEquals(tx, recoveredX)
                assertEquals(ty, recoveredY)
            }
        }
    }

    @Test
    fun testCalculateOptimalZoomForFarmAndRegion() {
        // Micro-plot scale (~50-100m, span ~ 0.0008 deg)
        val microZoom = SlippyTileMath.calculateOptimalZoom(spanLat = 0.0008, spanLon = 0.0008)
        assertEquals("Micro plot must pick zoom 18 for high detail", 18, microZoom)

        // Cluster farm scale (~300m, span ~ 0.003 deg)
        val clusterZoom = SlippyTileMath.calculateOptimalZoom(spanLat = 0.003, spanLon = 0.003)
        assertEquals("Cluster farm must pick zoom 17", 17, clusterZoom)

        // Regional scale (~2km, span ~ 0.02 deg)
        val regionalZoom = SlippyTileMath.calculateOptimalZoom(spanLat = 0.02, spanLon = 0.02)
        assertEquals("Regional plot must pick zoom 15", 15, regionalZoom)
    }

    @Test
    fun testBasemapStylesConfiguration() {
        val styles = BasemapStyle.values()
        assertEquals(4, styles.size)

        for (s in styles) {
            assertTrue("Style ID must not be blank", s.id.isNotBlank())
            assertTrue("Display name must not be blank", s.displayName.isNotBlank())
            assertTrue("Icon must not be blank", s.icon.isNotBlank())
            assertTrue("URL template must contain {z}", s.urlTemplate.contains("{z}"))
            assertTrue("URL template must contain {x}", s.urlTemplate.contains("{x}"))
            assertTrue("URL template must contain {y}", s.urlTemplate.contains("{y}"))
        }
    }
}
