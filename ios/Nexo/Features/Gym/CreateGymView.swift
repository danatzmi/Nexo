//
//  CreateGymView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 25/04/2026.
//

import SwiftUI

struct CreateGymView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppState.self) private var appState

    @State private var gymName = ""
    @State private var city = ""
    @State private var customJoinCode = ""
    private let suggestedCategories = [
        "CrossFit", "HIIT", "Strength", "Yoga", "Pilates",
        "Boxing", "Open Gym", "Spinning", "Cardio", "Mobility"
    ]

    @State private var selectedWorkoutTypes: Set<String> = []
    @State private var customCategories: [String] = []
    @State private var newWorkoutType = ""
    @State private var showAddWorkoutType = false
    @State private var isLoading = false
    @State private var createdGym: Gym?
    @State private var showSuccessModal = false
    @State private var errorMessage: String?
    @State private var copiedCode = false

    var onCreated: (Gym) -> Void

    private var trimmedName: String { gymName.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var trimmedCity: String { city.trimmingCharacters(in: .whitespacesAndNewlines) }

    private var allCategories: [String] {
        var list = suggestedCategories
        for custom in customCategories where !list.contains(custom) {
            list.append(custom)
        }
        return list
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Gym Details") {
                    TextField("Gym Name", text: $gymName)
                        .autocorrectionDisabled()

                    TextField("Gym Location", text: $city)
                        .autocorrectionDisabled()
                }

                Section("Member Join Code (Optional)") {
                    TextField("e.g. IRON99 (Optional)", text: $customJoinCode)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()

                    Text("Leave blank to auto-generate from gym name. Members use this code or your invite link to join.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Section("Select Workout Categories") {
                    Text("Tap the categories your gym offers:")
                        .font(.caption)
                        .foregroundStyle(.secondary)

                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 100), spacing: 8)], spacing: 8) {
                        ForEach(allCategories, id: \.self) { cat in
                            let isSelected = selectedWorkoutTypes.contains(cat)
                            Button {
                                if isSelected {
                                    selectedWorkoutTypes.remove(cat)
                                } else {
                                    selectedWorkoutTypes.insert(cat)
                                }
                            } label: {
                                HStack(spacing: 5) {
                                    Text(cat)
                                        .font(.caption.weight(.medium))
                                    if isSelected {
                                        Image(systemName: "checkmark")
                                            .font(.system(size: 10, weight: .bold))
                                    }
                                }
                                .padding(.horizontal, 10)
                                .padding(.vertical, 7)
                                .frame(maxWidth: .infinity)
                                .background(
                                    RoundedRectangle(cornerRadius: 8)
                                        .fill(isSelected ? Color.accentColor : Color(uiColor: .tertiarySystemBackground))
                                )
                                .foregroundStyle(isSelected ? .white : .primary)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(isSelected ? Color.accentColor : Color.secondary.opacity(0.2), lineWidth: 1)
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.vertical, 4)

                    Button {
                        showAddWorkoutType = true
                    } label: {
                        Label("Add Custom Category", systemImage: "plus.circle")
                            .font(.subheadline)
                    }
                }

                if let errorMessage {
                    Section {
                        Text(errorMessage)
                            .foregroundStyle(.red)
                            .font(.footnote)
                    }
                }

                Section {
                    Button {
                        Task { await createGym() }
                    } label: {
                        if isLoading {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                        } else {
                            Text("Create & Launch Gym")
                                .font(.headline)
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .disabled(trimmedName.isEmpty || isLoading)
                }
            }
            .navigationTitle("Set Up Your Gym")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .alert("Add Workout Category", isPresented: $showAddWorkoutType) {
                TextField("e.g. Olympic Lifting", text: $newWorkoutType)
                    .autocorrectionDisabled()
                Button("Add") {
                    let trimmed = newWorkoutType.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !trimmed.isEmpty {
                        customCategories.append(trimmed)
                        selectedWorkoutTypes.insert(trimmed)
                    }
                    newWorkoutType = ""
                }
                Button("Cancel", role: .cancel) { newWorkoutType = "" }
            }
            .sheet(isPresented: $showSuccessModal) {
                if let gym = createdGym {
                    GymCreatedSuccessSheet(gym: gym) {
                        onCreated(gym)
                        dismiss()
                    }
                }
            }
        }
    }

    private func createGym() async {
        isLoading = true
        errorMessage = nil

        let resolvedCategories = selectedWorkoutTypes.isEmpty ? ["General Fitness"] : Array(selectedWorkoutTypes).sorted()

        do {
            let gym = try await FirebaseBackend.shared.createGymForCurrentUser(
                name: trimmedName,
                city: trimmedCity.isEmpty ? nil : trimmedCity,
                joinCode: customJoinCode.isEmpty ? nil : customJoinCode,
                workoutTypes: resolvedCategories
            )
            await MainActor.run {
                self.createdGym = gym
                self.isLoading = false
                self.showSuccessModal = true
            }
        } catch {
            await MainActor.run {
                self.errorMessage = error.localizedDescription
                self.isLoading = false
            }
        }
    }
}

// MARK: - Success / Invite Share Sheet

struct GymCreatedSuccessSheet: View {
    let gym: Gym
    var onFinish: () -> Void

    @State private var copied = false

    private var code: String { gym.joinCode ?? "NEXO" }
    private var inviteLink: URL {
        URL(string: "https://nexo.fit/join/\(code)") ?? URL(string: "https://nexo.fit")!
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Spacer()

                ZStack {
                    Circle()
                        .fill(Color.accentColor.opacity(0.12))
                        .frame(width: 90, height: 90)
                    Image(systemName: "checkmark.seal.fill")
                        .font(.system(size: 48))
                        .foregroundStyle(Color.accentColor)
                }

                VStack(spacing: 8) {
                    Text(gym.name)
                        .font(.title2.bold())
                    Text("Your gym is live and ready!")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                // Join Code Card
                VStack(spacing: 12) {
                    Text("Member Join Code")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .textCase(.uppercase)

                    Text(code)
                        .font(.system(size: 34, weight: .heavy, design: .monospaced))
                        .foregroundStyle(.primary)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 8)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(Color(uiColor: .secondarySystemBackground))
                        )

                    Button {
                        UIPasteboard.general.string = code
                        copied = true
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2) { copied = false }
                    } label: {
                        Label(copied ? "Copied to Clipboard!" : "Copy Join Code", systemImage: copied ? "checkmark" : "doc.on.doc")
                            .font(.footnote.weight(.medium))
                    }
                }
                .padding()
                .frame(maxWidth: .infinity)
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.secondary.opacity(0.2), lineWidth: 1)
                )
                .padding(.horizontal)

                // Share Buttons
                VStack(spacing: 12) {
                    ShareLink(
                        item: inviteLink,
                        subject: Text("Join \(gym.name) on Nexo"),
                        message: Text("Join our gym on Nexo! Download the app and use join code: \(code) or tap the link:")
                    ) {
                        Label("Share Invite Link", systemImage: "square.and.arrow.up")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Color.accentColor)
                            .foregroundStyle(.white)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }

                    Button {
                        onFinish()
                    } label: {
                        Text("Enter Gym Dashboard")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.secondary)
                    }
                    .padding(.top, 4)
                }
                .padding(.horizontal)

                Spacer()
            }
            .padding()
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
