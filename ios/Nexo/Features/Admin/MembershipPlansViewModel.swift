//
//  MembershipPlansViewModel.swift
//  Nexo
//

import Foundation

@MainActor
@Observable
final class MembershipPlansViewModel {
    private let backend: BackendService
    let gymId: UUID

    var plans: [MembershipPlan] = []
    var isLoading = false
    var errorMessage: String?

    init(gymId: UUID, backend: BackendService? = nil) {
        self.gymId = gymId
        self.backend = backend ?? FirebaseBackend.shared
    }

    func loadPlans() async {
        isLoading = true
        do {
            plans = try await backend.fetchMembershipPlans(gymId: gymId)
        } catch {
            errorMessage = "Error loading plans: \(error.localizedDescription)"
        }
        isLoading = false
    }

    func createPlan(_ plan: MembershipPlan) async {
        do {
            try await backend.createMembershipPlan(gymId: gymId, plan: plan)
            await loadPlans()
        } catch {
            errorMessage = "Failed to create plan: \(error.localizedDescription)"
        }
    }

    func updatePlan(_ plan: MembershipPlan) async {
        do {
            try await backend.updateMembershipPlan(gymId: gymId, plan: plan)
            if let index = plans.firstIndex(where: { $0.id == plan.id }) {
                plans[index] = plan
            }
        } catch {
            errorMessage = "Failed to update plan: \(error.localizedDescription)"
        }
    }

    func deletePlan(_ planId: UUID) async {
        do {
            try await backend.deleteMembershipPlan(gymId: gymId, planId: planId)
            plans.removeAll { $0.id == planId }
        } catch {
            errorMessage = "Failed to delete plan: \(error.localizedDescription)"
        }
    }
}
