//
//  MembershipPlansViewModelTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

@MainActor
@Suite("MembershipPlansViewModel")
struct MembershipPlansViewModelTests {
    private func makeSUT() -> (viewModel: MembershipPlansViewModel, mock: MockBackendService, gymId: UUID) {
        let mock = MockBackendService()
        let gymId = UUID()
        let viewModel = MembershipPlansViewModel(gymId: gymId, backend: mock)
        return (viewModel, mock, gymId)
    }

    @Test("updatePlan persists the change and updates the plan in place, without a full reload")
    func updatePlanReloadsWithChanges() async {
        let (viewModel, mock, gymId) = makeSUT()
        let original = MembershipPlan(name: "Basic", price: 49, components: [PlanComponent(type: .unlimited, validityValue: 1, validityUnit: .months)])
        mock.membershipPlans[gymId] = [original.id: original]
        await viewModel.loadPlans()

        let updated = MembershipPlan(
            id: original.id,
            name: "10-Class Pass",
            price: 150,
            components: [PlanComponent(type: .credits, creditCount: 10, validityValue: 3, validityUnit: .months)]
        )
        await viewModel.updatePlan(updated)

        #expect(viewModel.plans.count == 1)
        #expect(viewModel.plans.first?.name == "10-Class Pass")
        #expect(viewModel.plans.first?.price == 150)
        #expect(viewModel.errorMessage == nil)
    }

    @Test("updatePlan sets errorMessage on failure")
    func updatePlanFailure() async {
        let (viewModel, mock, gymId) = makeSUT()
        let plan = MembershipPlan(name: "Basic", price: 49, components: [PlanComponent(type: .unlimited, validityValue: 1, validityUnit: .months)])
        mock.membershipPlans[gymId] = [plan.id: plan]
        await viewModel.loadPlans()
        mock.errorToThrow = MockBackendError.injected

        await viewModel.updatePlan(plan)

        #expect(viewModel.errorMessage != nil)
    }

    @Test("deletePlan removes exactly that plan from state")
    func deletePlanUpdatesState() async {
        let (viewModel, mock, gymId) = makeSUT()
        let toDelete = MembershipPlan(name: "Basic", price: 49, components: [PlanComponent(type: .unlimited, validityValue: 1, validityUnit: .months)])
        let toKeep = MembershipPlan(name: "Premium", price: 99, components: [PlanComponent(type: .unlimited, validityValue: 1, validityUnit: .months)])
        mock.membershipPlans[gymId] = [toDelete.id: toDelete, toKeep.id: toKeep]
        await viewModel.loadPlans()

        await viewModel.deletePlan(toDelete.id)

        #expect(viewModel.plans.map(\.id) == [toKeep.id])
        #expect(viewModel.errorMessage == nil)
    }

    @Test("deletePlan sets errorMessage on failure and leaves state untouched")
    func deletePlanFailure() async {
        let (viewModel, mock, gymId) = makeSUT()
        let plan = MembershipPlan(name: "Basic", price: 49, components: [PlanComponent(type: .unlimited, validityValue: 1, validityUnit: .months)])
        mock.membershipPlans[gymId] = [plan.id: plan]
        await viewModel.loadPlans()
        mock.errorToThrow = MockBackendError.injected

        await viewModel.deletePlan(plan.id)

        #expect(viewModel.errorMessage != nil)
        #expect(viewModel.plans.count == 1)
    }
}

