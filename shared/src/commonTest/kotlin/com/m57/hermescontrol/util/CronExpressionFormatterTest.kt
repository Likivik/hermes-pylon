// Modified from Hy4ri/hermes-mobile for this fork; see NOTICE.

package com.m57.hermescontrol.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The exception paths used to need `mockkStatic(Log::class)` here: the production
 * code called `android.util.Log.w`, which throws "not mocked" on a plain JVM.
 * Routing through [HermesLog] removes the need - the default sink is a no-op, so
 * nothing has to be stubbed and the assertions below are unchanged.
 */
class CronExpressionFormatterTest {
    @Test
    fun testCronToHumanReadable() {
        assertEquals("At 09:00 and 21:00 every day", CronExpressionFormatter.cronToHumanReadable("0 9,21 * * *"))
        assertEquals("At 08:30 Monday through Friday", CronExpressionFormatter.cronToHumanReadable("30 8 * * 1-5"))
        assertEquals("Every day at midnight", CronExpressionFormatter.cronToHumanReadable("0 0 * * *"))
        assertEquals("Every 15 minutes", CronExpressionFormatter.cronToHumanReadable("*/15 * * * *"))
        assertEquals("Every Monday at 09:00", CronExpressionFormatter.cronToHumanReadable("0 9 * * 1"))
        assertEquals("Every Sunday at 06:00", CronExpressionFormatter.cronToHumanReadable("0 6 * * 0"))
        assertEquals("On the 1st of every month at 12:00", CronExpressionFormatter.cronToHumanReadable("0 12 1 * *"))
        assertEquals("On January 1st at midnight", CronExpressionFormatter.cronToHumanReadable("0 0 1 1 *"))
        assertEquals("Every 2 hours", CronExpressionFormatter.cronToHumanReadable("0 */2 * * *"))
    }

    @Test
    fun testMalformedCron() {
        assertEquals("invalid cron string", CronExpressionFormatter.cronToHumanReadable("invalid cron string"))
        assertEquals("0 9 * *", CronExpressionFormatter.cronToHumanReadable("0 9 * *"))
    }

    @Test
    fun testExceptionFallback() {
        // This will cause an IndexOutOfBoundsException because 15 is out of bounds for monthNames
        assertEquals("Raw: 0 12 1 15 *", CronExpressionFormatter.cronToHumanReadable("0 12 1 15 *"))

        // This will cause an IndexOutOfBoundsException because 8 is out of bounds for dowNames
        assertEquals("Raw: 0 9 * * 8", CronExpressionFormatter.cronToHumanReadable("0 9 * * 8"))
    }
}
