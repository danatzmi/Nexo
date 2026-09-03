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
                SplashScreen()
                    .transition(.opacity)
            } else if !appState.isAuthenticated {
                AuthView {
                    Task { await loadAfterAuth() }
                }
                .transition(.opacity)
            } else {
                MainTabView()
                    .transition(.opacity)
            }
        }
        .environment(appState)
        .task {
            appState.isAuthenticated = FirebaseBackend.shared.currentUID() != nil
            if appState.isAuthenticated {
                await loadAfterAuth()
            }
            withAnimation(.easeInOut(duration: 0.5)) {
                isCheckingAuth = false
            }
        }
    }

    private func loadAfterAuth() async {
        let resolvedRole = (try? await FirebaseBackend.shared.fetchPlatformRole()) ?? PlatformRole.user
        appState.appRole = resolvedRole
        appState.myGyms = await resolveMyGyms(role: resolvedRole, backend: FirebaseBackend.shared)
        appState.isAuthenticated = true
        if resolvedRole != .admin {
            appState.autoSelectGym()
        }
    }
}

/// Resolves which gyms populate the switcher's list for the given platform
/// role. Platform admins see and can switch into every gym in the system —
/// as if they owned each one, for management purposes — rather than only
/// gyms they have an explicit membership document for; everyone else sees
/// just their own memberships, as before. A free function (not a method on
/// `ContentView`) so this role-based decision is directly testable, per
/// `CLAUDE.md`'s "business logic stays out of SwiftUI views" rule.
func resolveMyGyms(role: PlatformRole, backend: BackendService) async -> [(gym: Gym, role: UserRole)] {
    if role == .admin {
        let allGyms = (try? await backend.fetchAvailableGyms()) ?? []
        return allGyms.map { ($0, UserRole.owner) }
    } else {
        return (try? await backend.fetchMyGyms()) ?? []
    }
}

// MARK: - Splash Screen

/// Shown while `ContentView` checks Firebase Auth state on launch, in place
/// of a bare `ProgressView` — a branded moment instead of a blank flash.
private struct SplashScreen: View {
    @State private var isAnimating = false

    var body: some View {
        ZStack {
            Color(red: 0.1, green: 0.1, blue: 0.1)
                .ignoresSafeArea()

            VStack(spacing: 16) {
                Image("NexoLogo")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 100, height: 100)
                    .clipShape(RoundedRectangle(cornerRadius: 20))

                Text("Nexo")
                    .font(.system(size: 32, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)

                ProgressView()
                    .tint(.white)
                    .opacity(0.4)
                    .scaleEffect(0.8)
                    .padding(.top, 8)
            }
            .scaleEffect(isAnimating ? 1 : 0.85)
            .opacity(isAnimating ? 1 : 0)
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.6)) {
                isAnimating = true
            }
        }
    }
}

// MARK: - Main Tab View

/// All authenticated users — members, coaches, owners, and platform admins
/// alike — land here. Each tab handles `currentGym == nil` on its own
/// (a `SelectGymPlaceholder`, or `HomeView` routing to the gym picker /
/// platform dashboard) rather than this view gating on it, since a platform
/// admin routinely has no gym selected at all.
private struct MainTabView: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        @Bindable var appState = appState
        TabView(selection: $appState.selectedTab) {
            HomeView()
                .tabItem { Label("Home", systemImage: "house") }
                .tag(0)

            Group {
                if let gym = appState.currentGym {
                    // `.id(gym.id)` forces a fresh view identity on gym switch —
                    // without it, SwiftUI treats this as the same ScheduleView
                    // across gym changes, so its `@State` view model (captured
                    // once in `init`) and `.task` (which only runs once per
                    // identity) would both keep pointing at the old gym.
                    ScheduleView(gymId: gym.id)
                        .id(gym.id)
                } else {
                    SelectGymPlaceholder(icon: "calendar", text: "Select a gym on Home to view schedule")
                }
            }
            .tabItem { Label("Schedule", systemImage: "calendar") }
            .tag(1)

            Group {
                if let gym = appState.currentGym {
                    // Same `.id()` reasoning as the Schedule tab above.
                    LogbookView(gymId: gym.id)
                        .id(gym.id)
                } else {
                    SelectGymPlaceholder(icon: "doc.plaintext", text: "Select a gym on Home to view your logbook")
                }
            }
            .tabItem { Label("Logbook", systemImage: "doc.plaintext") }
            .tag(2)

            if appState.canManageClasses {
                Group {
                    if let gym = appState.currentGym {
                        // Same `.id()` reasoning as the Schedule tab above —
                        // AdminView's nested tabs (`GymMembersView`, `TeamView`,
                        // `MembershipPlansView`) each load their own data in a
                        // `.task` keyed to `appState.gymId`, which won't rerun
                        // on a gym switch without a fresh identity to reset it.
                        AdminView()
                            .id(gym.id)
                    } else {
                        SelectGymPlaceholder(icon: "wrench", text: "Select a gym on Home to manage it")
                    }
                }
                .tabItem { Label("Manage", systemImage: "wrench.and.screwdriver") }
                .tag(3)
            }

            ProfileView()
                .tabItem { Label("Profile", systemImage: "person.crop.circle") }
                .tag(4)
                // Same reasoning as Schedule/Manage — ProfileView's bookings/
                // activePlans are loaded once in `.task`. Deliberately
                // `appState.currentGym?.id`, not `appState.gymId`: the latter
                // fabricates a new random UUID on every access when there's no
                // gym selected, which would churn this identity continuously.
                .id(appState.currentGym?.id)
        }
    }
}

// MARK: - Select Gym Placeholder

private struct SelectGymPlaceholder: View {
    let icon: String
    let text: String

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                Spacer()
                Image(systemName: icon)
                    .font(.system(size: 20, weight: .light))
                    .foregroundStyle(Color.secondary.opacity(0.5))
                Text(text)
                    .font(.footnote)
                    .foregroundStyle(Color.secondary.opacity(0.6))
                Spacer()
            }
            .frame(maxWidth: .infinity)
        }
    }
}

#Preview {
    ContentView()
}
