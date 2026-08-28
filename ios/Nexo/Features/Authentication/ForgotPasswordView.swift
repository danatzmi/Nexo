//
//  ForgotPasswordView.swift
//  Nexo
//

import SwiftUI

struct ForgotPasswordView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var viewModel: ForgotPasswordViewModel

    init(backend: BackendService? = nil) {
        _viewModel = State(initialValue: ForgotPasswordViewModel(backend: backend))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 32) {
                    VStack(spacing: 8) {
                        Image(systemName: "lock.rotation")
                            .font(.system(size: 40))
                            .foregroundStyle(Color.accentColor)
                        Text("Reset Password")
                            .font(.title2)
                            .fontWeight(.bold)
                        Text("Enter your email address and we'll send you a link to reset your password.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.top, 32)

                    AuthInputField(
                        icon: "envelope", placeholder: "Email", text: $viewModel.email,
                        keyboardType: .emailAddress, textContentType: .emailAddress,
                        autocapitalization: .never, autocorrectionDisabled: true
                    )

                    if viewModel.didSucceed {
                        Label("Reset email sent! Please check your inbox.", systemImage: "checkmark.circle.fill")
                            .font(.footnote)
                            .foregroundStyle(.green)
                    }

                    if let errorMessage = viewModel.errorMessage {
                        Text(errorMessage)
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }

                    Button {
                        Task { await viewModel.sendResetLink() }
                    } label: {
                        Group {
                            if viewModel.isLoading {
                                ProgressView().tint(.white)
                            } else {
                                Text("Send Reset Link").fontWeight(.semibold)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(!viewModel.isValid || viewModel.isLoading)
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 32)
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}

#Preview {
    ForgotPasswordView()
}
