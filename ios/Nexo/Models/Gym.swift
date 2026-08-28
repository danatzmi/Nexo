//
//  Gym.swift
//  Nexo
//
//  Created by Atzmi, Dan on 25/04/2026.
//

import Foundation

struct Gym: Identifiable, Hashable, Codable {
    let id: UUID
    var name: String
    var ownerUID: String
    /// This gym's own set of class/workout categories (e.g. "CrossFit WOD",
    /// "Yoga") — every gym can customize this list rather than being locked
    /// to a fixed set. Seeded with `WorkoutCategory.defaults` for new gyms.
    var workoutTypes: [String]
    /// Short unique alphanumeric join code (e.g. "IRON99") for one-tap member onboarding.
    var joinCode: String?
    /// Physical location / city (e.g. "Tel Aviv, Israel").
    var city: String?

    init(
        id: UUID = UUID(),
        name: String,
        ownerUID: String,
        workoutTypes: [String] = WorkoutCategory.defaults,
        joinCode: String? = nil,
        city: String? = nil
    ) {
        self.id = id
        self.name = name
        self.ownerUID = ownerUID
        self.workoutTypes = workoutTypes
        self.joinCode = joinCode
        self.city = city
    }
}
