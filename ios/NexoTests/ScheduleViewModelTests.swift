//
//  ScheduleViewModelTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

/// Tests the coordination logic (`ScheduleViewModel`) that was previously
/// trapped in `@State` on `ScheduleView` and unreachable by any test:
/// loading-state transitions, error/alert-message formatting, and local
/// state mutation (`bookedClassIds`/`waitlistedClassIds`) on success vs.
/// failure. This is additive to — not a replacement for — `BookingTests`
/// and `WaitlistTests`, which already cover the underlying backend
/// contracts (capacity checks, FIFO waitlist promotion, etc.) directly
/// against `MockBackendService`.
@MainActor
@Suite("ScheduleViewModel")
struct ScheduleViewModelTests {
    private func makeSUT(capacity: Int = 12, currentAttendees: Int = 0) -> (viewModel: ScheduleViewModel, mock: MockBackendService, gymId: UUID, gymClass: GymClass) {
        let mock = MockBackendService()
        mock.signedInUID = "member-1"
        let gymId = UUID()
        let gymClass = GymClass(title: "Morning HIIT", coach: "Alex", startTime: Date().addingTimeInterval(3600), capacity: capacity, currentAttendees: currentAttendees)
        mock.classes[gymId] = [gymClass.id: gymClass]
        // Unrelated to billing — give the test member an unlimited plan so booking isn't blocked.
        mock.grantUnlimitedForTesting(gymId: gymId, userId: "member-1")
        let viewModel = ScheduleViewModel(gymId: gymId, backend: mock)
        return (viewModel, mock, gymId, gymClass)
    }

    @Test("Booking successfully adds the class to bookedClassIds and leaves bookingMessage nil")
    func bookClassSuccessUpdatesState() async {
        let (viewModel, _, _, gymClass) = makeSUT()

        await viewModel.bookClass(gymClass)

        #expect(viewModel.bookedClassIds.contains(gymClass.id))
        #expect(viewModel.bookingMessage == nil)
    }

    @Test("Booking successfully sets bookingSuccessMessage naming the class and time, for the popup")
    func bookClassSuccessSetsSuccessMessage() async {
        let (viewModel, _, _, gymClass) = makeSUT()

        await viewModel.bookClass(gymClass)

        #expect(viewModel.bookingSuccessMessage == "\(gymClass.title) · \(gymClass.formattedTime)")
    }

    @Test("Booking a full class sets bookingMessage and does not mutate bookedClassIds")
    func bookClassFailureSetsMessageWithoutMutatingState() async {
        let (viewModel, _, _, gymClass) = makeSUT(capacity: 1, currentAttendees: 1)

        await viewModel.bookClass(gymClass)

        #expect(viewModel.bookingMessage != nil)
        #expect(viewModel.bookedClassIds.contains(gymClass.id) == false)
    }

    @Test("Booking a full class leaves bookingSuccessMessage nil")
    func bookClassFailureLeavesSuccessMessageNil() async {
        let (viewModel, _, _, gymClass) = makeSUT(capacity: 1, currentAttendees: 1)

        await viewModel.bookClass(gymClass)

        #expect(viewModel.bookingSuccessMessage == nil)
    }

    @Test("Booking a class that has already started sets bookingMessage and does not mutate bookedClassIds")
    func bookClassPastClassSetsMessageWithoutMutatingState() async {
        let (viewModel, _, _, gymClass) = makeSUT()
        let pastClass = GymClass(id: gymClass.id, title: gymClass.title, coach: gymClass.coach, startTime: Date().addingTimeInterval(-3600))

        await viewModel.bookClass(pastClass)

        #expect(viewModel.bookingMessage != nil)
        #expect(viewModel.bookedClassIds.contains(pastClass.id) == false)
    }

    @Test("Joining the waitlist successfully sets waitlistSuccessMessage naming the class and time, for the popup")
    func joinWaitlistSuccessSetsSuccessMessage() async {
        let (viewModel, _, _, gymClass) = makeSUT()

        await viewModel.joinWaitlist(gymClass)

        #expect(viewModel.waitlistSuccessMessage == "\(gymClass.title) · \(gymClass.formattedTime)")
    }

