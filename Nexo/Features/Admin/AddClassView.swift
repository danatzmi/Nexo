//
//  AddClassView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 20/04/2026.
//

import SwiftUI

enum RepeatFrequency: String, CaseIterable, Identifiable {
    case weekly = "Weekly"
    case biweekly = "Bi-weekly"
    case monthly = "Monthly"

    var id: String { rawValue }

    var calendarComponent: Calendar.Component {
        switch self {
        case .weekly: return .weekOfYear
        case .biweekly: return .weekOfYear
        case .monthly: return .month
        }
    }

    var step: Int { self == .biweekly ? 2 : 1 }
}

struct AddClassView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppState.self) private var appState

    @State private var title: String
    @State private var coach: String
    @State private var startTime: Date
    @State private var duration: Int
    @State private var capacity: Int
    @State private var selectedWorkout: Workout?
    @State private var availableWorkouts: [Workout] = []
    @State private var showWorkoutPicker = false
    @State private var repeatFrequency: RepeatFrequency? = nil
    @State private var repeatEndDate: Date
    @State private var isLoading = false
    @State private var errorMessage: String?

    private let existingClass: GymClass?
    var onSaved: () -> Void

    init(gymClass: GymClass? = nil, onSaved: @escaping () -> Void) {
        self.existingClass = gymClass
        self.onSaved = onSaved
        _title = State(initialValue: gymClass?.title ?? "")
        _coach = State(initialValue: gymClass?.coach ?? "")
        _startTime = State(initialValue: gymClass?.startTime ?? Date().addingTimeInterval(3600))
        _duration = State(initialValue: gymClass?.durationMinutes ?? 60)
        _capacity = State(initialValue: gymClass?.capacity ?? 12)
        _repeatEndDate = State(initialValue: Calendar.current.date(byAdding: .month, value: 3, to: Date()) ?? Date())
    }

    private var isEditMode: Bool { existingClass != nil }

    var body: some View {
        Form {
            Section("Class Details") {
                TextField("Title", text: $title)
                    .autocorrectionDisabled()
                TextField("Coach", text: $coach)
                    .autocorrectionDisabled()
            }

            Section("Schedule") {
                DatePicker("Start Time", selection: $startTime)
                Stepper(value: $duration, in: 15...180, step: 5) {
                    Text("Duration: \(duration) min")
                }
            }

            Section("Capacity") {
                Stepper(value: $capacity, in: 1...50) {
                    Text("Max participants: \(capacity)")
                }
            }

            if !isEditMode {
                Section("Repeat") {
                    Picker("Repeats", selection: $repeatFrequency) {
                        Text("Never").tag(RepeatFrequency?.none)
                        ForEach(RepeatFrequency.allCases) { freq in
                            Text(freq.rawValue).tag(RepeatFrequency?.some(freq))
                        }
                    }
                    if repeatFrequency != nil {
                        DatePicker("End Date", selection: $repeatEndDate, in: startTime..., displayedComponents: .date)
                    }
                }
            }

            Section("Workout (Optional)") {
                Button {
                    showWorkoutPicker = true
                } label: {
                    HStack {
                        Text("Workout")
                            .foregroundStyle(.primary)
                        Spacer()
                        if let workout = selectedWorkout {
                            Text(workout.name)
                                .foregroundStyle(.secondary)
                        } else {
                            Text("None")
                                .foregroundStyle(.tertiary)
                        }
                        Image(systemName: "chevron.right")
                            .font(.caption)
                            .foregroundStyle(.tertiary)
                    }
                }
            }

            if let error = errorMessage {
                Section {
                    Text(error)
                        .foregroundStyle(.red)
                        .font(.footnote)
                }
            }

            Section {
                Button(isEditMode ? "Save Changes" : "Create Class") {
                    Task { await save() }
                }
                .frame(maxWidth: .infinity)
                .disabled(title.isEmpty || coach.isEmpty || isLoading)

                if isLoading {
                    ProgressView().frame(maxWidth: .infinity)
                }
            }
        }
        .navigationTitle(isEditMode ? "Edit Class" : "New Class")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showWorkoutPicker) {
            WorkoutPickerSheet(workouts: availableWorkouts, selectedWorkout: $selectedWorkout)
        }
        .task { await loadWorkouts() }
    }

    private func loadWorkouts() async {
        do {
            availableWorkouts = try await FirebaseBackend.shared.fetchWorkouts(gymId: appState.gymId)
            if let workoutId = existingClass?.workoutId {
                selectedWorkout = availableWorkouts.first { $0.id == workoutId }
            }
        } catch {
            print("Error loading workouts: \(error)")
        }
    }

    private func save() async {
        isLoading = true
        errorMessage = nil

        do {
            if isEditMode {
                let updated = GymClass(
                    id: existingClass!.id,
                    title: title, coach: coach, startTime: startTime,
                    durationMinutes: duration, capacity: capacity,
                    currentAttendees: existingClass!.currentAttendees,
                    attendees: [], workoutId: selectedWorkout?.id,
                    seriesId: existingClass?.seriesId
                )
                try await FirebaseBackend.shared.updateClass(gymId: appState.gymId, updated)
            } else if let freq = repeatFrequency {
                let seriesId = UUID()
                let instances = generateDates(frequency: freq).map { date in
                    GymClass(
                        title: title, coach: coach, startTime: date,
                        durationMinutes: duration, capacity: capacity,
                        workoutId: selectedWorkout?.id, seriesId: seriesId
                    )
                }
                try await FirebaseBackend.shared.createClasses(gymId: appState.gymId, instances)
            } else {
                let single = GymClass(
                    title: title, coach: coach, startTime: startTime,
                    durationMinutes: duration, capacity: capacity,
                    workoutId: selectedWorkout?.id
                )
                try await FirebaseBackend.shared.createClass(gymId: appState.gymId, single)
            }
            await MainActor.run { onSaved(); dismiss() }
        } catch {
            await MainActor.run {
                errorMessage = "Error saving class: \(error.localizedDescription)"
                isLoading = false
            }
        }
    }

    private func generateDates(frequency: RepeatFrequency) -> [Date] {
        var dates: [Date] = []
        var current = startTime
        let calendar = Calendar.current
        while current <= repeatEndDate {
            dates.append(current)
            current = calendar.date(byAdding: frequency.calendarComponent, value: frequency.step, to: current) ?? current
        }
        return dates
    }
}

// MARK: - Workout Picker Sheet

private struct WorkoutPickerSheet: View {
    @Environment(\.dismiss) private var dismiss
    let workouts: [Workout]
    @Binding var selectedWorkout: Workout?

    var body: some View {
        NavigationStack {
            List {
                Button {
                    selectedWorkout = nil
                    dismiss()
                } label: {
                    HStack {
                        Text("No Workout")
                        Spacer()
                        if selectedWorkout == nil {
                            Image(systemName: "checkmark").foregroundStyle(.blue)
                        }
                    }
                }
                .foregroundStyle(.primary)

                ForEach(workouts) { workout in
                    Button {
                        selectedWorkout = workout
                        dismiss()
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: workout.type.icon)
                                .foregroundStyle(.blue)
                                .frame(width: 30)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(workout.name).font(.body).foregroundStyle(.primary)
                                Text(workout.type.rawValue).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            if selectedWorkout?.id == workout.id {
                                Image(systemName: "checkmark").foregroundStyle(.blue)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Select Workout")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}
