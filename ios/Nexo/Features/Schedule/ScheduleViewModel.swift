//
//  ScheduleViewModel.swift
//  Nexo
//

import Foundation

@MainActor
@Observable
final class ScheduleViewModel {
    private let backend: BackendService
    let gymId: UUID

    var classes: [GymClass] = []
    var bookedClassIds: Set<UUID> = []
    var waitlistedClassIds: Set<UUID> = []
    /// The signed-in user's position in a given class's waitlist — populated
    /// per-row by `loadRowDetails(for:canManage:)` as each `ClassRow` appears,
    /// rather than upfront for the whole day (mirrors `ClassDetailViewModel`'s
    /// same fetch, just scoped to one row instead of one screen).
    var waitlistPositions: [UUID: Int] = [:]
    /// Checked-in attendee count per class, staff-only — same per-row
    /// loading rationale as `waitlistPositions`.
    var checkedInCounts: [UUID: Int] = [:]
    var isLoading = false
    var errorMessage: String?
    var selectedDate: Date = .now
    /// Non-nil signals a booking-action failure the view should present as an alert.
    var bookingMessage: String?
    /// Non-nil signals a successful booking the view should present as a
    /// transient, non-blocking toast — cleared by the view immediately after
    /// it's shown, mirroring how `bookingMessage` is cleared by its alert's
    /// dismiss binding, so back-to-back bookings of the same class each
    /// still trigger a fresh toast.
    var bookingSuccessMessage: String?
    /// Non-nil signals a successful waitlist join the view should present as
    /// a transient "Waitlisted!" toast — same pattern as `bookingSuccessMessage`,
    /// kept as a separate property so the view can tell the two outcomes apart
    /// and show the right title/icon.
    var waitlistSuccessMessage: String?
    /// This member's active wallet items for this gym — loaded once
    /// alongside booking/waitlist status, used by `bookingBlockedReason(for:)`
    /// to proactively dim the Book button before a doomed attempt is even
    /// made. Owners/Coaches/Platform Admins bypass this check entirely (the
    /// view skips calling `bookingBlockedReason` for them via
    /// `appState.canManageClasses`), so it doesn't matter that this is
    /// fetched for them too.
    var activePlans: [ActivePlanItem] = []

    private var stopListener: (() -> Void)?

    init(gymId: UUID, backend: BackendService? = nil) {
        self.gymId = gymId
        self.backend = backend ?? FirebaseBackend.shared
    }

    // MARK: - Data

    func loadInitialData() async {
        isLoading = true
        await loadBookingStatus()
        startObserving()
    }

    func dateChanged() {
        classes = []
        isLoading = true
        stopObserving()
        Task {
            await loadBookingStatus()
            startObserving()
        }
    }

    func loadBookingStatus() async {
        do {
            async let bookings = backend.fetchUserBookings(gymId: gymId)
            async let waitlist = backend.fetchUserWaitlist(gymId: gymId)
            bookedClassIds = (try? await bookings) ?? []
            waitlistedClassIds = (try? await waitlist) ?? []
        } catch {
            errorMessage = "Error loading bookings: \(error.localizedDescription)"
        }
        if let uid = backend.currentUID() {
            activePlans = (try? await backend.fetchActivePlans(gymId: gymId, userId: uid)) ?? []
        }
    }

    /// nil when this member has an active plan/credit balance covering
    /// `gymClass` (or when the caller bypasses this check — gated via
    /// `appState.canManageClasses`); otherwise a short reason to show in
    /// place of a dimmed Book button. Mirrors
    /// `ClassDetailViewModel.bookingBlockedReason`.
    func bookingBlockedReason(for gymClass: GymClass) -> String? {
        let matching = activePlans.filter { $0.matches(gymClass: gymClass) }
        if matching.contains(where: { $0.type == .unlimited || $0.availableCredits() > 0 }) {
            return nil
        }
        return matching.isEmpty ? "No active plan" : "No credits remaining"
    }

    func startObserving() {
        stopListener = backend.observeClasses(gymId: gymId, for: selectedDate) { [weak self] updatedClasses in
            self?.classes = updatedClasses.sorted { $0.startTime < $1.startTime }
            self?.isLoading = false
        }
    }

    func stopObserving() {
        stopListener?()
        stopListener = nil
    }

    /// Fetches this row's waitlist position (if the user is waitlisted for
    /// it) and checked-in count (if [canManage]) — called from `ClassRow`'s
    /// `.task(id:)` as each row appears, so the Schedule list can show the
    /// same "Attendees: (x/y) · Waitlist: (...) · Checked In: (...)" text as
    /// `ClassDetailView` without fetching this for every class up front.
    func loadRowDetails(for gymClass: GymClass, canManage: Bool) async {
        if waitlistedClassIds.contains(gymClass.id) {
            if let result = (try? await backend.fetchWaitlistPosition(gymId: gymId, classId: gymClass.id)) ?? nil {
                waitlistPositions[gymClass.id] = result.position
            }
        }
        if canManage {
            if let attendees = try? await backend.fetchAttendees(gymId: gymId, classId: gymClass.id) {
                checkedInCounts[gymClass.id] = attendees.filter { $0.isCheckedIn }.count
            }
        }
    }

    // MARK: - Actions

