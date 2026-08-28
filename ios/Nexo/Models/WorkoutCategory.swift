//
//  WorkoutCategory.swift
//  Nexo
//
//  Created by Atzmi, Dan on 08/05/2026.
//

import Foundation

/// Class/workout categories are gym-defined strings (`Gym.workoutTypes`),
/// not a fixed enum — every gym can add its own (e.g. "Boxing"). This
/// provides the seed list for newly created gyms and an icon fallback for
/// names this app doesn't recognize.
enum WorkoutCategory {
    static let defaults = ["CrossFit WOD", "HIIT", "Strength Training", "Cardio", "Yoga", "Pilates"]
}

extension String {
    /// Maps a class/workout category name to an SF Symbol, falling back to a
    /// generic fitness icon for gym-defined custom categories with no known mapping.
    var categoryIcon: String {
        switch self {
        case "CrossFit WOD": return "figure.strengthtraining.traditional"
        case "HIIT": return "bolt.fill"
        case "Yoga": return "figure.mind.and.body"
        case "Pilates": return "figure.pilates"
        case "Strength Training": return "dumbbell.fill"
        case "Cardio": return "heart.fill"
        default: return "figure.run"
        }
    }
}