    @Test("Joining the waitlist for a class that has already started sets bookingMessage and does not mutate waitlistedClassIds")
    func joinWaitlistPastClassSetsMessageWithoutMutatingState() async {
        let (viewModel, _, _, gymClass) = makeSUT()
        let pastClass = GymClass(id: gymClass.id, title: gymClass.title, coach: gymClass.coach, startTime: Date().addingTimeInterval(-3600))

        await viewModel.joinWaitlist(pastClass)

        #expect(viewModel.bookingMessage != nil)
        #expect(viewModel.waitlistedClassIds.contains(pastClass.id) == false)
    }

    @Test("Cancelling a booking removes the class from bookedClassIds")
    func cancelBookingUpdatesState() async {
        let (viewModel, _, _, gymClass) = makeSUT()
        await viewModel.bookClass(gymClass)

        await viewModel.cancelBooking(gymClass)

        #expect(viewModel.bookedClassIds.contains(gymClass.id) == false)
    }

    @Test("Joining and leaving the waitlist updates waitlistedClassIds")
    func waitlistTogglesUpdateState() async {
        let (viewModel, _, _, gymClass) = makeSUT(capacity: 1, currentAttendees: 1)

        await viewModel.joinWaitlist(gymClass)
        #expect(viewModel.waitlistedClassIds.contains(gymClass.id))

        await viewModel.leaveWaitlist(gymClass)
        #expect(viewModel.waitlistedClassIds.contains(gymClass.id) == false)
    }

    @Test("loadBookingStatus populates bookedClassIds and waitlistedClassIds from the backend")
    func loadBookingStatusPopulatesState() async throws {
        let (viewModel, mock, gymId, gymClass) = makeSUT()
        mock.signedInUID = "member-1"
        try await mock.book(gymId: gymId, classId: gymClass.id)

        await viewModel.loadBookingStatus()

        #expect(viewModel.bookedClassIds.contains(gymClass.id))
    }

    @Test("loadInitialData ends with isLoading false once classes arrive")
    func loadInitialDataResolvesLoadingState() async {
        let (viewModel, _, _, _) = makeSUT()

        await viewModel.loadInitialData()

        #expect(viewModel.isLoading == false)
    }

    @Test("Booking immediately increments the matching class's currentAttendees in the local classes array, without a reload")
    func bookClassSyncsLocalAttendeeCount() async {
        let (viewModel, _, _, gymClass) = makeSUT(currentAttendees: 3)
        viewModel.startObserving()

        await viewModel.bookClass(gymClass)

        #expect(viewModel.classes.first?.currentAttendees == 4)
    }

    @Test("Cancelling immediately decrements the matching class's currentAttendees in the local classes array, clamped at 0")
    func cancelBookingSyncsLocalAttendeeCount() async {
        let (viewModel, _, _, gymClass) = makeSUT(currentAttendees: 1)
        viewModel.startObserving()
        await viewModel.bookClass(gymClass)

        await viewModel.cancelBooking(gymClass)

        #expect(viewModel.classes.first?.currentAttendees == 1, "started at 1, +1 for the book, -1 for the cancel")
    }

    @Test("Joining the waitlist immediately increments the matching class's waitlistCount in the local classes array")
    func joinWaitlistSyncsLocalWaitlistCount() async {
        let (viewModel, _, _, gymClass) = makeSUT(capacity: 1, currentAttendees: 1)
        viewModel.startObserving()

        await viewModel.joinWaitlist(gymClass)

        #expect(viewModel.classes.first?.waitlistCount == 1)
    }

    @Test("Leaving the waitlist immediately decrements the matching class's waitlistCount in the local classes array, clamped at 0")
    func leaveWaitlistSyncsLocalWaitlistCount() async {
        let (viewModel, _, _, gymClass) = makeSUT(capacity: 1, currentAttendees: 1)
        viewModel.startObserving()
        await viewModel.joinWaitlist(gymClass)

        await viewModel.leaveWaitlist(gymClass)

        #expect(viewModel.classes.first?.waitlistCount == 0)
    }
}
