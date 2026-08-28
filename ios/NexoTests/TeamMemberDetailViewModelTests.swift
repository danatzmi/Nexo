//
//  TeamMemberDetailViewModelTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

@MainActor
@Suite("TeamMemberDetailViewModel")
struct TeamMemberDetailViewModelTests {
    private func makeSUT(
        memberId: String = "coach-1", signedInAs uid: String? = "owner-1"
    ) -> (viewModel: TeamMemberDetailViewModel, mock: MockBackendService, gymId: UUID, member: TeamMember) {
        let mock = MockBackendService()
        let gymId = UUID()
        let member = TeamMember(id: memberId, firstName: "Sam", lastName: "Rivera", email: "sam@example.com", role: .coach)
        mock.team[gymId] = [member]
        mock.signedInUID = uid
        let viewModel = TeamMemberDetailViewModel(gymId: gymId, member: member, backend: mock)
        return (viewModel, mock, gymId, member)
    }

    @Test("isSelf is true when the member is the signed-in user")
    func isSelfTrueForSignedInUser() {
        let (viewModel, _, _, _) = makeSUT(memberId: "owner-1", signedInAs: "owner-1")
        #expect(viewModel.isSelf)
    }

    @Test("isSelf is false for a different team member")
    func isSelfFalseForOtherMember() {
        let (viewModel, _, _, _) = makeSUT(memberId: "coach-1", signedInAs: "owner-1")
        #expect(!viewModel.isSelf)
    }

    @Test("updateRole changes the member's role in the backend")
    func updateRoleChangesBackendState() async throws {
        let (viewModel, mock, gymId, member) = makeSUT()

        await viewModel.updateRole(.owner)

        let team = try await mock.fetchTeam(gymId: gymId)
        #expect(team.first { $0.id == member.id }?.role == .owner)
        #expect(viewModel.errorMessage == nil)
    }

    @Test("updateRole sets errorMessage on failure")
    func updateRoleFailure() async {
        let (viewModel, mock, _, _) = makeSUT()
        mock.errorToThrow = MockBackendError.injected

        await viewModel.updateRole(.owner)

        #expect(viewModel.errorMessage != nil)
    }

    @Test("removeTeamMember removes the member and returns true")
    func removeTeamMemberSucceeds() async throws {
        let (viewModel, mock, gymId, member) = makeSUT()

        let result = await viewModel.removeTeamMember()

        #expect(result)
        let team = try await mock.fetchTeam(gymId: gymId)
        #expect(!team.contains { $0.id == member.id })
        #expect(viewModel.errorMessage == nil)
    }

    @Test("removeTeamMember returns false and sets errorMessage on failure")
    func removeTeamMemberFailure() async {
        let (viewModel, mock, _, _) = makeSUT()
        mock.errorToThrow = MockBackendError.injected

        let result = await viewModel.removeTeamMember()

        #expect(!result)
        #expect(viewModel.errorMessage != nil)
    }
}
