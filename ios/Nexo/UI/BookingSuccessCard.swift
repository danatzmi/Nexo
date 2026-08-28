//
//  BookingSuccessCard.swift
//  Nexo
//
//  Created by Atzmi, Dan on 22/06/2026.
//

import SwiftUI

/// Centered, auto-dismissing success confirmation — replaces the default
/// top toast/system alert for a more premium feel. Generic over title/icon/
/// color so the same card covers both "Booked!" (green checkmark) and
/// "Waitlisted!" (orange clock) — shown via `.bookingSuccessOverlay`.
private struct SuccessCard: View {
    let title: String
    let message: String
    let iconName: String
    let iconColor: Color
    @State private var isAnimating = false

    var body: some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(iconColor)
                    .frame(width: 64, height: 64)
                Image(systemName: iconName)
                    .font(.system(size: 26, weight: .bold))
                    .foregroundStyle(.white)
            }
            .scaleEffect(isAnimating ? 1 : 0.4)

            VStack(spacing: 4) {
                Text(title)
                    .font(.title3)
                    .fontWeight(.bold)
                Text(message)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(28)
        .frame(maxWidth: 260)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 24))
        .shadow(color: .black.opacity(0.15), radius: 20, y: 8)
        .onAppear {
            withAnimation(.spring(response: 0.4, dampingFraction: 0.62)) {
                isAnimating = true
            }
        }
    }
}

private struct SuccessOverlayModifier: ViewModifier {
    @Binding var isPresented: Bool
    let title: String
    let message: String
    let iconName: String
    let iconColor: Color

    func body(content: Content) -> some View {
        content
            .overlay {
                if isPresented {
                    ZStack {
                        Color.clear
                            .ignoresSafeArea()
                        SuccessCard(title: title, message: message, iconName: iconName, iconColor: iconColor)
                    }
                    .transition(.opacity.combined(with: .scale(scale: 0.92)))
                    .allowsHitTesting(false)
                    .task {
                        try? await Task.sleep(nanoseconds: 1_800_000_000)
                        withAnimation(.easeOut(duration: 0.25)) {
                            isPresented = false
                        }
                    }
                }
            }
            .animation(.easeOut(duration: 0.25), value: isPresented)
    }
}

extension View {
    /// A centered, dimmed-background success popup — colored icon circle with a
    /// soft spring scale-up, auto-dismisses after ~1.8s. `message` is shown in
    /// small text below the title (e.g. the class type and time). Defaults to
    /// the original "Booked!" green-checkmark look; pass `title`/`iconName`/
    /// `iconColor` for other outcomes (e.g. "Waitlisted!" / `"clock.fill"` / `.orange`).
    func bookingSuccessOverlay(
        isPresented: Binding<Bool>,
        title: String = "Booked!",
        message: String,
        iconName: String = "checkmark",
        iconColor: Color = .green
    ) -> some View {
        modifier(SuccessOverlayModifier(isPresented: isPresented, title: title, message: message, iconName: iconName, iconColor: iconColor))
    }
}
