//
//  ActivePlanItem.swift
//  Nexo
//

import Foundation

/// One item in a member's credit wallet — created when a `MembershipPlan`
/// is granted to them. Stored at
/// `users/{uid}/memberships/{gymId}/activePlans/{id}`.
struct ActivePlanItem: Codable, Hashable, Identifiable {
    var id: String
    var planName: String
    var type: PlanComponentType
    var resetPeriod: PlanResetPeriod
    /// nil means "all class types". Otherwise one of the gym's `workoutTypes`.
    var workoutType: String?
    /// Base credit allowance (e.g. 12 credits/month or 10 total credits).
    var creditCount: Int
    /// Remaining credits for fixed punch cards (`resetPeriod == .none`).
    var remainingCredits: Int
    /// How many credits have been consumed in the current monthly cycle (`resetPeriod == .monthly`).
    var cycleCreditsUsed: Int
    /// When the plan started / was granted, used as anchor for monthly cycle boundaries.
    var cycleAnchorDate: Date
    /// Month index when credits were last consumed (0 for month 1, 1 for month 2, etc.).
    var lastCycleIndex: Int
    /// Overall expiration date of the plan/contract.
    var expiresAt: Date

    init(
        id: String,
        planName: String,
        type: PlanComponentType = .unlimited,
        resetPeriod: PlanResetPeriod = .none,
        workoutType: String? = nil,
        creditCount: Int = 0,
        remainingCredits: Int = 0,
        cycleCreditsUsed: Int = 0,
        cycleAnchorDate: Date = Date(),
        lastCycleIndex: Int = 0,
        expiresAt: Date
    ) {
        self.id = id
        self.planName = planName
        self.type = type
        self.resetPeriod = resetPeriod
        self.workoutType = workoutType
        self.creditCount = creditCount
        self.remainingCredits = remainingCredits
        self.cycleCreditsUsed = cycleCreditsUsed
        self.cycleAnchorDate = cycleAnchorDate
        self.lastCycleIndex = lastCycleIndex
        self.expiresAt = expiresAt
    }

    var isExpired: Bool {
        expiresAt < Date()
    }

    /// Current cycle index relative to anchor date (0 for 1st month, 1 for 2nd month, etc.).
    func currentCycleIndex(relativeTo now: Date = Date()) -> Int {
        let calendar = Calendar.current
        let components = calendar.dateComponents([.month], from: cycleAnchorDate, to: now)
        return max(0, components.month ?? 0)
    }

    /// The start and end bounds of the current monthly reset cycle.
    func currentCycleBounds(relativeTo now: Date = Date()) -> (start: Date, end: Date) {
        let calendar = Calendar.current
        let index = currentCycleIndex(relativeTo: now)
        let start = calendar.date(byAdding: .month, value: index, to: cycleAnchorDate) ?? cycleAnchorDate
        let end = calendar.date(byAdding: .month, value: index + 1, to: cycleAnchorDate) ?? expiresAt
        return (start, min(end, expiresAt))
    }

    /// Available credits in the current period.
    func availableCredits(relativeTo now: Date = Date()) -> Int {
        guard !isExpired else { return 0 }
        switch type {
        case .unlimited:
            return 0
        case .credits:
            if resetPeriod == .monthly {
                let currentIndex = currentCycleIndex(relativeTo: now)
                let used = (currentIndex == lastCycleIndex) ? cycleCreditsUsed : 0
                return max(0, creditCount - used)
            } else {
                return max(0, remainingCredits)
            }
        }
    }

    /// Whether this item authorizes booking the given class. Premium classes can
    /// only be authorized by a component that explicitly names their `classType` —
    /// a generic "all classes" component (`workoutType == nil`) doesn't cover them.
    func matches(gymClass: GymClass) -> Bool {
        guard !isExpired else { return false }
        if gymClass.isPremium {
            return workoutType == gymClass.classType
        } else {
            return workoutType == nil || workoutType == gymClass.classType
        }
    }
}

