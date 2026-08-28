//
//  BackendService.swift
//  Nexo
//
//  Created by Atzmi, Dan on 20/04/2026.
//

import Foundation

/// `@MainActor` so every conformance's methods run on the same actor as the
/// `@MainActor` ViewModels that call them — without this, a struct built from
/// SwiftUI `@State` (e.g. an array-of-structs like `MembershipPlan.components`)
/// can cross onto a different thread mid-call while the view still holds a
/// live reference to the same underlying buffer, a data race that manifests
/// as a hard-to-diagnose `EXC_BAD_ACCESS`.
@MainActor
protocol BackendService {
    // MARK: - Authentication
    func signIn(email: String, password: String) async throws
    func signUp(email: String, password: String, firstName: String, lastName: String) async throws
    func signOut() throws
    func sendPasswordReset(email: String) async throws
    func currentUID() -> String?
    func fetchPlatformRole() async throws -> PlatformRole
    func fetchUserProfile() async throws -> PlatformUser
    /// Uploads a new profile photo for the signed-in user — `base64String`
    /// is expected to already be a compressed (small) JPEG, Base64-encoded,
    /// per `ProfileView`'s upload flow.
    func updateProfilePicture(base64String: String) async throws

    // MARK: - Platform (admin only)
    func fetchAllUsers() async throws -> [PlatformUser]
    func updatePlatformRole(uid: String, role: PlatformRole) async throws

    // MARK: - Gyms
    func fetchMyGyms() async throws -> [(gym: Gym, role: UserRole)]
    func fetchAvailableGyms() async throws -> [Gym]
    func createGym(name: String, ownerFirstName: String, ownerLastName: String, ownerEmail: String, ownerPassword: String) async throws -> Gym
    /// Creates a new gym owned by the currently signed-in user, with an optional custom join code and city.
    func createGymForCurrentUser(name: String, city: String?, joinCode: String?, workoutTypes: [String]) async throws -> Gym
    func joinGym(gymId: UUID) async throws
    /// Resolves a 6-character join code (e.g. "IRON99") and attaches the signed-in user as a member.
    func joinGymByCode(code: String) async throws -> Gym
    /// Looks up gym details by join code without joining yet, for the live confirmation preview card.
    func fetchGymByJoinCode(code: String) async throws -> Gym?
    /// Attaches an existing platform user (found via `fetchAllUsers()`) to a
    /// gym with the given role, without registering a new Auth account —
    /// the counterpart to `addMember`/`addTeamMember`'s "brand-new account"
    /// path when the person already has one.
    func addExistingUserToGym(gymId: UUID, userId: String, role: UserRole) async throws
    func updateGymSettings(gymId: UUID, name: String, workoutTypes: [String]) async throws
    func deleteGym(gymId: UUID) async throws

    // MARK: - Team
    func fetchTeam(gymId: UUID) async throws -> [TeamMember]
    func addTeamMember(gymId: UUID, firstName: String, lastName: String, email: String, password: String, role: UserRole) async throws
    func updateTeamMemberRole(gymId: UUID, userId: String, role: UserRole) async throws
    func removeTeamMember(gymId: UUID, userId: String) async throws

    // MARK: - Classes
    func fetchClasses(gymId: UUID, for date: Date) async throws -> [GymClass]
    func fetchAllClasses(gymId: UUID) async throws -> [GymClass]
    func createClass(gymId: UUID, _ newClass: GymClass) async throws
    func createClasses(gymId: UUID, _ classes: [GymClass]) async throws
    func updateClass(gymId: UUID, _ gymClass: GymClass) async throws
    /// Applies `updatedTemplate`'s editable fields (title/coach/duration/
    /// capacity/isPremium/description/classType) to every occurrence in the
    /// series starting on or after `startTime`, while preserving each
    /// occurrence's own date — only the time-of-day shifts to match
    /// `updatedTemplate.startTime`. The counterpart to `updateClass` for
    /// `AddClassView`'s "This & Future Classes" edit option.
    func updateClassSeries(gymId: UUID, seriesId: UUID, from startTime: Date, updatedTemplate: GymClass) async throws
    func deleteClass(gymId: UUID, classId: UUID) async throws
    func deleteClassSeries(gymId: UUID, seriesId: UUID, from date: Date) async throws
    func observeClasses(gymId: UUID, for date: Date, onChange: @escaping ([GymClass]) -> Void) -> () -> Void
    func copySchedule(gymId: UUID, fromWeekOf sourceDate: Date, toWeekOf targetDate: Date) async throws

    // MARK: - Booking
    func book(gymId: UUID, classId: UUID) async throws
    func cancelBooking(gymId: UUID, classId: UUID) async throws
    func cancelBooking(gymId: UUID, classId: UUID, onBehalfOf userId: String) async throws
    func fetchUserBookings(gymId: UUID) async throws -> Set<UUID>
    func isUserBooked(gymId: UUID, classId: UUID) async throws -> Bool

    // MARK: - Waitlist
    func joinWaitlist(gymId: UUID, classId: UUID) async throws
    func leaveWaitlist(gymId: UUID, classId: UUID) async throws
    func fetchUserWaitlist(gymId: UUID) async throws -> Set<UUID>
    /// This user's 1-based position in `classId`'s waitlist (sorted by
    /// `joinedAt`) and the total number waiting, or `nil` if they're not
    /// on the waitlist at all.
    func fetchWaitlistPosition(gymId: UUID, classId: UUID) async throws -> (position: Int, total: Int)?

    // MARK: - Attendees
    func fetchAttendees(gymId: UUID, classId: UUID) async throws -> [Member]
    func toggleAttendance(gymId: UUID, classId: UUID, userId: String, isCheckedIn: Bool) async throws

    // MARK: - Members
    func fetchMembers(gymId: UUID) async throws -> [Member]
    func fetchMemberBookings(gymId: UUID, userId: String) async throws -> [GymClass]
    func addMember(gymId: UUID, firstName: String, lastName: String, email: String, password: String) async throws
    func removeMember(gymId: UUID, userId: String) async throws

    // MARK: - Membership Plans & Credit Wallet
    func createMembershipPlan(gymId: UUID, plan: MembershipPlan) async throws
    func fetchMembershipPlans(gymId: UUID) async throws -> [MembershipPlan]
    func updateMembershipPlan(gymId: UUID, plan: MembershipPlan) async throws
    func deleteMembershipPlan(gymId: UUID, planId: UUID) async throws
    func grantPlanToMember(gymId: UUID, userId: String, plan: MembershipPlan, customExpiresAt: Date?) async throws
    func revokeActivePlan(gymId: UUID, userId: String, activePlanId: String) async throws
    func fetchActivePlans(gymId: UUID, userId: String) async throws -> [ActivePlanItem]

    // MARK: - Logbook
    /// The signed-in member's own workout logs for this gym — stored per-member
    /// (`gyms/{gymId}/members/{userId}/workoutLogs`), not shared across the gym.
    func fetchWorkoutLogs(gymId: UUID) async throws -> [WorkoutLog]
    func addWorkoutLog(gymId: UUID, log: WorkoutLog) async throws
    func updateWorkoutLog(gymId: UUID, log: WorkoutLog) async throws
    func deleteWorkoutLog(gymId: UUID, logId: String) async throws
}

extension BackendService {
    func grantPlanToMember(gymId: UUID, userId: String, plan: MembershipPlan) async throws {
        try await grantPlanToMember(gymId: gymId, userId: userId, plan: plan, customExpiresAt: nil)
    }
}
