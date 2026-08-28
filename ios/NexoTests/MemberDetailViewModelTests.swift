//
//  MemberDetailViewModelTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

@MainActor
@Suite("MemberDetailViewModel")
struct MemberDetailViewModelTests {
    private func makeSUT() -> (viewModel: MemberDetailViewModel, mock: MockBackendService, gymId: UUID, member: Member) {
        let mock = MockBackendService()
        let gymId = UUID()
        let member = Member(id: "member-1", name: "Jane Doe", email: "jane@example.com", joinedAt: Date())
        let viewModel = MemberDetailViewModel(gymId: gymId, member: member, backend: mock)
        return (viewModel, mock, gymId, member)
    }

    @Test("loadBookings populates bookings and splits into upcoming/past")
    func loadBookingsSplitsUpcomingAndPast() async throws {
        let (viewModel, mock, gymId, member) = makeSUT()
        let upcoming = GymClass(title: "Future Class", coach: "Alex", startTime: Date().addingTimeInterval(3600))
        let past = GymClass(title: "Past Class", coach: "Alex", startTime: Date().addingTimeInterval(-3600))
        mock.classes[gymId] = [upcoming.id: upcoming, past.id: past]
        mock.signedInUID = member.id
        mock.grantUnlimitedForTesting(gymId: gymId, userId: member.id)
        try await mock.book(gymId: gymId, classId: upcoming.id)
        mock.seedBookingForTesting(gymId: gymId, classId: past.id, userId: member.id)

        await viewModel.loadBookings()

        #expect(viewModel.upcomingBookings.map(\.id) == [upcoming.id])
        #expect(viewModel.pastBookings.map(\.id) == [past.id])
        #expect(viewModel.errorMessage == nil)
    }

    @Test("loadBookings sets errorMessage on failure")
    func loadBookingsFailure() async {
        let (viewModel, mock, _, _) = makeSUT()
        mock.errorToThrow = MockBackendError.injected

        await viewModel.loadBookings()

        #expect(viewModel.errorMessage != nil)
        #expect(viewModel.bookings.isEmpty)
    }

    @Test("cancelBooking removes the class from bookings on success")
    func cancelBookingSuccess() async throws {
        let (viewModel, mock, gymId, member) = makeSUT()
        let gymClass = GymClass(title: "Morning HIIT", coach: "Alex", startTime: Date().addingTimeInterval(3600))
        mock.classes[gymId] = [gymClass.id: gymClass]
        mock.signedInUID = member.id
        mock.grantUnlimitedForTesting(gymId: gymId, userId: member.id)
        try await mock.book(gymId: gymId, classId: gymClass.id)
        await viewModel.loadBookings()
        #expect(viewModel.bookings.count == 1)

        await viewModel.cancelBooking(gymClass)

        #expect(viewModel.bookings.isEmpty)
        #expect(viewModel.errorMessage == nil)
    }

    @Test("cancelBooking sets errorMessage on failure and leaves bookings unchanged")
    func cancelBookingFailure() async throws {
        let (viewModel, mock, gymId, member) = makeSUT()
        let gymClass = GymClass(title: "Morning HIIT", coach: "Alex", startTime: Date().addingTimeInterval(3600))
        mock.classes[gymId] = [gymClass.id: gymClass]
        mock.signedInUID = member.id
        mock.grantUnlimitedForTesting(gymId: gymId, userId: member.id)
        try await mock.book(gymId: gymId, classId: gymClass.id)
        await viewModel.loadBookings()

        mock.errorToThrow = MockBackendError.injected
        await viewModel.cancelBooking(gymClass)

        #expect(viewModel.errorMessage != nil)
        #expect(viewModel.bookings.count == 1)
    }

    @Test("loadWallet populates the member's active plan items")
    func loadWalletPopulatesPlans() async {
        let (viewModel, mock, gymId, member) = makeSUT()
        let item = ActivePlanItem(
            id: "item-1", planName: "Premium", type: .credits,
            remainingCredits: 4, expiresAt: Date().addingTimeInterval(86400 * 30)
        )
        mock.activePlans[gymId] = [member.id: [item.id: item]]

        await viewModel.loadWallet()

        #expect(viewModel.activePlans.count == 1)
        #expect(viewModel.activePlans.first?.remainingCredits == 4)
        #expect(viewModel.errorMessage == nil)
    }

