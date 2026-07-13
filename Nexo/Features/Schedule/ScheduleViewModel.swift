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

    func bookClass(_ gymClass: GymClass) async {
        do {
            try await backend.book(gymId: gymId, classId: gymClass.id)
            bookedClassIds.insert(gymClass.id)
        } catch {
            bookingMessage = "Failed to book: \(error.localizedDescription)"
        }
    }

    func cancelBooking(_ gymClass: GymClass) async {
        do {
            try await backend.cancelBooking(gymId: gymId, classId: gymClass.id)
            bookedClassIds.remove(gymClass.id)
        } catch {
            bookingMessage = "Failed to cancel: \(error.localizedDescription)"
        }
    }

    func joinWaitlist(_ gymClass: GymClass) async {
        do {
            try await backend.joinWaitlist(gymId: gymId, classId: gymClass.id)
            waitlistedClassIds.insert(gymClass.id)
        } catch {
            bookingMessage = "Failed to join waitlist: \(error.localizedDescription)"
        }
    }

    func leaveWaitlist(_ gymClass: GymClass) async {
        do {
            try await backend.leaveWaitlist(gymId: gymId, classId: gymClass.id)
            waitlistedClassIds.remove(gymClass.id)
        } catch {
            bookingMessage = "Failed to leave waitlist: \(error.localizedDescription)"
        }
    }
}
