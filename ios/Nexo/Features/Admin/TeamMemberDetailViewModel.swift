//
//  TeamMemberDetailViewModel.swift
//  Nexo
//

import Foundation

@MainActor
@Observable
final class TeamMemberDetailViewModel {
    private let backend: BackendService
    let gymId: UUID
    let member: TeamMember

    var errorMessage: String?

    /// The safety gate in `FEEDBACK.md` for this feature: an owner/admin can't
    /// demote or remove themselves via this screen, which would otherwise risk
    /// locking a gym without any owner/coach able to manage it.
    var isSelf: Bool { member.id == backend.currentUID() }

    init(gymId: UUID, member: TeamMember, backend: BackendService? = nil) {
        self.gymId = gymId
        self.member = member
        self.backend = backend ?? FirebaseBackend.shared
    }

    func updateRole(_ role: UserRole) async {
        do {
            try await backend.updateTeamMemberRole(gymId: gymId, userId: member.id, role: role)
        } catch {
            errorMessage = "Failed to update role: \(error.localizedDescription)"
        }
    }

    /// Removes the team member from the gym. Returns whether it succeeded, so
    /// the view knows whether to dismiss.
    func removeTeamMember() async -> Bool {
        do {
            try await backend.removeTeamMember(gymId: gymId, userId: member.id)
            return true
        } catch {
            errorMessage = "Failed to remove team member: \(error.localizedDescription)"
            return false
        }
    }
}
