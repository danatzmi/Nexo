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
        try await mock.book(gymId: gymId, classId: upcoming.id)
        try await mock.book(gymId: gymId, classId: past.id)

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
        try await mock.book(gymId: gymId, classId: gymClass.id)
        await viewModel.loadBookings()

        mock.errorToThrow = MockBackendError.injected
        await viewModel.cancelBooking(gymClass)

        #expect(viewModel.errorMessage != nil)
        #expect(viewModel.bookings.count == 1)
    }
}
