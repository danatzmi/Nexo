//
//  GymHomeViewModel.swift
//  Nexo
//

import Foundation

@MainActor
@Observable
final class GymHomeViewModel {
    private let backend: BackendService
    let gymId: UUID

    /// Owner/admin layout: every class scheduled today.
    /// Coach layout: only today's classes coached by the signed-in user.
    var todayClasses: [GymClass] = []
    /// Member layout only.
    var nextBooking: GymClass?
    /// Member layout only — the signed-in member's credit wallet for this
    /// gym, for the Home dashboard's plan/credits widget.
    var activePlans: [ActivePlanItem] = []
    /// The signed-in user's first name, for the dashboard's greeting header.
    /// Best-effort — a failure here never surfaces as `errorMessage`, since
    /// the greeting is cosmetic and shouldn't block the rest of the dashboard.
    var userDisplayName = ""
    var isLoading = false
    var errorMessage: String?

    var totalBookingsToday: Int {
        todayClasses.reduce(0) { $0 + $1.currentAttendees }
    }

    init(gymId: UUID, backend: BackendService? = nil) {
        self.gymId = gymId
        self.backend = backend ?? FirebaseBackend.shared
    }

    /// Owner/admin: every class scheduled today, for the "today's metrics" summary.
    func loadOwnerData() async {
        isLoading = true
        async let profileResult = backend.fetchUserProfile()
        do {
            todayClasses = try await backend.fetchClasses(gymId: gymId, for: Date())
        } catch {
            errorMessage = "Error loading today's classes: \(error.localizedDescription)"
        }
        userDisplayName = (try? await profileResult)?.firstName ?? ""
        isLoading = false
    }

    /// Coach: today's classes filtered to ones this coach teaches. `GymClass.coach`
    /// is a free-text display name (not a UID reference), so matching is by the
    /// signed-in user's profile name — the same value `AddClassView`'s coach field
    /// would have been filled in with.
    func loadCoachData() async {
        isLoading = true
        do {
            let profile = try await backend.fetchUserProfile()
            userDisplayName = profile.firstName
            let classes = try await backend.fetchClasses(gymId: gymId, for: Date())
            todayClasses = classes.filter { $0.coach == profile.fullName }
        } catch {
            errorMessage = "Error loading your classes: \(error.localizedDescription)"
        }
        isLoading = false
    }

    /// Member: their next upcoming booking, if any.
    func loadMemberData() async {
        isLoading = true
        guard let uid = backend.currentUID() else {
            isLoading = false
            return
        }
        async let profileResult = backend.fetchUserProfile()
        do {
            async let bookingsResult = backend.fetchMemberBookings(gymId: gymId, userId: uid)
            async let plansResult = backend.fetchActivePlans(gymId: gymId, userId: uid)
            let bookings = try await bookingsResult
            nextBooking = bookings
                .filter { $0.startTime >= Date() }
                .sorted { $0.startTime < $1.startTime }
                .first
            activePlans = try await plansResult
        } catch {
            errorMessage = "Error loading your dashboard: \(error.localizedDescription)"
        }
        userDisplayName = (try? await profileResult)?.firstName ?? ""
        isLoading = false
    }
}
