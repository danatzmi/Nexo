//
//  GymManagementTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

@MainActor
@Suite("Gym Settings & Cascade Deletion")
struct GymManagementTests {
    @Test("updateGymSettings updates both the gym's name and its workoutTypes")
    func updateGymSettingsUpdatesNameAndCategories() async throws {
        let mock = MockBackendService()
        let gym = Gym(name: "Old Name", ownerUID: "owner-1")
        mock.gyms[gym.id] = gym

        try await mock.updateGymSettings(gymId: gym.id, name: "New Name", workoutTypes: ["Boxing", "Spinning"])

        #expect(mock.gyms[gym.id]?.name == "New Name")
        #expect(mock.gyms[gym.id]?.workoutTypes == ["Boxing", "Spinning"])
    }

    @Test("deleteGym removes the gym document itself")
    func deleteGymRemovesGymDocument() async throws {
        let mock = MockBackendService()
        let gym = Gym(name: "Doomed Gym", ownerUID: "owner-1")
        mock.gyms[gym.id] = gym

        try await mock.deleteGym(gymId: gym.id)

        #expect(mock.gyms[gym.id] == nil)
    }

    @Test("deleteGym cascades across classes, team, members, and membership plans")
    func deleteGymCascadesAcrossSubcollections() async throws {
        let mock = MockBackendService()
        let gym = Gym(name: "Doomed Gym", ownerUID: "owner-1")
        mock.gyms[gym.id] = gym

        let gymClass = GymClass(title: "CrossFit WOD", coach: "Alex", startTime: Date().addingTimeInterval(3600))
        mock.classes[gym.id] = [gymClass.id: gymClass]
        mock.team[gym.id] = [TeamMember(id: "coach-1", firstName: "Alex", lastName: "Rivera", email: "alex@example.com", role: .coach)]
        mock.members[gym.id] = [Member(id: "member-1", name: "Jane Doe", email: "jane@example.com")]
        let plan = MembershipPlan(name: "Premium", price: 99, components: [PlanComponent(type: .unlimited, validityValue: 1, validityUnit: .months)])
        mock.membershipPlans[gym.id] = [plan.id: plan]

        try await mock.deleteGym(gymId: gym.id)

        #expect(try await mock.fetchAllClasses(gymId: gym.id).isEmpty)
        #expect(try await mock.fetchTeam(gymId: gym.id).isEmpty)
        #expect(try await mock.fetchMembers(gymId: gym.id).isEmpty)
        #expect(try await mock.fetchMembershipPlans(gymId: gym.id).isEmpty)
    }

    @Test("deleteGym removes bookings, waitlist entries, and wallet items scoped to that gym")
    func deleteGymCascadesAcrossBookingsWaitlistAndWallet() async throws {
        let mock = MockBackendService()
        let gym = Gym(name: "Doomed Gym", ownerUID: "owner-1")
        mock.gyms[gym.id] = gym
        mock.signedInUID = "member-1"

        let bookedClass = GymClass(title: "CrossFit WOD", coach: "Alex", startTime: Date().addingTimeInterval(3600), capacity: 1)
        let waitlistClass = GymClass(title: "Yoga", coach: "Sam", startTime: Date().addingTimeInterval(3600), capacity: 1)
        mock.classes[gym.id] = [bookedClass.id: bookedClass, waitlistClass.id: waitlistClass]
        mock.grantUnlimitedForTesting(gymId: gym.id, userId: "member-1")
        try await mock.book(gymId: gym.id, classId: bookedClass.id)
        // Someone else fills the waitlist class first so joining actually waitlists rather than books.
        mock.seedBookingForTesting(gymId: gym.id, classId: waitlistClass.id, userId: "other-user")
        try await mock.joinWaitlist(gymId: gym.id, classId: waitlistClass.id)

        try await mock.deleteGym(gymId: gym.id)

        #expect(try await mock.fetchUserBookings(gymId: gym.id).isEmpty)
        #expect(try await mock.fetchUserWaitlist(gymId: gym.id).isEmpty)
        #expect(try await mock.fetchActivePlans(gymId: gym.id, userId: "member-1").isEmpty)
    }

    @Test("deleteGym does not affect a different gym's data")
    func deleteGymDoesNotAffectOtherGyms() async throws {
        let mock = MockBackendService()
        let doomedGym = Gym(name: "Doomed Gym", ownerUID: "owner-1")
        let survivingGym = Gym(name: "Surviving Gym", ownerUID: "owner-2")
        mock.gyms[doomedGym.id] = doomedGym
        mock.gyms[survivingGym.id] = survivingGym

        let survivingClass = GymClass(title: "Pilates", coach: "Jordan", startTime: Date().addingTimeInterval(3600))
        mock.classes[survivingGym.id] = [survivingClass.id: survivingClass]

        try await mock.deleteGym(gymId: doomedGym.id)

        #expect(mock.gyms[survivingGym.id] != nil)
        #expect(try await mock.fetchAllClasses(gymId: survivingGym.id).count == 1)
    }

