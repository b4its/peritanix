package id.peritanix.dape.android

import id.peritanix.dape.android.core.onboarding.OnboardingState
import id.peritanix.dape.android.core.onboarding.OnboardingStep
import id.peritanix.dape.android.core.onboarding.OnboardingSyncClient
import id.peritanix.dape.android.core.onboarding.WhiteReferenceReading
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the onboarding/calibration logic (master spec item 2.2):
 *  - OnboardingStep state machine
 *  - OnboardingState completion + step advancement
 *  - OnboardingSyncClient.decide (offline-first routing policy)
 */
class AndroidOnboardingTest {

    private fun wr(luxVerified: Boolean = true) = WhiteReferenceReading(
        rawR = 210, rawG = 200, rawB = 190,
        rScale = 1.214, gScale = 1.275, bScale = 1.342,
        ambientLux = 0.1f, luxVerified = luxVerified
    )

    // ---- state machine ------------------------------------------------------

    @Test
    fun testStepOrder() {
        assertEquals(
            listOf(
                OnboardingStep.WELCOME, OnboardingStep.SPECTRAL,
                OnboardingStep.KEYPAIR, OnboardingStep.DONE
            ),
            OnboardingStep.ordered
        )
        assertEquals(OnboardingStep.SPECTRAL, OnboardingStep.next(OnboardingStep.WELCOME))
        assertEquals(OnboardingStep.KEYPAIR, OnboardingStep.next(OnboardingStep.SPECTRAL))
        assertEquals(OnboardingStep.DONE, OnboardingStep.next(OnboardingStep.KEYPAIR))
        // next of DONE clamps to DONE
        assertEquals(OnboardingStep.DONE, OnboardingStep.next(OnboardingStep.DONE))
        assertEquals(OnboardingStep.WELCOME, OnboardingStep.previous(OnboardingStep.WELCOME))
    }

    @Test
    fun testInitiallyNotComplete() {
        val s = OnboardingState()
        assertFalse(s.isComplete)
        assertNotNull(s.blockingReason())
        assertTrue(s.blockingReason()!!.contains("spektral", ignoreCase = true))
    }

    @Test
    fun testSpectralOnlyNotComplete() {
        val s = OnboardingState().withSpectral(wr())
        assertFalse(s.isComplete)
        assertEquals(OnboardingStep.KEYPAIR, s.step)
        assertTrue(s.blockingReason()!!.contains("kunci", ignoreCase = true))
    }

    @Test
    fun testBothDoneComplete() {
        val s = OnboardingState().withSpectral(wr()).withKeyPair("BASE64PUBKEY==")
        assertTrue(s.isComplete)
        assertEquals(OnboardingStep.DONE, s.step)
        assertNull(s.blockingReason())
        assertEquals("BASE64PUBKEY==", s.publicKeyBase64)
    }

    @Test
    fun testUnverifiedSpectralDoesNotAdvance() {
        // Lux not verified → spectral not marked done → does not advance to KEYPAIR.
        val s = OnboardingState().withSpectral(wr(luxVerified = false))
        assertFalse(s.spectralDone)
        assertEquals(OnboardingStep.SPECTRAL, s.step)
    }

    @Test
    fun testWhiteReferenceArray() {
        val arr = wr().toWhiteReferenceArray()
        assertEquals(listOf(1.214, 1.275, 1.342), arr)
    }

    @Test
    fun testBlankKeyNotDone() {
        val s = OnboardingState().withSpectral(wr()).withKeyPair("   ")
        assertFalse(s.keyPairDone)
        assertTrue(s.blockingReason()!!.contains("kunci", ignoreCase = true))
    }

    // ---- sync client routing (offline-first policy) -------------------------

    @Test
    fun testDecideRouting() {
        val client = OnboardingSyncClient()
        // No token → queue (defer to delta sync), regardless of connectivity.
        assertEquals(OnboardingSyncClient.Action.QUEUE, client.decide(hasToken = false, isOnline = false))
        assertEquals(OnboardingSyncClient.Action.QUEUE, client.decide(hasToken = false, isOnline = true))
        // Token but offline → queue.
        assertEquals(OnboardingSyncClient.Action.QUEUE, client.decide(hasToken = true, isOnline = false))
        // Token + online → push now.
        assertEquals(OnboardingSyncClient.Action.PUSH_NOW, client.decide(hasToken = true, isOnline = true))
    }

    @Test
    fun testRegisterKeyWithoutTokenQueues() {
        val client = OnboardingSyncClient(apiBaseUrl = "http://127.0.0.1:1") // unreachable on purpose
        val res = client.registerKey(jwt = null, publicKeyBase64 = "ABC==", whiteReference = listOf(1.0, 1.0, 1.0))
        assertEquals(OnboardingSyncClient.Result.Queued, res)
    }
}
