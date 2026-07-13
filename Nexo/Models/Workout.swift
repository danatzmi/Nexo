//
//  Workout.swift
//  Nexo
//
//  Created by Atzmi, Dan on 22/04/2026.
//

import Foundation

struct Workout: Identifiable, Hashable, Codable {
    let id: UUID
    var name: String
    var type: WorkoutType
    var description: String
    var exercises: [Exercise]
    var durationMinutes: Int
    var difficulty: Difficulty
    
    init(
        id: UUID = UUID(),
        name: String,
        type: WorkoutType,
        description: String,
        exercises: [Exercise] = [],
        durationMinutes: Int = 60,
        difficulty: Difficulty = .intermediate
    ) {
        self.id = id
        self.name = name
        self.type = type
        self.description = description
        self.exercises = exercises
        self.durationMinutes = durationMinutes
        self.difficulty = difficulty
    }
}

// MARK: - Workout Type

enum WorkoutType: String, Codable, CaseIterable {
    case crossfit = "CrossFit WOD"
    case hiit = "HIIT"
    case yoga = "Yoga"
    case pilates = "Pilates"
    case strength = "Strength Training"
    case cardio = "Cardio"
    
    var icon: String {
        switch self {
        case .crossfit: return "figure.strengthtraining.traditional"
        case .hiit: return "bolt.fill"
        case .yoga: return "figure.mind.and.body"
        case .pilates: return "figure.pilates"
        case .strength: return "dumbbell.fill"
        case .cardio: return "heart.fill"
        }
    }
}

// MARK: - Difficulty

enum Difficulty: String, Codable, CaseIterable {
    case beginner = "Beginner"
    case intermediate = "Intermediate"
    case advanced = "Advanced"
    
    var color: String {
        switch self {
        case .beginner: return "green"
        case .intermediate: return "orange"
        case .advanced: return "red"
        }
    }
}

// MARK: - Exercise

struct Exercise: Identifiable, Hashable, Codable {
    let id: UUID
    var name: String
    var reps: String? // "21-15-9" or "50" or "AMRAP"
    var duration: String? // "2 minutes" or "30 seconds"
    var notes: String?
    
    init(
        id: UUID = UUID(),
        name: String,
        reps: String? = nil,
        duration: String? = nil,
        notes: String? = nil
    ) {
        self.id = id
        self.name = name
        self.reps = reps
        self.duration = duration
        self.notes = notes
    }
}

// MARK: - Sample Workouts

extension Workout {
    static let sampleFran = Workout(
        name: "Fran",
        type: .crossfit,
        description: "21-15-9 reps for time",
        exercises: [
            Exercise(name: "Thrusters", reps: "21-15-9", notes: "95/65 lbs"),
            Exercise(name: "Pull-ups", reps: "21-15-9")
        ],
        durationMinutes: 20,
        difficulty: .advanced
    )
    
    static let sampleMurph = Workout(
        name: "Murph",
        type: .crossfit,
        description: "Hero WOD - For time with 20lb vest",
        exercises: [
            Exercise(name: "Run", duration: "1 mile"),
            Exercise(name: "Pull-ups", reps: "100"),
            Exercise(name: "Push-ups", reps: "200"),
            Exercise(name: "Air Squats", reps: "300"),
            Exercise(name: "Run", duration: "1 mile")
        ],
        durationMinutes: 60,
        difficulty: .advanced
    )
    
    static let sampleHIIT = Workout(
        name: "Cardio Blast",
        type: .hiit,
        description: "High intensity intervals",
        exercises: [
            Exercise(name: "Burpees", duration: "30 seconds"),
            Exercise(name: "Rest", duration: "15 seconds"),
            Exercise(name: "Mountain Climbers", duration: "30 seconds"),
            Exercise(name: "Rest", duration: "15 seconds"),
            Exercise(name: "Jump Squats", duration: "30 seconds"),
            Exercise(name: "Rest", duration: "15 seconds")
        ],
        durationMinutes: 30,
        difficulty: .intermediate
    )
    
    static let sampleYoga = Workout(
        name: "Morning Flow",
        type: .yoga,
        description: "Energizing vinyasa flow",
        exercises: [
            Exercise(name: "Sun Salutation A", reps: "5 rounds"),
            Exercise(name: "Sun Salutation B", reps: "5 rounds"),
            Exercise(name: "Warrior Sequence", duration: "10 minutes"),
            Exercise(name: "Cool Down", duration: "5 minutes")
        ],
        durationMinutes: 60,
        difficulty: .beginner
    )
    
    static let samples = [sampleFran, sampleMurph, sampleHIIT, sampleYoga]
}
