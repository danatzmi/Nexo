//
//  SignUpForm.swift
//  Nexo
//
//  Created by Atzmi, Dan on 20/04/2026.
//

import SwiftUI

struct SignUpForm: View {
    @State private var name = ""
    @State private var email = ""
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var error: String?
    @State private var isLoading = false

    var onAuthenticated: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            VStack(spacing: 1) {
                AuthField(label: "Full Name", text: $name) {
                    TextField("", text: $name)
                        .textContentType(.name)
                }

                AuthField(label: "Email", text: $email) {
                    TextField("", text: $email)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.emailAddress)
                        .textContentType(.emailAddress)
                        .autocorrectionDisabled()
                }

                AuthField(label: "Password", text: $password) {
                    SecureField("", text: $password)
                        .textContentType(.newPassword)
                }

                AuthField(label: "Confirm", text: $confirmPassword) {
                    SecureField("", text: $confirmPassword)
                        .textContentType(.newPassword)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 12))

            if let error {
                Text(error)
                    .foregroundStyle(.red)
                    .font(.footnote)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            Button {
                Task { await createAccount() }
            } label: {
                Group {
                    if isLoading {
                        ProgressView().tint(.white)
                    } else {
                        Text("Create Account").fontWeight(.semibold)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 50)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!isValid || isLoading)
        }
    }

    private var isValid: Bool {
        !name.isEmpty && email.contains("@") && password.count >= 6 && password == confirmPassword
    }

    private func createAccount() async {
        guard isValid else {
            error = "Please fill all fields correctly"
            return
        }

        error = nil
        isLoading = true

        let names = name.split(separator: " ", maxSplits: 1)
        let firstName = String(names.first ?? "")
        let lastName = names.count > 1 ? String(names[1]) : ""

        do {
            try await FirebaseBackend.shared.signUp(
                email: email,
                password: password,
                firstName: firstName,
                lastName: lastName
            )
            await MainActor.run {
                isLoading = false
                onAuthenticated()
            }
        } catch {
            await MainActor.run {
                self.error = error.localizedDescription
                isLoading = false
            }
        }
    }
}
