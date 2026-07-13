//
//  WorkoutsLibraryView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 22/04/2026.
//

import SwiftUI

struct WorkoutsLibraryView: View {
    @State private var viewModel: WorkoutsLibraryViewModel
    @State private var showingAddWorkout = false

    init(gymId: UUID) {
        _viewModel = State(initialValue: WorkoutsLibraryViewModel(gymId: gymId))
    }

    var body: some View {
        VStack(spacing: 0) {
            // Add Workout Button
            HStack {
                Text("\(viewModel.workouts.count) Workouts")
                    .font(.headline)
                    .foregroundStyle(.secondary)
                Spacer()
                Button {
                    showingAddWorkout = true
                } label: {
                    Label("Add Workout", systemImage: "plus")
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()

            // Filter Chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    FilterChip(
                        title: "All",
                        isSelected: viewModel.selectedType == nil
                    ) {
                        viewModel.selectedType = nil
                    }

                    ForEach(WorkoutType.allCases, id: \.self) { type in
                        FilterChip(
                            title: type.rawValue,
                            icon: type.icon,
                            isSelected: viewModel.selectedType == type
                        ) {
                            viewModel.selectedType = type
                        }
                    }
                }
                .padding(.horizontal)
            }
            .padding(.vertical, 12)

            Divider()

            // Workouts List
            if viewModel.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if viewModel.filteredWorkouts.isEmpty {
                ContentUnavailableView(
                    "No Workouts",
                    systemImage: "figure.strengthtraining.traditional",
                    description: Text(viewModel.selectedType == nil ? "Create your first workout to get started" : "No \(viewModel.selectedType?.rawValue ?? "") workouts found")
                )
            } else {
                List(viewModel.filteredWorkouts) { workout in
                    NavigationLink {
                        WorkoutDetailView(workout: workout)
                    } label: {
                        WorkoutCard(workout: workout)
                    }
                }
                .listStyle(.plain)
                .refreshable {
                    await viewModel.loadWorkouts()
                }
            }
        }
        .searchable(text: $viewModel.searchText, prompt: "Search workouts")
        .sheet(isPresented: $showingAddWorkout) {
            NavigationStack {
                AddWorkoutView {
                    Task { await viewModel.loadWorkouts() }
                }
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") {
                            showingAddWorkout = false
                        }
                    }
                }
            }
        }
        .task {
            await viewModel.loadWorkouts()
        }
    }
}

// MARK: - Workout Card

private struct WorkoutCard: View {
    let workout: Workout
    
    var body: some View {
        HStack(spacing: 12) {
            // Icon
            Image(systemName: workout.type.icon)
                .font(.title2)
                .foregroundStyle(.white)
                .frame(width: 50, height: 50)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(.blue.gradient)
                )
            
            // Info
            VStack(alignment: .leading, spacing: 4) {
                Text(workout.name)
                    .font(.headline)
                
                Text(workout.type.rawValue)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                
                Label("\(workout.durationMinutes) min", systemImage: "clock")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            
            Spacer()
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Filter Chip

private struct FilterChip: View {
    let title: String
    var icon: String? = nil
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if let icon = icon {
                    Image(systemName: icon)
                        .font(.caption)
                }
                Text(title)
                    .font(.subheadline)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(
                Capsule()
                    .fill(isSelected ? Color.accentColor : Color(.systemGray5))
            )
            .foregroundStyle(isSelected ? .white : .primary)
        }
    }
}


#Preview {
    WorkoutsLibraryView(gymId: UUID())
}
