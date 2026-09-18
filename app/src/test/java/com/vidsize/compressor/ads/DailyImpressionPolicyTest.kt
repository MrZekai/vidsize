package com.vidsize.compressor.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The daily allowance, pinned at its boundary.
 *
 * The number itself comes from the AdMob panel - three impressions per user per
 * day - and the app mirrors it. What these tests protect is the edge: off by one
 * in the permissive direction serves a fourth ad the panel will refuse anyway
 * (a wasted request and a confused tester); off by one in the strict direction
 * silently gives away a third of the format's revenue.
 */
class DailyImpressionPolicyTest {

    @Test
    fun theCapMatchesTheAdMobPanel() {
        assertEquals(
            "The panel allows 3 interstitials per user per day; the app must agree",
            3,
            DailyImpressionPolicy.MAX_PER_DAY,
        )
    }

    @Test
    fun theFirstThreeAreAllowedAndTheFourthIsNot() {
        assertTrue(DailyImpressionPolicy.canShow(shownToday = 0))
        assertTrue(DailyImpressionPolicy.canShow(shownToday = 1))
        assertTrue(DailyImpressionPolicy.canShow(shownToday = 2))
        assertFalse(
            "The fourth impression of the day must be refused",
            DailyImpressionPolicy.canShow(shownToday = 3),
        )
    }

    /**
     * Past the cap stays past the cap.
     *
     * The stored counter can exceed the limit if the cap is ever lowered while a
     * user is mid-day, so "greater than" has to behave like "equal to".
     */
    @Test
    fun aCountAboveTheCapStaysBlocked() {
        assertFalse(DailyImpressionPolicy.canShow(shownToday = 4))
        assertFalse(DailyImpressionPolicy.canShow(shownToday = 99))
    }

    /**
     * A nonsensical stored value must not switch ads off.
     *
     * The count comes from SharedPreferences, which can be absent on a first run
     * or after the user clears app data. Refusing to serve because an integer
     * looked odd is the wrong failure direction for a limit whose only job is to
     * stop the fourth ad.
     */
    @Test
    fun aNegativeCountIsTreatedAsNoneShown() {
        assertTrue(DailyImpressionPolicy.canShow(shownToday = -1))
        assertTrue(DailyImpressionPolicy.canShow(shownToday = Int.MIN_VALUE))
        assertEquals(3, DailyImpressionPolicy.remainingToday(shownToday = -5))
    }

    @Test
    fun remainingCountsDownAndStopsAtZero() {
        assertEquals(3, DailyImpressionPolicy.remainingToday(shownToday = 0))
        assertEquals(2, DailyImpressionPolicy.remainingToday(shownToday = 1))
        assertEquals(1, DailyImpressionPolicy.remainingToday(shownToday = 2))
        assertEquals(0, DailyImpressionPolicy.remainingToday(shownToday = 3))
        assertEquals(
            "Remaining is a count, never a debt",
            0,
            DailyImpressionPolicy.remainingToday(shownToday = 10),
        )
    }

    /**
     * canShow and remainingToday must never disagree.
     *
     * They are read by different callers - the gate asks the first, the
     * diagnostics screen shows the second - and a screen reporting "1 left"
     * beside a format that refuses to show one would send a tester hunting a
     * bug that is not there.
     */
    @Test
    fun theTwoAnswersAgreeAcrossTheWholeRange() {
        for (shown in -2..8) {
            assertEquals(
                "shownToday=$shown",
                DailyImpressionPolicy.canShow(shown),
                DailyImpressionPolicy.remainingToday(shown) > 0,
            )
        }
    }

    /** The cap is a parameter so a future panel change needs one edit, not two. */
    @Test
    fun anExplicitCapOverridesTheDefault() {
        assertTrue(DailyImpressionPolicy.canShow(shownToday = 3, maxPerDay = 5))
        assertFalse(DailyImpressionPolicy.canShow(shownToday = 5, maxPerDay = 5))
        assertFalse(
            "A cap of zero means the format is off, not unlimited",
            DailyImpressionPolicy.canShow(shownToday = 0, maxPerDay = 0),
        )
    }
}
