//
//  LogbookView.swift
//  Nexo
//

import SwiftUI

struct LogbookView: View {
    @State private var viewModel: LogbookViewModel
    @State private var segment: Segment = .activity
    @State private var showingAddLog = false
    @State private var selectedMovement: MovementItem?

    private struct MovementItem: Identifiable {
        var id: String { name }
        let name: String
    }

    private enum Segment: String, CaseIterable {
        case activity = "Activity"
        case exercises = "Exercises"
    }

    init(gymId: UUID) {
        _viewModel = State(initialValue: LogbookViewModel(gymId: gymId))
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Picker("Section", selection: $segment) {
                    ForEach(Segment.allCases, id: \.self) { tab in
                        Text(tab.rawValue).tag(tab)
                    }
                }
                .pickerStyle(.segmented)
                .padding()

                ScrollView {
                    VStack(spacing: 16) {
                        if viewModel.isLoading {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                                .padding(.top, 40)
                        } else if segment == .activity {
                            activitySegment
                        } else {
                            exercisesSegment
                        }
                    }
                    .padding(.horizontal)
                    .padding(.top, 4)
                    .padding(.bottom, segment == .exercises ? 100 : 20)
                }
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Logbook")
            .navigationBarTitleDisplayMode(.inline)
            .overlay(alignment: .bottom) {
                if segment == .exercises {
                    addLogButton
                        .padding(.horizontal)
                        .padding(.bottom, 16)
                }
            }
            .alert(
                "Error",
                isPresented: Binding(get: { viewModel.errorMessage != nil }, set: { if !$0 { viewModel.errorMessage = nil } })
            ) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
            .sheet(isPresented: $showingAddLog) {
                AddWorkoutLogSheet(viewModel: viewModel)
            }
            .sheet(item: $selectedMovement) { item in
                MovementHistorySheet(movement: item.name, viewModel: viewModel)
            }
            .task { await viewModel.load() }
        }
    }

    // MARK: - Activity Segment

    private var activitySegment: some View {
        VStack(spacing: 16) {
            HStack(spacing: 12) {
                StatCard(icon: "figure.strengthtraining.traditional", title: "Total Sessions", value: "\(viewModel.totalWorkouts)")
                StatCard(icon: "calendar.badge.clock", title: "Weekly Avg (\(viewModel.previousMonthName))", value: viewModel.formattedWeeklyAveragePrevMonth, valueColor: .orange)
            }

            VStack(alignment: .leading, spacing: 12) {
                Text("Activity Timeline")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundStyle(.secondary)
                    .tracking(0.5)

                if viewModel.activityTimeline.isEmpty {
                    Text("No completed classes yet — book and attend a class to start your timeline.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                } else {
                    VStack(spacing: 14) {
                        ForEach(viewModel.activityTimeline) { gymClass in
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(gymClass.title)
                                        .font(.headline)
                                    Text(gymClass.formattedDateTime)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                            }
                        }
                    }
                }
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .hairlineCard()
        }
    }

    // MARK: - Exercises Segment

    private var exercisesSegment: some View {
        VStack(spacing: 12) {
            if viewModel.displayedMovements.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "list.bullet.clipboard")
                        .font(.title)
                        .foregroundStyle(.secondary)
                    Text("No Logged Activities Yet")
                        .font(.headline)
                    Text("Track building blocks or activities for any gym type — lifts, classes, or bodyweight sessions.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 32)
                .hairlineCard()
            } else {
                ForEach(viewModel.displayedMovements, id: \.self) { movement in
                    Button {
                        selectedMovement = MovementItem(name: movement)
                    } label: {
                        MovementCard(movement: movement, pr: viewModel.personalRecords[movement])
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var addLogButton: some View {
        Button {
            showingAddLog = true
        } label: {
            Label("Log Activity", systemImage: "plus")
                .fontWeight(.semibold)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .foregroundStyle(.white)
                .background(LinearGradient(colors: [.blue, .cyan], startPoint: .leading, endPoint: .trailing))
                .clipShape(Capsule())
                .shadow(color: .blue.opacity(0.35), radius: 16, y: 6)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Stat Card

private struct StatCard: View {
    let icon: String
    let title: String
    let value: String
    var valueColor: Color = .primary

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.system(size: 28, weight: .bold))
                .foregroundStyle(valueColor)
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 20)
        .hairlineCard()
    }
}

// MARK: - Movement Card

private struct MovementCard: View {
    let movement: String
    let pr: WorkoutLog?

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(movement)
                    .font(.headline)
                    .foregroundStyle(.primary)
                if let pr {
                    Text(pr.formattedDetail)
                        .font(.title3)
                        .fontWeight(.bold)
                        .foregroundStyle(.primary)
                    Text("\(pr.score != nil ? "PR" : "Last logged") on \(pr.date.formatted(date: .abbreviated, time: .omitted))")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } else {
                    Text("No sessions logged yet")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundStyle(.tertiary)
        }
        .padding()
        .hairlineCard()
    }
}

// MARK: - Movement History Sheet

private struct MovementHistorySheet: View {
    let movement: String
    var viewModel: LogbookViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var editingLog: WorkoutLog?

    var body: some View {
        NavigationStack {
            Group {
                let logs = viewModel.logs(for: movement)
                if logs.isEmpty {
                    ContentUnavailableView(
                        "No Logs Yet",
                        systemImage: "list.bullet",
                        description: Text("Log a session for \(movement) to see it here")
                    )
                } else {
                    List {
                        ForEach(logs) { log in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(log.formattedDetail)
                                    .font(.headline)
                                Text(log.date.formatted(date: .abbreviated, time: .omitted))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    Task { await viewModel.deleteLog(log) }
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                            }
                            .swipeActions(edge: .leading) {
                                Button {
                                    editingLog = log
                                } label: {
                                    Label("Edit", systemImage: "pencil")
                                }
                                .tint(.blue)
                            }
                        }
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle(movement)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .sheet(item: $editingLog) { log in
                EditActivitySheet(viewModel: viewModel, log: log)
            }
        }
    }
}

// MARK: - Activity Form Fields

/// The movement/score/reps/sets/date fields shared by `AddWorkoutLogSheet`
/// and `EditActivitySheet` — both forms edit the same shape of data,
/// differing only in what happens to it on Save. Score/Reps/Sets are all
/// optional free-text fields (not Steppers, which can't express "blank")
/// so any activity — scored, rep-based, or neither — fits the same form.
private struct ActivityFormFields: View {
    @Binding var movement: String
    @Binding var scoreText: String
    @Binding var repsText: String
    @Binding var setsText: String
    @Binding var date: Date

    var body: some View {
        Section("Activity") {
            TextField("e.g. Squat, Yoga Flow, Heavy Bag Work", text: $movement)
        }

        Section("Details") {
            TextField("Score / Value — optional", text: $scoreText)
                .keyboardType(.decimalPad)
            TextField("Reps — optional", text: $repsText)
                .keyboardType(.numberPad)
            TextField("Sets — optional", text: $setsText)
                .keyboardType(.numberPad)
            DatePicker("Date", selection: $date, displayedComponents: .date)
        }
    }
}

// MARK: - Add Workout Log Sheet

private struct AddWorkoutLogSheet: View {
    var viewModel: LogbookViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var movement = ""
    @State private var scoreText = ""
    @State private var repsText = ""
    @State private var setsText = ""
    @State private var date = Date()
    @State private var isSaving = false

    private var resolvedMovement: String {
        movement.trimmingCharacters(in: .whitespaces)
    }

    private var score: Double? { scoreText.isEmpty ? nil : Double(scoreText) }
    private var reps: Int? { repsText.isEmpty ? nil : Int(repsText) }
    private var sets: Int? { setsText.isEmpty ? nil : Int(setsText) }

    private var isValid: Bool {
        !resolvedMovement.isEmpty
    }

    var body: some View {
        NavigationStack {
            Form {
                ActivityFormFields(movement: $movement, scoreText: $scoreText, repsText: $repsText, setsText: $setsText, date: $date)
            }
            .navigationTitle("Log Activity")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        isSaving = true
                        Task {
                            await viewModel.addLog(movement: resolvedMovement, score: score, reps: reps, sets: sets, date: date)
                            dismiss()
                        }
                    }
                    .disabled(!isValid || isSaving)
                }
            }
        }
    }
}

// MARK: - Edit Activity Sheet

private struct EditActivitySheet: View {
    var viewModel: LogbookViewModel
    let log: WorkoutLog
    @Environment(\.dismiss) private var dismiss

    @State private var movement: String
    @State private var scoreText: String
    @State private var repsText: String
    @State private var setsText: String
    @State private var date: Date
    @State private var isSaving = false

    init(viewModel: LogbookViewModel, log: WorkoutLog) {
        self.viewModel = viewModel
        self.log = log
        _movement = State(initialValue: log.movement)
        _scoreText = State(initialValue: log.score.map { $0.formatted() } ?? "")
        _repsText = State(initialValue: log.reps.map(String.init) ?? "")
        _setsText = State(initialValue: log.sets.map(String.init) ?? "")
        _date = State(initialValue: log.date)
    }

    private var resolvedMovement: String {
        movement.trimmingCharacters(in: .whitespaces)
    }

    private var score: Double? { scoreText.isEmpty ? nil : Double(scoreText) }
    private var reps: Int? { repsText.isEmpty ? nil : Int(repsText) }
    private var sets: Int? { setsText.isEmpty ? nil : Int(setsText) }

    private var isValid: Bool {
        !resolvedMovement.isEmpty
    }

    var body: some View {
        NavigationStack {
            Form {
                ActivityFormFields(movement: $movement, scoreText: $scoreText, repsText: $repsText, setsText: $setsText, date: $date)
            }
            .navigationTitle("Edit Activity")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        isSaving = true
                        Task {
                            await viewModel.updateLog(log, movement: resolvedMovement, score: score, reps: reps, sets: sets, date: date)
                            dismiss()
                        }
                    }
                    .disabled(!isValid || isSaving)
                }
            }
        }
    }
}

#Preview {
    LogbookView(gymId: UUID())
}
