//
//  ClassDetailViewModel.swift
//  Nexo
//

import Foundation

@MainActor
@Observable
final class ClassDetailViewModel {
    private let backend: BackendService
    let gymId: UUID
    /// Mutable (not `let`) so booking actions below can optimistically update
    /// `currentAttendees`/`waitlistCount` locally, mirroring the pattern in
    /// `ScheduleViewModel` — this is a single-class detail page, not a list,
    /// so there's no `updateLocalClass(_:)` helper to route through.
    var gymClass: GymClass

    var attendees: [Member] = []
    var isLoadingAttendees = false
    var isTogglingAttendance = false
    var errorMessage: String?
    /// Flips true once a delete succeeds — the view observes this to dismiss itself.
    var didDelete = false

    var checkedInCount: Int {
        attendees.filter { $0.isCheckedIn }.count
    }

    var isBooked = false
    var isWaitlisted = false
    /// This user's 1-based position in the waitlist, and the total number
    /// waiting — populated by `loadBookingStatus()` whenever `isWaitlisted`
    /// is true, for the "Waitlist Position" stat badge. `nil` when not
    /// waitlisted (or not yet loaded).
    var waitlistPosition: Int? = nil
    var waitlistTotal = 0
    /// Non-nil signals a booking-action failure the view should present as an alert.
    var bookingMessage: String?
    /// Non-nil signals a successful booking the view should present as the
    /// centered "Booked!" popup — mirrors `ScheduleViewModel.bookingSuccessMessage`.
    var bookingSuccessMessage: String?
    /// Non-nil signals a successful waitlist join the view should present as
    /// the centered "Waitlisted!" popup — mirrors `bookingSuccessMessage`,
    /// kept separate so the view can tell the two outcomes apart and show
    /// the right title/icon.
    var waitlistSuccessMessage: String?

    init(gymId: UUID, gymClass: GymClass, backend: BackendService? = nil) {
        self.gymId = gymId
        self.gymClass = gymClass
        self.backend = backend ?? FirebaseBackend.shared
    }

    func loadAttendees() async {
        isLoadingAttendees = true
        do {
            attendees = try await backend.fetchAttendees(gymId: gymId, classId: gymClass.id)
        } catch {
            errorMessage = "Error loading attendees: \(error.localizedDescription)"
        }
        isLoadingAttendees = false
    }

    func toggleAttendance(for member: Member) async {
        guard let index = attendees.firstIndex(where: { $0.id == member.id }) else { return }
        let newStatus = !member.isCheckedIn

        // Optimistic update
        attendees[index].isCheckedIn = newStatus
        attendees[index].checkedInAt = newStatus ? Date() : nil

        do {
            try await backend.toggleAttendance(gymId: gymId, classId: gymClass.id, userId: member.id, isCheckedIn: newStatus)
        } catch {
            // Revert on error
            attendees[index].isCheckedIn = !newStatus
            attendees[index].checkedInAt = !newStatus ? Date() : nil
            errorMessage = "Failed to update check-in: \(error.localizedDescription)"
        }
    }

    func deleteClass() async {
        do {
            try await backend.deleteClass(gymId: gymId, classId: gymClass.id)
            didDelete = true
        } catch {
            errorMessage = "Failed to delete class: \(error.localizedDescription)"
        }
    }

    func deleteSeries() async {
        guard let seriesId = gymClass.seriesId else { return }
        do {
            try await backend.deleteClassSeries(gymId: gymId, seriesId: seriesId, from: gymClass.startTime)
            didDelete = true
        } catch {
            errorMessage = "Failed to delete series: \(error.localizedDescription)"
        }
    }

    // MARK: - Booking

    func loadBookingStatus() async {
        async let bookings = backend.fetchUserBookings(gymId: gymId)
        async let waitlist = backend.fetchUserWaitlist(gymId: gymId)
        isBooked = (try? await bookings)?.contains(gymClass.id) ?? false
        isWaitlisted = (try? await waitlist)?.contains(gymClass.id) ?? false

        if isWaitlisted, let result = (try? await backend.fetchWaitlistPosition(gymId: gymId, classId: gymClass.id)) ?? nil {
            waitlistPosition = result.position
            waitlistTotal = result.total
        } else {
            waitlistPosition = nil
            waitlistTotal = 0
        }
    }

    /// Mirrors `ScheduleViewModel`'s own `guardNotPast` — checked client-side too
    /// so the UI fails fast instead of relying solely on a round-trip to the backend.
    private func guardNotPast() -> Bool {
        guard gymClass.startTime < Date() else { return true }
        bookingMessage = "Cannot book or join waitlist for a class that has already started."
        return false
    }

    func book() async {
        guard guardNotPast() else { return }

        isBooked = true
        gymClass.currentAttendees += 1
        bookingSuccessMessage = "\(gymClass.title) · \(gymClass.formattedTime)"

        do {
            try await backend.book(gymId: gymId, classId: gymClass.id)
        } catch {
            isBooked = false
            gymClass.currentAttendees = max(0, gymClass.currentAttendees - 1)
            bookingSuccessMessage = nil
            bookingMessage = "Failed to book: \(error.localizedDescription)"
        }
    }

    func cancelBooking() async {
        guard guardNotPast() else { return }

        isBooked = false
        gymClass.currentAttendees = max(0, gymClass.currentAttendees - 1)

        do {
            try await backend.cancelBooking(gymId: gymId, classId: gymClass.id)
        } catch {
            isBooked = true
            gymClass.currentAttendees += 1
            bookingMessage = "Failed to cancel: \(error.localizedDescription)"
        }
    }

    func joinWaitlist() async {
        guard guardNotPast() else { return }

        isWaitlisted = true
        gymClass.waitlistCount += 1
        waitlistSuccessMessage = "\(gymClass.title) · \(gymClass.formattedTime)"

        do {
            try await backend.joinWaitlist(gymId: gymId, classId: gymClass.id)
        } catch {
            isWaitlisted = false
            gymClass.waitlistCount = max(0, gymClass.waitlistCount - 1)
            waitlistSuccessMessage = nil
            bookingMessage = "Failed to join waitlist: \(error.localizedDescription)"
        }
    }

    func leaveWaitlist() async {
        guard guardNotPast() else { return }

        isWaitlisted = false
        gymClass.waitlistCount = max(0, gymClass.waitlistCount - 1)

        do {
            try await backend.leaveWaitlist(gymId: gymId, classId: gymClass.id)
        } catch {
            isWaitlisted = true
            gymClass.waitlistCount += 1
            bookingMessage = "Failed to leave waitlist: \(error.localizedDescription)"
        }
    }
}
