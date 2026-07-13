//
//  AppState.swift
//  Nexo
//
//  Created by Atzmi, Dan on 25/04/2026.
//

import Foundation
import Observation

@Observable
final class AppState {
    var isAuthenticated = false
    var myGyms: [(gym: Gym, role: UserRole)] = []
    var currentGym: Gym?
    var gymRole: UserRole = .member
    var appRole: PlatformRole = .user

    var gymId: UUID { currentGym!.id }
    var isAdmin: Bool { appRole == .admin }

    private let lastGymKey = "lastGymId"

    func enter(gym: Gym, role: UserRole) {
        currentGym = gym
        gymRole = role
        UserDefaults.standard.set(gym.id.uuidString, forKey: lastGymKey)
    }

    func leaveGym() {
        currentGym = nil
        gymRole = .member
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

    func signOut() {
        try? FirebaseBackend.shared.signOut()
        isAuthenticated = false
        currentGym = nil
        gymRole = .member
        appRole = .user
        myGyms = []
    }
}
