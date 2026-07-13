//
//  WorkoutsLibraryViewModelTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

@MainActor
@Suite("WorkoutsLibraryViewModel")
struct WorkoutsLibraryViewModelTests {
    private func makeSUT() -> (viewModel: WorkoutsLibraryViewModel, mock: MockBackendService, gymId: UUID) {
        let mock = MockBackendService()
        let gymId = UUID()
        let viewModel = WorkoutsLibraryViewModel(gymId: gymId, backend: mock)
        return (viewModel, mock, gymId)
    }

    @Test("loadWorkouts populates workouts sorted by name")
    func loadWorkoutsSortsByName() async {
        let (viewModel, mock, gymId) = makeSUT()
        let zebra = Workout(name: "Zebra WOD", type: .crossfit, description: "")
        let apple = Workout(name: "Apple WOD", type: .yoga, description: "")
        mock.workouts[gymId] = [zebra.id: zebra, apple.id: apple]

        await viewModel.loadWorkouts()

        #expect(viewModel.workouts.map(\.name) == ["Apple WOD", "Zebra WOD"])
        #expect(viewModel.errorMessage == nil)
    }

    @Test("loadWorkouts sets errorMessage on failure")
    func loadWorkoutsFailure() async {
        let (viewModel, mock, _) = makeSUT()
        mock.errorToThrow = MockBackendError.injected

        await viewModel.loadWorkouts()

        #expect(viewModel.errorMessage != nil)
        #expect(viewModel.workouts.isEmpty)
    }

    @Test("filteredWorkouts filters by selectedType")
    func filteredWorkoutsByType() async {
        let (viewModel, mock, gymId) = makeSUT()
        let crossfit = Workout(name: "Fran", type: .crossfit, description: "")
        let yoga = Workout(name: "Morning Flow", type: .yoga, description: "")
        mock.workouts[gymId] = [crossfit.id: crossfit, yoga.id: yoga]
        await viewModel.loadWorkouts()

        viewModel.selectedType = .yoga

        #expect(viewModel.filteredWorkouts.map(\.id) == [yoga.id])
    }

    @Test("filteredWorkouts filters by searchText across name and description")
    func filteredWorkoutsBySearchText() async {
        let (viewModel, mock, gymId) = makeSUT()
        let fran = Workout(name: "Fran", type: .crossfit, description: "21-15-9 thrusters and pull-ups")
        let murph = Workout(name: "Murph", type: .crossfit, description: "Hero WOD")
        mock.workouts[gymId] = [fran.id: fran, murph.id: murph]
        await viewModel.loadWorkouts()

        viewModel.searchText = "thrusters"

        #expect(viewModel.filteredWorkouts.map(\.id) == [fran.id])
    }

    @Test("filteredWorkouts combines type and search filters")
    func filteredWorkoutsCombinesFilters() async {
        let (viewModel, mock, gymId) = makeSUT()
        let fran = Workout(name: "Fran", type: .crossfit, description: "")
        let murph = Workout(name: "Murph", type: .crossfit, description: "")
        let yogaFlow = Workout(name: "Fran's Flow", type: .yoga, description: "")
        mock.workouts[gymId] = [fran.id: fran, murph.id: murph, yogaFlow.id: yogaFlow]
        await viewModel.loadWorkouts()

        viewModel.selectedType = .crossfit
        viewModel.searchText = "fran"

        #expect(viewModel.filteredWorkouts.map(\.id) == [fran.id])
    }
}
