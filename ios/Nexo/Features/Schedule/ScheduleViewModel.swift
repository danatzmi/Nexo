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

        // Optimistic update — the popup fires here too, in step with the rest
        // of the optimistic state, instead of waiting on the network round-trip
        // below. The card supplies its own "Booked!" heading, so this is just
        // the supporting subtitle (class type + time), not a full sentence.
        bookedClassIds.insert(gymClass.id)
        updateLocalClass(gymClass.id) { $0.currentAttendees += 1 }
        bookingSuccessMessage = "\(gymClass.title) · \(gymClass.formattedTime)"

        do {
            try await backend.book(gymId: gymId, classId: gymClass.id)
        } catch {
            // Revert on failure
            bookedClassIds.remove(gymClass.id)
            updateLocalClass(gymClass.id) { $0.currentAttendees = max(0, $0.currentAttendees - 1) }
            bookingSuccessMessage = nil
            bookingMessage = "Failed to book: \(error.localizedDescription)"
        }
    }

    func cancelBooking(_ gymClass: GymClass) async {
        guard guardNotPast(gymClass) else { return }

        // Optimistic update
        bookedClassIds.remove(gymClass.id)
        updateLocalClass(gymClass.id) { $0.currentAttendees = max(0, $0.currentAttendees - 1) }

        do {
            try await backend.cancelBooking(gymId: gymId, classId: gymClass.id)
        } catch {
            // Revert on failure
            bookedClassIds.insert(gymClass.id)
            updateLocalClass(gymClass.id) { $0.currentAttendees += 1 }
            bookingMessage = "Failed to cancel: \(error.localizedDescription)"
        }
    }

    func joinWaitlist(_ gymClass: GymClass) async {
        guard guardNotPast(gymClass) else { return }

        // Optimistic update — same pattern as `bookClass`.
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

}
