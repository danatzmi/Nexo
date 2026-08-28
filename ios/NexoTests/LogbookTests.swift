//
//  LogbookTests.swift
//  NexoTests
//

import Testing
import Foundation
@testable import Nexo

@MainActor
@Suite("Logbook")
struct LogbookTests {
    // MARK: - calculateWeeklyAveragePreviousMonth

    private func isoCalendar() -> Calendar {
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }

    @Test("calculateWeeklyAveragePreviousMonth averages logged dates over the previous calendar month")
    func weeklyAverageComputesOverPreviousMonth() {
        let calendar = isoCalendar()
        // Reference date is in March; previous month is February 2026 (28 days -> 4 weeks exactly).
        let reference = calendar.date(from: DateComponents(year: 2026, month: 3, day: 18))!
        let datesInFebruary = [
            calendar.date(from: DateComponents(year: 2026, month: 2, day: 2))!,
            calendar.date(from: DateComponents(year: 2026, month: 2, day: 9))!,
            calendar.date(from: DateComponents(year: 2026, month: 2, day: 16))!,
            calendar.date(from: DateComponents(year: 2026, month: 2, day: 23))!
        ]

        let average = calculateWeeklyAveragePreviousMonth(from: datesInFebruary, referenceDate: reference, calendar: calendar)

        // 4 workouts over 28/7 = 4 weeks -> 1.0/wk.
        #expect(average == 1.0)
    }

    @Test("calculateWeeklyAveragePreviousMonth ignores dates outside the previous month")
    func weeklyAverageIgnoresDatesOutsidePreviousMonth() {
        let calendar = isoCalendar()
        let reference = calendar.date(from: DateComponents(year: 2026, month: 3, day: 18))!
        let dates = [
            calendar.date(from: DateComponents(year: 2026, month: 2, day: 5))!,
            calendar.date(from: DateComponents(year: 2026, month: 1, day: 5))!, // gap month — not counted
            calendar.date(from: DateComponents(year: 2026, month: 3, day: 5))! // reference's own month — not counted
        ]

        let average = calculateWeeklyAveragePreviousMonth(from: dates, referenceDate: reference, calendar: calendar)

        // 1 workout over 28/7 = 4 weeks -> 0.25/wk.
        #expect(average == 0.25)
    }

    @Test("calculateWeeklyAveragePreviousMonth is 0.0 when the previous month has no logged dates")
    func weeklyAverageZeroWithNoDatesInPreviousMonth() {
        let calendar = isoCalendar()
        let reference = calendar.date(from: DateComponents(year: 2026, month: 3, day: 18))!
        let dates = [calendar.date(from: DateComponents(year: 2026, month: 1, day: 5))!]

        #expect(calculateWeeklyAveragePreviousMonth(from: dates, referenceDate: reference, calendar: calendar) == 0.0)
    }

    @Test("calculateWeeklyAveragePreviousMonth is 0.0 with no dates at all")
    func weeklyAverageZeroWithNoDates() {
        #expect(calculateWeeklyAveragePreviousMonth(from: [], referenceDate: Date()) == 0.0)
    }

    // MARK: - previousMonthName

    @Test("previousMonthName returns the full localized name of the month before referenceDate's month")
    func previousMonthNameReturnsFullMonthName() {
        let calendar = isoCalendar()
        let reference = calendar.date(from: DateComponents(year: 2026, month: 3, day: 18))!

        #expect(previousMonthName(referenceDate: reference, calendar: calendar) == "February")
    }

    @Test("previousMonthName wraps back to December across a year boundary")
    func previousMonthNameWrapsAcrossYearBoundary() {
        let calendar = isoCalendar()
        let reference = calendar.date(from: DateComponents(year: 2026, month: 1, day: 5))!

        #expect(previousMonthName(referenceDate: reference, calendar: calendar) == "December")
    }

    // MARK: - formattedWeeklyAveragePrevMonth

    @Test("formattedWeeklyAveragePrevMonth formats to 1 decimal place with no unit suffix")
    func formattedWeeklyAverageHasNoSuffix() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        mock.signedInUID = "member-1"
        let viewModel = LogbookViewModel(gymId: gymId, backend: mock)
        await viewModel.load()

