//
//  HairlineCard.swift
//  Nexo
//

import SwiftUI

/// A flat, quiet card surface used throughout the app's minimalist visual
/// language: a plain system background, rounded corners, a hairline border,
/// and a very soft, premium shadow for a touch of depth instead of a filled
/// color or a heavy drop shadow.
struct HairlineCard: ViewModifier {
    var cornerRadius: CGFloat = 16

    func body(content: Content) -> some View {
        content
            .background(Color(.systemBackground))
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(Color.primary.opacity(0.06), lineWidth: 0.5)
            )
            .shadow(color: Color.black.opacity(0.02), radius: 8, x: 0, y: 4)
    }
}

extension View {
    func hairlineCard(cornerRadius: CGFloat = 16) -> some View {
        modifier(HairlineCard(cornerRadius: cornerRadius))
    }
}

/// A titled, spacious card block used in place of `Form`'s `Section` on
/// screens that moved off standard grouped lists — groups related controls
/// inside one hairline-bordered block with generous internal spacing.
struct FormCard<Content: View>: View {
    var title: String?
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            if let title {
                Text(title.uppercased())
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundStyle(.secondary)
                    .tracking(0.5)
            }
            VStack(spacing: 16) {
                content()
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .hairlineCard()
    }
}
