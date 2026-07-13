//
//  AddMemberView.swift
//  Nexo
//

import SwiftUI

struct AddMemberView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppState.self) private var appState
    @State private var firstName = ""
    @State private var lastName = ""
    @State private var email = ""
    @State private var password = ""
    @State private var isLoading = false
    @State private var error: String?

    var onAdded: () -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section("Member Account") {
                    TextField("First Name", text: $firstName)
                    TextField("Last Name", text: $lastName)
                    TextField("Email", text: $email)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.emailAddress)
                        .autocorrectionDisabled()
                    SecureField("Password", text: $password)
                }

                if let error {
                    Section {
                        Text(error)
                            .foregroundStyle(.red)
                            .font(.footnote)
                    }
                }

                Section {
                    Button("Add Member") {
                        Task { await addMember() }
                    }
                    .frame(maxWidth: .infinity)
                    .disabled(!isValid || isLoading)

                    if isLoading { ProgressView().frame(maxWidth: .infinity) }
                }
            }
            .navigationTitle("Add Member")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private var isValid: Bool {
        !firstName.isEmpty && email.contains("@") && password.count >= 6
    }

    private func addMember() async {
        isLoading = true
        error = nil
        do {
            try await FirebaseBackend.shared.addMember(
                gymId: appState.gymId,
                firstName: firstName.trimmingCharacters(in: .whitespaces),
                lastName: lastName.trimmingCharacters(in: .whitespaces),
                email: email.trimmingCharacters(in: .whitespaces),
                password: password
            )
            await MainActor.run {
                onAdded()
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