        #expect(viewModel.formattedWeeklyAveragePrevMonth == "0.0")
        #expect(!viewModel.formattedWeeklyAveragePrevMonth.contains("/wk"))
        #expect(!viewModel.formattedWeeklyAveragePrevMonth.contains("wk"))
    }

    // MARK: - personalRecords

    @Test("personalRecords picks the highest logged score per movement")
    func personalRecordsPicksHeaviestWeight() {
        let logs = [
            WorkoutLog(id: "1", movement: "Squat", score: 100, reps: 5, sets: 3, date: Date().addingTimeInterval(-86400 * 3)),
            WorkoutLog(id: "2", movement: "Squat", score: 120, reps: 3, sets: 3, date: Date().addingTimeInterval(-86400)),
            WorkoutLog(id: "3", movement: "Deadlift", score: 150, reps: 5, sets: 1, date: Date())
        ]

        let prs = personalRecords(from: logs)

        #expect(prs["Squat"]?.score == 120)
        #expect(prs["Deadlift"]?.score == 150)
    }

    @Test("personalRecords breaks score ties by the earliest date")
    func personalRecordsTieBreaksByEarliestDate() {
        let earlier = Date().addingTimeInterval(-86400 * 10)
        let later = Date().addingTimeInterval(-86400 * 2)
        let logs = [
            WorkoutLog(id: "1", movement: "Squat", score: 100, reps: 5, sets: 3, date: later),
            WorkoutLog(id: "2", movement: "Squat", score: 100, reps: 5, sets: 3, date: earlier)
        ]

        let prs = personalRecords(from: logs)

        #expect(prs["Squat"]?.date == earlier)
    }

    @Test("personalRecords treats a nil score as 0, so a scored entry always beats a scoreless one")
    func personalRecordsTreatsNilScoreAsZero() {
        let logs = [
            WorkoutLog(id: "1", movement: "Yoga Flow", score: nil, reps: nil, sets: nil, date: Date().addingTimeInterval(-86400)),
            WorkoutLog(id: "2", movement: "Yoga Flow", score: 5, reps: nil, sets: nil, date: Date())
        ]

        let prs = personalRecords(from: logs)

        #expect(prs["Yoga Flow"]?.score == 5)
    }

    // MARK: - WorkoutLog.formattedDetail

    @Test("formattedDetail shows <sets> × <reps> @ <score> when sets, reps, and score are present")
    func formattedDetailShowsSetsRepsScore() {
        let log = WorkoutLog(id: "1", movement: "Squat", score: 100, reps: 5, sets: 3, date: Date())
        #expect(log.formattedDetail == "3 × 5 @ 100")
    }

    @Test("formattedDetail shows only score when sets or reps are not present")
    func formattedDetailShowsOnlyScoreWhenSetsOrRepsMissing() {
        let logScoreOnly = WorkoutLog(id: "1", movement: "Run", score: 5.5, reps: nil, sets: nil, date: Date())
        #expect(logScoreOnly.formattedDetail == "5.5")

        let logScoreAndReps = WorkoutLog(id: "2", movement: "Pull-ups", score: 20, reps: 15, sets: nil, date: Date())
        #expect(logScoreAndReps.formattedDetail == "20")

        let logScoreAndSets = WorkoutLog(id: "3", movement: "Plank", score: 60, reps: nil, sets: 3, date: Date())
        #expect(logScoreAndSets.formattedDetail == "60")
    }

    @Test("formattedDetail shows sets × reps when score is nil")
    func formattedDetailShowsSetsAndRepsWhenScoreNil() {
        let log = WorkoutLog(id: "1", movement: "Push-ups", score: nil, reps: 20, sets: 4, date: Date())
        #expect(log.formattedDetail == "4 × 20")
    }

    @Test("formattedDetail shows reps or sets when score is nil and only one is present")
    func formattedDetailShowsRepsOrSetsWhenScoreNil() {
        let logReps = WorkoutLog(id: "1", movement: "Yoga Flow", score: nil, reps: 10, sets: nil, date: Date())
        #expect(logReps.formattedDetail == "10 reps")

        let logSets = WorkoutLog(id: "2", movement: "Stretching", score: nil, reps: nil, sets: 3, date: Date())
        #expect(logSets.formattedDetail == "3 sets")
    }

    @Test("formattedDetail falls back to \"Logged\" when score, reps, and sets are all nil")
    func formattedDetailFallsBackWhenAllNil() {
        let log = WorkoutLog(id: "1", movement: "Rest Day Check-In", score: nil, reps: nil, sets: nil, date: Date())
        #expect(log.formattedDetail == "Logged")
    }

    // MARK: - MockBackendService CRUD

    @Test("addWorkoutLog then fetchWorkoutLogs returns the saved log")
    func addAndFetchWorkoutLog() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        mock.signedInUID = "member-1"
        let log = WorkoutLog(id: "log-1", movement: "Squat", score: 100, reps: 5, sets: 3, date: Date())

        try await mock.addWorkoutLog(gymId: gymId, log: log)
        let fetched = try await mock.fetchWorkoutLogs(gymId: gymId)

        #expect(fetched == [log])
    }

    @Test("fetchWorkoutLogs is scoped per-user — another member's logs aren't visible")
    func fetchWorkoutLogsIsScopedPerUser() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        mock.signedInUID = "member-1"
        try await mock.addWorkoutLog(gymId: gymId, log: WorkoutLog(id: "log-1", movement: "Squat", score: 100, reps: 5, sets: 3, date: Date()))

        mock.signedInUID = "member-2"
        let fetched = try await mock.fetchWorkoutLogs(gymId: gymId)

        #expect(fetched.isEmpty)
    }

    @Test("deleteWorkoutLog removes exactly that log")
    func deleteWorkoutLogRemovesLog() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        mock.signedInUID = "member-1"
        let logA = WorkoutLog(id: "log-1", movement: "Squat", score: 100, reps: 5, sets: 3, date: Date())
        let logB = WorkoutLog(id: "log-2", movement: "Deadlift", score: 150, reps: 5, sets: 3, date: Date())
        try await mock.addWorkoutLog(gymId: gymId, log: logA)
        try await mock.addWorkoutLog(gymId: gymId, log: logB)

        try await mock.deleteWorkoutLog(gymId: gymId, logId: "log-1")

        let fetched = try await mock.fetchWorkoutLogs(gymId: gymId)
        #expect(fetched == [logB])
    }

    // MARK: - LogbookViewModel

    @Test("load populates workoutLogs and filters pastBookings to already-started classes")
    func loadPopulatesLogsAndPastBookings() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        mock.signedInUID = "member-1"
        try await mock.addWorkoutLog(gymId: gymId, log: WorkoutLog(id: "log-1", movement: "Squat", score: 100, reps: 5, sets: 3, date: Date()))

        let past = GymClass(title: "Past Class", coach: "Alex", startTime: Date().addingTimeInterval(-3600))
        let future = GymClass(title: "Future Class", coach: "Sam", startTime: Date().addingTimeInterval(3600))
        mock.classes[gymId] = [past.id: past, future.id: future]
        mock.seedBookingForTesting(gymId: gymId, classId: past.id, userId: "member-1")
        mock.grantUnlimitedForTesting(gymId: gymId, userId: "member-1")
        try await mock.book(gymId: gymId, classId: future.id)

        let viewModel = LogbookViewModel(gymId: gymId, backend: mock)
        await viewModel.load()

        #expect(viewModel.workoutLogs.count == 1)
        #expect(viewModel.pastBookings.map(\.id) == [past.id])
        #expect(viewModel.totalWorkouts == 1)
    }

    @Test("addLog optimistically appends, then persists to the backend")
    func addLogPersistsToBackend() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        mock.signedInUID = "member-1"
        let viewModel = LogbookViewModel(gymId: gymId, backend: mock)

        await viewModel.addLog(movement: "Squat", score: 100, reps: 5, sets: 3, date: Date())

        #expect(viewModel.workoutLogs.count == 1)
        let fetched = try await mock.fetchWorkoutLogs(gymId: gymId)
        #expect(fetched.count == 1)
    }

    @Test("addLog supports nil score/reps/sets for activities with no numeric value")
    func addLogSupportsAllNilFields() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        mock.signedInUID = "member-1"
        let viewModel = LogbookViewModel(gymId: gymId, backend: mock)

        await viewModel.addLog(movement: "Yoga Flow", score: nil, reps: nil, sets: nil, date: Date())

        #expect(viewModel.workoutLogs.count == 1)
        #expect(viewModel.workoutLogs.first?.score == nil)
        #expect(viewModel.workoutLogs.first?.reps == nil)
        #expect(viewModel.workoutLogs.first?.sets == nil)
        let fetched = try await mock.fetchWorkoutLogs(gymId: gymId)
        #expect(fetched.first?.score == nil)
    }

    @Test("addLog reverts the optimistic append on backend failure")
    func addLogRevertsOnFailure() async {
        let mock = MockBackendService()
        let gymId = UUID()
        mock.signedInUID = "member-1"
        mock.errorToThrow = MockBackendError.injected
        let viewModel = LogbookViewModel(gymId: gymId, backend: mock)

        await viewModel.addLog(movement: "Squat", score: 100, reps: 5, sets: 3, date: Date())

        #expect(viewModel.workoutLogs.isEmpty)
        #expect(viewModel.errorMessage != nil)
    }

    @Test("updateLog optimistically edits, then persists to the backend")
    func updateLogPersistsToBackend() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        mock.signedInUID = "member-1"
        let log = WorkoutLog(id: "log-1", movement: "Squat", score: 100, reps: 5, sets: 3, date: Date())
        try await mock.addWorkoutLog(gymId: gymId, log: log)
        let viewModel = LogbookViewModel(gymId: gymId, backend: mock)
        await viewModel.load()

        await viewModel.updateLog(log, movement: "Front Squat", score: 90, reps: 3, sets: 5, date: log.date)

        #expect(viewModel.workoutLogs.count == 1)
        #expect(viewModel.workoutLogs.first?.movement == "Front Squat")
        #expect(viewModel.workoutLogs.first?.score == 90)
        let fetched = try await mock.fetchWorkoutLogs(gymId: gymId)
        #expect(fetched.first?.movement == "Front Squat")
    }

    @Test("updateLog can clear a previously-set score/reps/sets back to nil")
    func updateLogCanClearFieldsToNil() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        mock.signedInUID = "member-1"
        let log = WorkoutLog(id: "log-1", movement: "Squat", score: 100, reps: 5, sets: 3, date: Date())
        try await mock.addWorkoutLog(gymId: gymId, log: log)
        let viewModel = LogbookViewModel(gymId: gymId, backend: mock)
        await viewModel.load()

        await viewModel.updateLog(log, movement: "Squat", score: nil, reps: nil, sets: nil, date: log.date)

        #expect(viewModel.workoutLogs.first?.score == nil)
        #expect(viewModel.workoutLogs.first?.reps == nil)
        #expect(viewModel.workoutLogs.first?.sets == nil)
    }

    @Test("updateLog reverts the optimistic edit on backend failure")
    func updateLogRevertsOnFailure() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        mock.signedInUID = "member-1"
        let log = WorkoutLog(id: "log-1", movement: "Squat", score: 100, reps: 5, sets: 3, date: Date())
        try await mock.addWorkoutLog(gymId: gymId, log: log)
        let viewModel = LogbookViewModel(gymId: gymId, backend: mock)
        await viewModel.load()

        mock.errorToThrow = MockBackendError.injected
        await viewModel.updateLog(log, movement: "Front Squat", score: 90, reps: 3, sets: 5, date: log.date)

        #expect(viewModel.workoutLogs.first?.movement == "Squat")
        #expect(viewModel.workoutLogs.first?.score == 100)
        #expect(viewModel.errorMessage != nil)
    }

    @Test("deleteLog optimistically removes, then persists to the backend")
    func deleteLogPersistsToBackend() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        mock.signedInUID = "member-1"
        let log = WorkoutLog(id: "log-1", movement: "Squat", score: 100, reps: 5, sets: 3, date: Date())
        try await mock.addWorkoutLog(gymId: gymId, log: log)
        let viewModel = LogbookViewModel(gymId: gymId, backend: mock)
        await viewModel.load()

        await viewModel.deleteLog(log)

        #expect(viewModel.workoutLogs.isEmpty)
        let fetched = try await mock.fetchWorkoutLogs(gymId: gymId)
        #expect(fetched.isEmpty)
    }

    @Test("deleteLog reverts the optimistic removal on backend failure")
    func deleteLogRevertsOnFailure() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        mock.signedInUID = "member-1"
        let log = WorkoutLog(id: "log-1", movement: "Squat", score: 100, reps: 5, sets: 3, date: Date())
        try await mock.addWorkoutLog(gymId: gymId, log: log)
        let viewModel = LogbookViewModel(gymId: gymId, backend: mock)
        await viewModel.load()

        mock.errorToThrow = MockBackendError.injected
        await viewModel.deleteLog(log)

        #expect(viewModel.workoutLogs.count == 1)
        #expect(viewModel.errorMessage != nil)
    }

    @Test("displayedMovements lists every logged movement/activity name alphabetically, with no fixed baseline list")
    func displayedMovementsOrdering() async throws {
        let mock = MockBackendService()
        let gymId = UUID()
        mock.signedInUID = "member-1"
        try await mock.addWorkoutLog(gymId: gymId, log: WorkoutLog(id: "1", movement: "Yoga Flow", score: nil, reps: nil, sets: nil, date: Date()))
        try await mock.addWorkoutLog(gymId: gymId, log: WorkoutLog(id: "2", movement: "Squat", score: 100, reps: 5, sets: 3, date: Date()))
        try await mock.addWorkoutLog(gymId: gymId, log: WorkoutLog(id: "3", movement: "Curl", score: 15, reps: 10, sets: 3, date: Date()))

        let viewModel = LogbookViewModel(gymId: gymId, backend: mock)
        await viewModel.load()

        #expect(viewModel.displayedMovements == ["Curl", "Squat", "Yoga Flow"])
    }
}
