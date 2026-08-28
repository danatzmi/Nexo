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
    var seriesId: UUID?
    /// What kind of class this is — one of the gym's own `Gym.workoutTypes`
    /// category names. Purely descriptive/filterable; the credit wallet
    /// (`ActivePlanItem`) no longer gates bookings by class type.
    var classType: String
    /// Marked in the class editor as "Requires Additional Pay". Currently
    /// display-only — the simplified credit wallet (see `MembershipPlan`)
    /// authorizes a booking purely from plan state (unlimited vs. credits),
    /// with no per-class gating, so this no longer restricts who can book.
    var isPremium: Bool
    /// Free-text description of what this class slot is about — e.g. "Open
    /// gym, bring your own program" or today's programming notes.
    var description: String

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
        seriesId: UUID? = nil,
        classType: String = "CrossFit WOD",
        isPremium: Bool = false,
        description: String = ""
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
        self.seriesId = seriesId
        self.classType = classType
        self.isPremium = isPremium
        self.description = description
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
