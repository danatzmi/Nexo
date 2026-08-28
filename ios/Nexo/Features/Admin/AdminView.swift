//
//  AdminView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 20/04/2026.
//

import SwiftUI

struct AdminView: View {
    @Environment(AppState.self) private var appState
    @State private var selectedTab: AdminTab = .members
    @State private var showingGymSettings = false

    enum AdminTab: String, CaseIterable {
        case members = "Members"
        case plans = "Plans"
        case reports = "Reports"

        var icon: String {
            switch self {
            case .members: return "person.2"
            case .plans: return "creditcard"
            case .reports: return "chart.bar"
            }
        }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Tab Picker
                Picker("Admin Section", selection: $selectedTab) {
                    ForEach(AdminTab.allCases, id: \.self) { tab in
                        Label(tab.rawValue, systemImage: tab.icon)
                            .tag(tab)
                    }
                }
                .pickerStyle(.segmented)
                .padding()

                // Content based on selected tab
                switch selectedTab {
                case .members:
                    MembersTabView()
                case .plans:
                    MembershipPlansView(gymId: appState.gymId)
                case .reports:
                    PlaceholderView(title: "Reports", icon: "chart.bar", message: "Analytics coming soon")
                }
            }
            .navigationTitle("Admin Tools")
            .toolbar {
                if appState.gymRole == .owner || appState.isAdmin {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button {
                            showingGymSettings = true
                        } label: {
                            Image(systemName: "gearshape")
                        }
                    }
                }
            }
            .sheet(isPresented: $showingGymSettings) {
                if let gym = appState.currentGym {
                    GymSettingsSheet(gym: gym) { _ in }
                }
            }
        }
    }
}

// MARK: - Members Tab View

private struct MembersTabView: View {
    @Environment(AppState.self) private var appState

    private enum SubTab: String, CaseIterable {
        case members = "Members"
        case team = "Team"
    }

    @State private var subTab: SubTab = .members

    var body: some View {
        VStack(spacing: 0) {
            Picker("Members Section", selection: $subTab) {
                ForEach(SubTab.allCases, id: \.self) { tab in
                    Text(tab.rawValue).tag(tab)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
            .padding(.bottom, 8)

            switch subTab {
            case .members:
                GymMembersView(gymId: appState.gymId)
            case .team:
                TeamView()
            }
        }
    }
}

// MARK: - Team View

private struct TeamView: View {
    @Environment(AppState.self) private var appState
    @State private var team: [TeamMember] = []
    @State private var isLoading = false
    @State private var showingAddTeamMember = false
    @State private var searchText = ""

    private var filteredTeam: [TeamMember] {
        if searchText.isEmpty {
            return team
        }
        return team.filter {
            $0.fullName.localizedCaseInsensitiveContains(searchText) ||
            $0.email.localizedCaseInsensitiveContains(searchText)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("\(team.count) Members")
                    .font(.headline)
                    .foregroundStyle(.secondary)
                Spacer()
                if appState.canManageClasses {
                    Button {
                        showingAddTeamMember = true
                    } label: {
                        Label("Add Team Member", systemImage: "plus")
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
            .padding()

            if isLoading {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if team.isEmpty {
                ContentUnavailableView(
                    "No Team Members",
                    systemImage: "person.2",
                    description: Text("Add team members to get started")
                )
            } else if filteredTeam.isEmpty {
                ContentUnavailableView.search(text: searchText)
            } else {
                List(filteredTeam) { member in
                    NavigationLink {
                        TeamMemberDetailView(gymId: appState.gymId, member: member)
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(member.fullName)
                                    .font(.headline)
                                Text(member.email)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Text(member.role.displayName)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Capsule().fill(Color(.systemGray5)))
                        }
                    }
                }
                .listStyle(.plain)
                .refreshable { await loadTeam() }
            }
        }
        .searchable(text: $searchText, prompt: "Search team")
        .task { await loadTeam() }
        .sheet(isPresented: $showingAddTeamMember) {
            AddTeamMemberView { Task { await loadTeam() } }
        }
    }

    private func loadTeam() async {
        isLoading = true
        do {
            team = try await FirebaseBackend.shared.fetchTeam(gymId: appState.gymId)
        } catch {
            print("Error loading team: \(error)")
        }
        isLoading = false
    }
}

// MARK: - Placeholder View

private struct PlaceholderView: View {
    let title: String
    let icon: String
    let message: String
    
    var body: some View {
        ContentUnavailableView(
            title,
            systemImage: icon,
            description: Text(message)
        )
    }
}

#Preview {
    AdminView()
}
