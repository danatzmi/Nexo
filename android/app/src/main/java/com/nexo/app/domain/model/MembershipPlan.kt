package com.nexo.app.domain.model

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** The gym owner's plan category — drives editor presets/defaults. Mirrors iOS's `PlanType`. */
enum class PlanType(val firestoreValue: String, val displayName: String, val shortName: String) {
    MONTHLY("monthly", "Monthly Membership", "Monthly"),
    CLASS_PASS("class_pass", "Multi-Visit Pass", "Multi-Pass");

    companion object {
        fun fromFirestoreValue(value: String): PlanType =
            entries.firstOrNull { it.firestoreValue.equals(value, ignoreCase = true) } ?: MONTHLY
    }
}

enum class PlanComponentType(val firestoreValue: String, val displayName: String) {
    UNLIMITED("unlimited", "Unlimited"),
    CREDITS("credits", "Credits");

    companion object {
        fun fromFirestoreValue(value: String): PlanComponentType =
            entries.firstOrNull { it.firestoreValue.equals(value, ignoreCase = true) } ?: UNLIMITED
    }
}

/** Whether a component's credits reset every month or are a fixed total. Mirrors iOS's `PlanResetPeriod`. */
enum class PlanResetPeriod(val firestoreValue: String, val displayName: String) {
    NONE("none", "No reset (Fixed Total)"),
    MONTHLY("monthly", "Resets Monthly");

    companion object {
        fun fromFirestoreValue(value: String): PlanResetPeriod =
            entries.firstOrNull { it.firestoreValue.equals(value, ignoreCase = true) } ?: NONE
    }
}

enum class ValidityUnit(val firestoreValue: String, val displayName: String) {
    DAYS("days", "Days"),
    WEEKS("weeks", "Weeks"),
    MONTHS("months", "Months"),
    YEARS("years", "Years");

    companion object {
        fun fromFirestoreValue(value: String): ValidityUnit =
            entries.firstOrNull { it.firestoreValue.equals(value, ignoreCase = true) } ?: MONTHS
    }
}

/**
 * One line item within a `MembershipPlan` template, e.g. "Unlimited
 * CrossFit, valid 1 month" or "12 credits/month (resets monthly), valid 1
 * year". Mirrors iOS's `PlanComponent`.
 */
data class PlanComponent(
    val id: String = UUID.randomUUID().toString(),
    val type: PlanComponentType = PlanComponentType.UNLIMITED,
    /** Whether credits reset every month (e.g. 12 classes/month) or are a fixed total. */
    val resetPeriod: PlanResetPeriod = PlanResetPeriod.NONE,
    /** `null` means "all class types" — matches any [GymClass.classType]. */
    val workoutType: String? = null,
    /** Only meaningful when [type] is [PlanComponentType.CREDITS]. */
    val creditCount: Int = 10,
    val validityValue: Int = 1,
    val validityUnit: ValidityUnit = ValidityUnit.MONTHS
) {
    val summary: String
        get() {
            val scope = workoutType ?: "All Classes"
            val unitStr = if (validityValue == 1) validityUnit.displayName.dropLast(1) else validityUnit.displayName
            return when (type) {
                PlanComponentType.UNLIMITED -> "Unlimited $scope · Valid for $validityValue $unitStr"
                PlanComponentType.CREDITS -> {
                    if (resetPeriod == PlanResetPeriod.MONTHLY) {
                        "$creditCount $scope credits/mo (resets monthly) · Valid for $validityValue $unitStr"
                    } else {
                        "$creditCount total $scope credits · Valid for $validityValue $unitStr"
                    }
                }
            }
        }

    /** [fromMillis] + [validityValue] of [validityUnit] — mirrors iOS's `Calendar.date(byAdding: validityUnit.calendarComponent, value: validityValue, to:)`, for computing a granted `ActivePlanItem.expiresAtMillis`. */
    fun expiresAtMillis(fromMillis: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val from = Instant.ofEpochMilli(fromMillis).atZone(zoneId)
        val result = when (validityUnit) {
            ValidityUnit.DAYS -> from.plusDays(validityValue.toLong())
            ValidityUnit.WEEKS -> from.plusWeeks(validityValue.toLong())
            ValidityUnit.MONTHS -> from.plusMonths(validityValue.toLong())
            ValidityUnit.YEARS -> from.plusYears(validityValue.toLong())
        }
        return result.toInstant().toEpochMilli()
    }
}

/**
 * A purchasable package template a gym owner defines, categorized as
 * either a Monthly Membership or Multi-Visit Pass, containing one or more
 * components. Mirrors iOS's `MembershipPlan`.
 */
data class MembershipPlan(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: PlanType = PlanType.MONTHLY,
    val price: Double = 0.0,
    val components: List<PlanComponent> = emptyList()
) {
    val summary: String
        get() = when {
            components.isEmpty() -> "No components added"
            components.size == 1 -> components[0].summary
            else -> components.joinToString(" + ") { comp ->
                val scope = comp.workoutType ?: "All"
                if (comp.type == PlanComponentType.UNLIMITED) {
                    "Unlimited $scope"
                } else if (comp.resetPeriod == PlanResetPeriod.MONTHLY) {
                    "${comp.creditCount} $scope credits/mo"
                } else {
                    "${comp.creditCount} $scope credits"
                }
            }
        }
}
