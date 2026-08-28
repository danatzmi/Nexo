//
//  LogbookViewModel.swift
//  Nexo
//

import Foundation

/// The average number of completed workouts per week in the calendar month
/// immediately before `referenceDate`'s month — e.g. on any date in March,
/// this covers all of February. Weeks-in-month is measured as a fraction
/// (days in month / 7), not a whole-week count, so short and long months
/// stay comparable. A free function (not a method on the view model) so
/// this is directly testable, per `CLAUDE.md`'s "business logic stays out
/// of SwiftUI views" rule.
func calculateWeeklyAveragePreviousMonth(from dates: [Date], referenceDate: Date = Date(), calendar: Calendar = .current) -> Double {
    guard let currentMonthStart = calendar.date(from: calendar.dateComponents([.year, .month], from: referenceDate)),
          let previousMonthStart = calendar.date(byAdding: .month, value: -1, to: currentMonthStart),
          let previousMonthEnd = calendar.date(byAdding: .month, value: 1, to: previousMonthStart),
          let daysInPreviousMonth = calendar.range(of: .day, in: .month, for: previousMonthStart)?.count
    else { return 0.0 }

    let countInPreviousMonth = dates.filter { $0 >= previousMonthStart && $0 < previousMonthEnd }.count
    guard countInPreviousMonth > 0 else { return 0.0 }

    let weeksInPreviousMonth = Double(daysInPreviousMonth) / 7.0
    return Double(countInPreviousMonth) / weeksInPreviousMonth
}

/// The full localized name of the calendar month immediately before
/// `referenceDate`'s month — e.g. "July" for any date in August. Backs the
/// "Weekly Avg (<month>)" stat card label, so it always names the same
/// month `calculateWeeklyAveragePreviousMonth` averages over. A free
/// function for the same testability reason as that one.
func previousMonthName(referenceDate: Date = Date(), calendar: Calendar = .current) -> String {
    guard let currentMonthStart = calendar.date(from: calendar.dateComponents([.year, .month], from: referenceDate)),
          let previousMonthStart = calendar.date(byAdding: .month, value: -1, to: currentMonthStart)
    else { return "" }

    let formatter = DateFormatter()
    formatter.calendar = calendar
    formatter.dateFormat = "MMMM"
    return formatter.string(from: previousMonthStart)
}

/// The personal record (highest logged score) per movement. An entry with
/// no score counts as 0 for comparison, so it only wins when every logged
/// entry for that movement is scoreless — falling back to "most recent
/// session" in that case. Ties broken by earliest date — the date a PR was
/// *first* achieved, not the most recent time it was merely matched again.
/// A free function for the same testability reason as
/// `calculateWeeklyAveragePreviousMonth`.
func personalRecords(from logs: [WorkoutLog]) -> [String: WorkoutLog] {
    var best: [String: WorkoutLog] = [:]
    for log in logs {
        if let existing = best[log.movement] {
            let logScore = log.score ?? 0
            let existingScore = existing.score ?? 0
            if logScore > existingScore || (logScore == existingScore && log.date < existing.date) {
                best[log.movement] = log
            }
        } else {
            best[log.movement] = log
        }
    }
    return best
}

@MainActor
@Observable
final class LogbookViewModel {
    private let backend: BackendService
    let gymId: UUID

    var workoutLogs: [WorkoutLog] = []
    /// Completed (already-started) classes the member has booked in this
    /// gym — the "Total Sessions" count and Activity Timeline source.
    var pastBookings: [GymClass] = []
    var isLoading = false
    var errorMessage: String?

    var totalWorkouts: Int { pastBookings.count }
    var weeklyAveragePrevMonth: Double { calculateWeeklyAveragePreviousMonth(from: pastBookings.map(\.startTime)) }
    var formattedWeeklyAveragePrevMonth: String { String(format: "%.1f", weeklyAveragePrevMonth) }
    var previousMonthName: String { Nexo.previousMonthName() }
    var activityTimeline: [GymClass] { pastBookings.sorted { $0.startTime > $1.startTime } }
    var personalRecords: [String: WorkoutLog] { Nexo.personalRecords(from: workoutLogs) }

    /// Every movement/activity name the member has logged, alphabetically —
    /// no fixed baseline list, so this fits any gym type (strength, yoga,
    /// combat sports, ...) rather than assuming CrossFit-style lifts.
    var displayedMovements: [String] {
        Array(Set(workoutLogs.map(\.movement))).sorted()
    }

    init(gymId: UUID, backend: BackendService? = nil) {
        self.gymId = gymId
        self.backend = backend ?? FirebaseBackend.shared
    }

    func logs(for movement: String) -> [WorkoutLog] {
        workoutLogs.filter { $0.movement == movement }.sorted { $0.date > $1.date }
    }

    func load() async {
        isLoading = true
        guard let uid = backend.currentUID() else {
            isLoading = false
            return
        }
        do {
            async let logsResult = backend.fetchWorkoutLogs(gymId: gymId)
            async let bookingsResult = backend.fetchMemberBookings(gymId: gymId, userId: uid)
            workoutLogs = try await logsResult
            pastBookings = try await bookingsResult.filter { $0.startTime < Date() }
        } catch {
            errorMessage = "Error loading logbook: \(error.localizedDescription)"
        }
        isLoading = false
    }

    func addLog(movement: String, score: Double?, reps: Int?, sets: Int?, date: Date) async {
        let log = WorkoutLog(id: UUID().uuidString, movement: movement, score: score, reps: reps, sets: sets, date: date)

        // Optimistic update — the movement's PR card and history sheet
        // reflect the new entry immediately.
        workoutLogs.append(log)

        do {
            try await backend.addWorkoutLog(gymId: gymId, log: log)
        } catch {
            workoutLogs.removeAll { $0.id == log.id }
            errorMessage = "Failed to save activity: \(error.localizedDescription)"
        }
    }

    func updateLog(_ log: WorkoutLog, movement: String, score: Double?, reps: Int?, sets: Int?, date: Date) async {
        guard let index = workoutLogs.firstIndex(where: { $0.id == log.id }) else { return }
        let original = workoutLogs[index]
        let updated = WorkoutLog(id: log.id, movement: movement, score: score, reps: reps, sets: sets, date: date)

        // Optimistic update — the movement's PR card and history sheet
        // reflect the edit immediately.
        workoutLogs[index] = updated

        do {
            try await backend.updateWorkoutLog(gymId: gymId, log: updated)
        } catch {
            if let currentIndex = workoutLogs.firstIndex(where: { $0.id == log.id }) {
                workoutLogs[currentIndex] = original
            }
            errorMessage = "Failed to update activity: \(error.localizedDescription)"
        }
    }

    func deleteLog(_ log: WorkoutLog) async {
        let index = workoutLogs.firstIndex { $0.id == log.id }
        workoutLogs.removeAll { $0.id == log.id }

        do {
            try await backend.deleteWorkoutLog(gymId: gymId, logId: log.id)
        } catch {
            if let index, index <= workoutLogs.count {
                workoutLogs.insert(log, at: index)
            } else {
                workoutLogs.append(log)
            }
            errorMessage = "Failed to delete activity: \(error.localizedDescription)"
        }
    }
}
