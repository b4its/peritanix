package id.peritanix.dape.android

import id.peritanix.dape.android.ui.components.NotificationItem
import id.peritanix.dape.android.ui.components.NotificationType
import id.peritanix.dape.android.ui.components.PentasixNotificationHub
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PentasixNotificationHubTest {

    @Before
    fun setUp() {
        PentasixNotificationHub.clearHistory()
    }

    @Test
    fun testNotifyUpdatesCurrentNotificationAndHistory() {
        assertNull(PentasixNotificationHub.currentNotification.value)
        assertEquals(0, PentasixNotificationHub.history.size)

        val item = PentasixNotificationHub.notify(
            context = null,
            title = "Batas Lahan Disimpan",
            message = "Poligon 0.850 Ha tersimpan ke Room SQLite.",
            type = NotificationType.SUCCESS,
            postSystemNotification = false,
            haptic = false
        )

        assertEquals("Batas Lahan Disimpan", item.title)
        assertEquals("Poligon 0.850 Ha tersimpan ke Room SQLite.", item.message)
        assertEquals(NotificationType.SUCCESS, item.type)
        assertTrue(item.timestamp > 0L)

        // Verify state
        val current = PentasixNotificationHub.currentNotification.value
        assertNotNull(current)
        assertEquals(item.id, current.id)
        assertEquals("Batas Lahan Disimpan", current.title)

        // Verify history
        assertEquals(1, PentasixNotificationHub.history.size)
        assertEquals(item.id, PentasixNotificationHub.history[0].id)
    }

    @Test
    fun testDismissCurrentNotification() {
        PentasixNotificationHub.notify(
            context = null,
            title = "Peringatan",
            message = "Kelembaban tinggi.",
            type = NotificationType.WARNING,
            postSystemNotification = false,
            haptic = false
        )

        assertNotNull(PentasixNotificationHub.currentNotification.value)
        assertEquals(1, PentasixNotificationHub.history.size)

        PentasixNotificationHub.dismissCurrent()

        assertNull(PentasixNotificationHub.currentNotification.value)
        assertEquals(1, PentasixNotificationHub.history.size, "History should remain intact after dismissing banner")
    }

    @Test
    fun testClearHistory() {
        PentasixNotificationHub.notify(context = null, title = "A1", message = "M1", type = NotificationType.INFO, postSystemNotification = false, haptic = false)
        PentasixNotificationHub.notify(context = null, title = "A2", message = "M2", type = NotificationType.RAIN, postSystemNotification = false, haptic = false)

        assertEquals(2, PentasixNotificationHub.history.size)
        assertNotNull(PentasixNotificationHub.currentNotification.value)

        PentasixNotificationHub.clearHistory()

        assertEquals(0, PentasixNotificationHub.history.size)
        assertNull(PentasixNotificationHub.currentNotification.value)
    }

    @Test
    fun testAllNotificationTypesHaveValidIconsAndColors() {
        for (type in NotificationType.values()) {
            assertTrue(type.label.isNotEmpty(), "Label for $type must not be empty")
            assertNotNull(type.color, "Color for $type must be defined")
            val icon = type.icon()
            assertNotNull(icon, "Icon for $type must be valid")
            assertTrue(icon.name.isNotEmpty(), "Icon name for $type should be defined")
        }
    }

    @Test
    fun testHistoryCappedAtFiftyItems() {
        for (i in 1..65) {
            PentasixNotificationHub.notify(
                context = null,
                title = "Notif #$i",
                message = "Pesan aksi ke-$i",
                type = NotificationType.INFO,
                postSystemNotification = false,
                haptic = false
            )
        }

        assertEquals(50, PentasixNotificationHub.history.size, "History must be capped at 50 items")
        assertEquals("Notif #65", PentasixNotificationHub.history[0].title, "Most recent notification must be at index 0")
        assertEquals("Notif #65", PentasixNotificationHub.currentNotification.value?.title)
    }

    @Test
    fun testSuccessiveNotificationReplacesCurrent() {
        val first = PentasixNotificationHub.notify(
            context = null,
            title = "Aksi 1",
            message = "Mulai perendaman",
            type = NotificationType.INFO,
            postSystemNotification = false,
            haptic = false
        )
        val second = PentasixNotificationHub.notify(
            context = null,
            title = "Aksi 2",
            message = "Sertifikat terbit",
            type = NotificationType.SUCCESS,
            postSystemNotification = false,
            haptic = false
        )

        assertEquals(second.id, PentasixNotificationHub.currentNotification.value?.id)
        assertEquals("Aksi 2", PentasixNotificationHub.currentNotification.value?.title)
        assertEquals(2, PentasixNotificationHub.history.size)
        assertEquals("Aksi 2", PentasixNotificationHub.history[0].title)
        assertEquals("Aksi 1", PentasixNotificationHub.history[1].title)
    }

    @Test
    fun testRainNotificationSpecifics() {
        val item = PentasixNotificationHub.notify(
            context = null,
            title = "Hujan Ditandai Berakhir",
            message = "Durasi 42 menit disimpan.",
            type = NotificationType.RAIN,
            postSystemNotification = false,
            haptic = false
        )

        assertEquals(NotificationType.RAIN, item.type)
        assertEquals("Cuaca Hujan", item.type.label)
    }

    @Test
    fun testCalibrationNotificationSpecifics() {
        val item = PentasixNotificationHub.notify(
            context = null,
            title = "Kalibrasi Zero-Tilt Disimpan",
            message = "Acuan bidang datar 0.0°.",
            type = NotificationType.CALIBRATION,
            postSystemNotification = false,
            haptic = false
        )

        assertEquals(NotificationType.CALIBRATION, item.type)
        assertEquals("Sensor & Arah", item.type.label)
    }
}
