//
//  GymMembersView.swift
//  Nexo
//

import SwiftUI

struct GymMembersView: View {
    @Environment(AppState.self) private var appState
    @State private var viewModel: GymMembersViewModel
    @State private var showingAddMember = false

    init(gymId: UUID) {
        _viewModel = State(initialValue: GymMembersViewModel(gymId: gymId))
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("\(viewModel.members.count) Members")
                    .font(.headline)
                    .foregroundStyle(.secondary)
                Spacer()
                if appState.canManageClasses {
                    Button {
                        showingAddMember = true
                    } label: {
                        Label("Add Member", systemImage: "plus")
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
            .padding()

            if viewModel.isLoading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if viewModel.members.isEmpty {
                ContentUnavailableView(
                    "No Members Yet",
                    systemImage: "person.3",
                    description: Text("Members will appear here once they join your gym")
                )
            } else if viewModel.filteredMembers.isEmpty {
                ContentUnavailableView.search(text: viewModel.searchText)
            } else {
                List(viewModel.filteredMembers) { member in
                    NavigationLink {
                        MemberDetailView(gymId: appState.gymId, member: member)
                    } label: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(member.name)
                                .font(.headline)
                            Text(member.email)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .listStyle(.plain)
                .refreshable { await viewModel.loadMembers() }
            }
        }
        .searchable(text: $viewModel.searchText, prompt: "Search members")
        .alert(
            "Error",
            isPresented: Binding(get: { viewModel.errorMessage != nil }, set: { if !$0 { viewModel.errorMessage = nil } })
        ) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
        .sheet(isPresented: $showingAddMember) {
            AddMemberView { Task { await viewModel.loadMembers() } }
        }
        .task { await viewModel.loadMembers() }
    }
}

#Preview {
    NavigationStack {
        GymMembersView(gymId: UUID())
    }
}
