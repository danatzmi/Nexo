//
//  WaitlistTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

@MainActor
@Suite("Waitlist")
struct WaitlistTests {
    private func makeFullClass() -> (mock: MockBackendService, gymId: UUID, classId: UUID) {
        let mock = MockBackendService()
        let gymId = UUID()
        let gymClass = GymClass(
            title: "Morning HIIT",
            coach: "Alex",
            startTime: Date().addingTimeInterval(3600),
            capacity: 1,
            currentAttendees: 1
        )
        mock.classes[gymId] = [gymClass.id: gymClass]
        return (mock, gymId, gymClass.id)
    }

    @Test("Joining the waitlist for a full class increments waitlistCount")
    func joinWaitlistIncrementsCount() async throws {
        let (mock, gymId, classId) = makeFullClass()
        mock.signedInUID = "waiting-user"

        try await mock.joinWaitlist(gymId: gymId, classId: classId)

        let updated = try #require(mock.classes[gymId]?[classId])
        #expect(updated.waitlistCount == 1)
        #expect(try await mock.fetchUserWaitlist(gymId: gymId).contains(classId))
    }

    @Test("Leaving the waitlist decrements waitlistCount")
    func leaveWaitlistDecrementsCount() async throws {
        let (mock, gymId, classId) = makeFullClass()
        mock.signedInUID = "waiting-user"
        try await mock.joinWaitlist(gymId: gymId, classId: classId)

        try await mock.leaveWaitlist(gymId: gymId, classId: classId)

        let updated = try #require(mock.classes[gymId]?[classId])
        #expect(updated.waitlistCount == 0)
        #expect(try await mock.fetchUserWaitlist(gymId: gymId).isEmpty)
    }

    @Test("Cancelling a booking promotes the first waiting user and decrements waitlistCount")
    func cancellingBookingPromotesFirstWaitingUser() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        let gymClass = GymClass(title: "Morning HIIT", coach: "Alex", startTime: Date().addingTimeInterval(3600), capacity: 1, currentAttendees: 0)
        mock.classes[gymId] = [gymClass.id: gymClass]
        let classId = gymClass.id

        mock.signedInUID = "attendee-1"
        mock.grantUnlimitedForTesting(gymId: gymId, userId: "attendee-1")
        try await mock.book(gymId: gymId, classId: classId) // fills the only spot

        mock.signedInUID = "waiting-user-1"
        try await mock.joinWaitlist(gymId: gymId, classId: classId)
        mock.signedInUID = "waiting-user-2"
        try await mock.joinWaitlist(gymId: gymId, classId: classId)

        // The original attendee cancels, which should promote waiting-user-1 (first in line).
        try await mock.cancelBooking(gymId: gymId, classId: classId, onBehalfOf: "attendee-1")

        let updated = try #require(mock.classes[gymId]?[classId])
        #expect(updated.waitlistCount == 1, "only waiting-user-2 should remain on the waitlist")
        #expect(updated.currentAttendees == 1, "the promoted user takes the freed spot, so attendance stays full")

        mock.signedInUID = "waiting-user-1"
        #expect(try await mock.isUserBooked(gymId: gymId, classId: classId), "waiting-user-1 should now be booked")

        mock.signedInUID = "waiting-user-2"
        #expect(try await mock.fetchUserWaitlist(gymId: gymId).contains(classId), "waiting-user-2 should still be waiting")
    }

    @Test("Joining the waitlist for a class that has already started throws classInPast")
    func joinWaitlistForPastClassIsBlocked() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        let gymClass = GymClass(title: "Morning HIIT", coach: "Alex", startTime: Date().addingTimeInterval(-3600), capacity: 1, currentAttendees: 1)
        mock.classes[gymId] = [gymClass.id: gymClass]
        mock.signedInUID = "waiting-user"

        await #expect(throws: MockBackendError.classInPast) {
            try await mock.joinWaitlist(gymId: gymId, classId: gymClass.id)
        }
    }

    @Test("Joining the waitlist for a current/future class still succeeds")
    func joinWaitlistForFutureClassSucceeds() async throws {
        let (mock, gymId, classId) = makeFullClass()
        mock.signedInUID = "waiting-user"

        try await mock.joinWaitlist(gymId: gymId, classId: classId)

        #expect(try await mock.fetchUserWaitlist(gymId: gymId).contains(classId))
    }

    // MARK: - fetchWaitlistPosition

    @Test("fetchWaitlistPosition reports 1-based position and total, ordered by join time")
    func fetchWaitlistPositionReportsPositionAndTotal() async throws {
        let (mock, gymId, classId) = makeFullClass()

        mock.signedInUID = "waiting-user-1"
        try await mock.joinWaitlist(gymId: gymId, classId: classId)
        mock.signedInUID = "waiting-user-2"
        try await mock.joinWaitlist(gymId: gymId, classId: classId)
        mock.signedInUID = "waiting-user-3"
        try await mock.joinWaitlist(gymId: gymId, classId: classId)

        mock.signedInUID = "waiting-user-2"
        let result = try await mock.fetchWaitlistPosition(gymId: gymId, classId: classId)

        let position = try #require(result)
        #expect(position.position == 2)
        #expect(position.total == 3)
    }

    @Test("fetchWaitlistPosition returns nil for a user who isn't on the waitlist")
    func fetchWaitlistPositionReturnsNilWhenNotWaitlisted() async throws {
        let (mock, gymId, classId) = makeFullClass()
        mock.signedInUID = "waiting-user-1"
        try await mock.joinWaitlist(gymId: gymId, classId: classId)

        mock.signedInUID = "someone-else"
        let result = try await mock.fetchWaitlistPosition(gymId: gymId, classId: classId)

        #expect(result == nil)
    }
}
