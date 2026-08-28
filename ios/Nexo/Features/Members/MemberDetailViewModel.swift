//
//  MemberDetailViewModel.swift
//  Nexo
//

import Foundation

@MainActor
@Observable
final class MemberDetailViewModel {
    private let backend: BackendService
    let gymId: UUID
    let member: Member

    var bookings: [GymClass] = []
    var isLoading = false
    var errorMessage: String?
    /// The member's credit wallet — one item per granted plan component.
    var activePlans: [ActivePlanItem] = []
    /// The gym's plan templates, for the Grant Plan picker.
    var availablePlans: [MembershipPlan] = []

    var upcomingBookings: [GymClass] {
        bookings.filter { $0.startTime >= Date() }.sorted { $0.startTime < $1.startTime }
    }

    var pastBookings: [GymClass] {
        bookings.filter { $0.startTime < Date() }
    }

    init(gymId: UUID, member: Member, backend: BackendService? = nil) {
        self.gymId = gymId
        self.member = member
        self.backend = backend ?? FirebaseBackend.shared
    }

    func loadBookings() async {
        isLoading = true
        do {
            bookings = try await backend.fetchMemberBookings(gymId: gymId, userId: member.id)
        } catch {
            errorMessage = "Error loading bookings: \(error.localizedDescription)"
        }
        isLoading = false
    }

    func cancelBooking(_ gymClass: GymClass) async {
        do {
            try await backend.cancelBooking(gymId: gymId, classId: gymClass.id, onBehalfOf: member.id)
            bookings.removeAll { $0.id == gymClass.id }
        } catch {
            errorMessage = "Failed to cancel booking: \(error.localizedDescription)"
        }
    }

    // MARK: - Membership Plans & Credit Wallet

    func loadWallet() async {
        do {
            activePlans = try await backend.fetchActivePlans(gymId: gymId, userId: member.id)
        } catch {
            errorMessage = "Error loading wallet: \(error.localizedDescription)"
        }
    }

    func loadAvailablePlans() async {
        do {
            availablePlans = try await backend.fetchMembershipPlans(gymId: gymId)
        } catch {
            errorMessage = "Error loading plans: \(error.localizedDescription)"
        }
    }

    func grantPlan(_ plan: MembershipPlan, customExpiresAt: Date? = nil) async {
        do {
            try await backend.grantPlanToMember(gymId: gymId, userId: member.id, plan: plan, customExpiresAt: customExpiresAt)
            await loadWallet()
        } catch {
            errorMessage = "Failed to grant plan: \(error.localizedDescription)"
        }
    }

    func revokeActivePlan(_ item: ActivePlanItem) async {
        do {
            try await backend.revokeActivePlan(gymId: gymId, userId: member.id, activePlanId: item.id)
            activePlans.removeAll { $0.id == item.id }
        } catch {
            errorMessage = "Failed to revoke plan: \(error.localizedDescription)"
        }
    }

    /// Removes the member from the gym entirely. Returns whether it succeeded,
    /// so the view knows whether to dismiss.
    func removeMember() async -> Bool {
        do {
            try await backend.removeMember(gymId: gymId, userId: member.id)
            return true
        } catch {
            errorMessage = "Failed to remove member: \(error.localizedDescription)"
            return false
        }
    }
}
