//
//  TeamMemberDetailView.swift
//  Nexo
//

import SwiftUI

struct TeamMemberDetailView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppState.self) private var appState
    @State private var viewModel: TeamMemberDetailViewModel
    @State private var selectedRole: UserRole
    @State private var showRemoveConfirmation = false

    init(gymId: UUID, member: TeamMember) {
        _viewModel = State(initialValue: TeamMemberDetailViewModel(gymId: gymId, member: member))
        _selectedRole = State(initialValue: member.role)
    }

    private var canManageTeam: Bool { appState.isAdmin || appState.gymRole == .owner }

    var body: some View {
        List {
            Section("Profile") {
                LabeledContent("Name", value: viewModel.member.fullName)
                LabeledContent("Email", value: viewModel.member.email)
            }

            if canManageTeam {
                Section("Role") {
                    Picker("Role", selection: $selectedRole) {
                        Text(UserRole.owner.displayName).tag(UserRole.owner)
                        Text(UserRole.coach.displayName).tag(UserRole.coach)
                    }
                    .disabled(viewModel.isSelf)
                    .onChange(of: selectedRole) { _, newRole in
                        Task { await viewModel.updateRole(newRole) }
                    }

                    if viewModel.isSelf {
                        Text("You can't change your own role.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                Section {
                    Button("Remove from Team", role: .destructive) {
                        showRemoveConfirmation = true
                    }
                    .disabled(viewModel.isSelf)

                    if viewModel.isSelf {
                        Text("You can't remove yourself from the team.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .navigationTitle(viewModel.member.fullName)
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog(
            "Remove \(viewModel.member.fullName) from the team?",
            isPresented: $showRemoveConfirmation,
            titleVisibility: .visible
        ) {
            Button("Remove", role: .destructive) {
                Task {
                    if await viewModel.removeTeamMember() {
                        dismiss()
                    }
                }
            }
            Button("Cancel", role: .cancel) { }
        } message: {
            Text("This cannot be undone.")
        }
        .alert(
            "Error",
            isPresented: Binding(get: { viewModel.errorMessage != nil }, set: { if !$0 { viewModel.errorMessage = nil } })
        ) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
    }
}

#Preview {
    NavigationStack {
        TeamMemberDetailView(gymId: UUID(), member: TeamMember(id: "preview-uid", firstName: "Jane", lastName: "Doe", email: "jane@example.com", role: .coach))
    }
}
