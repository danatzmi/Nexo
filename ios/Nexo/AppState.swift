//
//  AppState.swift
//  Nexo
//
//  Created by Atzmi, Dan on 25/04/2026.
//

import Foundation
import Observation

@MainActor
@Observable
final class AppState {
    var isAuthenticated = false
    var myGyms: [(gym: Gym, role: UserRole)] = []
    var currentGym: Gym?
    var gymRole: UserRole = .member
    var appRole: PlatformRole = .user
    /// Backs `MainTabView`'s `TabView` selection, so child views (e.g. a
    /// member's "Book a Class" prompt on the Home tab) can switch tabs
    /// programmatically. Tags: 0 = Home, 1 = Schedule, 2 = Logbook, 3 = Manage, 4 = Profile.
    var selectedTab: Int = 0

    /// Was `currentGym!.id`. SwiftUI can evaluate a `.navigationDestination(for:)`
    /// closure speculatively even when the content that would trigger navigation
    /// is otherwise unreachable (e.g. gated behind `if currentGym != nil`) — that
    /// speculative evaluation doesn't respect the app-level precondition this
    /// used to rely on, so a force-unwrap here could still crash. A random
    /// fallback UUID is deliberately meaningless (matches nothing real) rather
    /// than crashing; callers that need a real gym context must still check
    /// `currentGym` themselves — this only prevents the crash, not incorrect use.
    var gymId: UUID { currentGym?.id ?? UUID() }
    var isAdmin: Bool { appRole == .admin }

    /// Unified class-management permission that also covers platform admins
    /// visiting a gym they don't otherwise have a role in — mirrors the
    /// backend bypass in `FirebaseBackend.validateAndConsumeMembership`.
    var canManageClasses: Bool {
        isAdmin || gymRole == .owner || gymRole == .coach
    }

    private let lastGymKey = "lastGymId"

    func enter(gym: Gym, role: UserRole) {
        currentGym = gym
        gymRole = role
        UserDefaults.standard.set(gym.id.uuidString, forKey: lastGymKey)
    }

    func leaveGym() {
        currentGym = nil
        gymRole = .member
        selectedTab = 0
    }

    func autoSelectGym() {
        guard currentGym == nil, !myGyms.isEmpty else { return }

        if let savedId = UserDefaults.standard.string(forKey: lastGymKey),
           let savedUUID = UUID(uuidString: savedId),
           let entry = myGyms.first(where: { $0.gym.id == savedUUID }) {
            enter(gym: entry.gym, role: entry.role)
        } else if myGyms.count == 1 {
            enter(gym: myGyms[0].gym, role: myGyms[0].role)
        }
    }

    /// Re-fetches `myGyms` and auto-selects if it's no longer empty —
    /// backs the "Check Again" button on `GymPickerView`'s awaiting-
    /// enrollment screen, for a user whose owner just added them.
    func refreshMyGyms() async {
        myGyms = await resolveMyGyms(role: appRole, backend: FirebaseBackend.shared)
        autoSelectGym()
    }

    func signOut() {
        try? FirebaseBackend.shared.signOut()
        isAuthenticated = false
        currentGym = nil
        gymRole = .member
        appRole = .user
        myGyms = []
        selectedTab = 0
    }
}
