//
//  CreateGymView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 25/04/2026.
//

import SwiftUI

/// Platform-Admin-only: creates a gym and assigns its owner by email —
/// the only way a gym gets created (see FEEDBACK.md's "Admin-Only Gym
/// Creation" model). Reachable only from `PlatformDashboardView`.
struct CreateGymView: View {
    @Environment(\.dismiss) private var dismiss

    @State private var gymName = ""
    @State private var city = ""
    private let suggestedCategories = [
        "CrossFit", "HIIT", "Strength", "Yoga", "Pilates",
        "Boxing", "Open Gym", "Spinning", "Cardio", "Mobility"
    ]

    @State private var selectedWorkoutTypes: Set<String> = []
    @State private var customCategories: [String] = []
    @State private var newWorkoutType = ""
    @State private var showAddWorkoutType = false

    @State private var ownerFirstName = ""
    @State private var ownerLastName = ""
    @State private var ownerEmail = ""
    @State private var ownerPassword = ""

    @State private var isLoading = false
    @State private var createdGym: Gym?
    @State private var showSuccessModal = false
    @State private var errorMessage: String?

    var onCreated: (Gym) -> Void

    private var trimmedName: String { gymName.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var trimmedCity: String { city.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var trimmedOwnerEmail: String { ownerEmail.trimmingCharacters(in: .whitespacesAndNewlines) }

    private var allCategories: [String] {
        var list = suggestedCategories
        for custom in customCategories where !list.contains(custom) {
            list.append(custom)
        }
        return list
    }

    private var isValid: Bool {
        !trimmedName.isEmpty && !ownerFirstName.isEmpty && trimmedOwnerEmail.contains("@") && ownerPassword.count >= 6
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

                Section("Select Workout Categories") {
                    Text("Tap the categories this gym offers:")
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

                Section("Assign Owner") {
                    TextField("First Name", text: $ownerFirstName)
                        .autocorrectionDisabled()
                    TextField("Last Name", text: $ownerLastName)
                        .autocorrectionDisabled()
                    TextField("Owner Email", text: $ownerEmail)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.emailAddress)
                        .autocorrectionDisabled()
                    SecureField("Password", text: $ownerPassword)

                    Text("If this email already belongs to a Nexo user, they become the owner — no duplicate account is created.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
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
                            Text("Create & Assign Owner")
                                .font(.headline)
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .disabled(!isValid || isLoading)
                }
            }
            .navigationTitle("Create a Gym")
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
            let gym = try await FirebaseBackend.shared.createGym(
                name: trimmedName,
                city: trimmedCity.isEmpty ? nil : trimmedCity,
                workoutTypes: resolvedCategories,
                ownerFirstName: ownerFirstName.trimmingCharacters(in: .whitespaces),
                ownerLastName: ownerLastName.trimmingCharacters(in: .whitespaces),
                ownerEmail: trimmedOwnerEmail,
                ownerPassword: ownerPassword
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

// MARK: - Success Sheet

struct GymCreatedSuccessSheet: View {
    let gym: Gym
    var onFinish: () -> Void

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
                    Text("Gym created and owner assigned. It's live now.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                }

                Button {
                    onFinish()
                } label: {
                    Text("Continue")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Color.accentColor)
                        .foregroundStyle(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .padding(.horizontal)

                Spacer()
            }
            .padding()
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
