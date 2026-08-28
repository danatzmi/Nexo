//
//  MembershipTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

@MainActor
@Suite("Membership Plans & Credit Wallet")
struct MembershipTests {
    private func makeSUT() -> (mock: MockBackendService, gymId: UUID, classId: UUID, userId: String) {
        let mock = MockBackendService()
        let gymId = UUID()
        let userId = "member-1"
        let gymClass = GymClass(title: "Morning HIIT", coach: "Alex", startTime: Date().addingTimeInterval(3600), capacity: 12, currentAttendees: 0)
        mock.classes[gymId] = [gymClass.id: gymClass]
        mock.signedInUID = userId
        return (mock, gymId, gymClass.id, userId)
    }

    @discardableResult
    private func addItem(
        _ mock: MockBackendService, gymId: UUID, userId: String,
        type: PlanComponentType, workoutType: String? = nil, remainingCredits: Int = 0, expiresAt: Date = Date().addingTimeInterval(86400 * 30)
    ) -> ActivePlanItem {
        let item = ActivePlanItem(id: UUID().uuidString, planName: "Test Plan", type: type, workoutType: workoutType, remainingCredits: remainingCredits, expiresAt: expiresAt)
        mock.activePlans[gymId, default: [:]][userId, default: [:]][item.id] = item
        return item
    }

    // MARK: - Booking resolution

    @Test("Booking consumes an unlimited plan without touching credits")
    func bookingConsumesUnlimitedFirst() async throws {
        let (mock, gymId, classId, userId) = makeSUT()
        addItem(mock, gymId: gymId, userId: userId, type: .unlimited)
        addItem(mock, gymId: gymId, userId: userId, type: .credits, remainingCredits: 5)

        try await mock.book(gymId: gymId, classId: classId)

        let wallet = try await mock.fetchActivePlans(gymId: gymId, userId: userId)
        let creditsItem = try #require(wallet.first { $0.type == .credits })
        #expect(creditsItem.remainingCredits == 5, "unlimited plan authorized the booking, credits untouched")
        #expect(try await mock.isUserBooked(gymId: gymId, classId: classId))
    }

    @Test("Booking consumes credits when no unlimited plan is active")
    func bookingConsumesCreditsWhenNoUnlimited() async throws {
        let (mock, gymId, classId, userId) = makeSUT()
        let item = addItem(mock, gymId: gymId, userId: userId, type: .credits, remainingCredits: 3)

        try await mock.book(gymId: gymId, classId: classId)

        let wallet = try await mock.fetchActivePlans(gymId: gymId, userId: userId)
        #expect(wallet.first { $0.id == item.id }?.remainingCredits == 2)
    }

    @Test("Booking consumes the credit pass expiring soonest when multiple are active")
    func bookingConsumesEarliestExpiringCreditsFirst() async throws {
        let (mock, gymId, classId, userId) = makeSUT()
        let soonExpiring = addItem(mock, gymId: gymId, userId: userId, type: .credits, remainingCredits: 2, expiresAt: Date().addingTimeInterval(86400 * 5))
        let laterExpiring = addItem(mock, gymId: gymId, userId: userId, type: .credits, remainingCredits: 2, expiresAt: Date().addingTimeInterval(86400 * 30))

        try await mock.book(gymId: gymId, classId: classId)

        let wallet = try await mock.fetchActivePlans(gymId: gymId, userId: userId)
        #expect(wallet.first { $0.id == soonExpiring.id }?.remainingCredits == 1, "the pass expiring soonest is consumed first")
        #expect(wallet.first { $0.id == laterExpiring.id }?.remainingCredits == 2, "the later-expiring pass is untouched")
    }

    @Test("Cancelling a booking refunds the specific class pass that was consumed")
    func cancellingRefundsTheConsumedItem() async throws {
        let (mock, gymId, classId, userId) = makeSUT()
        let onlyItem = addItem(mock, gymId: gymId, userId: userId, type: .credits, remainingCredits: 3)
        try await mock.book(gymId: gymId, classId: classId)

        try await mock.cancelBooking(gymId: gymId, classId: classId)

        let wallet = try await mock.fetchActivePlans(gymId: gymId, userId: userId)
        #expect(wallet.first { $0.id == onlyItem.id }?.remainingCredits == 3)
    }

