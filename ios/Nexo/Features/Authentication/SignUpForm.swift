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
    var onToggleMode: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            VStack(spacing: 12) {
                AuthInputField(icon: "person", placeholder: "Full Name", text: $name, textContentType: .name)

                AuthInputField(
                    icon: "envelope", placeholder: "Email", text: $email,
                    keyboardType: .emailAddress, textContentType: .emailAddress,
                    autocapitalization: .never, autocorrectionDisabled: true
                )

                AuthInputField(
                    icon: "lock", placeholder: "Password", text: $password,
                    isSecure: true, textContentType: .newPassword
                )

                AuthInputField(
                    icon: "lock", placeholder: "Confirm Password", text: $confirmPassword,
                    isSecure: true, textContentType: .newPassword
                )
            }

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

            Button(action: onToggleMode) {
                Text("Already have an account? \(Text("Sign in").foregroundStyle(.blue).fontWeight(.semibold))")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .padding(.top, 8)
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
