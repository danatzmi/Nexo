//
//  GymMembersViewModelTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

@MainActor
@Suite("GymMembersViewModel")
struct GymMembersViewModelTests {
    private func makeSUT() -> (viewModel: GymMembersViewModel, mock: MockBackendService, gymId: UUID) {
        let mock = MockBackendService()
        let gymId = UUID()
        let viewModel = GymMembersViewModel(gymId: gymId, backend: mock)
        return (viewModel, mock, gymId)
    }

    @Test("loadMembers populates members on success")
    func loadMembersSuccess() async {
        let (viewModel, mock, gymId) = makeSUT()
        mock.members[gymId] = [
            Member(id: "1", name: "Jane Doe", email: "jane@example.com"),
            Member(id: "2", name: "John Smith", email: "john@example.com")
        ]

        await viewModel.loadMembers()

        #expect(viewModel.members.count == 2)
        #expect(viewModel.errorMessage == nil)
    }

    @Test("loadMembers sets errorMessage on failure")
    func loadMembersFailure() async {
        let (viewModel, mock, _) = makeSUT()
        mock.errorToThrow = MockBackendError.injected

        await viewModel.loadMembers()

        #expect(viewModel.errorMessage != nil)
        #expect(viewModel.members.isEmpty)
    }

    @Test("filteredMembers returns everyone when searchText is empty")
    func filteredMembersEmptySearch() async {
        let (viewModel, mock, gymId) = makeSUT()
        mock.members[gymId] = [
            Member(id: "1", name: "Jane Doe", email: "jane@example.com"),
            Member(id: "2", name: "John Smith", email: "john@example.com")
        ]
        await viewModel.loadMembers()

        #expect(viewModel.filteredMembers.count == 2)
    }

    @Test("filteredMembers matches by name, case-insensitively")
    func filteredMembersMatchesName() async {
        let (viewModel, mock, gymId) = makeSUT()
        mock.members[gymId] = [
            Member(id: "1", name: "Jane Doe", email: "jane@example.com"),
            Member(id: "2", name: "John Smith", email: "john@example.com")
        ]
        await viewModel.loadMembers()

        viewModel.searchText = "jane"

        #expect(viewModel.filteredMembers.map(\.id) == ["1"])
    }

    @Test("filteredMembers matches by email")
    func filteredMembersMatchesEmail() async {
        let (viewModel, mock, gymId) = makeSUT()
        mock.members[gymId] = [
            Member(id: "1", name: "Jane Doe", email: "jane@example.com"),
            Member(id: "2", name: "John Smith", email: "john@example.com")
        ]
        await viewModel.loadMembers()

        viewModel.searchText = "john@"

        #expect(viewModel.filteredMembers.map(\.id) == ["2"])
    }

    @Test("filteredMembers returns nothing when nothing matches")
    func filteredMembersNoMatch() async {
        let (viewModel, mock, gymId) = makeSUT()
        mock.members[gymId] = [Member(id: "1", name: "Jane Doe", email: "jane@example.com")]
        await viewModel.loadMembers()

        viewModel.searchText = "nonexistent"

        #expect(viewModel.filteredMembers.isEmpty)
    }
}
