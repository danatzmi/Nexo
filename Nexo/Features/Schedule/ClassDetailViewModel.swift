//
//  ClassDetailViewModel.swift
//  Nexo
//

import Foundation

@MainActor
@Observable
final class ClassDetailViewModel {
    private let backend: BackendService
    let gymId: UUID
    let gymClass: GymClass

    var workout: Workout?
    var isLoadingWorkout = false
    var attendees: [Member] = []
    var isLoadingAttendees = false
    var errorMessage: String?
    /// Flips true once a delete succeeds — the view observes this to dismiss itself.
    var didDelete = false

    init(gymId: UUID, gymClass: GymClass, backend: BackendService? = nil) {
        self.gymId = gymId
        self.gymClass = gymClass
        self.backend = backend ?? FirebaseBackend.shared
    }

    func loadWorkout() async {
        guard let workoutId = gymClass.workoutId else { return }
        isLoadingWorkout = true
        do {
            workout = try await backend.fetchWorkout(gymId: gymId, id: workoutId)
        } catch {
            errorMessage = "Error loading workout: \(error.localizedDescription)"
        }
        isLoadingWorkout = false
    }

    func loadAttendees() async {
        isLoadingAttendees = true
        do {
            attendees = try await backend.fetchAttendees(gymId: gymId, classId: gymClass.id)
        } catch {
            errorMessage = "Error loading attendees: \(error.localizedDescription)"
        }
        isLoadingAttendees = false
    }

    func deleteClass() async {
        do {
            try await backend.deleteClass(gymId: gymId, classId: gymClass.id)
            didDelete = true
        } catch {
            errorMessage = "Failed to delete class: \(error.localizedDescription)"
        }
    }

    func deleteSeries() async {
        guard let seriesId = gymClass.seriesId else { return }
        do {
            try await backend.deleteClassSeries(gymId: gymId, seriesId: seriesId, from: gymClass.startTime)
            didDelete = true
        } catch {
            errorMessage = "Failed to delete series: \(error.localizedDescription)"
        }
    }
}
