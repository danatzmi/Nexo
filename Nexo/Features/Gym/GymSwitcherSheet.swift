//
//  GymSwitcherSheet.swift
//  Nexo
//
//  Created by Atzmi, Dan on 25/04/2026.
//

import SwiftUI

struct GymSwitcherSheet: View {
    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            if appState.isAdmin && appState.myGyms.isEmpty {
                adminView
            } else {
                gymListView
            }
        }
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
    }

    private var gymListView: some View {
        List(appState.myGyms, id: \.gym.id) { entry in
            Button {
                appState.enter(gym: entry.gym, role: entry.role)
                dismiss()
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
                    if entry.gym.id == appState.currentGym?.id {
                        Image(systemName: "checkmark")
                            .foregroundStyle(Color.accentColor)
                            .fontWeight(.semibold)
                    }
                }
            }
        }
        .navigationTitle("Switch Gym")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("Manage") {
                    dismiss()
                    appState.leaveGym()
                }
            }
        }
    }

    private var adminView: some View {
        VStack(spacing: 20) {
            Spacer()
            Image(systemName: "building.2")
                .font(.system(size: 48))
                .foregroundStyle(.secondary)
            Text(appState.currentGym?.name ?? "")
                .font(.title2)
                .fontWeight(.bold)
            Text("You entered this gym as platform admin.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Button {
                dismiss()
                appState.leaveGym()
            } label: {
                Label("Back to Dashboard", systemImage: "arrow.left")
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
            }
            .buttonStyle(.borderedProminent)
            .padding(.horizontal)
            Spacer()
        }
        .navigationTitle(appState.currentGym?.name ?? "")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }
            }
        }
    }
}
