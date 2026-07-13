//
//  CreateGymView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 25/04/2026.
//

import SwiftUI

struct CreateGymView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var gymName = ""
    @State private var ownerFirstName = ""
    @State private var ownerLastName = ""
    @State private var ownerEmail = ""
    @State private var ownerPassword = ""
    @State private var isLoading = false
    @State private var error: String?

    var onCreated: (Gym) -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section("Gym") {
                    TextField("Gym Name", text: $gymName)
                        .autocorrectionDisabled()
                }

                Section("Owner Account") {
                    TextField("First Name", text: $ownerFirstName)
                    TextField("Last Name", text: $ownerLastName)
                    TextField("Email", text: $ownerEmail)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.emailAddress)
                        .autocorrectionDisabled()
                    SecureField("Password", text: $ownerPassword)
                }

                if let error {
                    Section {
                        Text(error)
                            .foregroundStyle(.red)
                            .font(.footnote)
                    }
                }

                Section {
                    Button("Create Gym") {
                        Task { await createGym() }
                    }
                    .frame(maxWidth: .infinity)
                    .disabled(!isValid || isLoading)

                    if isLoading { ProgressView().frame(maxWidth: .infinity) }
                }
            }
            .navigationTitle("New Gym")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private var isValid: Bool {
        !gymName.trimmingCharacters(in: .whitespaces).isEmpty &&
        !ownerFirstName.isEmpty &&
        ownerEmail.contains("@") &&
        ownerPassword.count >= 6
    }

    private func createGym() async {
        isLoading = true
        error = nil
        do {
            let gym = try await FirebaseBackend.shared.createGym(
                name: gymName.trimmingCharacters(in: .whitespaces),
                ownerFirstName: ownerFirstName.trimmingCharacters(in: .whitespaces),
                ownerLastName: ownerLastName.trimmingCharacters(in: .whitespaces),
                ownerEmail: ownerEmail.trimmingCharacters(in: .whitespaces),
                ownerPassword: ownerPassword
            )
            await MainActor.run {
                onCreated(gym)
                dismiss()
            }
        } catch {
            await MainActor.run {
                self.error = error.localizedDescription
                isLoading = false
            }
        }
    }
}
