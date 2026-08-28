//
//  ForgotPasswordViewModel.swift
//  Nexo
//

import Foundation

@MainActor
@Observable
final class ForgotPasswordViewModel {
    private let backend: BackendService

    var email = ""
    var isLoading = false
    var errorMessage: String?
    var didSucceed = false

    var isValid: Bool {
        !email.isEmpty && email.contains("@")
    }

    init(backend: BackendService? = nil) {
        self.backend = backend ?? FirebaseBackend.shared
    }

    func sendResetLink() async {
        guard isValid else { return }
        isLoading = true
        errorMessage = nil
        do {
            try await backend.sendPasswordReset(email: email)
            didSucceed = true
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}
