//
//  GymPickerView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 25/04/2026.
//

import SwiftUI

/// Shown to a signed-in, non-platform-admin user. There's no self-serve
/// gym creation or public join directory (see FEEDBACK.md's "Owner-Driven
/// Membership" model) — a user either already belongs to at least one gym
/// (`gymsListView`) or is waiting for a gym owner to add them by email
/// (`awaitingEnrollmentView`).
struct GymPickerView: View {
    @Environment(AppState.self) private var appState
    @State private var userEmail: String = ""
    @State private var isRefreshing = false

    var body: some View {
        NavigationStack {
            Group {
                if appState.myGyms.isEmpty {
                    awaitingEnrollmentView
                } else {
                    gymsListView
                }
            }
            .navigationTitle(appState.myGyms.isEmpty ? "Welcome" : "Your Gyms")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Sign Out") { appState.signOut() }
                }
            }
            .task { await loadEmail() }
        }
    }

    // MARK: - Awaiting Enrollment

    private var awaitingEnrollmentView: some View {
        VStack(spacing: 20) {
            Spacer()

            ZStack {
                Circle()
                    .fill(Color.accentColor.opacity(0.12))
                    .frame(width: 80, height: 80)
                Image(systemName: "hourglass")
                    .font(.system(size: 34))
                    .foregroundStyle(Color.accentColor)
            }

            VStack(spacing: 10) {
                Text("Welcome to Nexo!")
                    .font(.title2.bold())

                Text("You haven't been added to a gym yet.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)

                if !userEmail.isEmpty {
                    Text("Ask your gym owner to add you using your email:")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.top, 4)

                    Text(userEmail)
                        .font(.subheadline.weight(.semibold))
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(
                            RoundedRectangle(cornerRadius: 10)
                                .fill(Color(uiColor: .secondarySystemBackground))
                        )
                }
            }
            .padding(.horizontal, 32)

            Button {
                Task { await refresh() }
            } label: {
                if isRefreshing {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                } else {
                    Text("Check Again")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.borderedProminent)
            .frame(maxWidth: 240)
            .disabled(isRefreshing)

            Spacer()
        }
        .padding()
    }

    // MARK: - Existing Gyms List

    private var gymsListView: some View {
        List(appState.myGyms, id: \.gym.id) { entry in
            Button {
                appState.enter(gym: entry.gym, role: entry.role)
            } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(entry.gym.name)
                            .font(.headline)
                            .foregroundStyle(.primary)
                        Text(entry.role.displayName)
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
    }

    // MARK: - Actions

    private func loadEmail() async {
        if let profile = try? await FirebaseBackend.shared.fetchUserProfile() {
            userEmail = profile.email
        }
    }

    private func refresh() async {
        isRefreshing = true
        await appState.refreshMyGyms()
        isRefreshing = false
    }
}
