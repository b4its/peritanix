package id.peritanix.dape.android

import id.peritanix.dape.android.core.hardware.IndonesianTtsVoiceAdvisor
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class AndroidIndonesianTtsVoiceAdvisorTest {

    @Test
    fun testIndonesianLocaleConfiguration() {
        val locale = IndonesianTtsVoiceAdvisor.getIndonesianLocale()
        assertEquals("id", locale.language)
        assertEquals("ID", locale.country)
    }

    @Test
    fun testComposePreparationScriptLowland() {
        val script = IndonesianTtsVoiceAdvisor.composePreparationScript(
            elevationM = 50.0,
            boilingPointC = 99.8,
            boilingParts = 5.2,
            wellParts = 9.0
        )
        assertTrue(script.contains("50 meter"))
        assertTrue(script.contains("99.8 derajat"))
        assertTrue(script.contains("5.2 gayung air mendidih"))
        assertTrue(script.contains("9 gayung air sumur"))
        assertTrue(script.contains("52 derajat Celsius"))
    }

    @Test
    fun testComposePreparationScriptHighland() {
        val script = IndonesianTtsVoiceAdvisor.composePreparationScript(
            elevationM = 1450.0,
            boilingPointC = 95.2,
            boilingParts = 6.0,
            wellParts = 9.0
        )
        assertTrue(script.contains("1450 meter"))
        assertTrue(script.contains("95.2 derajat"))
        assertTrue(script.contains("6 gayung air mendidih"))
        assertTrue(script.contains("9 gayung air sumur"))
    }

    @Test
    fun testImmersionCountdownCheckpoints() {
        val cues = IndonesianTtsVoiceAdvisor.getImmersionCheckpoints()
        assertEquals(4, cues.size)

        val cue180 = IndonesianTtsVoiceAdvisor.evaluateCueForSecond(180)
        assertNotNull(cue180)
        assertEquals("INFO", cue180?.alertLevel)
        assertTrue(cue180?.textPrompt?.contains("Perendaman dimulai") == true)

        val cue90 = IndonesianTtsVoiceAdvisor.evaluateCueForSecond(90)
        assertNotNull(cue90)
        assertEquals("INFO", cue90?.alertLevel)
        assertTrue(cue90?.textPrompt?.contains("Satu setengah menit tersisa") == true)

        val cue30 = IndonesianTtsVoiceAdvisor.evaluateCueForSecond(30)
        assertNotNull(cue30)
        assertEquals("WARNING", cue30?.alertLevel)
        assertTrue(cue30?.textPrompt?.contains("Tiga puluh detik terakhir") == true)

        val cue0 = IndonesianTtsVoiceAdvisor.evaluateCueForSecond(0)
        assertNotNull(cue0)
        assertEquals("SUCCESS", cue0?.alertLevel)
        assertTrue(cue0?.textPrompt?.contains("selesai") == true)

        // Non-checkpoint second returns null
        assertNull(IndonesianTtsVoiceAdvisor.evaluateCueForSecond(125))
        assertNull(IndonesianTtsVoiceAdvisor.evaluateCueForSecond(45))
    }
}
