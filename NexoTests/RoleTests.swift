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
}
