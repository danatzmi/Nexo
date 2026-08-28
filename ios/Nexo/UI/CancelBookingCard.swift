//
//  CancelBookingCard.swift
//  Nexo
//
//  Created by Atzmi, Dan on 22/06/2026.
//

import SwiftUI

/// Centered cancel-booking confirmation card — replaces the system
/// `.confirmationDialog` bottom sheet for a more premium, on-brand feel.
/// Shown via `.cancelBookingOverlay`.
private struct CancelBookingCard: View {
    let classTitle: String
    let onConfirm: () -> Void
    let onKeep: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            VStack(spacing: 8) {
                Text("Cancel Booking?")
                    .font(.headline)
                Text("Are you sure you want to cancel your booking for \(classTitle)?")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            VStack(spacing: 10) {
                Button(role: .destructive, action: onConfirm) {
                    Text("Cancel Booking")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .tint(.red)
                .clipShape(Capsule())

                Button(action: onKeep) {
                    Text("Keep Booking")
                        .fontWeight(.medium)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                }
                .buttonStyle(.bordered)
                .tint(.gray)
                .clipShape(Capsule())
            }
        }
        .padding(24)
        .frame(maxWidth: 300)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24))
        .shadow(color: .black.opacity(0.15), radius: 20, y: 8)
    }
}

private struct CancelBookingOverlayModifier: ViewModifier {
    @Binding var isPresented: Bool
    let classTitle: String
    let onConfirm: () -> Void

    func body(content: Content) -> some View {
        content
            .overlay {
                if isPresented {
                    ZStack {
                        Color.black.opacity(0.001) // Invisible touch blocker
                            .ignoresSafeArea()
                        CancelBookingCard(
                            classTitle: classTitle,
                            onConfirm: {
                                isPresented = false
                                onConfirm()
                            },
                            onKeep: {
                                isPresented = false
                            }
                        )
                    }
                    .transition(.opacity.combined(with: .scale(scale: 0.92)))
                }
            }
            .animation(.easeOut(duration: 0.2), value: isPresented)
    }
}

extension View {
    /// A centered, dimmed-background cancel-booking confirmation card —
    /// replaces the system `.confirmationDialog` bottom sheet. `onConfirm`
    /// runs (and the card dismisses) only when "Cancel Booking" is tapped;
    /// "Keep Booking" just dismisses.
    func cancelBookingOverlay(isPresented: Binding<Bool>, classTitle: String, onConfirm: @escaping () -> Void) -> some View {
        modifier(CancelBookingOverlayModifier(isPresented: isPresented, classTitle: classTitle, onConfirm: onConfirm))
    }
}
