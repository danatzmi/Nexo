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
    /// JPEG-compressed (quality 0.3) profile photo, Base64-encoded — kept small
    /// enough to store inline on the user document rather than in Storage.
    /// `nil` means no photo has been uploaded; the UI falls back to a
    /// monogram avatar.
    var profilePicBase64: String? = nil

    var fullName: String {
        "\(firstName) \(lastName)".trimmingCharacters(in: .whitespaces)
    }

    var displayName: String {
        fullName.isEmpty ? email : fullName
    }
}
