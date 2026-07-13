//
//  PlatformDashboardView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 25/04/2026.
//

import SwiftUI

struct PlatformDashboardView: View {
    @Environment(AppState.self) private var appState
    @State private var gyms: [Gym] = []
    @State private var users: [PlatformUser] = []
    @State private var isLoading = false
    @State private var selectedTab: DashboardTab = .gyms
    @State private var showCreateGym = false
    @State private var showSignOutConfirm = false

    enum DashboardTab { case gyms, users }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    StatCard(title: "Gyms", value: gyms.count, icon: "building.2.fill", color: .blue)
                    StatCard(title: "Users", value: users.count, icon: "person.2.fill", color: .green)
                }
                .padding()

                Picker("Section", selection: $selectedTab) {
                    Text("Gyms").tag(DashboardTab.gyms)
                    Text("Users").tag(DashboardTab.users)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                .padding(.bottom, 8)

                Divider()

                if isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if selectedTab == .gyms {
                    GymsListView(gyms: $gyms)
                } else {
                    UsersListView(users: $users)
                }
            }
            .navigationTitle("Dashboard")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    if selectedTab == .gyms {
                        Button { showCreateGym = true } label: {
                            Image(systemName: "plus")
                        }
                    }
                }
                ToolbarItem(placement: .navigationBarLeading) {
                    Button { showSignOutConfirm = true } label: {
                        Image(systemName: "rectangle.portrait.and.arrow.right")
                    }
                }
            }
            .task { await loadData() }
            .sheet(isPresented: $showCreateGym) {
                CreateGymView { gym in gyms.append(gym) }
            }
            .alert("Sign Out", isPresented: $showSignOutConfirm) {
                Button("Sign Out", role: .destructive) { appState.signOut() }
                Button("Cancel", role: .cancel) { }
            } message: {
                Text("Are you sure you want to sign out?")
            }
        }
    }

    private func loadData() async {
        isLoading = true
        async let fetchedGyms = FirebaseBackend.shared.fetchAvailableGyms()
        async let fetchedUsers = FirebaseBackend.shared.fetchAllUsers()
        gyms = (try? await fetchedGyms) ?? []
        users = (try? await fetchedUsers) ?? []
        isLoading = false
    }
}

// MARK: - Stat Card

private struct StatCard: View {
    let title: String
    let value: Int
    let icon: String
    let color: Color

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(color)
            VStack(alignment: .leading, spacing: 2) {
                Text("\(value)")
                    .font(.title2)
                    .fontWeight(.bold)
                Text(title)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
        .padding()
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

// MARK: - Gyms List

private struct GymsListView: View {
    @Environment(AppState.self) private var appState
    @Binding var gyms: [Gym]

    var body: some View {
        if gyms.isEmpty {
            ContentUnavailableView(
                "No Gyms Yet",
                systemImage: "building.2",
                description: Text("Tap + to create the first gym")
            )
        } else {
            List(gyms) { gym in
                Button { appState.enter(gym: gym, role: .owner) } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(gym.name)
                                .font(.headline)
                                .foregroundStyle(.primary)
                            Text("Tap to manage")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .foregroundStyle(.secondary)
                            .font(.caption)
                    }
                }
            }
            .listStyle(.plain)
        }
    }
}

// MARK: - Users List

private struct UsersListView: View {
    @Binding var users: [PlatformUser]
    @State private var pendingUser: PlatformUser?

    var body: some View {
        if users.isEmpty {
            ContentUnavailableView("No Users", systemImage: "person.2")
        } else {
            List(users) { user in
                Button { pendingUser = user } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(user.displayName)
                                .font(.headline)
                                .foregroundStyle(.primary)
                            Text(user.email)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        RoleBadge(role: user.role)
                    }
                }
            }
            .listStyle(.plain)
            .confirmationDialog(
                "Change role for \(pendingUser?.displayName ?? "")",
                isPresented: Binding(get: { pendingUser != nil }, set: { if !$0 { pendingUser = nil } }),
                titleVisibility: .visible
            ) {
                if let user = pendingUser {
                    if user.role == .admin {
                        Button("Remove Admin", role: .destructive) {
                            Task { await updateRole(for: user, to: .user) }
                        }
                    } else {
                        Button("Make Admin") {
                            Task { await updateRole(for: user, to: .admin) }
                        }
                    }
                    Button("Cancel", role: .cancel) { pendingUser = nil }
                }
            }
        }
    }

    private func updateRole(for user: PlatformUser, to role: PlatformRole) async {
        do {
            try await FirebaseBackend.shared.updatePlatformRole(uid: user.id, role: role)
            if let index = users.firstIndex(where: { $0.id == user.id }) {
                users[index].role = role
            }
        } catch {
            print("Failed to update role: \(error)")
        }
        pendingUser = nil
    }
}

// MARK: - Role Badge

private struct RoleBadge: View {
    let role: PlatformRole

    var body: some View {
        Text(role == .admin ? "Admin" : "User")
            .font(.caption)
            .fontWeight(.medium)
            .foregroundStyle(role == .admin ? Color.orange : Color.secondary)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(
                Capsule().fill(role == .admin ? Color.orange.opacity(0.15) : Color(.systemGray5))
            )
    }
}
