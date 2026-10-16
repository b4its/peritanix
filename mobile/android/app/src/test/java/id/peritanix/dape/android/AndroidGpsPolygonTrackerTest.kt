package id.peritanix.dape.android

import id.peritanix.dape.android.core.hardware.GpsPolygonTracker
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AndroidGpsPolygonTrackerTest {

    private lateinit var tracker: GpsPolygonTracker

    @Before
    fun setUp() {
        tracker = GpsPolygonTracker()
    }

    @Test
    fun testAccuracyFilterRejectsInaccuratePoints() {
        val accepted = tracker.addPoint(-7.123, 110.456, 850.0, 5.0f)
        assertTrue("Titik akurasi 5m harus diterima", accepted)

        val rejected = tracker.addPoint(-7.124, 110.457, 851.0, 18.0f)
        assertFalse("Titik akurasi 18m harus ditolak", rejected)

        assertEquals("Hanya 1 titik yang tersimpan", 1, tracker.getPoints().size)
    }

    @Test
    fun testPerimeterAndAreaCalculationForMicroFarm() {
        // Create a rectangular chili plot of approximately 50m x 50m = 2500 m² = 0.25 Ha
        val baseLat = -7.000000
        val baseLon = 110.000000

        // ~50m delta in latitude is ~0.00045 degrees
        // ~50m delta in longitude at equator is ~0.00045 degrees
        val dLat = 0.00045
        val dLon = 0.00045

        tracker.addPoint(baseLat, baseLon, 800.0, 3.0f)
        tracker.addPoint(baseLat + dLat, baseLon, 800.0, 3.0f)
        tracker.addPoint(baseLat + dLat, baseLon + dLon, 800.0, 3.0f)
        tracker.addPoint(baseLat, baseLon + dLon, 800.0, 3.0f)

        val perimeter = tracker.calculatePerimeterMeters()
        assertTrue("Keliling plot harus mendekati ~200m (ditemukan: $perimeter)", perimeter in 180.0..220.0)

        val areaM2 = tracker.calculateAreaSquareMeters()
        assertTrue("Luas lahan harus mendekati ~2500 m² (ditemukan: $areaM2)", areaM2 in 2000.0..3000.0)

        val areaHa = tracker.calculateAreaHectares()
        assertTrue("Luas lahan harus mendekati ~0.25 Ha (ditemukan: $areaHa)", areaHa in 0.20..0.30)
        assertTrue("Lahan harus masuk skala mikro 0.1 - 2.0 Ha", tracker.isWithinSmallholderRange())
    }

    @Test
    fun testPolygonClosureValidation() {
        tracker.addPoint(-7.1000, 110.1000, 500.0, 2.0f)
        tracker.addPoint(-7.1005, 110.1000, 500.0, 2.0f)
        tracker.addPoint(-7.1005, 110.1005, 500.0, 2.0f)

        // Currently open: last point is ~55m away from first point
        assertFalse("Poligon belum tertutup jika jarak > 15m", tracker.isPolygonClosed(maxGapMeters = 15.0))

        // Return close to origin
        tracker.addPoint(-7.10005, 110.10005, 500.0, 2.0f)
        assertTrue("Poligon harus terdeteksi tertutup saat kembali dekat titik awal", tracker.isPolygonClosed(maxGapMeters = 15.0))
    }

    @Test
    fun testWktAndGeoJsonExport() {
        tracker.addPoint(-7.1234567, 110.4567890, 850.0, 3.0f)
        tracker.addPoint(-7.1244567, 110.4567890, 851.0, 3.0f)
        tracker.addPoint(-7.1244567, 110.4577890, 852.0, 3.0f)

        val wkt = tracker.toWktPolygon()
        assertTrue("WKT harus diawali POLYGON((", wkt.startsWith("POLYGON(("))
        assertTrue("WKT harus diakhiri ))", wkt.endsWith("))"))
        assertTrue("WKT harus memuat koordinat longitude latitude", wkt.contains("110.4567890 -7.1234567"))

        val geoJson = tracker.toGeoJson()
        assertTrue("GeoJSON harus tipe Polygon", geoJson.contains("\"type\":\"Polygon\""))
        assertTrue("GeoJSON harus memuat array koordinat", geoJson.contains("\"coordinates\":[[["))
    }
}
