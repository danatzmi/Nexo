//
//  ClassDetailViewModelTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

@MainActor
@Suite("ClassDetailViewModel")
struct ClassDetailViewModelTests {
    private func makeSUT(
        seriesId: UUID? = nil,
        startTime: Date = Date().addingTimeInterval(3600), capacity: Int = 12
    ) -> (viewModel: ClassDetailViewModel, mock: MockBackendService, gymId: UUID) {
        let mock = MockBackendService()
        let gymId = UUID()
        let gymClass = GymClass(title: "Morning HIIT", coach: "Alex", startTime: startTime, capacity: capacity, seriesId: seriesId)
        mock.classes[gymId] = [gymClass.id: gymClass]
        let viewModel = ClassDetailViewModel(gymId: gymId, gymClass: gymClass, backend: mock)
        return (viewModel, mock, gymId)
    }

    @Test("loadAttendees populates attendees on success")
    func loadAttendeesSuccess() async throws {
        let (viewModel, mock, gymId) = makeSUT()
        mock.signedInUID = "member-1"
        mock.users["member-1"] = PlatformUser(id: "member-1", firstName: "Jane", lastName: "Doe", email: "jane@example.com", role: .user)
        mock.grantUnlimitedForTesting(gymId: gymId, userId: "member-1")
        try await mock.book(gymId: gymId, classId: viewModel.gymClass.id)

        await viewModel.loadAttendees()

        #expect(viewModel.attendees.count == 1)
        #expect(viewModel.errorMessage == nil)
    }

    @Test("loadAttendees sets errorMessage on failure")
    func loadAttendeesFailure() async {
        let (viewModel, mock, _) = makeSUT()
        mock.errorToThrow = MockBackendError.injected

        await viewModel.loadAttendees()

        #expect(viewModel.errorMessage != nil)
        #expect(viewModel.attendees.isEmpty)
    }

    @Test("deleteClass succeeds and flips didDelete")
    func deleteClassSuccess() async {
        let (viewModel, mock, gymId) = makeSUT()

        await viewModel.deleteClass()

        #expect(viewModel.didDelete)
        #expect(viewModel.errorMessage == nil)
        #expect(mock.classes[gymId]?[viewModel.gymClass.id] == nil)
    }

    @Test("deleteClass failure sets errorMessage and leaves didDelete false")
    func deleteClassFailure() async {
        let (viewModel, mock, _) = makeSUT()
        mock.errorToThrow = MockBackendError.injected

        await viewModel.deleteClass()

        #expect(viewModel.didDelete == false)
        #expect(viewModel.errorMessage != nil)
    }

    @Test("deleteSeries succeeds and flips didDelete")
    func deleteSeriesSuccess() async {
        let seriesId = UUID()
        let (viewModel, _, _) = makeSUT(seriesId: seriesId)

        await viewModel.deleteSeries()

        #expect(viewModel.didDelete)
    }

    // MARK: - Booking

    @Test("loadBookingStatus reflects an existing booking")
    func loadBookingStatusReflectsBooking() async {
        let (viewModel, mock, gymId) = makeSUT()
        mock.signedInUID = "member-1"
        mock.seedBookingForTesting(gymId: gymId, classId: viewModel.gymClass.id, userId: "member-1")

        await viewModel.loadBookingStatus()

        #expect(viewModel.isBooked)
        #expect(viewModel.isWaitlisted == false)
    }

    @Test("loadBookingStatus populates waitlistPosition/waitlistTotal when the user is waitlisted")
    func loadBookingStatusPopulatesWaitlistPosition() async throws {
        let (viewModel, mock, gymId) = makeSUT()
        let classId = viewModel.gymClass.id

        mock.signedInUID = "first-in-line"
        try await mock.joinWaitlist(gymId: gymId, classId: classId)
        mock.signedInUID = "member-1"
        try await mock.joinWaitlist(gymId: gymId, classId: classId)

        await viewModel.loadBookingStatus()

        #expect(viewModel.isWaitlisted)
        #expect(viewModel.waitlistPosition == 2)
        #expect(viewModel.waitlistTotal == 2)
    }

