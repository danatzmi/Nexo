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
            VStack(spacing: 32) {
                VStack(spacing: 8) {
                    Image(systemName: "figure.strengthtraining.traditional")
                        .font(.system(size: 56))
                        .foregroundStyle(Color.accentColor)
                    Text("GymApp")
                        .font(.largeTitle)
                        .fontWeight(.bold)
                }
                .padding(.top, 48)

                Picker("Mode", selection: $mode.animation()) {
                    Text("Sign In").tag(Mode.login)
                    Text("Create Account").tag(Mode.signup)
                }
                .pickerStyle(.segmented)

                if mode == .login {
                    LoginForm(onAuthenticated: onAuthenticated)
                        .transition(.opacity)
                } else {
                    SignUpForm(onAuthenticated: onAuthenticated)
                        .transition(.opacity)
                }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 32)
        }
    }
}
