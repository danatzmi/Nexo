//
//  GymClass.swift
//  Nexo
//
//  Created by Atzmi, Dan on 20/04/2026.
//

import Foundation

struct GymClass: Identifiable, Hashable {
    let id: UUID
    var title: String
    var coach: String
    var startTime: Date
    var durationMinutes: Int
    var capacity: Int
    var currentAttendees: Int
    var waitlistCount: Int
    var attendees: [Member]
    var workoutId: UUID?
    var seriesId: UUID?

    init(
        id: UUID = UUID(),
        title: String,
        coach: String,
        startTime: Date,
        durationMinutes: Int = 60,
        capacity: Int = 12,
        currentAttendees: Int = 0,
        waitlistCount: Int = 0,
        attendees: [Member] = [],
        workoutId: UUID? = nil,
        seriesId: UUID? = nil
    ) {
        self.id = id
        self.title = title
        self.coach = coach
        self.startTime = startTime
        self.durationMinutes = durationMinutes
        self.capacity = capacity
        self.currentAttendees = currentAttendees
        self.waitlistCount = waitlistCount
        self.attendees = attendees
        self.workoutId = workoutId
        self.seriesId = seriesId
    }
}

// MARK: - Computed Properties

extension GymClass {
    var isFull: Bool {
        currentAttendees >= capacity
    }
    
    var availableSpots: Int {
        max(0, capacity - currentAttendees)
    }
    
    var formattedTime: String {
        startTime.formatted(date: .omitted, time: .shortened)
    }
    
    var formattedDateTime: String {
        startTime.formatted(date: .abbreviated, time: .shortened)
    }
}