    @Test("loadBookingStatus leaves waitlistPosition nil when the user isn't waitlisted")
    func loadBookingStatusLeavesWaitlistPositionNilWhenNotWaitlisted() async {
        let (viewModel, mock, _) = makeSUT()
        mock.signedInUID = "member-1"

        await viewModel.loadBookingStatus()

        #expect(viewModel.isWaitlisted == false)
        #expect(viewModel.waitlistPosition == nil)
        #expect(viewModel.waitlistTotal == 0)
    }

    @Test("book succeeds, marks isBooked, and increments currentAttendees optimistically")
    func bookSuccess() async {
        let (viewModel, mock, gymId) = makeSUT()
        mock.signedInUID = "member-1"
        mock.grantUnlimitedForTesting(gymId: gymId, userId: "member-1")

        await viewModel.book()

        #expect(viewModel.isBooked)
        #expect(viewModel.gymClass.currentAttendees == 1)
        #expect(viewModel.bookingMessage == nil)
    }

    @Test("book failure reverts the optimistic update and sets bookingMessage")
    func bookFailureReverts() async {
        let (viewModel, mock, _) = makeSUT()
        mock.signedInUID = "member-1"
        mock.errorToThrow = MockBackendError.injected

        await viewModel.book()

        #expect(viewModel.isBooked == false)
        #expect(viewModel.gymClass.currentAttendees == 0)
        #expect(viewModel.bookingMessage != nil)
    }

    @Test("book rejects a class that has already started")
    func bookRejectsPastClass() async {
        let (viewModel, mock, gymId) = makeSUT(startTime: Date().addingTimeInterval(-3600))
        mock.signedInUID = "member-1"
        mock.grantUnlimitedForTesting(gymId: gymId, userId: "member-1")

        await viewModel.book()

        #expect(viewModel.isBooked == false)
        #expect(viewModel.gymClass.currentAttendees == 0)
        #expect(viewModel.bookingMessage != nil)
    }

    @Test("cancelBooking succeeds, clears isBooked, and decrements currentAttendees")
    func cancelBookingSuccess() async {
        let (viewModel, mock, gymId) = makeSUT()
        mock.signedInUID = "member-1"
        mock.grantUnlimitedForTesting(gymId: gymId, userId: "member-1")
        await viewModel.book()

        await viewModel.cancelBooking()

        #expect(viewModel.isBooked == false)
        #expect(viewModel.gymClass.currentAttendees == 0)
    }

    @Test("cancelBooking failure reverts the optimistic update")
    func cancelBookingFailureReverts() async {
        let (viewModel, mock, gymId) = makeSUT()
        mock.signedInUID = "member-1"
        mock.grantUnlimitedForTesting(gymId: gymId, userId: "member-1")
        await viewModel.book()
        mock.errorToThrow = MockBackendError.injected

        await viewModel.cancelBooking()

        #expect(viewModel.isBooked)
        #expect(viewModel.gymClass.currentAttendees == 1)
        #expect(viewModel.bookingMessage != nil)
    }

    @Test("joinWaitlist succeeds, marks isWaitlisted, and increments waitlistCount")
    func joinWaitlistSuccess() async {
        let (viewModel, mock, _) = makeSUT(capacity: 1)
        mock.signedInUID = "member-1"

        await viewModel.joinWaitlist()

        #expect(viewModel.isWaitlisted)
        #expect(viewModel.gymClass.waitlistCount == 1)
    }

    @Test("joinWaitlist success sets waitlistSuccessMessage naming the class and time, for the popup")
    func joinWaitlistSuccessSetsSuccessMessage() async {
        let (viewModel, mock, _) = makeSUT(capacity: 1)
        mock.signedInUID = "member-1"

        await viewModel.joinWaitlist()

        #expect(viewModel.waitlistSuccessMessage == "\(viewModel.gymClass.title) · \(viewModel.gymClass.formattedTime)")
    }

    @Test("leaveWaitlist succeeds, clears isWaitlisted, and decrements waitlistCount")
    func leaveWaitlistSuccess() async {
        let (viewModel, mock, _) = makeSUT(capacity: 1)
        mock.signedInUID = "member-1"
        await viewModel.joinWaitlist()

        await viewModel.leaveWaitlist()

        #expect(viewModel.isWaitlisted == false)
        #expect(viewModel.gymClass.waitlistCount == 0)
    }

