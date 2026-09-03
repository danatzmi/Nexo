//
//  GymSettingsSheet.swift
//  Nexo
//
//  Created by Atzmi, Dan on 22/05/2026.
//

import SwiftUI

/// Owner/Admin-only gym settings: rename the gym and manage its class
/// types. Reused from two entry points — the gym switcher menu (for
/// the currently entered gym) and the Platform Dashboard's gym list swipe
/// action (for any gym, as a platform admin).
struct GymSettingsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppState.self) private var appState

    @State private var name: String
    @State private var workoutTypes: [String]
    @State private var showAddCategory = false
    @State private var newCategoryName = ""
    @State private var isSaving = false
    @State private var errorMessage: String?

    let gym: Gym
    var onSaved: (Gym) -> Void

    init(gym: Gym, onSaved: @escaping (Gym) -> Void) {
        self.gym = gym
        self.onSaved = onSaved
        _name = State(initialValue: gym.name)
        _workoutTypes = State(initialValue: gym.workoutTypes)
    }

    private var trimmedName: String { name.trimmingCharacters(in: .whitespaces) }

    var body: some View {
        NavigationStack {
            Form {
                Section("General Details") {
                    TextField("Gym Name", text: $name)
                        .autocorrectionDisabled()
                }

                Section("Class Types") {
                    ForEach(workoutTypes, id: \.self) { category in
                        Label(category, systemImage: category.categoryIcon)
                            .swipeActions(edge: .trailing, allowsFullSwipe: workoutTypes.count > 1) {
                                if workoutTypes.count > 1 {
                                    Button(role: .destructive) {
                                        workoutTypes.removeAll { $0 == category }
                                    } label: {
                                        Label("Delete", systemImage: "trash")
                                    }
                                }
                            }
                    }

                    Button {
                        showAddCategory = true
                    } label: {
                        Label("Add Class Type", systemImage: "plus")
                    }
                }

                if let errorMessage {
                    Section {
                        Text(errorMessage).foregroundStyle(.red).font(.footnote)
                    }
                }
            }
            .navigationTitle("Gym Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        Task { await save() }
                    }
                    .disabled(trimmedName.isEmpty || isSaving)
                }
            }
            .alert("Add Class Type", isPresented: $showAddCategory) {
                TextField("e.g. Spinning", text: $newCategoryName)
                    .autocorrectionDisabled()
                Button("Add") { addCategory() }
                Button("Cancel", role: .cancel) { newCategoryName = "" }
            } message: {
                Text("Enter a name for the new class type.")
            }
        }
    }

    private func addCategory() {
        let trimmed = newCategoryName.trimmingCharacters(in: .whitespaces)
        newCategoryName = ""
        guard !trimmed.isEmpty, !workoutTypes.contains(trimmed) else { return }
        workoutTypes.append(trimmed)
    }

    private func save() async {
        isSaving = true
        errorMessage = nil

        do {
            try await FirebaseBackend.shared.updateGymSettings(gymId: gym.id, name: trimmedName, workoutTypes: workoutTypes)
            let updatedGym = Gym(id: gym.id, name: trimmedName, ownerUID: gym.ownerUID, workoutTypes: workoutTypes, city: gym.city)
            if appState.currentGym?.id == gym.id {
                appState.currentGym = updatedGym
            }
            if let index = appState.myGyms.firstIndex(where: { $0.gym.id == gym.id }) {
                appState.myGyms[index].gym = updatedGym
            }
            await MainActor.run {
                onSaved(updatedGym)
                dismiss()
            }
        } catch {
            await MainActor.run {
                errorMessage = "Error saving gym settings: \(error.localizedDescription)"
                isSaving = false
            }
        }
    }
}

#Preview {
    GymSettingsSheet(gym: Gym(name: "CrossFit Example", ownerUID: "preview-uid")) { _ in }
}
