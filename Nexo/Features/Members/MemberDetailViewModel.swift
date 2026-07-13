//
//  MemberDetailViewModel.swift
//  Nexo
//

import Foundation

@MainActor
@Observable
final class MemberDetailViewModel {
    private let backend: BackendService
    let gymId: UUID
    let member: Member

    var bookings: [GymClass] = []
    var isLoading = false
    var errorMessage: String?

    var upcomingBookings: [GymClass] {
        bookings.filter { $0.startTime >= Date() }.sorted { $0.startTime < $1.startTime }
    }

    var pastBookings: [GymClass] {
        bookings.filter { $0.startTime < Date() }
    }

    init(gymId: UUID, member: Member, backend: BackendService? = nil) {
        self.gymId = gymId
        self.member = member
        self.backend = backend ?? FirebaseBackend.shared
    }

    func loadBookings() async {
        isLoading = true
        do {
            bookings = try await backend.fetchMemberBookings(gymId: gymId, userId: member.id)
        } catch {
            errorMessage = "Error loading bookings: \(error.localizedDescription)"
        }
        isLoading = false
    }

    func cancelBooking(_ gymClass: GymClass) async {
        do {
            try await backend.cancelBooking(gymId: gymId, classId: gymClass.id, onBehalfOf: member.id)
            bookings.removeAll { $0.id == gymClass.id }
        } catch {
            errorMessage = "Failed to cancel booking: \(error.localizedDescription)"
        }
    }
}