    /// Mirrors the backend's own `FBError.classInPast`/`MockBackendError.classInPast`
    /// gating — checked here too so the UI fails fast with a clear message instead
    /// of relying solely on a round-trip to the backend to reject the action.
    private func guardNotPast(_ gymClass: GymClass) -> Bool {
        guard gymClass.startTime < Date() else { return true }
        bookingMessage = "Cannot book or join waitlist for a class that has already started."
        return false
    }

    /// The live `observeClasses` listener eventually reconciles `classes` with the
    /// backend anyway, but that round-trip isn't instant — mutating the matching
    /// entry locally right after a successful action keeps the visible capacity/
    /// waitlist counts in sync immediately, without waiting on the listener.
    private func updateLocalClass(_ classId: UUID, _ transform: (inout GymClass) -> Void) {
        guard let index = classes.firstIndex(where: { $0.id == classId }) else { return }
        transform(&classes[index])
    }

    func bookClass(_ gymClass: GymClass) async {
        guard guardNotPast(gymClass) else { return }

        // Optimistic update
        let previousPlans = activePlans
        activePlans = consumeCreditLocally(activePlans, for: gymClass)
        bookedClassIds.insert(gymClass.id)
        updateLocalClass(gymClass.id) { $0.currentAttendees += 1 }
        bookingSuccessMessage = "\(gymClass.title) · \(gymClass.formattedTime)"

        do {
            try await backend.book(gymId: gymId, classId: gymClass.id)
        } catch {
            // Revert on failure
            activePlans = previousPlans
            bookedClassIds.remove(gymClass.id)
            updateLocalClass(gymClass.id) { $0.currentAttendees = max(0, $0.currentAttendees - 1) }
            bookingSuccessMessage = nil
            bookingMessage = "Failed to book: \(error.localizedDescription)"
        }
    }

    func cancelBooking(_ gymClass: GymClass) async {
        guard guardNotPast(gymClass) else { return }

        // Optimistic update
        let previousPlans = activePlans
        activePlans = refundCreditLocally(activePlans, for: gymClass)
        bookedClassIds.remove(gymClass.id)
        updateLocalClass(gymClass.id) { $0.currentAttendees = max(0, $0.currentAttendees - 1) }

        do {
            try await backend.cancelBooking(gymId: gymId, classId: gymClass.id)
        } catch {
            // Revert on failure
            activePlans = previousPlans
            bookedClassIds.insert(gymClass.id)
            updateLocalClass(gymClass.id) { $0.currentAttendees += 1 }
            bookingMessage = "Failed to cancel: \(error.localizedDescription)"
        }
    }

    func joinWaitlist(_ gymClass: GymClass) async {
        guard guardNotPast(gymClass) else { return }

        // Optimistic update
        waitlistedClassIds.insert(gymClass.id)
        updateLocalClass(gymClass.id) { $0.waitlistCount += 1 }
        waitlistSuccessMessage = "\(gymClass.title) · \(gymClass.formattedTime)"

        do {
            try await backend.joinWaitlist(gymId: gymId, classId: gymClass.id)
        } catch {
            // Revert on failure
            waitlistedClassIds.remove(gymClass.id)
            updateLocalClass(gymClass.id) { $0.waitlistCount = max(0, $0.waitlistCount - 1) }
            waitlistSuccessMessage = nil
            bookingMessage = "Failed to join waitlist: \(error.localizedDescription)"
        }
    }

    func leaveWaitlist(_ gymClass: GymClass) async {
        guard guardNotPast(gymClass) else { return }

        // Optimistic update
        waitlistedClassIds.remove(gymClass.id)
        updateLocalClass(gymClass.id) { $0.waitlistCount = max(0, $0.waitlistCount - 1) }

        do {
            try await backend.leaveWaitlist(gymId: gymId, classId: gymClass.id)
        } catch {
            // Revert on failure
            waitlistedClassIds.insert(gymClass.id)
            updateLocalClass(gymClass.id) { $0.waitlistCount += 1 }
            bookingMessage = "Failed to leave waitlist: \(error.localizedDescription)"
        }
    }

    private func consumeCreditLocally(_ plans: [ActivePlanItem], for gymClass: GymClass) -> [ActivePlanItem] {
        if plans.contains(where: { $0.matches(gymClass: gymClass) && $0.type == .unlimited }) { return plans }
        var list = plans
        if let targetIndex = list.firstIndex(where: { $0.matches(gymClass: gymClass) && $0.type == .credits && $0.availableCredits() > 0 }) {
            var item = list[targetIndex]
            if item.resetPeriod == .monthly {
                item.cycleCreditsUsed += 1
            } else {
                item.remainingCredits = max(0, item.remainingCredits - 1)
            }
            list[targetIndex] = item
        }
        return list
    }

    private func refundCreditLocally(_ plans: [ActivePlanItem], for gymClass: GymClass) -> [ActivePlanItem] {
        if plans.contains(where: { $0.matches(gymClass: gymClass) && $0.type == .unlimited }) { return plans }
        var list = plans
        if let targetIndex = list.firstIndex(where: { $0.matches(gymClass: gymClass) && $0.type == .credits }) {
            var item = list[targetIndex]
            if item.resetPeriod == .monthly {
                item.cycleCreditsUsed = max(0, item.cycleCreditsUsed - 1)
            } else {
                item.remainingCredits += 1
            }
            list[targetIndex] = item
        }
        return list
    }
}
