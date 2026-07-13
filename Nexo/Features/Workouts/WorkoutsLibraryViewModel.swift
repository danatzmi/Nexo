//
//  WorkoutsLibraryViewModel.swift
//  Nexo
//

import Foundation

@MainActor
@Observable
final class WorkoutsLibraryViewModel {
    private let backend: BackendService
    let gymId: UUID

    var workouts: [Workout] = []
    var isLoading = false
    var errorMessage: String?
    var searchText = ""
    var selectedType: WorkoutType?

    var filteredWorkouts: [Workout] {
        var filtered = workouts

        if let selectedType {
            filtered = filtered.filter { $0.type == selectedType }
        }

        if !searchText.isEmpty {
            filtered = filtered.filter {
                $0.name.localizedCaseInsensitiveContains(searchText) ||
                $0.description.localizedCaseInsensitiveContains(searchText)
            }
        }

        return filtered
    }

    init(gymId: UUID, backend: BackendService? = nil) {
        self.gymId = gymId
        self.backend = backend ?? FirebaseBackend.shared
    }

    func loadWorkouts() async {
        isLoading = true
        errorMessage = nil
        do {
            workouts = try await backend.fetchWorkouts(gymId: gymId).sorted { $0.name < $1.name }
        } catch {
            errorMessage = "Error loading workouts: \(error.localizedDescription)"
        }
        isLoading = false
    }
}
