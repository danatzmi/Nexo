//
//  UserRole.swift
//  Nexo
//
//  Created by Atzmi, Dan on 20/04/2026.
//

import Foundation

enum UserRole: String, CaseIterable, Identifiable, Codable {
    case owner
    case coach
    case member
    
    var id: String { rawValue }
    
    var displayName: String {
        switch self {
        case .owner: return "Owner"
        case .coach: return "Coach"
        case .member: return "Member"
        }
    }
    
    var canManageClasses: Bool {
        self == .owner || self == .coach
    }
}
