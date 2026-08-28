//
//  GymSwitcherResolutionTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

@MainActor
@Suite("Gym Switcher Resolution")
struct GymSwitcherResolutionTests {
    @Test("Platform admins load every available gym as switcher entries, each as owner")
    func adminLoadsAllAvailableGyms() async {
        let mock = MockBackendService()
        let gymA = Gym(name: "Gym A", ownerUID: "owner-a")
        let gymB = Gym(name: "Gym B", ownerUID: "owner-b")
        mock.gyms[gymA.id] = gymA
        mock.gyms[gymB.id] = gymB
        // Deliberately left empty/unrelated to prove admins bypass explicit memberships entirely.
        mock.myGymsList = []

        let result = await resolveMyGyms(role: .admin, backend: mock)

        #expect(result.count == 2)
        #expect(result.allSatisfy { $0.role == .owner })
        #expect(Set(result.map { $0.gym.id }) == Set([gymA.id, gymB.id]))
    }

    @Test("Non-admins load only their explicit gym memberships, with their real role")
    func nonAdminLoadsOwnMembershipsOnly() async {
        let mock = MockBackendService()
        let gym = Gym(name: "My Gym", ownerUID: "owner-1")
        mock.myGymsList = [(gym: gym, role: .coach)]
        // Deliberately populated to prove non-admins don't see every gym in the system.
        let otherGym = Gym(name: "Someone Else's Gym", ownerUID: "owner-2")
        mock.gyms[gym.id] = gym
        mock.gyms[otherGym.id] = otherGym

        let result = await resolveMyGyms(role: .user, backend: mock)

        #expect(result.count == 1)
        #expect(result.first?.gym.id == gym.id)
        #expect(result.first?.role == .coach)
    }
}
