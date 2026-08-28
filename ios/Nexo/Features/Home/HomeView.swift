//
//  HomeView.swift
//  Nexo
//

import SwiftUI

struct HomeView: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        if let gym = appState.currentGym {
            // Same reasoning as `MainTabView`'s Schedule/Manage/Profile tabs
            // (see `ContentView.swift`) — without `.id(gym.id)`, GymHomeView's
            // `@State` view model and `.task` load would keep pointing at
            // whichever gym was active when this view first appeared.
            GymHomeView(gym: gym)
                .id(gym.id)
        } else if appState.isAdmin {
            PlatformDashboardView()
        } else {
            GymPickerView()
        }
    }
}

#Preview {
    HomeView()
}
