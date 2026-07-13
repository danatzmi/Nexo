//
//  ClassDetailView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 20/04/2026.
//

import SwiftUI

struct ClassDetailView: View {
    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss
    @State private var viewModel: ClassDetailViewModel
    @State private var showEdit = false
    @State private var showDeleteConfirm = false

    private var gymClass: GymClass { viewModel.gymClass }

    init(gymId: UUID, gymClass: GymClass) {
        _viewModel = State(initialValue: ClassDetailViewModel(gymId: gymId, gymClass: gymClass))
    }

    var body: some View {
        List {
            Section("Class Info") {
                LabeledContent("Title", value: gymClass.title)
                LabeledContent("Coach", value: gymClass.coach)
                LabeledContent("Starts", value: gymClass.formattedDateTime)
                LabeledContent("Duration", value: "\(gymClass.durationMinutes) min")
                LabeledContent("Capacity", value: "\(gymClass.capacity)")
                LabeledContent("Available Spots", value: "\(gymClass.availableSpots)")
                if gymClass.waitlistCount > 0 {
                    LabeledContent("Waitlist", value: "\(gymClass.waitlistCount) waiting")
                }
            }

            if let workout = viewModel.workout {
                Section("Workout") {
                    NavigationLink {
                        WorkoutDetailView(workout: workout)
                    } label: {
                        HStack {
                            Image(systemName: workout.type.icon)
                                .foregroundStyle(.blue)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(workout.name)
                                    .font(.headline)
                                Text(workout.type.rawValue)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            } else if viewModel.isLoadingWorkout {
                Section("Workout") { ProgressView() }
            } else if gymClass.workoutId != nil {
                Section("Workout") {
                    Text("Failed to load workout")
                        .foregroundStyle(.secondary)
                }
            }

            Section("Attendees (\(viewModel.attendees.count))") {
                if viewModel.isLoadingAttendees {
                    ProgressView()
                } else if viewModel.attendees.isEmpty {
                    Text("No attendees yet")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(viewModel.attendees) { attendee in
                        VStack(alignment: .leading) {
                            Text(attendee.name)
                                .font(.body)
                            Text(attendee.email)
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
        .navigationTitle("Class Details")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if appState.gymRole.canManageClasses {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Button { showEdit = true } label: {
                            Label("Edit", systemImage: "pencil")
                        }
                        Button("Delete", role: .destructive) {
                            showDeleteConfirm = true
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
            }
        }
        .sheet(isPresented: $showEdit) {
            NavigationStack {
                AddClassView(gymClass: gymClass) { }
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { showEdit = false }
                    }
                }
            }
        }
        .confirmationDialog(
            "Delete \"\(gymClass.title)\"?",
            isPresented: $showDeleteConfirm,
            titleVisibility: .visible
        ) {
            if gymClass.seriesId != nil {
                Button("Delete This Class Only", role: .destructive) { Task { await viewModel.deleteClass() } }
                Button("Delete This & Future Classes", role: .destructive) { Task { await viewModel.deleteSeries() } }
            } else {
                Button("Delete", role: .destructive) { Task { await viewModel.deleteClass() } }
            }
            Button("Cancel", role: .cancel) { }
        } message: {
            Text(gymClass.seriesId != nil ? "This is part of a recurring series." : "This cannot be undone.")
        }
        .alert(
            "Error",
            isPresented: Binding(get: { viewModel.errorMessage != nil }, set: { if !$0 { viewModel.errorMessage = nil } })
        ) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
        .onChange(of: viewModel.didDelete) { _, didDelete in
            if didDelete { dismiss() }
        }
        .task { await viewModel.loadWorkout() }
        .task { await viewModel.loadAttendees() }
    }
}

#Preview {
    NavigationStack {
        ClassDetailView(
            gymId: UUID(),
            gymClass: GymClass(
                title: "Morning HIIT",
                coach: "Alex",
                startTime: Date(),
                durationMinutes: 60,
                capacity: 12,
                currentAttendees: 8,
                workoutId: UUID()
            )
        )
    }
}
