//
//  AddClassView.swift
//  Nexo
//
//  Created by Atzmi, Dan on 20/04/2026.
//

import SwiftUI

enum RecurrenceType: String, CaseIterable, Identifiable {
    case none = "Never"
    case daily = "Every Day"
    case weekly = "Weekly"
    case biweekly = "Bi-weekly"
    case monthly = "Monthly"
    case custom = "Custom Days"

    var id: String { rawValue }
}

/// Computes each occurrence date for a class series under the given
/// recurrence rule. A free function (not a method on the `AddClassView`
/// struct) so it's a pure, directly-testable calculation — per
/// `CLAUDE.md`'s "business logic stays out of SwiftUI views" rule, even
/// for a helper this small.
func generateRecurrenceDates(
    recurrenceType: RecurrenceType,
    startTime: Date,
    endDate: Date,
    selectedWeekdays: Set<Int> = [],
    calendar: Calendar = .current
) -> [Date] {
    guard recurrenceType != .none else {
        return [startTime]
    }

    var dates: [Date] = []
    var current = startTime

    while current <= endDate {
        switch recurrenceType {
        case .none:
            break
        case .daily:
            dates.append(current)
            current = calendar.date(byAdding: .day, value: 1, to: current) ?? current
        case .weekly:
            dates.append(current)
            current = calendar.date(byAdding: .weekOfYear, value: 1, to: current) ?? current
        case .biweekly:
            dates.append(current)
            current = calendar.date(byAdding: .weekOfYear, value: 2, to: current) ?? current
        case .monthly:
            dates.append(current)
            current = calendar.date(byAdding: .month, value: 1, to: current) ?? current
        case .custom:
            let weekday = calendar.component(.weekday, from: current)
            if selectedWeekdays.contains(weekday) {
                dates.append(current)
            }
            current = calendar.date(byAdding: .day, value: 1, to: current) ?? current
        }
    }
    return dates
}

