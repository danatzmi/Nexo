//
//  MembershipPlan.swift
//  Nexo
//

import Foundation

enum PlanType: String, Codable, CaseIterable {
    case monthly = "monthly"
    case classPass = "class_pass"

    var displayName: String {
        switch self {
        case .monthly: return "Monthly Membership"
        case .classPass: return "Multi-Visit Pass"
        }
    }

    var shortName: String {
        switch self {
        case .monthly: return "Monthly"
        case .classPass: return "Multi-Pass"
        }
    }
}

enum PlanComponentType: String, Codable, CaseIterable {
    case unlimited
    case credits

    var displayName: String {
        switch self {
        case .unlimited: return "Unlimited"
        case .credits: return "Credits"
        }
    }
}

enum PlanResetPeriod: String, Codable, CaseIterable {
    case none = "none"
    case monthly = "monthly"

    var displayName: String {
        switch self {
        case .none: return "No reset (Fixed Total)"
        case .monthly: return "Resets Monthly"
        }
    }
}

enum ValidityUnit: String, Codable, CaseIterable {
    case days
    case weeks
    case months
    case years

    var displayName: String {
        rawValue.capitalized
    }

    var calendarComponent: Calendar.Component {
        switch self {
        case .days: return .day
        case .weeks: return .weekOfYear
        case .months: return .month
        case .years: return .year
        }
    }
}

/// One line item within a `MembershipPlan` template, e.g. "Unlimited CrossFit,
/// valid 1 month" or "12 credits/month (resets monthly), valid 1 year".
struct PlanComponent: Codable, Hashable, Identifiable {
    var id: UUID
    var type: PlanComponentType
    /// Whether credits reset every month (e.g. 12 classes / month) or are a fixed total.
    var resetPeriod: PlanResetPeriod
    /// nil means "all class types" — matches any `GymClass.classType`.
    var workoutType: String?
    /// Only meaningful when `type == .credits`.
    var creditCount: Int
    var validityValue: Int
    var validityUnit: ValidityUnit

    init(
        id: UUID = UUID(),
        type: PlanComponentType = .unlimited,
        resetPeriod: PlanResetPeriod = .none,
        workoutType: String? = nil,
        creditCount: Int = 10,
        validityValue: Int = 1,
        validityUnit: ValidityUnit = .months
    ) {
        self.id = id
        self.type = type
        self.resetPeriod = resetPeriod
        self.workoutType = workoutType
        self.creditCount = creditCount
        self.validityValue = validityValue
        self.validityUnit = validityUnit
    }

    var summary: String {
        let scope = workoutType ?? "All Classes"
        let unitName = validityValue == 1 ? String(validityUnit.rawValue.dropLast()) : validityUnit.rawValue
        switch type {
        case .unlimited:
            return "Unlimited \(scope) · Valid for \(validityValue) \(unitName)"
        case .credits:
            if resetPeriod == .monthly {
                return "\(creditCount) \(scope) credits/mo (resets monthly) · Valid for \(validityValue) \(unitName)"
            } else {
                return "\(creditCount) total \(scope) credits · Valid for \(validityValue) \(unitName)"
            }
        }
    }
}

/// A purchasable package template a gym owner defines, categorized as either
/// a Monthly Membership or Multi-Visit Pass, containing one or more components.
struct MembershipPlan: Codable, Hashable, Identifiable {
    var id: UUID
    var name: String
    var type: PlanType
    var price: Double
    var components: [PlanComponent]

    init(
        id: UUID = UUID(),
        name: String = "",
        type: PlanType = .monthly,
        price: Double = 0.0,
        components: [PlanComponent] = []
    ) {
        self.id = id
        self.name = name
        self.type = type
        self.price = price
        self.components = components
    }

    var summary: String {
        if components.isEmpty {
            return "No components added"
        } else if components.count == 1 {
            return components[0].summary
        } else {
            return components.map { comp in
                let scope = comp.workoutType ?? "All"
                if comp.type == .unlimited {
                    return "Unlimited \(scope)"
                } else if comp.resetPeriod == .monthly {
                    return "\(comp.creditCount) \(scope) credits/mo"
                } else {
                    return "\(comp.creditCount) \(scope) credits"
                }
            }.joined(separator: " + ")
        }
    }
}


