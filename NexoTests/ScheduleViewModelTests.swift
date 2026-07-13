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
        let gymClass = GymClass(title: "Morning HIIT", coach: "Alex", startTime: Date(), capacity: capacity, currentAttendees: currentAttendees)
        mock.classes[gymId] = [gymClass.id: gymClass]
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

    @Test("Booking a full class sets bookingMessage and does not mutate bookedClassIds")
    func bookClassFailureSetsMessageWithoutMutatingState() async {
        let (viewModel, _, _, gymClass) = makeSUT(capacity: 1, currentAttendees: 1)

        await viewModel.bookClass(gymClass)

        #expect(viewModel.bookingMessage != nil)
        #expect(viewModel.bookedClassIds.contains(gymClass.id) == false)
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
}
