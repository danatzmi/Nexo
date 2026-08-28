//
//  RoleTests.swift
//  NexoTests
//

import Testing
@testable import Nexo

@Suite("Roles")
struct RoleTests {
    @Test("canManageClasses is true for owner and coach, false for member", arguments: UserRole.allCases)
    func canManageClassesPermissions(role: UserRole) {
        let expected = (role == .owner || role == .coach)
        #expect(role.canManageClasses == expected)
    }

    @Test("AppState.isAdmin reflects the platform role", arguments: [PlatformRole.admin, PlatformRole.user])
    @MainActor
    func platformRoleAdminCheck(role: PlatformRole) {
        let appState = AppState()
        appState.appRole = role
        #expect(appState.isAdmin == (role == .admin))
    }

    @Test(
        "AppState.canManageClasses is true for platform admins, gym owners, and gym coaches; false for regular members",
        arguments: [
            (appRole: PlatformRole.admin, gymRole: UserRole.member, expected: true),
            (appRole: PlatformRole.user, gymRole: UserRole.owner, expected: true),
            (appRole: PlatformRole.user, gymRole: UserRole.coach, expected: true),
            (appRole: PlatformRole.user, gymRole: UserRole.member, expected: false)
        ]
    )
    @MainActor
    func canManageClassesHierarchy(appRole: PlatformRole, gymRole: UserRole, expected: Bool) {
        let appState = AppState()
        appState.appRole = appRole
        appState.gymRole = gymRole
        #expect(appState.canManageClasses == expected)
    }

    @Test("AppState.gymId doesn't crash when no gym is selected — returns a fallback UUID instead of force-unwrapping")
    @MainActor
    func gymIdFallsBackWithoutCrashingWhenNoGymSelected() {
        let appState = AppState()
        appState.currentGym = nil

        _ = appState.gymId // must not crash

        let gym = Gym(name: "Test Gym", ownerUID: "owner-1")
        appState.currentGym = gym
        #expect(appState.gymId == gym.id, "with a real gym selected, gymId still returns its actual id")
    }
}
