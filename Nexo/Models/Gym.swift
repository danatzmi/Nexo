//
//  Gym.swift
//  Nexo
//
//  Created by Atzmi, Dan on 25/04/2026.
//

import Foundation

struct Gym: Identifiable, Hashable, Codable {
    let id: UUID
    var name: String
    var ownerUID: String

    init(id: UUID = UUID(), name: String, ownerUID: String) {
        self.id = id
        self.name = name
        self.ownerUID = ownerUID
    }
}
