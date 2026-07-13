//
//  GymMembersViewModel.swift
//  Nexo
//

import Foundation

@MainActor
@Observable
final class GymMembersViewModel {
    private let backend: BackendService
    let gymId: UUID

    var members: [Member] = []
    var isLoading = false
    var errorMessage: String?
    var searchText = ""

    var filteredMembers: [Member] {
        guard !searchText.isEmpty else { return members }
        return members.filter {
            $0.name.localizedCaseInsensitiveContains(searchText) ||
            $0.email.localizedCaseInsensitiveContains(searchText)
        }
    }

    init(gymId: UUID, backend: BackendService? = nil) {
        self.gymId = gymId
        self.backend = backend ?? FirebaseBackend.shared
    }

    func loadMembers() async {
        isLoading = true
        do {
            members = try await backend.fetchMembers(gymId: gymId)
        } catch {
            errorMessage = "Error loading members: \(error.localizedDescription)"
        }
        isLoading = false
    }
}
