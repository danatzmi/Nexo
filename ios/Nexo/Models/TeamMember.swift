//
//  TeamMember.swift
//  Nexo
//
//  Created by Atzmi, Dan on 25/04/2026.
//

import Foundation

struct TeamMember: Identifiable {
    let id: String  // Firebase UID
    var firstName: String
    var lastName: String
    var email: String
    var role: UserRole

    var fullName: String {
        "\(firstName) \(lastName)".trimmingCharacters(in: .whitespaces)
    }
}
