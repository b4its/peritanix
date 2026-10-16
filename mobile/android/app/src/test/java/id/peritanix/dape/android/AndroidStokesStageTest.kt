package id.peritanix.dape.android

import id.peritanix.dape.android.core.hardware.WaterLevelOverlayController
import id.peritanix.dape.android.core.physics.StokesStageManager
import org.junit.Assert.*
import org.junit.Test

class AndroidStokesStageTest {

    @Test
    fun testStokesSettlingVelocityEquation() {
        // Sand particle radius 0.0001m (100 microns = 0.1mm)
        // v = (2/9) * ((2650 - 1000) * 9.80665 / 0.001) * (0.0001)^2
        val velocity = StokesStageManager.calculateSettlingVelocity(radiusM = 0.0001)
        assertTrue("Kecepatan pengendapan pasir harus positif", velocity > 0)
        // ~0.0359 m/s (sinks 3.6 cm per second)
        assertEquals(0.0359, velocity, 0.005)
    }

    @Test
    fun testWaterLevelShutterLockWhenTilted() {
        // Tilted phone: gravityZ = 3.5 m/s^2, gravityY = 9.0 m/s^2 -> pitch ~21.2° > 5.0°
        val tilt = WaterLevelOverlayController.evaluateTilt(0.0f, 9.0f, 3.5f)
        assertFalse("Shutter harus terkunci jika HP miring", tilt.isUpright)
        assertTrue(tilt.feedbackText.contains("HP Terlalu Menunduk"))

        // Upright phone: gravityZ = 0.2 m/s^2, gravityY = 9.8 m/s^2 -> pitch ~1.17° <= 5.0°
        val upright = WaterLevelOverlayController.evaluateTilt(0.1f, 9.8f, 0.2f)
        assertTrue("Shutter harus terbuka saat posisi 90° tegak lurus", upright.isUpright)
        assertTrue(upright.feedbackText.contains("POSISI TEGAK 90° IDEAL"))
    }

    @Test
    fun testStokesStageShutterGuidance() {
        // Upright but time t=25s < 40s
        val (unlockedEarly, msgEarly) = StokesStageManager.getShutterStatus(
            isPerpendicular = true,
            stage = StokesStageManager.Stage.SAND_SETTLING,
            secondsElapsed = 25
        )
        assertFalse("Shutter terkunci sebelum waktu 40s tercapai", unlockedEarly)
        assertTrue(msgEarly.contains("15s tersisa"))

        // Upright and time t=40s reached
        val (unlockedReady, msgReady) = StokesStageManager.getShutterStatus(
            isPerpendicular = true,
            stage = StokesStageManager.Stage.SAND_SETTLING,
            secondsElapsed = 40
        )
        assertTrue("Shutter terbuka saat waktu 40s tercapai", unlockedReady)
        assertTrue(msgReady.contains("Shutter TERBUKA"))
    }
}