    // MARK: - createGym with an existing user

    @Test("createGym reuses an existing user's account when the owner's email is already registered")
    func createGymReusesExistingUserByEmail() async throws {
        let mock = MockBackendService()
        let existingUID = "existing-uid"
        mock.users[existingUID] = PlatformUser(id: existingUID, firstName: "Dana", lastName: "Existing", email: "dana@example.com", role: .user)

        let gym = try await mock.createGym(
            name: "Second Gym",
            ownerFirstName: "Ignored", ownerLastName: "Ignored",
            ownerEmail: "dana@example.com", ownerPassword: "irrelevant"
        )

        #expect(gym.ownerUID == existingUID)
        #expect(mock.users.count == 1, "no duplicate user record was created")
        let teamEntry = try #require(mock.team[gym.id]?.first)
        #expect(teamEntry.id == existingUID)
        #expect(teamEntry.firstName == "Dana", "uses the existing user's real name, not the (possibly stale) form input")
        #expect(teamEntry.lastName == "Existing")
        #expect(teamEntry.role == .owner)
    }

    @Test("createGym still registers a brand-new user when the owner's email isn't already registered")
    func createGymRegistersNewUserWhenEmailUnknown() async throws {
        let mock = MockBackendService()

        let gym = try await mock.createGym(
            name: "First Gym",
            ownerFirstName: "Jamie", ownerLastName: "New",
            ownerEmail: "jamie@example.com", ownerPassword: "irrelevant"
        )

        #expect(mock.users[gym.ownerUID]?.email == "jamie@example.com")
        let teamEntry = try #require(mock.team[gym.id]?.first)
        #expect(teamEntry.firstName == "Jamie")
        #expect(teamEntry.lastName == "New")
    }

    // MARK: - Self-serve Gym Creation & Join by Code

    @Test("createGymForCurrentUser creates gym with owner role and joinCode")
    func createGymForCurrentUserSetsOwnerRoleAndJoinCode() async throws {
        let mock = MockBackendService()
        let uid = "owner-uid"
        mock.signedInUID = uid
        mock.users[uid] = PlatformUser(id: uid, firstName: "Dana", lastName: "Owner", email: "dana@example.com", role: .user)

        let gym = try await mock.createGymForCurrentUser(
            name: "Iron Temple",
            city: "Tel Aviv",
            joinCode: "IRON99",
            workoutTypes: ["CrossFit", "Strength"]
        )

        #expect(gym.name == "Iron Temple")
        #expect(gym.ownerUID == uid)
        #expect(gym.joinCode == "IRON99")
        #expect(gym.city == "Tel Aviv")
        #expect(mock.userRoles[gym.id]?[uid] == .owner)
        #expect(mock.myGymsList.contains(where: { $0.gym.id == gym.id && $0.role == .owner }))
    }

    @Test("fetchGymByJoinCode finds matching gym case-insensitively")
    func fetchGymByJoinCodeFindsMatchingGymCaseInsensitively() async throws {
        let mock = MockBackendService()
        let gym = Gym(name: "CrossFit Central", ownerUID: "owner-1", joinCode: "CENTRAL10")
        mock.gyms[gym.id] = gym

        let foundUpper = try await mock.fetchGymByJoinCode(code: "CENTRAL10")
        let foundLower = try await mock.fetchGymByJoinCode(code: "central10")

        #expect(foundUpper?.id == gym.id)
        #expect(foundLower?.id == gym.id)
    }

    @Test("joinGymByCode adds user to member list and user's gyms")
    func joinGymByCodeAddsMemberToGymAndMyGyms() async throws {
        let mock = MockBackendService()
        let gym = Gym(name: "Iron Temple", ownerUID: "owner-1", joinCode: "IRON99")
        mock.gyms[gym.id] = gym

        let memberUID = "member-1"
        mock.signedInUID = memberUID
        mock.users[memberUID] = PlatformUser(id: memberUID, firstName: "Alex", lastName: "Cohen", email: "alex@example.com", role: .user)

        let joinedGym = try await mock.joinGymByCode(code: "IRON99")

        #expect(joinedGym.id == gym.id)
        #expect(mock.members[gym.id]?.contains(where: { $0.id == memberUID }) == true)
        #expect(mock.myGymsList.contains(where: { $0.gym.id == gym.id && $0.role == .member }) == true)
    }
}
