//
//  GymPickerView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 25/04/2026.
//

import SwiftUI

struct GymPickerView: View {
    @Environment(AppState.self) private var appState
    @State private var showCreateGym = false
    @State private var showJoinGym = false
    @State private var quickJoinCode = ""
    @State private var isJoiningQuickCode = false
    @State private var quickCodeError: String?

    var body: some View {
        NavigationStack {
            Group {
                if appState.myGyms.isEmpty {
                    welcomeOnboardingView
                } else {
                    gymsListView
                }
            }
            .navigationTitle(appState.myGyms.isEmpty ? "Welcome" : "Your Gyms")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Button {
                            showJoinGym = true
                        } label: {
                            Label("Join a Gym", systemImage: "person.badge.plus")
                        }

                        Button {
                            showCreateGym = true
                        } label: {
                            Label("Create a Gym", systemImage: "plus.circle")
                        }
                    } label: {
                        Image(systemName: "plus")
                    }
                }
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Sign Out") { appState.signOut() }
                }
            }
            .sheet(isPresented: $showCreateGym) {
                CreateGymView { gym in
                    appState.myGyms.append((gym: gym, role: .owner))
                    appState.enter(gym: gym, role: .owner)
                }
            }
            .sheet(isPresented: $showJoinGym) {
                JoinGymView { gym in
                    appState.myGyms.append((gym: gym, role: .member))
                    appState.enter(gym: gym, role: .member)
                }
            }
        }
    }

    // MARK: - Welcome / No Gyms Onboarding Hub

    private var welcomeOnboardingView: some View {
        ScrollView {
            VStack(spacing: 28) {
                // Hero Header
                VStack(spacing: 10) {
                    ZStack {
                        Circle()
                            .fill(Color.accentColor.opacity(0.12))
                            .frame(width: 80, height: 80)
                        Image(systemName: "dumbbell.fill")
                            .font(.system(size: 38))
                            .foregroundStyle(Color.accentColor)
                    }
                    .padding(.top, 24)

                    Text("Welcome to Nexo")
                        .font(.title.bold())

                    Text("Choose how you'd like to get started:")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                // 2 Big Choice Cards
                VStack(spacing: 16) {
                    // Option 1: Gym Member
                    Button {
                        showJoinGym = true
                    } label: {
                        HStack(spacing: 18) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 14)
                                    .fill(Color.accentColor.opacity(0.15))
                                    .frame(width: 56, height: 56)
                                Image(systemName: "figure.run")
                                    .font(.system(size: 26, weight: .semibold))
                                    .foregroundStyle(Color.accentColor)
                            }

                            VStack(alignment: .leading, spacing: 4) {
                                Text("I am a Gym Member")
                                    .font(.headline)
                                    .foregroundStyle(.primary)

                                Text("Find your gym, book classes, and track your daily workouts.")
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                                    .multilineTextAlignment(.leading)
                            }

                            Spacer()

                            Image(systemName: "chevron.right")
                                .font(.subheadline.bold())
                                .foregroundStyle(.secondary)
                        }
                        .padding(18)
                        .background(
                            RoundedRectangle(cornerRadius: 18)
                                .fill(Color(uiColor: .secondarySystemBackground))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 18)
                                        .stroke(Color.accentColor.opacity(0.3), lineWidth: 1.5)
                                )
                        )
                    }
                    .buttonStyle(.plain)

                    // Option 2: Gym Owner
                    Button {
                        showCreateGym = true
                    } label: {
                        HStack(spacing: 18) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 14)
                                    .fill(Color.purple.opacity(0.15))
                                    .frame(width: 56, height: 56)
                                Image(systemName: "building.2.fill")
                                    .font(.system(size: 24, weight: .semibold))
                                    .foregroundStyle(Color.purple)
                            }

                            VStack(alignment: .leading, spacing: 4) {
                                Text("I am a Gym Owner")
                                    .font(.headline)
                                    .foregroundStyle(.primary)

                                Text("Set up a new gym, schedule classes, and manage your members.")
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                                    .multilineTextAlignment(.leading)
                            }

                            Spacer()

                            Image(systemName: "chevron.right")
                                .font(.subheadline.bold())
                                .foregroundStyle(.secondary)
                        }
                        .padding(18)
                        .background(
                            RoundedRectangle(cornerRadius: 18)
                                .fill(Color(uiColor: .secondarySystemBackground))
                        )
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 20)

                Spacer(minLength: 32)
            }
        }
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
}
