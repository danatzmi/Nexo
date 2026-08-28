//
//  AuthenticationTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

@MainActor
@Suite("Authentication")
struct AuthenticationTests {
    @Test("sendPasswordReset succeeds for a registered user's email")
    func sendPasswordResetSucceedsForRegisteredEmail() async throws {
        let mock = MockBackendService()
        mock.users["uid-1"] = PlatformUser(id: "uid-1", firstName: "Jane", lastName: "Doe", email: "jane@example.com", role: .user)

        try await mock.sendPasswordReset(email: "jane@example.com")
    }

    @Test("sendPasswordReset throws userNotFound for an unregistered email")
    func sendPasswordResetThrowsForUnregisteredEmail() async throws {
        let mock = MockBackendService()

        await #expect(throws: MockBackendError.userNotFound) {
            try await mock.sendPasswordReset(email: "nobody@example.com")
        }
    }
}
