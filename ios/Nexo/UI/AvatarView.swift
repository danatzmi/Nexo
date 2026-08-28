//
//  AvatarView.swift
//  Nexo
//

import SwiftUI
import UIKit

/// Circular profile avatar — renders the decoded Base64 photo when present,
/// otherwise a colored monogram capsule built from the person's initials.
/// The monogram's color is derived deterministically from the name so the
/// same person always gets the same color across screens.
struct AvatarView: View {
    let name: String
    var base64: String?
    var size: CGFloat = 44
    @State private var showFullscreen = false

    private var initials: String {
        let parts = name.split(separator: " ").prefix(2)
        let letters = parts.compactMap { $0.first }.map { String($0) }
        let result = letters.joined().uppercased()
        return result.isEmpty ? "?" : result
    }

    /// Picks one of a fixed palette based on a hash of the name, so the
    /// monogram color is stable across app launches without persisting it.
    private var gradientColors: [Color] {
        let palette: [[Color]] = [
            [.blue, .cyan],
            [.purple, .pink],
            [.orange, .red],
            [.green, .teal],
            [.indigo, .blue],
            [.pink, .orange]
        ]
        let index = abs(name.hashValue) % palette.count
        return palette[index]
    }

    private var uiImage: UIImage? {
        guard let base64, let data = Data(base64Encoded: base64) else { return nil }
        return UIImage(data: data)
    }

    var body: some View {
        let image = uiImage
        return Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                LinearGradient(colors: gradientColors, startPoint: .topLeading, endPoint: .bottomTrailing)
                    .overlay {
                        Text(initials)
                            .font(.system(size: size * 0.38, weight: .semibold))
                            .foregroundStyle(.white)
                    }
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .contentShape(Circle())
        .onTapGesture {
            if image != nil {
                showFullscreen = true
            }
        }
        .fullScreenCover(isPresented: $showFullscreen) {
            if let image {
                AvatarFullscreenView(image: image)
            }
        }
    }
}

/// Fullscreen, high-resolution preview of a tapped avatar photo — dark
/// background, pinch-to-zoom via `scaledToFit` + a close button. Only
/// reachable when the avatar has a real photo (see `AvatarView`'s tap
/// gesture) — tapping a monogram-only avatar does nothing, since there's
/// no photo to preview.
private struct AvatarFullscreenView: View {
    let image: UIImage
    @Environment(\.dismiss) private var dismiss
    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            Image(uiImage: image)
                .resizable()
                .scaledToFit()
                .scaleEffect(scale)
                .padding()
                .gesture(
                    MagnificationGesture()
                        .onChanged { value in
                            scale = max(1, min(4, lastScale * value))
                        }
                        .onEnded { _ in
                            lastScale = scale
                        }
                )

            VStack {
                HStack {
                    Spacer()
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(.white)
                            .padding(12)
                            .background(.ultraThinMaterial, in: Circle())
                    }
                }
                Spacer()
            }
            .padding()
        }
    }
}

#Preview {
    VStack(spacing: 16) {
        AvatarView(name: "Dan Atzmi", size: 64)
        AvatarView(name: "Alex Coach", size: 44)
    }
    .padding()
}
