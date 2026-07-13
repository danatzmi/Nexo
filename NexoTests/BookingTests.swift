//
//  BookingTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

@Suite("Booking")
struct BookingTests {
    private func makeClass(capacity: Int = 12, currentAttendees: Int = 0) -> (mock: MockBackendService, gymId: UUID, classId: UUID) {
        let mock = MockBackendService()
        mock.signedInUID = "member-1"
        let gymId = UUID()
        let gymClass = GymClass(
            title: "Morning HIIT",
            coach: "Alex",
            startTime: Date(),
            capacity: capacity,
            currentAttendees: currentAttendees
        )
        mock.classes[gymId] = [gymClass.id: gymClass]
        return (mock, gymId, gymClass.id)
    }

    @Test("Booking a class with available capacity increments attendees and records the booking")
    func bookWithAvailableCapacity() async throws {
        let (mock, gymId, classId) = makeClass(capacity: 12, currentAttendees: 3)

        try await mock.book(gymId: gymId, classId: classId)

        let updated = try #require(mock.classes[gymId]?[classId])
        #expect(updated.currentAttendees == 4)
        #expect(try await mock.isUserBooked(gymId: gymId, classId: classId))
    }

    @Test("Booking the same class twice does not double-book or double-increment")
    func doubleBookingIsPrevented() async throws {
        let (mock, gymId, classId) = makeClass(capacity: 12, currentAttendees: 0)

        try await mock.book(gymId: gymId, classId: classId)
        try await mock.book(gymId: gymId, classId: classId)

        let updated = try #require(mock.classes[gymId]?[classId])
        #expect(updated.currentAttendees == 1)
        #expect(try await mock.fetchUserBookings(gymId: gymId).count == 1)
    }

    @Test("Booking a full class throws instead of overbooking")
    func bookingFullClassIsBlocked() async throws {
        let (mock, gymId, classId) = makeClass(capacity: 1, currentAttendees: 1)

        await #expect(throws: MockBackendError.classFull) {
            try await mock.book(gymId: gymId, classId: classId)
        }

        let updated = try #require(mock.classes[gymId]?[classId])
        #expect(updated.currentAttendees == 1)
    }

    @Test("Cancelling a booking decrements attendees")
    func cancelBookingDecrementsAttendees() async throws {
        let (mock, gymId, classId) = makeClass(capacity: 12, currentAttendees: 0)
        try await mock.book(gymId: gymId, classId: classId)

        try await mock.cancelBooking(gymId: gymId, classId: classId)

        let updated = try #require(mock.classes[gymId]?[classId])
        #expect(updated.currentAttendees == 0)
        #expect(try await mock.isUserBooked(gymId: gymId, classId: classId) == false)
    }
}
