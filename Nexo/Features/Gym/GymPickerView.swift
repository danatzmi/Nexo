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

    var body: some View {
        NavigationStack {
            Group {
                if appState.myGyms.isEmpty {
                    ContentUnavailableView(
                        "No Gyms Yet",
                        systemImage: "building.2",
                        description: Text("Create a new gym or join an existing one")
                    )
                } else {
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
            .navigationTitle("Your Gyms")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    if appState.isAdmin {
                        Menu {
                            Button("Create a Gym", systemImage: "plus.circle") {
                                showCreateGym = true
                            }
                            Button("Join a Gym", systemImage: "person.badge.plus") {
                                showJoinGym = true
                            }
                        } label: {
                            Image(systemName: "plus")
                        }
                    } else {
                        Button { showJoinGym = true } label: {
                            Image(systemName: "plus")
                        }
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
}