struct AddClassView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppState.self) private var appState

    @State private var coach: String
    @State private var startTime: Date
    @State private var duration: Int
    @State private var capacity: Int
    @State private var classType: String
    @State private var isPremium: Bool
    @State private var description: String
    @State private var availableCoaches: [String] = []
    @State private var recurrenceType: RecurrenceType = .none
    @State private var selectedWeekdays: Set<Int> = []
    @State private var repeatEndDate: Date
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var showSeriesUpdatePrompt = false

    private let existingClass: GymClass?
    var onSaved: () -> Void

    init(gymClass: GymClass? = nil, onSaved: @escaping () -> Void) {
        self.existingClass = gymClass
        self.onSaved = onSaved
        _coach = State(initialValue: gymClass?.coach ?? "")
        _startTime = State(initialValue: gymClass?.startTime ?? Date().addingTimeInterval(3600))
        _duration = State(initialValue: gymClass?.durationMinutes ?? 60)
        _capacity = State(initialValue: gymClass?.capacity ?? 12)
        _classType = State(initialValue: gymClass?.classType ?? "CrossFit WOD")
        _isPremium = State(initialValue: gymClass?.isPremium ?? false)
        _description = State(initialValue: gymClass?.description ?? "")
        _repeatEndDate = State(initialValue: Calendar.current.date(byAdding: .month, value: 3, to: Date()) ?? Date())
    }

    private var isEditMode: Bool { existingClass != nil }
    private var availableCategories: [String] { appState.currentGym?.workoutTypes ?? WorkoutCategory.defaults }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                FormCard(title: "Class Details") {
                    HStack {
                        Text("Class Type")
                        Spacer()
                        Picker("", selection: $classType) {
                            ForEach(availableCategories, id: \.self) { type in
                                Text(type).tag(type)
                            }
                        }
                        .labelsHidden()
                    }
                    Divider().opacity(0.5)
                    HStack {
                        Text("Coach")
                        Spacer()
                        Picker("", selection: $coach) {
                            Text("Unassigned").tag("")
                            ForEach(availableCoaches, id: \.self) { name in
                                Text(name).tag(name)
                            }
                        }
                        .labelsHidden()
                    }
                    Divider().opacity(0.5)
                    Toggle("Requires Additional Pay", isOn: $isPremium)
                }

                FormCard(title: "Description") {
                    TextEditor(text: $description)
                        .frame(minHeight: 100)
                }

                FormCard(title: "Schedule") {
                    DatePicker("Start Time", selection: $startTime)
                    Divider().opacity(0.5)
                    Stepper(value: $duration, in: 15...180, step: 5) {
                        Text("Duration: \(duration) min")
                    }
                }

                FormCard(title: "Capacity") {
                    Stepper(value: $capacity, in: 1...50) {
                        Text("Max participants: \(capacity)")
                    }
                }

                if !isEditMode {
                    FormCard(title: "Repeat") {
                        Picker("Repeats", selection: $recurrenceType) {
                            ForEach(RecurrenceType.allCases) { type in
                                Text(type.rawValue).tag(type)
                            }
                        }
                        if recurrenceType == .custom {
                            Divider().opacity(0.5)
                            WeekdayToggleRow(selectedWeekdays: $selectedWeekdays)
                        }
                        if recurrenceType != .none {
                            Divider().opacity(0.5)
                            DatePicker("End Date", selection: $repeatEndDate, in: startTime..., displayedComponents: .date)
                        }
                    }
                }

                if let error = errorMessage {
                    Text(error)
                        .foregroundStyle(.red)
                        .font(.footnote)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                Button {
                    handleSaveTapped()
                } label: {
                    Group {
                        if isLoading {
                            ProgressView()
                        } else {
                            Text(isEditMode ? "Save Changes" : "Create Class").fontWeight(.semibold)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                }
                .buttonStyle(.borderedProminent)
                .clipShape(Capsule())
                .disabled(isLoading)
            }
            .padding(.horizontal)
            .padding(.vertical, 20)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle(isEditMode ? "Edit Class" : "New Class")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog(
            "Update this class only or all future occurrences in the series?",
            isPresented: $showSeriesUpdatePrompt,
            titleVisibility: .visible
        ) {
            Button("This Class Only") { Task { await save(applyToSeries: false) } }
            Button("This & Future Classes") { Task { await save(applyToSeries: true) } }
            Button("Cancel", role: .cancel) { }
        }
        .task {
            await loadCoaches()
        }
    }

    /// Intercepts Save for a recurring class being edited — prompts whether
    /// the change should apply to just this occurrence or the whole future
    /// series before actually writing anything. Non-recurring/new classes
    /// save immediately, same as before.
    private func handleSaveTapped() {
        if isEditMode, existingClass?.seriesId != nil {
            showSeriesUpdatePrompt = true
        } else {
            Task { await save() }
        }
    }

    private func loadCoaches() async {
        do {
            let team = try await FirebaseBackend.shared.fetchTeam(gymId: appState.gymId)
            var coachNames = team.filter { $0.role.canManageClasses }.map { $0.fullName }
            if let existingCoach = existingClass?.coach, !existingCoach.isEmpty, !coachNames.contains(existingCoach) {
                coachNames.append(existingCoach)
            }
            availableCoaches = coachNames.sorted()
        } catch {
            print("Error loading coaches: \(error)")
        }
    }

    private func save(applyToSeries: Bool = false) async {
        isLoading = true
        errorMessage = nil

        do {
            if isEditMode {
                let updated = GymClass(
                    id: existingClass!.id,
                    title: classType, coach: coach, startTime: startTime,
                    durationMinutes: duration, capacity: capacity,
                    currentAttendees: existingClass!.currentAttendees,
                    attendees: [],
                    seriesId: existingClass?.seriesId, classType: classType,
                    isPremium: isPremium, description: description
                )
                if applyToSeries, let seriesId = existingClass?.seriesId {
                    try await FirebaseBackend.shared.updateClassSeries(
                        gymId: appState.gymId,
                        seriesId: seriesId,
                        from: existingClass!.startTime,
                        updatedTemplate: updated
                    )
                } else {
                    try await FirebaseBackend.shared.updateClass(gymId: appState.gymId, updated)
                }
            } else if recurrenceType != .none {
                let seriesId = UUID()
                let instances = generateDates().map { date in
                    GymClass(
                        title: classType, coach: coach, startTime: date,
                        durationMinutes: duration, capacity: capacity,
                        seriesId: seriesId, classType: classType,
                        isPremium: isPremium, description: description
                    )
                }
                try await FirebaseBackend.shared.createClasses(gymId: appState.gymId, instances)
            } else {
                let single = GymClass(
                    title: classType, coach: coach, startTime: startTime,
                    durationMinutes: duration, capacity: capacity,
                    classType: classType,
                    isPremium: isPremium, description: description
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

    private func generateDates() -> [Date] {
        generateRecurrenceDates(
            recurrenceType: recurrenceType,
            startTime: startTime,
            endDate: repeatEndDate,
            selectedWeekdays: selectedWeekdays
        )
    }
}

// MARK: - Weekday Toggle Row

private struct WeekdayToggleRow: View {
    @Binding var selectedWeekdays: Set<Int>

    /// Index 0 = weekday `1` (Sunday) through index 6 = weekday `7` (Saturday),
    /// matching `Calendar.component(.weekday, from:)`'s 1-based convention.
    private let symbols = ["S", "M", "T", "W", "T", "F", "S"]

    var body: some View {
        HStack(spacing: 8) {
            ForEach(1...7, id: \.self) { day in
                let isSelected = selectedWeekdays.contains(day)
                Button {
                    if isSelected {
                        selectedWeekdays.remove(day)
                    } else {
                        selectedWeekdays.insert(day)
                    }
                } label: {
                    Text(symbols[day - 1])
                        .font(.caption)
                        .fontWeight(.semibold)
                        .frame(width: 32, height: 32)
                        .background(
                            Circle().fill(isSelected ? Color.blue : Color(.systemGray5))
                        )
                        .foregroundStyle(isSelected ? .white : .primary)
                }
                .buttonStyle(.plain)
            }
        }
        .frame(maxWidth: .infinity)
    }
}