    // MARK: - Attendance Check-In

    @Test("toggleAttendance updates check-in status and checkedInCount")
    func toggleAttendanceUpdatesStatusAndCount() async throws {
        let (viewModel, mock, gymId) = makeSUT()
        let member = Member(id: "member-1", name: "Jane Doe", email: "jane@example.com")
        mock.signedInUID = "member-1"
        mock.users["member-1"] = PlatformUser(id: "member-1", firstName: "Jane", lastName: "Doe", email: "jane@example.com", role: .user)
        mock.grantUnlimitedForTesting(gymId: gymId, userId: "member-1")
        try await mock.book(gymId: gymId, classId: viewModel.gymClass.id)

        await viewModel.loadAttendees()
        #expect(viewModel.attendees.count == 1)
        #expect(viewModel.checkedInCount == 0)

        await viewModel.toggleAttendance(for: member)

        #expect(viewModel.attendees.first?.isCheckedIn == true)
        #expect(viewModel.checkedInCount == 1)

        // Toggle back off
        await viewModel.toggleAttendance(for: viewModel.attendees.first!)

        #expect(viewModel.attendees.first?.isCheckedIn == false)
        #expect(viewModel.checkedInCount == 0)
    }

    // MARK: - Proactive plan/credit dimming

    @Test("bookingBlockedReason is nil once loadBookingStatus loads a covering unlimited plan")
    func bookingBlockedReasonNilWithUnlimitedPlan() async {
        let (viewModel, mock, gymId) = makeSUT()
        mock.signedInUID = "member-1"
        mock.grantUnlimitedForTesting(gymId: gymId, userId: "member-1")

        await viewModel.loadBookingStatus()

        #expect(viewModel.bookingBlockedReason == nil)
    }

    @Test("bookingBlockedReason is \"No active plan\" for a member with no active plans at all")
    func bookingBlockedReasonNoActivePlan() async {
        let (viewModel, mock, _) = makeSUT()
        mock.signedInUID = "member-1"

        await viewModel.loadBookingStatus()

        #expect(viewModel.bookingBlockedReason == "No active plan")
    }

    @Test("bookingBlockedReason is \"No credits remaining\" for a member with a matching but exhausted credit plan")
    func bookingBlockedReasonNoCreditsRemaining() async {
        let (viewModel, mock, gymId) = makeSUT()
        mock.signedInUID = "member-1"
        let exhaustedItem = ActivePlanItem(
            id: UUID().uuidString,
            planName: "10-Class Pass",
            type: .credits,
            resetPeriod: .none,
            workoutType: nil,
            creditCount: 10,
            remainingCredits: 0,
            cycleCreditsUsed: 0,
            cycleAnchorDate: Date(),
            lastCycleIndex: 0,
            expiresAt: Date().addingTimeInterval(86400 * 30)
        )
        mock.activePlans[gymId, default: [:]]["member-1", default: [:]][exhaustedItem.id] = exhaustedItem

        await viewModel.loadBookingStatus()

        #expect(viewModel.bookingBlockedReason == "No credits remaining")
    }

    @Test("toggleAttendance reverts on backend failure")
    func toggleAttendanceRevertsOnFailure() async throws {
        let (viewModel, mock, gymId) = makeSUT()
        let member = Member(id: "member-1", name: "Jane Doe", email: "jane@example.com")
        mock.signedInUID = "member-1"
        mock.users["member-1"] = PlatformUser(id: "member-1", firstName: "Jane", lastName: "Doe", email: "jane@example.com", role: .user)
        mock.grantUnlimitedForTesting(gymId: gymId, userId: "member-1")
        try await mock.book(gymId: gymId, classId: viewModel.gymClass.id)

        await viewModel.loadAttendees()

        mock.errorToThrow = MockBackendError.injected
        await viewModel.toggleAttendance(for: member)

        #expect(viewModel.attendees.first?.isCheckedIn == false)
        #expect(viewModel.errorMessage != nil)
    }
}
