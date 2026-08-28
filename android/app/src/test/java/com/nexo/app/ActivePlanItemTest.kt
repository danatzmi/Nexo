package com.nexo.app

import com.nexo.app.domain.model.ActivePlanItem
import com.nexo.app.domain.model.PlanComponentType
import com.nexo.app.domain.model.PlanResetPeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ActivePlanItemTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun millis(year: Int, month: Int, day: Int): Long =
        ZonedDateTime.of(year, month, day, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun monthlyResetItem(creditCount: Int, cycleCreditsUsed: Int, lastCycleIndex: Int, anchor: Long, now: Long) = ActivePlanItem(
        id = "item-1",
        planName = "Gold",
        type = PlanComponentType.CREDITS,
        resetPeriod = PlanResetPeriod.MONTHLY,
        creditCount = creditCount,
        cycleCreditsUsed = cycleCreditsUsed,
        cycleAnchorDateMillis = anchor,
        lastCycleIndex = lastCycleIndex,
        expiresAtMillis = millis(2027, 1, 1)
    )

    @Test
    fun currentCycleIndex_isZero_withinTheFirstMonth() {
        val anchor = millis(2026, 1, 15)
        val item = monthlyResetItem(12, 0, 0, anchor, anchor)
        assertEquals(0, item.currentCycleIndex(millis(2026, 1, 20), zone))
    }

    @Test
    fun currentCycleIndex_advances_oneMonthAtATime() {
        val anchor = millis(2026, 1, 15)
        val item = monthlyResetItem(12, 0, 0, anchor, anchor)
        assertEquals(1, item.currentCycleIndex(millis(2026, 2, 20), zone))
        assertEquals(3, item.currentCycleIndex(millis(2026, 4, 1), zone))
    }

    @Test
    fun availableCredits_reflectsUsageInTheCurrentCycleOnly() {
        val anchor = millis(2026, 1, 15)
        // 12 credits/month, used 5 in cycle index 0, still in cycle 0
        val item = monthlyResetItem(12, cycleCreditsUsed = 5, lastCycleIndex = 0, anchor = anchor, now = anchor)
        assertEquals(7, item.availableCredits(millis(2026, 1, 25), zone))
    }

    @Test
    fun availableCredits_resetsToFull_whenEnteringANewCycle_evenIfLastCycleWasFullyUsed() {
        val anchor = millis(2026, 1, 15)
        // Exhausted month 0 (12 of 12 used), but now() has rolled into month 1 —
        // the reset should apply even though the stored lastCycleIndex is stale.
        val item = monthlyResetItem(12, cycleCreditsUsed = 12, lastCycleIndex = 0, anchor = anchor, now = anchor)
        assertEquals(12, item.availableCredits(millis(2026, 2, 20), zone))
    }

    @Test
    fun availableCredits_isZero_whenExpired() {
        val item = ActivePlanItem(
            id = "item-1", planName = "Gold", type = PlanComponentType.CREDITS, resetPeriod = PlanResetPeriod.MONTHLY,
            creditCount = 12, cycleCreditsUsed = 0, lastCycleIndex = 0,
            cycleAnchorDateMillis = millis(2026, 1, 1), expiresAtMillis = millis(2026, 1, 1) - 1000
        )
        assertEquals(0, item.availableCredits(millis(2026, 1, 2), zone))
    }

    @Test
    fun availableCredits_isZero_forUnlimitedItems() {
        val item = ActivePlanItem(
            id = "item-1", planName = "Gold", type = PlanComponentType.UNLIMITED,
            expiresAtMillis = millis(2027, 1, 1)
        )
        assertEquals(0, item.availableCredits(millis(2026, 6, 1), zone))
    }

    @Test
    fun availableCredits_usesFixedRemainingCredits_whenResetPeriodIsNone() {
        val item = ActivePlanItem(
            id = "item-1", planName = "10-Class Pack", type = PlanComponentType.CREDITS, resetPeriod = PlanResetPeriod.NONE,
            remainingCredits = 4, expiresAtMillis = millis(2027, 1, 1)
        )
        assertEquals(4, item.availableCredits(millis(2026, 6, 1), zone))
    }

    @Test
    fun currentCycleBounds_isClampedToExpiresAt_nearTheEndOfThePlan() {
        val anchor = millis(2026, 1, 1)
        val expiresAt = millis(2026, 1, 20)
        val item = ActivePlanItem(
            id = "item-1", planName = "Short Plan", type = PlanComponentType.CREDITS, resetPeriod = PlanResetPeriod.MONTHLY,
            creditCount = 12, cycleAnchorDateMillis = anchor, expiresAtMillis = expiresAt
        )
        val (_, end) = item.currentCycleBounds(millis(2026, 1, 10), zone)
        assertEquals(expiresAt, end)
    }
}
