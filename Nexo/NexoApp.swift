//
//  NexoApp.swift
//  Nexo
//
//  Created by Atzmi, Dan on 18/04/2026.
//

import SwiftUI
import FirebaseCore

@main
struct NexoApp: App {
    init() {
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
