//
//  TeamTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

@MainActor
@Suite("Team Management")
struct TeamTests {
    @Test("addTeamMember persists the member with an owner role")
    func addTeamMemberPersistsOwnerRole() async throws {
        let mock = MockBackendService()
        let gymId = UUID()

        try await mock.addTeamMember(
            gymId: gymId, firstName: "Jamie", lastName: "Lee",
            email: "jamie@example.com", password: "password1", role: .owner
        )

        let team = try await mock.fetchTeam(gymId: gymId)
        let added = try #require(team.first)
        #expect(added.role == .owner)
        #expect(added.fullName == "Jamie Lee")
    }

    @Test("addTeamMember persists the member with a coach role")
    func addTeamMemberPersistsCoachRole() async throws {
        let mock = MockBackendService()
        let gymId = UUID()

        try await mock.addTeamMember(
            gymId: gymId, firstName: "Sam", lastName: "Rivera",
            email: "sam@example.com", password: "password1", role: .coach
        )

        let team = try await mock.fetchTeam(gymId: gymId)
        let added = try #require(team.first)
        #expect(added.role == .coach)
    }

    @Test("updateTeamMemberRole changes the member's role")
    func updateTeamMemberRoleChangesRole() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        try await mock.addTeamMember(
            gymId: gymId, firstName: "Sam", lastName: "Rivera",
            email: "sam@example.com", password: "password1", role: .coach
        )
        let member = try #require(try await mock.fetchTeam(gymId: gymId).first)

        try await mock.updateTeamMemberRole(gymId: gymId, userId: member.id, role: .owner)

        let updated = try await mock.fetchTeam(gymId: gymId)
        #expect(updated.first?.role == .owner)
    }

    @Test("removeTeamMember removes exactly that member, leaving others untouched")
    func removeTeamMemberRemovesOnlyThatMember() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        try await mock.addTeamMember(
            gymId: gymId, firstName: "Jamie", lastName: "Lee",
            email: "jamie@example.com", password: "password1", role: .owner
        )
        try await mock.addTeamMember(
            gymId: gymId, firstName: "Sam", lastName: "Rivera",
            email: "sam@example.com", password: "password1", role: .coach
        )
        let team = try await mock.fetchTeam(gymId: gymId)
        let toRemove = try #require(team.first { $0.fullName == "Sam Rivera" })

        try await mock.removeTeamMember(gymId: gymId, userId: toRemove.id)

        let remaining = try await mock.fetchTeam(gymId: gymId)
        #expect(remaining.count == 1)
        #expect(remaining.first?.fullName == "Jamie Lee")
    }

    // MARK: - addExistingUserToGym

    @Test("addExistingUserToGym with role .member writes a member record, not a team record")
    func addExistingUserToGymWritesMemberRecord() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        let uid = "existing-uid"
        mock.users[uid] = PlatformUser(id: uid, firstName: "Dana", lastName: "Existing", email: "dana@example.com", role: .user)

        try await mock.addExistingUserToGym(gymId: gymId, userId: uid, role: .member)

        let members = try await mock.fetchMembers(gymId: gymId)
        #expect(members.count == 1)
        #expect(members.first?.id == uid)
        #expect(members.first?.name == "Dana Existing")
        let team = try await mock.fetchTeam(gymId: gymId)
        #expect(team.isEmpty)
    }

    @Test("addExistingUserToGym with role .coach writes a team record, not a member record")
    func addExistingUserToGymWritesCoachTeamRecord() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        let uid = "existing-uid"
        mock.users[uid] = PlatformUser(id: uid, firstName: "Alex", lastName: "Rivera", email: "alex@example.com", role: .user)

        try await mock.addExistingUserToGym(gymId: gymId, userId: uid, role: .coach)

        let team = try await mock.fetchTeam(gymId: gymId)
        #expect(team.count == 1)
        #expect(team.first?.id == uid)
        #expect(team.first?.role == .coach)
        let members = try await mock.fetchMembers(gymId: gymId)
        #expect(members.isEmpty)
    }

    @Test("addExistingUserToGym with role .owner writes a team record with owner role")
    func addExistingUserToGymWritesOwnerTeamRecord() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        let uid = "existing-uid"
        mock.users[uid] = PlatformUser(id: uid, firstName: "Jordan", lastName: "Lee", email: "jordan@example.com", role: .user)

        try await mock.addExistingUserToGym(gymId: gymId, userId: uid, role: .owner)

        let team = try await mock.fetchTeam(gymId: gymId)
        #expect(team.first?.role == .owner)
    }

    @Test("addExistingUserToGym throws userNotFound for an unknown userId")
    func addExistingUserToGymThrowsForUnknownUser() async {
        let mock = MockBackendService()
        let gymId = UUID()

        await #expect(throws: MockBackendError.userNotFound) {
            try await mock.addExistingUserToGym(gymId: gymId, userId: "no-such-uid", role: .member)
        }
    }
}
