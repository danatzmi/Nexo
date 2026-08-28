//
//  Member.swift
//  Nexo
//
//  Created by Atzmi, Dan on 20/04/2026.
//

import Foundation

struct Member: Identifiable, Hashable {
    let id: String
    var name: String
    var email: String
    var joinedAt: Date?
    /// JPEG-compressed (quality 0.3) profile photo, Base64-encoded, denormalized
    /// from `users/{uid}` at read time — see `PlatformUser.profilePicBase64`.
    var profilePicBase64: String?
    /// Class attendance check-in status for roster views
    var isCheckedIn: Bool = false
    var checkedInAt: Date? = nil

    init(
        id: String,
        name: String,
        email: String,
        joinedAt: Date? = nil,
        profilePicBase64: String? = nil,
        isCheckedIn: Bool = false,
        checkedInAt: Date? = nil
    ) {
        self.id = id
        self.name = name
        self.email = email
        self.joinedAt = joinedAt
        self.profilePicBase64 = profilePicBase64
        self.isCheckedIn = isCheckedIn
        self.checkedInAt = checkedInAt
    }
}
