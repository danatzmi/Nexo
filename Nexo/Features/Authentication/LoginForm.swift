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

    var onAuthenticated: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            VStack(spacing: 1) {
                AuthField(label: "Email", text: $email) {
                    TextField("", text: $email)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.emailAddress)
                        .textContentType(.emailAddress)
                        .autocorrectionDisabled()
                }

                AuthField(label: "Password", text: $password) {
                    SecureField("", text: $password)
                        .textContentType(.password)
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

struct AuthField<Content: View>: View {
    let label: String
    @Binding var text: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        HStack {
            Text(label)
                .foregroundStyle(.secondary)
                .frame(width: 80, alignment: .leading)
            content()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Color(.secondarySystemBackground))
    }
}
