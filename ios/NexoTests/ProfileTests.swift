//
//  ProfileTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

@MainActor
@Suite("Profile")
struct ProfileTests {
    @Test("updateProfilePicture sets profilePicBase64 on the signed-in user's profile")
    func updateProfilePictureSetsBase64OnSignedInUser() async throws {
        let mock = MockBackendService()
        let uid = "user-1"
        mock.users[uid] = PlatformUser(id: uid, firstName: "Dan", lastName: "P", email: "dan@example.com", role: .user)
        mock.signedInUID = uid

        try await mock.updateProfilePicture(base64String: "fake-base64-data")

        let profile = try await mock.fetchUserProfile()
        #expect(profile.profilePicBase64 == "fake-base64-data")
    }

    @Test("updateProfilePicture throws notAuthenticated when no user is signed in")
    func updateProfilePictureThrowsWhenNotSignedIn() async throws {
        let mock = MockBackendService()
        await #expect(throws: MockBackendError.notAuthenticated) {
            try await mock.updateProfilePicture(base64String: "fake-base64-data")
        }
    }

    @Test("fetchAttendees carries a booked user's profilePicBase64 through onto the Member")
    func fetchAttendeesPropagatesProfilePicture() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        let uid = "user-1"
        mock.users[uid] = PlatformUser(
            id: uid, firstName: "Dan", lastName: "P", email: "dan@example.com", role: .user,
            profilePicBase64: "fake-base64-data"
        )
        mock.signedInUID = uid

        let gymClass = GymClass(title: "CrossFit WOD", coach: "Alex", startTime: Date().addingTimeInterval(3600))
        mock.classes[gymId] = [gymClass.id: gymClass]
        mock.grantUnlimitedForTesting(gymId: gymId, userId: uid)
        try await mock.book(gymId: gymId, classId: gymClass.id)

        let attendees = try await mock.fetchAttendees(gymId: gymId, classId: gymClass.id)

        #expect(attendees.first?.profilePicBase64 == "fake-base64-data")
    }

    @Test("fetchAttendees leaves profilePicBase64 nil for a user without a photo")
    func fetchAttendeesNilProfilePicture() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        let uid = "user-1"
        mock.users[uid] = PlatformUser(id: uid, firstName: "Dan", lastName: "P", email: "dan@example.com", role: .user)
        mock.signedInUID = uid

        let gymClass = GymClass(title: "CrossFit WOD", coach: "Alex", startTime: Date().addingTimeInterval(3600))
        mock.classes[gymId] = [gymClass.id: gymClass]
        mock.grantUnlimitedForTesting(gymId: gymId, userId: uid)
        try await mock.book(gymId: gymId, classId: gymClass.id)

        let attendees = try await mock.fetchAttendees(gymId: gymId, classId: gymClass.id)

        #expect(attendees.first?.profilePicBase64 == nil)
    }
}
