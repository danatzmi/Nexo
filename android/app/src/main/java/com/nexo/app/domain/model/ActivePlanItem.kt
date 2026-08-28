package com.nexo.app.domain.model

import java.time.Instant
import java.time.Period
import java.time.ZoneId

/**
 * One item in a member's credit wallet — created from a single
 * `PlanComponent` when a `MembershipPlan` is granted to them. Stored at
 * `users/{uid}/memberships/{gymId}/activePlans/{id}`. Mirrors iOS's
 * `ActivePlanItem`.
 */
data class ActivePlanItem(
    val id: String,
    val planName: String,
    val type: PlanComponentType,
    val resetPeriod: PlanResetPeriod = PlanResetPeriod.NONE,
    /** `null` means "all class types". Otherwise one of the gym's `workoutTypes`. */
    val workoutType: String? = null,
    /** Base credit allowance (e.g. 12 credits/month or 10 total credits). */
    val creditCount: Int = 0,
    /** Remaining credits for fixed punch cards ([resetPeriod] is [PlanResetPeriod.NONE]). */
    val remainingCredits: Int = 0,
    /** How many credits have been consumed in the current monthly cycle ([resetPeriod] is [PlanResetPeriod.MONTHLY]). */
    val cycleCreditsUsed: Int = 0,
    /** When the plan started / was granted, used as anchor for monthly cycle boundaries. */
    val cycleAnchorDateMillis: Long = System.currentTimeMillis(),
    /** Month index when credits were last consumed (0 for month 1, 1 for month 2, etc.). */
    val lastCycleIndex: Int = 0,
    val expiresAtMillis: Long
) {
    val isExpired: Boolean get() = expiresAtMillis < System.currentTimeMillis()

    /** Current cycle index relative to [cycleAnchorDateMillis] (0 for the 1st month, 1 for the 2nd, etc.). */
    fun currentCycleIndex(relativeToMillis: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): Int {
        val anchor = Instant.ofEpochMilli(cycleAnchorDateMillis).atZone(zoneId).toLocalDate()
        val now = Instant.ofEpochMilli(relativeToMillis).atZone(zoneId).toLocalDate()
        val months = Period.between(anchor.withDayOfMonth(1), now.withDayOfMonth(1)).toTotalMonths().toInt()
        return maxOf(0, months)
    }

    /** The start and end bounds of the current monthly reset cycle. */
    fun currentCycleBounds(relativeToMillis: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val index = currentCycleIndex(relativeToMillis, zoneId)
        val anchor = Instant.ofEpochMilli(cycleAnchorDateMillis).atZone(zoneId)
        val start = anchor.plusMonths(index.toLong()).toInstant().toEpochMilli()
        val end = anchor.plusMonths((index + 1).toLong()).toInstant().toEpochMilli()
        return start to minOf(end, expiresAtMillis)
    }

    /** Available credits in the current period. */
    fun availableCredits(relativeToMillis: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): Int {
        if (isExpired) return 0
        return when (type) {
            PlanComponentType.UNLIMITED -> 0
            PlanComponentType.CREDITS -> {
                if (resetPeriod == PlanResetPeriod.MONTHLY) {
                    val currentIndex = currentCycleIndex(relativeToMillis, zoneId)
                    val used = if (currentIndex == lastCycleIndex) cycleCreditsUsed else 0
                    maxOf(0, creditCount - used)
                } else {
                    maxOf(0, remainingCredits)
                }
            }
        }
    }

    /**
     * Whether this item authorizes booking [gymClass] — mirrors iOS's
     * `ActivePlanItem.matches(gymClass:)`. A premium class can only be
     * authorized by an item that explicitly names its `classType`; a
     * generic "all classes" item (`workoutType == null`) doesn't cover it.
     */
    fun matches(gymClass: GymClass): Boolean {
        if (isExpired) return false
        return if (gymClass.isPremium) {
            workoutType == gymClass.classType
        } else {
            workoutType == null || workoutType == gymClass.classType
        }
    }
}
