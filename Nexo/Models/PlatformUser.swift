//
//  PlatformUser.swift
//  Nexo
//
//  Created by Atzmi, Dan on 25/04/2026.
//

import Foundation

struct PlatformUser: Identifiable {
    let id: String // Firebase UID
    var firstName: String
    var lastName: String
    var email: String
    var role: PlatformRole

    var fullName: String {
        "\(firstName) \(lastName)".trimmingCharacters(in: .whitespaces)
    }

    var displayName: String {
        fullName.isEmpty ? email : fullName
    }
}
