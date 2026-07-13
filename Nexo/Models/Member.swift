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

    init(id: String, name: String, email: String, joinedAt: Date? = nil) {
        self.id = id
        self.name = name
        self.email = email
        self.joinedAt = joinedAt
    }
}
