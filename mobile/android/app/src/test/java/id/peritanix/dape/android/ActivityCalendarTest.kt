package id.peritanix.dape.android

import id.peritanix.dape.android.core.calendar.ActivityCalendar
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Unit tests for the 30-year activity calendar pure logic:
 * range bounds, epoch-day round-trip, month grids, labels, clamping.
 */
class ActivityCalendarTest {

    private val today = LocalDate.of(2026, 10, 17)

    @Test
    fun testRangeIsThirtyYearsAhead() {
        val start = ActivityCalendar.rangeStart(today)
        val end = ActivityCalendar.rangeEnd(today)
        assertEquals(2026, start.year)
        assertEquals(1, start.monthValue)
        assertEquals(2056, end.year)              // today + 30
        assertEquals(12, end.monthValue)
        assertEquals(31, end.dayOfMonth)
    }

    @Test
    fun testInRange() {
        assertTrue(ActivityCalendar.inRange(today, today))
        assertTrue(ActivityCalendar.inRange(LocalDate.of(2056, 12, 31), today))
        assertFalse(ActivityCalendar.inRange(LocalDate.of(2057, 1, 1), today))  // beyond +30y
        assertFalse(ActivityCalendar.inRange(LocalDate.of(2025, 12, 31), today)) // before this year
    }

    @Test
    fun testEpochDayRoundTrip() {
        val d = LocalDate.of(2030, 3, 15)
        val e = ActivityCalendar.toEpochDay(d)
        assertEquals(d, ActivityCalendar.fromEpochDay(e))
        // stable across calls
        assertEquals(e, ActivityCalendar.toEpochDay(d))
    }

    @Test
    fun testYearsListHasThirtyOneEntries() {
        val years = ActivityCalendar.years(today)
        assertEquals(31, years.size)            // 2026..2056 inclusive
        assertEquals(2026, years.first())
        assertEquals(2056, years.last())
    }

    @Test
    fun testMonthGridStructure() {
        val grid = ActivityCalendar.monthGrid(2026, 10) // Oct 2026
        assertEquals(0, grid.size % 7)                  // whole weeks
        // Oct 1 2026 is a Thursday → 3 leading blanks (Mon=0,Tue,Wed)
        assertEquals(null, grid[0])
        assertEquals(null, grid[1])
        assertEquals(null, grid[2])
        assertEquals(LocalDate.of(2026, 10, 1), grid[3])
        // 31 days present
        assertEquals(31, grid.count { it != null })
    }

    @Test
    fun testJune2026StartsOnMonday() {
        // Jun 1 2026 is a Monday → no leading blanks.
        val grid = ActivityCalendar.monthGrid(2026, 6)
        assertEquals(LocalDate.of(2026, 6, 1), grid[0])
        assertEquals(30, grid.count { it != null })
    }

    @Test
    fun testLeapYearFebruary() {
        assertEquals(29, ActivityCalendar.daysInMonth(2028, 2)) // 2028 leap
        assertEquals(28, ActivityCalendar.daysInMonth(2026, 2))
        assertEquals(31, ActivityCalendar.daysInMonth(2026, 12))
    }

    @Test
    fun testDayNames() {
        assertEquals("Sabtu", ActivityCalendar.dayName(LocalDate.of(2026, 10, 17)))
        assertEquals("Senin", ActivityCalendar.dayName(LocalDate.of(2026, 6, 1)))
    }

    @Test
    fun testMonthName() {
        assertEquals("Oktober", ActivityCalendar.monthName(10))
        assertEquals("Januari", ActivityCalendar.monthName(1))
        assertEquals("Desember", ActivityCalendar.monthName(12))
    }

    @Test
    fun testFullLabel() {
        assertEquals("Sabtu, 17 Oktober 2026", ActivityCalendar.fullLabel(LocalDate.of(2026, 10, 17)))
    }

    @Test
    fun testClampYearMonth() {
        // below current year → clamped to current
        assertEquals(YearMonth.of(2026, 5), ActivityCalendar.clampYearMonth(2020, 5, today))
        // above +30y → clamped to 2056
        assertEquals(YearMonth.of(2056, 5), ActivityCalendar.clampYearMonth(2099, 5, today))
        // in range passes through
        assertEquals(YearMonth.of(2030, 7), ActivityCalendar.clampYearMonth(2030, 7, today))
        // invalid month clamped 1..12
        assertEquals(YearMonth.of(2030, 12), ActivityCalendar.clampYearMonth(2030, 15, today))
    }

    @Test
    fun testDaysBetween() {
        assertEquals(1L, ActivityCalendar.daysBetween(today, today.plusDays(1)))
        assertEquals(-1L, ActivityCalendar.daysBetween(today, today.minusDays(1)))
        assertEquals(0L, ActivityCalendar.daysBetween(today, today))
    }
}
