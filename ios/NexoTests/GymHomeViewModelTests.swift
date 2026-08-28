//
//  GymHomeViewModelTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

@MainActor
@Suite("GymHomeViewModel")
struct GymHomeViewModelTests {
    private func makeSUT() -> (viewModel: GymHomeViewModel, mock: MockBackendService, gymId: UUID) {
        let mock = MockBackendService()
        let gymId = UUID()
        let viewModel = GymHomeViewModel(gymId: gymId, backend: mock)
        return (viewModel, mock, gymId)
    }

    @Test("loadOwnerData populates today's classes and totalBookingsToday")
    func loadOwnerDataPopulatesState() async {
        let (viewModel, mock, gymId) = makeSUT()
        let classA = GymClass(title: "Morning HIIT", coach: "Alex", startTime: Date(), currentAttendees: 5)
        let classB = GymClass(title: "Evening Yoga", coach: "Sam", startTime: Date(), currentAttendees: 3)
        mock.classes[gymId] = [classA.id: classA, classB.id: classB]

        await viewModel.loadOwnerData()

        #expect(viewModel.todayClasses.count == 2)
        #expect(viewModel.totalBookingsToday == 8)
        #expect(viewModel.errorMessage == nil)
    }

    @Test("loadOwnerData sets errorMessage on failure")
    func loadOwnerDataFailure() async {
        let (viewModel, mock, _) = makeSUT()
        mock.errorToThrow = MockBackendError.injected

        await viewModel.loadOwnerData()

        #expect(viewModel.errorMessage != nil)
    }

    @Test("loadCoachData only includes today's classes taught by the signed-in coach")
    func loadCoachDataFiltersByCoachName() async {
        let (viewModel, mock, gymId) = makeSUT()
        mock.signedInUID = "coach-1"
        mock.users["coach-1"] = PlatformUser(id: "coach-1", firstName: "Alex", lastName: "Rivera", email: "alex@example.com", role: .user)
        let ownClass = GymClass(title: "Morning HIIT", coach: "Alex Rivera", startTime: Date())
        let otherClass = GymClass(title: "Evening Yoga", coach: "Sam Lee", startTime: Date())
        mock.classes[gymId] = [ownClass.id: ownClass, otherClass.id: otherClass]

        await viewModel.loadCoachData()

        #expect(viewModel.todayClasses.map(\.id) == [ownClass.id])
        #expect(viewModel.errorMessage == nil)
    }

    @Test("loadMemberData populates the earliest upcoming booking as nextBooking")
    func loadMemberDataPopulatesNextBooking() async throws {
        let (viewModel, mock, gymId) = makeSUT()
        mock.signedInUID = "member-1"
        let soonest = GymClass(title: "Morning HIIT", coach: "Alex", startTime: Date().addingTimeInterval(3600))
        let later = GymClass(title: "Evening Yoga", coach: "Sam", startTime: Date().addingTimeInterval(7200))
        mock.classes[gymId] = [soonest.id: soonest, later.id: later]
        mock.grantUnlimitedForTesting(gymId: gymId, userId: "member-1")
        try await mock.book(gymId: gymId, classId: soonest.id)
        try await mock.book(gymId: gymId, classId: later.id)

        await viewModel.loadMemberData()

        #expect(viewModel.nextBooking?.id == soonest.id)
        #expect(viewModel.errorMessage == nil)
    }

    @Test("loadMemberData leaves nextBooking nil when there are no upcoming bookings")
    func loadMemberDataNoUpcomingBookings() async {
        let (viewModel, mock, _) = makeSUT()
        mock.signedInUID = "member-1"

        await viewModel.loadMemberData()

        #expect(viewModel.nextBooking == nil)
        #expect(viewModel.errorMessage == nil)
    }

    @Test("loadMemberData populates activePlans from the member's credit wallet")
    func loadMemberDataPopulatesActivePlans() async {
        let (viewModel, mock, gymId) = makeSUT()
        mock.signedInUID = "member-1"
        let plan = mock.grantUnlimitedForTesting(gymId: gymId, userId: "member-1")

        await viewModel.loadMemberData()

        #expect(viewModel.activePlans.map(\.id) == [plan.id])
        #expect(viewModel.errorMessage == nil)
    }

    @Test("loadMemberData leaves activePlans empty when the member has no active plans")
    func loadMemberDataNoActivePlans() async {
        let (viewModel, mock, _) = makeSUT()
        mock.signedInUID = "member-1"

        await viewModel.loadMemberData()

        #expect(viewModel.activePlans.isEmpty)
    }

    @Test("loadMemberData sets errorMessage on failure")
    func loadMemberDataFailure() async {
        let (viewModel, mock, _) = makeSUT()
        mock.signedInUID = "member-1"
        mock.errorToThrow = MockBackendError.injected

        await viewModel.loadMemberData()

        #expect(viewModel.errorMessage != nil)
    }

    @Test("loadOwnerData populates userDisplayName from the signed-in user's profile, for the dashboard greeting")
    func loadOwnerDataPopulatesUserDisplayName() async {
        let (viewModel, mock, _) = makeSUT()
        mock.signedInUID = "owner-1"
        mock.users["owner-1"] = PlatformUser(id: "owner-1", firstName: "Dana", lastName: "Lee", email: "dana@example.com", role: .user)

        await viewModel.loadOwnerData()

        #expect(viewModel.userDisplayName == "Dana")
    }

    @Test("loadMemberData populates userDisplayName from the signed-in user's profile, for the dashboard greeting")
    func loadMemberDataPopulatesUserDisplayName() async {
        let (viewModel, mock, _) = makeSUT()
        mock.signedInUID = "member-1"
        mock.users["member-1"] = PlatformUser(id: "member-1", firstName: "Jordan", lastName: "Kim", email: "jordan@example.com", role: .user)

        await viewModel.loadMemberData()

        #expect(viewModel.userDisplayName == "Jordan")
    }

    @Test("loadCoachData populates userDisplayName from the signed-in user's profile, for the dashboard greeting")
    func loadCoachDataPopulatesUserDisplayName() async {
        let (viewModel, mock, _) = makeSUT()
        mock.signedInUID = "coach-1"
        mock.users["coach-1"] = PlatformUser(id: "coach-1", firstName: "Alex", lastName: "Rivera", email: "alex@example.com", role: .user)

        await viewModel.loadCoachData()

        #expect(viewModel.userDisplayName == "Alex")
    }
}