    @Test("loadAvailablePlans populates the gym's plan templates")
    func loadAvailablePlansPopulates() async {
        let (viewModel, mock, gymId, _) = makeSUT()
        let plan = MembershipPlan(name: "Premium", price: 99, components: [PlanComponent(type: .unlimited, validityValue: 1, validityUnit: .months)])
        mock.membershipPlans[gymId] = [plan.id: plan]

        await viewModel.loadAvailablePlans()

        #expect(viewModel.availablePlans.map(\.id) == [plan.id])
    }

    @Test("grantPlan adds wallet items and refreshes state")
    func grantPlanUpdatesState() async {
        let (viewModel, _, _, _) = makeSUT()
        let plan = MembershipPlan(name: "10-Class Pass", price: 150, components: [PlanComponent(type: .credits, creditCount: 10, validityValue: 3, validityUnit: .months)])

        await viewModel.grantPlan(plan)

        #expect(viewModel.activePlans.count == 1)
        #expect(viewModel.activePlans.first?.remainingCredits == 10)
        #expect(viewModel.errorMessage == nil)
    }

    @Test("grantPlan with customExpiresAt applies custom expiration")
    func grantPlanWithCustomExpiration() async {
        let (viewModel, _, _, _) = makeSUT()
        let plan = MembershipPlan(name: "10-Class Pass", price: 150, components: [PlanComponent(type: .credits, creditCount: 10, validityValue: 3, validityUnit: .months)])
        let customDate = Date().addingTimeInterval(86400 * 60)

        await viewModel.grantPlan(plan, customExpiresAt: customDate)

        #expect(viewModel.activePlans.count == 1)
        let item = viewModel.activePlans.first
        #expect(item?.remainingCredits == 10)
        #expect(abs((item?.expiresAt ?? Date()).timeIntervalSince(customDate)) < 5)
        #expect(viewModel.errorMessage == nil)
    }

    @Test("grantPlan sets errorMessage on failure")
    func grantPlanFailure() async {
        let (viewModel, mock, _, _) = makeSUT()
        mock.errorToThrow = MockBackendError.injected
        let plan = MembershipPlan(name: "Premium", price: 99, components: [PlanComponent(type: .unlimited, validityValue: 1, validityUnit: .months)])

        await viewModel.grantPlan(plan)

        #expect(viewModel.errorMessage != nil)
    }

    @Test("revokeActivePlan removes just that item from the wallet")
    func revokeActivePlanUpdatesState() async throws {
        let (viewModel, _, _, _) = makeSUT()
        let plan = MembershipPlan(name: "Premium", price: 99, components: [PlanComponent(type: .unlimited, validityValue: 1, validityUnit: .months)])
        await viewModel.grantPlan(plan)
        let item = try #require(viewModel.activePlans.first)

        await viewModel.revokeActivePlan(item)

        #expect(viewModel.activePlans.isEmpty)
        #expect(viewModel.errorMessage == nil)
    }

    @Test("removeMember deletes the member and their wallet, returning true")
    func removeMemberSucceeds() async throws {
        let (viewModel, mock, gymId, member) = makeSUT()
        mock.members[gymId] = [member]
        mock.activePlans[gymId] = [
            member.id: ["item-1": ActivePlanItem(
                id: "item-1", planName: "Premium", type: .credits,
                remainingCredits: 4, expiresAt: Date().addingTimeInterval(86400)
            )]
        ]

        let result = await viewModel.removeMember()

        #expect(result)
        #expect(mock.members[gymId]?.isEmpty ?? true)
        #expect(mock.activePlans[gymId]?[member.id] == nil)
        #expect(viewModel.errorMessage == nil)
    }

    @Test("removeMember returns false and sets errorMessage on failure")
    func removeMemberFailure() async {
        let (viewModel, mock, _, _) = makeSUT()
        mock.errorToThrow = MockBackendError.injected

        let result = await viewModel.removeMember()

        #expect(!result)
        #expect(viewModel.errorMessage != nil)
    }
}
