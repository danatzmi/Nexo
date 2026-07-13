//
//  AddWorkoutView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 22/04/2026.
//

import SwiftUI

struct AddWorkoutView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppState.self) private var appState
    @State private var name: String = ""
    @State private var type: WorkoutType = .crossfit
    @State private var description: String = ""
    @State private var isLoading = false
    @State private var errorMessage: String?
    
    var onWorkoutCreated: () -> Void
    
    var body: some View {
        Form {
            Section("Workout Details") {
                TextField("Name", text: $name)
                    .autocorrectionDisabled()
                
                Picker("Type", selection: $type) {
                    ForEach(WorkoutType.allCases, id: \.self) { type in
                        Label(type.rawValue, systemImage: type.icon)
                            .tag(type)
                    }
                }
            }
            
            Section("Workout Description") {
                TextEditor(text: $description)
                    .frame(minHeight: 150)
            }
            
            if let error = errorMessage {
                Section {
                    Text(error)
                        .foregroundStyle(.red)
                        .font(.footnote)
                }
            }
            
            Section {
                Button("Create Workout") {
                    Task { await createWorkout() }
                }
                .frame(maxWidth: .infinity)
                .disabled(name.isEmpty || description.isEmpty || isLoading)
                
                if isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                }
            }
        }
        .navigationTitle("New Workout")
        .navigationBarTitleDisplayMode(.inline)
    }
    
    private func createWorkout() async {
        let workout = Workout(
            name: name,
            type: type,
            description: description,
            exercises: [], // Empty for simplified version
            durationMinutes: 60, // Default
            difficulty: .intermediate // Default
        )
        
        isLoading = true
        errorMessage = nil
        
        do {
            try await FirebaseBackend.shared.createWorkout(gymId: appState.gymId, workout)
            await MainActor.run {
                onWorkoutCreated()
                dismiss()
            }
        } catch {
            await MainActor.run {
                errorMessage = "Error creating workout: \(error.localizedDescription)"
                isLoading = false
            }
        }
    }
}

#Preview {
    NavigationStack {
        AddWorkoutView {
            print("Workout created")
        }
    }
}