    @Test("Cancelling an unlimited-authorized booking does not touch any credit pass")
    func cancellingUnlimitedBookingDoesNotRefundCredits() async throws {
        let (mock, gymId, classId, userId) = makeSUT()
        addItem(mock, gymId: gymId, userId: userId, type: .unlimited)
        let creditItem = addItem(mock, gymId: gymId, userId: userId, type: .credits, remainingCredits: 3)
        try await mock.book(gymId: gymId, classId: classId)

        try await mock.cancelBooking(gymId: gymId, classId: classId)

        let wallet = try await mock.fetchActivePlans(gymId: gymId, userId: userId)
        #expect(wallet.first { $0.id == creditItem.id }?.remainingCredits == 3)
    }

    @Test("Booking fails with noActiveMembership when the wallet is empty")
    func bookingFailsWithNoActiveMembershipWhenWalletEmpty() async {
        let (mock, gymId, classId, _) = makeSUT()

        await #expect(throws: MockBackendError.noActiveMembership) {
            try await mock.book(gymId: gymId, classId: classId)
        }
    }

    @Test("Booking fails with insufficientCredits when the class pass is exhausted")
    func bookingFailsWithInsufficientCreditsWhenExhausted() async {
        let (mock, gymId, classId, userId) = makeSUT()
        addItem(mock, gymId: gymId, userId: userId, type: .credits, remainingCredits: 0)

        await #expect(throws: MockBackendError.insufficientCredits) {
            try await mock.book(gymId: gymId, classId: classId)
        }
    }

    @Test("Expired items are ignored, even an unlimited plan")
    func expiredItemsAreIgnored() async {
        let (mock, gymId, classId, userId) = makeSUT()
        addItem(mock, gymId: gymId, userId: userId, type: .unlimited, expiresAt: Date().addingTimeInterval(-86400))

        await #expect(throws: MockBackendError.noActiveMembership) {
            try await mock.book(gymId: gymId, classId: classId)
        }
    }

    @Test("Owners and coaches bypass the wallet check entirely")
    func ownersAndCoachesBypassCheck() async throws {
        let (mock, gymId, classId, userId) = makeSUT()
        mock.userRoles[gymId] = [userId: .owner]

        try await mock.book(gymId: gymId, classId: classId)

        #expect(try await mock.isUserBooked(gymId: gymId, classId: classId))
    }

    @Test("Platform admins bypass the wallet check entirely, even with no gym role and an empty wallet")
    func platformAdminsBypassCheck() async throws {
        let (mock, gymId, classId, userId) = makeSUT()
        mock.users[userId] = PlatformUser(id: userId, firstName: "Ada", lastName: "Admin", email: "ada@example.com", role: .admin)

        try await mock.book(gymId: gymId, classId: classId)

        #expect(try await mock.isUserBooked(gymId: gymId, classId: classId))
    }

    @Test("Staff user with 1-credit pass consumes credit on first booking and is blocked when exhausted")
    func staffWithCreditPlanEnforcesCredits() async throws {
        let (mock, gymId, classId1, userId) = makeSUT()
        mock.userRoles[gymId] = [userId: .owner]
        let class2 = GymClass(title: "Evening HIIT", coach: "Alex", startTime: Date().addingTimeInterval(7200), capacity: 12, currentAttendees: 0)
        mock.classes[gymId]?[class2.id] = class2

        addItem(mock, gymId: gymId, userId: userId, type: .credits, remainingCredits: 1)

        // First booking succeeds and consumes 1 credit
        try await mock.book(gymId: gymId, classId: classId1)
        let walletAfterFirst = try await mock.fetchActivePlans(gymId: gymId, userId: userId)
        #expect(walletAfterFirst.first?.remainingCredits == 0)

        // Second booking is blocked with insufficientCredits
        await #expect(throws: MockBackendError.insufficientCredits) {
            try await mock.book(gymId: gymId, classId: class2.id)
        }
    }

    @Test("Staff user with no active plan at all bypasses wallet check")
    func staffWithoutPlanBypasses() async throws {
        let (mock, gymId, classId, userId) = makeSUT()
        mock.userRoles[gymId] = [userId: .owner]

        // No plan in wallet
        try await mock.book(gymId: gymId, classId: classId)
        #expect(try await mock.isUserBooked(gymId: gymId, classId: classId))
    }

    // MARK: - Plan management

    @Test("createMembershipPlan and fetchMembershipPlans round-trip")
    func createAndFetchMembershipPlans() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        let plan = MembershipPlan(
            name: "Gold Membership",
            price: 120,
            components: [
                PlanComponent(type: .unlimited, workoutType: "CrossFit WOD", validityValue: 1, validityUnit: .months),
                PlanComponent(type: .credits, workoutType: "Pilates", creditCount: 4, validityValue: 1, validityUnit: .months)
            ]
        )

        try await mock.createMembershipPlan(gymId: gymId, plan: plan)
        let fetched = try await mock.fetchMembershipPlans(gymId: gymId)

        #expect(fetched.map(\.id) == [plan.id])
        #expect(fetched.first?.components.count == 2)
    }

    @Test("updateMembershipPlan overwrites an existing plan's fields")
    func updateMembershipPlanOverwritesExistingPlan() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        let original = MembershipPlan(name: "Basic", price: 49, components: [PlanComponent(type: .unlimited, validityValue: 1, validityUnit: .months)])
        try await mock.createMembershipPlan(gymId: gymId, plan: original)

        let updated = MembershipPlan(
            id: original.id,
            name: "10-Class Pass",
            price: 150,
            components: [PlanComponent(type: .credits, creditCount: 10, validityValue: 3, validityUnit: .months)]
        )
        try await mock.updateMembershipPlan(gymId: gymId, plan: updated)

        let fetched = try await mock.fetchMembershipPlans(gymId: gymId)
        let plan = try #require(fetched.first { $0.id == original.id })
        #expect(plan.name == "10-Class Pass")
        #expect(plan.price == 150)
        #expect(plan.components.count == 1)
        #expect(plan.components.first?.creditCount == 10)
    }

    @Test("deleteMembershipPlan removes exactly that plan template")
    func deleteMembershipPlanRemovesOnlyThatPlan() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        let toDelete = MembershipPlan(name: "Basic", price: 49, components: [PlanComponent(type: .unlimited, validityValue: 1, validityUnit: .months)])
        let toKeep = MembershipPlan(name: "Premium", price: 99, components: [PlanComponent(type: .unlimited, validityValue: 1, validityUnit: .months)])
        try await mock.createMembershipPlan(gymId: gymId, plan: toDelete)
        try await mock.createMembershipPlan(gymId: gymId, plan: toKeep)

        try await mock.deleteMembershipPlan(gymId: gymId, planId: toDelete.id)

        let remaining = try await mock.fetchMembershipPlans(gymId: gymId)
        #expect(remaining.map(\.id) == [toKeep.id])
    }

    @Test("grantPlanToMember creates a wallet item for each component")
    func grantMultiComponentPlanCreatesWalletItems() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        let userId = "member-1"
        let plan = MembershipPlan(
            name: "Hybrid Plan",
            price: 120,
            components: [
                PlanComponent(type: .unlimited, workoutType: "CrossFit WOD", validityValue: 1, validityUnit: .months),
                PlanComponent(type: .credits, workoutType: "Pilates", creditCount: 4, validityValue: 1, validityUnit: .months)
            ]
        )

        try await mock.grantPlanToMember(gymId: gymId, userId: userId, plan: plan)

        let wallet = try await mock.fetchActivePlans(gymId: gymId, userId: userId)
        #expect(wallet.count == 2)
        #expect(wallet.contains { $0.type == .unlimited && $0.workoutType == "CrossFit WOD" && $0.remainingCredits == 0 })
        #expect(wallet.contains { $0.type == .credits && $0.workoutType == "Pilates" && $0.remainingCredits == 4 })
    }

    @Test("grantPlanToMember with customExpiresAt overrides default expiration")
    func grantPlanWithCustomExpiration() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        let userId = "member-1"
        let plan = MembershipPlan(
            name: "Special Pass",
            price: 100,
            components: [PlanComponent(type: .credits, creditCount: 10, validityValue: 1, validityUnit: .months)]
        )
        let customDate = Date().addingTimeInterval(86400 * 90) // 90 days from now

        try await mock.grantPlanToMember(gymId: gymId, userId: userId, plan: plan, customExpiresAt: customDate)

        let wallet = try await mock.fetchActivePlans(gymId: gymId, userId: userId)
        let item = try #require(wallet.first)
        #expect(abs(item.expiresAt.timeIntervalSince(customDate)) < 5)
    }

    @Test("revokeActivePlan removes exactly that item, leaving others untouched")
    func revokeActivePlanRemovesOnlyThatItem() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        let userId = "member-1"
        let plan = MembershipPlan(
            name: "Hybrid",
            price: 120,
            components: [
                PlanComponent(type: .unlimited, validityValue: 1, validityUnit: .months),
                PlanComponent(type: .credits, creditCount: 10, validityValue: 1, validityUnit: .months)
            ]
        )
        try await mock.grantPlanToMember(gymId: gymId, userId: userId, plan: plan)
        let wallet = try await mock.fetchActivePlans(gymId: gymId, userId: userId)
        let toRevoke = try #require(wallet.first { $0.type == .unlimited })

        try await mock.revokeActivePlan(gymId: gymId, userId: userId, activePlanId: toRevoke.id)

        let remaining = try await mock.fetchActivePlans(gymId: gymId, userId: userId)
        #expect(remaining.count == 1)
        #expect(remaining.first?.type == .credits)
    }

    // MARK: - Monthly Credit Reset

    @Test("Monthly recurring plan automatically resets available credits when entering a new cycle")
    func monthlyPlanResetsCreditsInNewCycle() async throws {
        let (mock, gymId, classId, userId) = makeSUT()
        let anchorDate = Calendar.current.date(byAdding: .month, value: -1, to: Date())! // started 1 month ago
        let item = ActivePlanItem(
            id: "monthly-plan-1",
            planName: "12-Class Monthly",
            type: .credits,
            resetPeriod: .monthly,
            creditCount: 12,
            remainingCredits: 0,
            cycleCreditsUsed: 12, // used all 12 last month
            cycleAnchorDate: anchorDate,
            lastCycleIndex: 0, // was in cycle 0
            expiresAt: Date().addingTimeInterval(86400 * 300)
        )
        mock.activePlans[gymId, default: [:]][userId, default: [:]][item.id] = item

        // In the new cycle (cycle index 1), all 12 credits should be available
        #expect(item.availableCredits() == 12)

        // Booking succeeds and increments cycle 1's usage to 1
        try await mock.book(gymId: gymId, classId: classId)

        let wallet = try await mock.fetchActivePlans(gymId: gymId, userId: userId)
        let updated = try #require(wallet.first { $0.id == item.id })
        #expect(updated.lastCycleIndex == 1)
        #expect(updated.cycleCreditsUsed == 1)
        #expect(updated.availableCredits() == 11)
    }

    @Test("Monthly recurring plan blocks booking when current month's allowance is exhausted")
    func monthlyPlanBlocksWhenCurrentCycleExhausted() async throws {
        let (mock, gymId, classId, userId) = makeSUT()
        let item = ActivePlanItem(
            id: "monthly-plan-2",
            planName: "2-Class Monthly",
            type: .credits,
            resetPeriod: .monthly,
            creditCount: 2,
            remainingCredits: 0,
            cycleCreditsUsed: 2,
            cycleAnchorDate: Date(),
            lastCycleIndex: 0,
            expiresAt: Date().addingTimeInterval(86400 * 300)
        )
        mock.activePlans[gymId, default: [:]][userId, default: [:]][item.id] = item

        #expect(item.availableCredits() == 0)

        await #expect(throws: MockBackendError.insufficientCredits) {
            try await mock.book(gymId: gymId, classId: classId)
        }
    }

    @Test("Cancelling booking on a monthly recurring plan refunds the cycle credit")
    func monthlyPlanCancellingRefundsCycleCredit() async throws {
        let (mock, gymId, classId, userId) = makeSUT()
        let item = ActivePlanItem(
            id: "monthly-plan-3",
            planName: "5-Class Monthly",
            type: .credits,
            resetPeriod: .monthly,
            creditCount: 5,
            remainingCredits: 0,
            cycleCreditsUsed: 0,
            cycleAnchorDate: Date(),
            lastCycleIndex: 0,
            expiresAt: Date().addingTimeInterval(86400 * 300)
        )
        mock.activePlans[gymId, default: [:]][userId, default: [:]][item.id] = item

        try await mock.book(gymId: gymId, classId: classId)
        let walletAfterBooking = try await mock.fetchActivePlans(gymId: gymId, userId: userId)
        #expect(walletAfterBooking.first?.cycleCreditsUsed == 1)

        try await mock.cancelBooking(gymId: gymId, classId: classId)
        let walletAfterCancel = try await mock.fetchActivePlans(gymId: gymId, userId: userId)
        #expect(walletAfterCancel.first?.cycleCreditsUsed == 0)
    }
}
