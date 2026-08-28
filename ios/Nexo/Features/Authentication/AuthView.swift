//
//  AuthView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 20/04/2026.
//

import SwiftUI

struct AuthView: View {
    enum Mode { case login, signup }

    @State private var mode: Mode = .login

    var onAuthenticated: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 40) {
                VStack(spacing: 8) {
                    Image("NexoLogo")
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(width: 80, height: 80)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                    Text("Nexo")
                        .font(.system(size: 40, weight: .bold, design: .rounded))
                    Text("Your training community, unified.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .padding(.top, 64)

                Group {
                    if mode == .login {
                        LoginForm(onAuthenticated: onAuthenticated, onToggleMode: toggleMode)
                            .transition(.opacity)
                    } else {
                        SignUpForm(onAuthenticated: onAuthenticated, onToggleMode: toggleMode)
                            .transition(.opacity)
                    }
                }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 32)
        }
    }

    private func toggleMode() {
        withAnimation {
            mode = mode == .login ? .signup : .login
        }
    }
}
