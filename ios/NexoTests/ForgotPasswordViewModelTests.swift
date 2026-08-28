//
//  ForgotPasswordViewModelTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

@MainActor
@Suite("ForgotPasswordViewModel")
struct ForgotPasswordViewModelTests {
    @Test("sendResetLink succeeds and sets didSucceed for a registered email")
    func sendResetLinkSucceeds() async {
        let mock = MockBackendService()
        mock.users["uid-1"] = PlatformUser(id: "uid-1", firstName: "Jane", lastName: "Doe", email: "jane@example.com", role: .user)
        let viewModel = ForgotPasswordViewModel(backend: mock)
        viewModel.email = "jane@example.com"

        await viewModel.sendResetLink()

        #expect(viewModel.didSucceed)
        #expect(viewModel.errorMessage == nil)
        #expect(viewModel.isLoading == false)
    }

    @Test("sendResetLink sets errorMessage and leaves didSucceed false for an unregistered email")
    func sendResetLinkFailureSetsErrorMessage() async {
        let mock = MockBackendService()
        let viewModel = ForgotPasswordViewModel(backend: mock)
        viewModel.email = "nobody@example.com"

        await viewModel.sendResetLink()

        #expect(viewModel.errorMessage != nil)
        #expect(viewModel.didSucceed == false)
        #expect(viewModel.isLoading == false)
    }

    @Test("isValid is false for an empty or malformed email, true for a plausible one")
    func isValidReflectsEmailShape() {
        let viewModel = ForgotPasswordViewModel(backend: MockBackendService())

        viewModel.email = ""
        #expect(viewModel.isValid == false)

        viewModel.email = "not-an-email"
        #expect(viewModel.isValid == false)

        viewModel.email = "jane@example.com"
        #expect(viewModel.isValid)
    }

    @Test("sendResetLink does nothing when the email is invalid")
    func sendResetLinkNoOpForInvalidEmail() async {
        let mock = MockBackendService()
        let viewModel = ForgotPasswordViewModel(backend: mock)
        viewModel.email = "not-an-email"

        await viewModel.sendResetLink()

        #expect(viewModel.didSucceed == false)
        #expect(viewModel.errorMessage == nil)
    }
}
