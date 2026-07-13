//
//  ClassDetailViewModelTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

@MainActor
@Suite("ClassDetailViewModel")
struct ClassDetailViewModelTests {
    private func makeSUT(workoutId: UUID? = nil, seriesId: UUID? = nil) -> (viewModel: ClassDetailViewModel, mock: MockBackendService, gymId: UUID) {
        let mock = MockBackendService()
        let gymId = UUID()
        let gymClass = GymClass(title: "Morning HIIT", coach: "Alex", startTime: Date(), workoutId: workoutId, seriesId: seriesId)
        mock.classes[gymId] = [gymClass.id: gymClass]
        let viewModel = ClassDetailViewModel(gymId: gymId, gymClass: gymClass, backend: mock)
        return (viewModel, mock, gymId)
    }

    @Test("loadWorkout populates workout on success")
    func loadWorkoutSuccess() async {
        let workoutId = UUID()
        let (viewModel, mock, gymId) = makeSUT(workoutId: workoutId)
        let workout = Workout(id: workoutId, name: "Fran", type: .crossfit, description: "21-15-9")
        mock.workouts[gymId] = [workoutId: workout]

        await viewModel.loadWorkout()

        #expect(viewModel.workout?.id == workoutId)
        #expect(viewModel.errorMessage == nil)
    }

    @Test("loadWorkout sets errorMessage on failure")
    func loadWorkoutFailure() async {
        let (viewModel, mock, _) = makeSUT(workoutId: UUID())
        mock.errorToThrow = MockBackendError.injected

        await viewModel.loadWorkout()

        #expect(viewModel.errorMessage != nil)
        #expect(viewModel.workout == nil)
    }

    @Test("loadAttendees populates attendees on success")
    func loadAttendeesSuccess() async throws {
        let (viewModel, mock, gymId) = makeSUT()
        mock.signedInUID = "member-1"
        mock.users["member-1"] = PlatformUser(id: "member-1", firstName: "Jane", lastName: "Doe", email: "jane@example.com", role: .user)
        try await mock.book(gymId: gymId, classId: viewModel.gymClass.id)

        await viewModel.loadAttendees()

        #expect(viewModel.attendees.count == 1)
        #expect(viewModel.errorMessage == nil)
    }

    @Test("loadAttendees sets errorMessage on failure")
    func loadAttendeesFailure() async {
        let (viewModel, mock, _) = makeSUT()
        mock.errorToThrow = MockBackendError.injected

        await viewModel.loadAttendees()

        #expect(viewModel.errorMessage != nil)
        #expect(viewModel.attendees.isEmpty)
    }

    @Test("deleteClass succeeds and flips didDelete")
    func deleteClassSuccess() async {
        let (viewModel, mock, gymId) = makeSUT()

        await viewModel.deleteClass()

        #expect(viewModel.didDelete)
        #expect(viewModel.errorMessage == nil)
        #expect(mock.classes[gymId]?[viewModel.gymClass.id] == nil)
    }

    @Test("deleteClass failure sets errorMessage and leaves didDelete false")
    func deleteClassFailure() async {
        let (viewModel, mock, _) = makeSUT()
        mock.errorToThrow = MockBackendError.injected

        await viewModel.deleteClass()

        #expect(viewModel.didDelete == false)
        #expect(viewModel.errorMessage != nil)
    }

    @Test("deleteSeries succeeds and flips didDelete")
    func deleteSeriesSuccess() async {
        let seriesId = UUID()
        let (viewModel, _, _) = makeSUT(seriesId: seriesId)

        await viewModel.deleteSeries()

        #expect(viewModel.didDelete)
    }
}
