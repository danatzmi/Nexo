//
//  JoinGymView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 25/04/2026.
//

import SwiftUI

struct JoinGymView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var availableGyms: [Gym] = []
    @State private var searchText = ""
    @State private var isLoading = false
    @State private var joiningGymId: UUID?
    @State private var error: String?

    var onJoined: (Gym) -> Void

    private var filteredGyms: [Gym] {
        guard !searchText.trimmingCharacters(in: .whitespaces).isEmpty else { return availableGyms }
        return availableGyms.filter { $0.name.localizedCaseInsensitiveContains(searchText) }
    }

    var body: some View {
        NavigationStack {
            Group {
                if isLoading && availableGyms.isEmpty {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if filteredGyms.isEmpty {
                    ContentUnavailableView.search(text: searchText)
                } else {
                    List(filteredGyms) { gym in
                        HStack {
                            Text(gym.name)
                                .font(.headline)
                            Spacer()
                            if joiningGymId == gym.id {
                                ProgressView()
                            } else {
                                Button("Join") {
                                    Task { await joinGym(gym) }
                                }
                                .buttonStyle(.borderedProminent)
                                .controlSize(.small)
                            }
                        }
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Join a Gym")
            .navigationBarTitleDisplayMode(.inline)
            .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search gyms")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .task { await loadGyms() }
        }
    }

    private func loadGyms() async {
        isLoading = true
        do {
            availableGyms = try await FirebaseBackend.shared.fetchAvailableGyms()
        } catch {
            print("Error loading gyms: \(error)")
        }
        isLoading = false
    }

    private func joinGym(_ gym: Gym) async {
        joiningGymId = gym.id
        do {
            try await FirebaseBackend.shared.joinGym(gymId: gym.id)
            await MainActor.run {
                onJoined(gym)
                dismiss()
            }
        } catch {
            await MainActor.run {
                self.error = error.localizedDescription
                joiningGymId = nil
            }
        }
    }
}
