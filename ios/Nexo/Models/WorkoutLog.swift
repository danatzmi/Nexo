//
//  WorkoutLog.swift
//  Nexo
//

import Foundation

/// A single logged entry for any activity or movement — e.g. "Squat, 100kg
/// x 5 x 3 sets" or "Yoga Flow" with no score at all. Stored per-member at
/// `gyms/{gymId}/members/{userId}/workoutLogs/{id}` — not shared across the
/// gym like `GymClass`/`Member`, since this is personal training data.
/// `score`/`reps`/`sets` are all optional since not every gym type or
/// activity is meaningfully expressed as weight-lifted-for-reps.
struct WorkoutLog: Identifiable, Codable, Hashable {
    let id: String
    let movement: String
    let score: Double?
    let reps: Int?
    let sets: Int?
    let date: Date

    /// The value line for this entry:
    /// - If sets and reps are present with score: "<sets> × <reps> @ <score>"
    /// - If sets and reps are present without score: "<sets> × <reps>"
    /// - If sets or reps are not present: just the score (or reps/sets if scoreless, falling back to "Logged")
    var formattedDetail: String {
        if let sets, let reps {
            if let score {
                return "\(sets) × \(reps) @ \(score.formatted())"
            } else {
                return "\(sets) × \(reps)"
            }
        } else if let score {
            return score.formatted()
        } else if let reps {
            return "\(reps) reps"
        } else if let sets {
            return "\(sets) sets"
        } else {
            return "Logged"
        }
    }
}
