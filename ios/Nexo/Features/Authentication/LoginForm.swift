//
//  LoginForm.swift
//  Nexo
//
//  Created by Atzmi, Dan on 20/04/2026.
//

import SwiftUI

struct LoginForm: View {
    @State private var email = ""
    @State private var password = ""
    @State private var error: String?
    @State private var isLoading = false
    @State private var showingForgotPassword = false

    var onAuthenticated: () -> Void
    var onToggleMode: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            VStack(spacing: 12) {
                AuthInputField(
                    icon: "envelope", placeholder: "Email", text: $email,
                    keyboardType: .emailAddress, textContentType: .emailAddress,
                    autocapitalization: .never, autocorrectionDisabled: true
                )
                AuthInputField(
                    icon: "lock", placeholder: "Password", text: $password,
                    isSecure: true, textContentType: .password
                )
            }

            Button {
                showingForgotPassword = true
            } label: {
                Text("Forgot Password?")
                    .font(.footnote)
                    .foregroundStyle(.blue)
            }
            .frame(maxWidth: .infinity, alignment: .trailing)

            if let error {
                Text(error)
                    .foregroundStyle(.red)
                    .font(.footnote)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            Button {
                Task { await signIn() }
            } label: {
                Group {
                    if isLoading {
                        ProgressView().tint(.white)
                    } else {
                        Text("Sign In").fontWeight(.semibold)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 50)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!isValid || isLoading)

            Button(action: onToggleMode) {
                Text("New to Nexo? \(Text("Create an account").foregroundStyle(.blue).fontWeight(.semibold))")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .padding(.top, 8)
        }
        .sheet(isPresented: $showingForgotPassword) {
            ForgotPasswordView()
        }
    }

    private var isValid: Bool {
        !email.isEmpty && email.contains("@") && password.count >= 6
    }

    private func signIn() async {
        guard isValid else {
            error = "Please enter a valid email and a 6+ char password"
            return
        }

        error = nil
        isLoading = true

        do {
            try await FirebaseBackend.shared.signIn(email: email, password: password)
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
