//
//  WorkoutDetailView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 22/04/2026.
//

import SwiftUI

struct WorkoutDetailView: View {
    let workout: Workout
    
    var body: some View {
        List {
            // Header Section
            Section {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Image(systemName: workout.type.icon)
                            .font(.title)
                            .foregroundStyle(.blue)
                        
                        VStack(alignment: .leading, spacing: 4) {
                            Text(workout.name)
                                .font(.title2)
                                .fontWeight(.bold)
                            Text(workout.type.rawValue)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        
                        Spacer()
                    }
                    
                    Text(workout.description)
                        .font(.body)
                        .foregroundStyle(.secondary)
                    
                    Label("\(workout.durationMinutes) min", systemImage: "clock")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 8)
            }
            
        }
        .navigationTitle("Workout")
        .navigationBarTitleDisplayMode(.inline)
    }
}



#Preview {
    NavigationStack {
        WorkoutDetailView(workout: Workout.sampleFran)
    }
}
