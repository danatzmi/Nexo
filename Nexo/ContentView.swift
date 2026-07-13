//
//  ContentView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 18/04/2026.
//

import SwiftUI

struct ContentView: View {
    @State private var appState = AppState()
    @State private var isCheckingAuth = true

    var body: some View {
        Group {
            if isCheckingAuth {
                ProgressView()
            } else if !appState.isAuthenticated {
                AuthView {
                    Task { await loadAfterAuth() }
                }
            } else if appState.isAdmin && appState.currentGym == nil {
                PlatformDashboardView()
            } else if appState.currentGym == nil {
                GymPickerView()
            } else {
                MainTabView()
            }
        }
        .environment(appState)
        .task {
            appState.isAuthenticated = FirebaseBackend.shared.currentUID() != nil
            if appState.isAuthenticated {
                await loadAfterAuth()
            }
            isCheckingAuth = false
        }
    }

    private func loadAfterAuth() async {
        async let role = FirebaseBackend.shared.fetchPlatformRole()
        async let gyms = FirebaseBackend.shared.fetchMyGyms()

        let resolvedRole = (try? await role) ?? PlatformRole.user
        appState.appRole = resolvedRole
        appState.myGyms = (try? await gyms) ?? []
        appState.isAuthenticated = true
        if resolvedRole != .admin {
            appState.autoSelectGym()
        }
    }
}

// MARK: - Main Tab View

private struct MainTabView: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        TabView {
            ScheduleView(gymId: appState.gymId)
                .tabItem { Label("Schedule", systemImage: "calendar") }

            if appState.gymRole.canManageClasses {
                AdminView()
                    .tabItem { Label("Manage", systemImage: "wrench.and.screwdriver") }
            }

            ProfileView()
                .tabItem { Label("Profile", systemImage: "person.crop.circle") }
        }
    }
}

#Preview {
    ContentView()
}
