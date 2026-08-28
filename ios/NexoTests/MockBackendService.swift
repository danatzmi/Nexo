//
//  MockBackendService.swift
//  NexoTests
//

import Foundation
@testable import Nexo

enum MockBackendError: Error {
    case notAuthenticated
    case classNotFound
    case classFull
    case userNotFound
    case bookingNotFound
    case noActiveMembership
    case insufficientCredits
    case classInPast
    case injected
}

/// In-memory `BackendService` conformance for unit tests — no network, no Firebase,
/// runs in milliseconds. State is scoped per gym the same way Firestore is
/// (`gyms/{gymId}/...`), so tests can set up exactly the fixtures they need.
@MainActor
final class MockBackendService: BackendService {
    var signedInUID: String?
    var platformRole: PlatformRole = .user

    var users: [String: PlatformUser] = [:]
    var gyms: [UUID: Gym] = [:]
    var myGymsList: [(gym: Gym, role: UserRole)] = []
    var team: [UUID: [TeamMember]] = [:]
    var members: [UUID: [Member]] = [:]
    var classes: [UUID: [UUID: GymClass]] = [:]
    /// gymId -> userId -> that user's role in this gym (used for the owner/coach
    /// booking bypass — mirrors the `role` field FirebaseBackend reads off the
    /// membership doc, now that billing fields no longer live there).
    var userRoles: [UUID: [String: UserRole]] = [:]
    /// gymId -> userId -> activePlanId -> wallet item.
    var activePlans: [UUID: [String: [String: ActivePlanItem]]] = [:]
    /// gymId -> planId -> plan template.
    var membershipPlans: [UUID: [UUID: MembershipPlan]] = [:]
    /// gymId -> userId -> logId -> log — mirrors
    /// `gyms/{gymId}/members/{userId}/workoutLogs`.
    var workoutLogs: [UUID: [String: [String: WorkoutLog]]] = [:]

    /// When set, every throwing method throws this instead of doing its normal work.
    /// Lets tests exercise failure paths (loading errors, action failures) without
    /// needing a real network failure.
    var errorToThrow: Error?

    private struct Booking {
        let userId: String
        let classId: UUID
        var activePlanId: String?
        var isCheckedIn: Bool = false
        var checkedInAt: Date? = nil
    }

    private struct WaitlistEntry {
        let userId: String
        let classId: UUID
        let joinedAt: Date
    }

    private var bookings: [UUID: [Booking]] = [:]
    private var waitlist: [UUID: [WaitlistEntry]] = [:]

    private func throwIfNeeded() throws {
        if let errorToThrow { throw errorToThrow }
    }

    // MARK: - Authentication

    func signIn(email: String, password: String) async throws { try throwIfNeeded() }
    func signUp(email: String, password: String, firstName: String, lastName: String) async throws { try throwIfNeeded() }
    func signOut() throws { signedInUID = nil }
    func currentUID() -> String? { signedInUID }

    func sendPasswordReset(email: String) async throws {
        try throwIfNeeded()
        guard users.values.contains(where: { $0.email == email }) else {
            throw MockBackendError.userNotFound
        }
    }
    func fetchPlatformRole() async throws -> PlatformRole { try throwIfNeeded(); return platformRole }

    func fetchUserProfile() async throws -> PlatformUser {
        try throwIfNeeded()
        guard let uid = signedInUID, let user = users[uid] else { throw MockBackendError.notAuthenticated }
        return user
    }

    func updateProfilePicture(base64String: String) async throws {
        try throwIfNeeded()
        guard let uid = signedInUID else { throw MockBackendError.notAuthenticated }
        users[uid]?.profilePicBase64 = base64String
    }

    // MARK: - Platform (admin only)

    func fetchAllUsers() async throws -> [PlatformUser] { try throwIfNeeded(); return Array(users.values) }

    func updatePlatformRole(uid: String, role: PlatformRole) async throws {
        try throwIfNeeded()
        users[uid]?.role = role
    }

    // MARK: - Gyms

    func fetchMyGyms() async throws -> [(gym: Gym, role: UserRole)] { try throwIfNeeded(); return myGymsList }
    func fetchAvailableGyms() async throws -> [Gym] { try throwIfNeeded(); return Array(gyms.values) }

    func createGym(name: String, ownerFirstName: String, ownerLastName: String, ownerEmail: String, ownerPassword: String) async throws -> Gym {
        try throwIfNeeded()

        let ownerUID: String
        let resolvedFirstName: String
        let resolvedLastName: String

        if let existingUser = users.values.first(where: { $0.email == ownerEmail }) {
            // Reuse the existing platform user rather than minting a
            // duplicate account — mirrors FirebaseBackend's email lookup.
            ownerUID = existingUser.id
            resolvedFirstName = existingUser.firstName
            resolvedLastName = existingUser.lastName
        } else {
            ownerUID = UUID().uuidString
            resolvedFirstName = ownerFirstName
            resolvedLastName = ownerLastName
            users[ownerUID] = PlatformUser(id: ownerUID, firstName: ownerFirstName, lastName: ownerLastName, email: ownerEmail, role: .user)
        }

        let code = String(name.filter { $0.isLetter }.uppercased().prefix(4)) + "99"
        let gym = Gym(name: name, ownerUID: ownerUID, joinCode: code)
        gyms[gym.id] = gym
        team[gym.id, default: []].append(TeamMember(id: ownerUID, firstName: resolvedFirstName, lastName: resolvedLastName, email: ownerEmail, role: .owner))
        return gym
    }

    func createGymForCurrentUser(name: String, city: String?, joinCode: String?, workoutTypes: [String]) async throws -> Gym {
        try throwIfNeeded()
        guard let uid = signedInUID, let user = users[uid] else { throw MockBackendError.notAuthenticated }

        let code = (joinCode?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased().isEmpty == false)
            ? joinCode!.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            : String(name.filter { $0.isLetter }.uppercased().prefix(4)) + "99"

        let resolvedWorkoutTypes = workoutTypes.isEmpty ? WorkoutCategory.defaults : workoutTypes
        let gym = Gym(name: name, ownerUID: uid, workoutTypes: resolvedWorkoutTypes, joinCode: code, city: city)
        gyms[gym.id] = gym
        userRoles[gym.id, default: [:]][uid] = .owner
        team[gym.id, default: []].append(TeamMember(id: uid, firstName: user.firstName, lastName: user.lastName, email: user.email, role: .owner))
        myGymsList.append((gym: gym, role: .owner))
        return gym
    }

    func fetchGymByJoinCode(code: String) async throws -> Gym? {
        try throwIfNeeded()
        let clean = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        return gyms.values.first { $0.joinCode?.uppercased() == clean }
    }

    func joinGym(gymId: UUID) async throws {
        try throwIfNeeded()
        guard let uid = signedInUID, let user = users[uid] else { throw MockBackendError.notAuthenticated }
        members[gymId, default: []].append(Member(id: uid, name: user.fullName, email: user.email, joinedAt: Date()))
    }

    func joinGymByCode(code: String) async throws -> Gym {
        try throwIfNeeded()
        guard let gym = try await fetchGymByJoinCode(code: code) else {
            throw MockBackendError.classNotFound
        }
        try await joinGym(gymId: gym.id)
        if !myGymsList.contains(where: { $0.gym.id == gym.id }) {
            myGymsList.append((gym: gym, role: .member))
        }
        return gym
    }

    func addExistingUserToGym(gymId: UUID, userId: String, role: UserRole) async throws {
        try throwIfNeeded()
        guard let user = users[userId] else { throw MockBackendError.userNotFound }
        userRoles[gymId, default: [:]][userId] = role
        if role == .member {
            members[gymId, default: []].append(Member(id: userId, name: user.fullName, email: user.email, joinedAt: Date()))
        } else {
            team[gymId, default: []].append(TeamMember(id: userId, firstName: user.firstName, lastName: user.lastName, email: user.email, role: role))
        }
    }

    func updateGymSettings(gymId: UUID, name: String, workoutTypes: [String]) async throws {
        try throwIfNeeded()
        gyms[gymId]?.name = name
        gyms[gymId]?.workoutTypes = workoutTypes
    }

    func deleteGym(gymId: UUID) async throws {
        try throwIfNeeded()
        gyms[gymId] = nil
        myGymsList.removeAll { $0.gym.id == gymId }
        team[gymId] = nil
        members[gymId] = nil
        classes[gymId] = nil
        userRoles[gymId] = nil
        activePlans[gymId] = nil
        membershipPlans[gymId] = nil
        bookings[gymId] = nil
        waitlist[gymId] = nil
        workoutLogs[gymId] = nil
    }

    // MARK: - Team

    func fetchTeam(gymId: UUID) async throws -> [TeamMember] { try throwIfNeeded(); return team[gymId] ?? [] }

    func addTeamMember(gymId: UUID, firstName: String, lastName: String, email: String, password: String, role: UserRole) async throws {
        try throwIfNeeded()
        let uid = UUID().uuidString
        team[gymId, default: []].append(TeamMember(id: uid, firstName: firstName, lastName: lastName, email: email, role: role))
    }

    func updateTeamMemberRole(gymId: UUID, userId: String, role: UserRole) async throws {
        try throwIfNeeded()
        guard let index = team[gymId]?.firstIndex(where: { $0.id == userId }) else { return }
        team[gymId]?[index].role = role
        userRoles[gymId, default: [:]][userId] = role
    }

    func removeTeamMember(gymId: UUID, userId: String) async throws {
        try throwIfNeeded()
        team[gymId]?.removeAll { $0.id == userId }
    }

    // MARK: - Classes

    func fetchClasses(gymId: UUID, for date: Date) async throws -> [GymClass] {
        try throwIfNeeded()
        return (classes[gymId] ?? [:]).values.filter { Calendar.current.isDate($0.startTime, inSameDayAs: date) }
    }

    func fetchAllClasses(gymId: UUID) async throws -> [GymClass] {
        try throwIfNeeded()
        return Array((classes[gymId] ?? [:]).values)
    }

    func createClass(gymId: UUID, _ newClass: GymClass) async throws {
        try throwIfNeeded()
        classes[gymId, default: [:]][newClass.id] = newClass
    }

    func createClasses(gymId: UUID, _ newClasses: [GymClass]) async throws {
        try throwIfNeeded()
        for gymClass in newClasses {
            classes[gymId, default: [:]][gymClass.id] = gymClass
        }
    }

    func updateClass(gymId: UUID, _ gymClass: GymClass) async throws {
        try throwIfNeeded()
        classes[gymId, default: [:]][gymClass.id] = gymClass
    }

    func deleteClass(gymId: UUID, classId: UUID) async throws {
        try throwIfNeeded()
        classes[gymId]?[classId] = nil
    }

    func deleteClassSeries(gymId: UUID, seriesId: UUID, from date: Date) async throws {
        try throwIfNeeded()
        guard var gymClasses = classes[gymId] else { return }
        for (id, gymClass) in gymClasses where gymClass.seriesId == seriesId && gymClass.startTime >= date {
            gymClasses[id] = nil
        }
        classes[gymId] = gymClasses
    }

    /// Mirrors `FirebaseBackend.updateClassSeries` — preserves each occurrence's
    /// own date (and its own `currentAttendees`/`waitlistCount`), shifting only
    /// the time-of-day to match `updatedTemplate.startTime`.
    func updateClassSeries(gymId: UUID, seriesId: UUID, from startTime: Date, updatedTemplate: GymClass) async throws {
        try throwIfNeeded()
        guard var gymClasses = classes[gymId] else { return }

        let calendar = Calendar.current
        let templateTime = calendar.dateComponents([.hour, .minute], from: updatedTemplate.startTime)

        for (id, gymClass) in gymClasses where gymClass.seriesId == seriesId && gymClass.startTime >= startTime {
            var dateComponents = calendar.dateComponents([.year, .month, .day], from: gymClass.startTime)
            dateComponents.hour = templateTime.hour
            dateComponents.minute = templateTime.minute
            let newStart = calendar.date(from: dateComponents) ?? gymClass.startTime

            gymClasses[id] = GymClass(
                id: id,
                title: updatedTemplate.title,
                coach: updatedTemplate.coach,
                startTime: newStart,
                durationMinutes: updatedTemplate.durationMinutes,
                capacity: updatedTemplate.capacity,
                currentAttendees: gymClass.currentAttendees,
                waitlistCount: gymClass.waitlistCount,
                seriesId: seriesId,
                classType: updatedTemplate.classType,
                isPremium: updatedTemplate.isPremium,
                description: updatedTemplate.description
            )
        }
        classes[gymId] = gymClasses
    }

    func observeClasses(gymId: UUID, for date: Date, onChange: @escaping ([GymClass]) -> Void) -> () -> Void {
        onChange((classes[gymId] ?? [:]).values.filter { Calendar.current.isDate($0.startTime, inSameDayAs: date) })
        return {}
    }

    /// Mirrors `FirebaseBackend.mondayStart` — must stay in sync so `copySchedule` tests
    /// exercise the same week-boundary logic the real backend uses.
    private func mondayStart(for date: Date) -> Date {
        let calendar = Calendar.current
        let startOfDay = calendar.startOfDay(for: date)
        let weekday = calendar.component(.weekday, from: startOfDay)
        let daysSinceMonday = (weekday + 5) % 7
        return calendar.date(byAdding: .day, value: -daysSinceMonday, to: startOfDay)!
    }

    func copySchedule(gymId: UUID, fromWeekOf sourceDate: Date, toWeekOf targetDate: Date) async throws {
        try throwIfNeeded()

        let sourceWeekStart = mondayStart(for: sourceDate)
        let sourceWeekEnd = Calendar.current.date(byAdding: .day, value: 7, to: sourceWeekStart)!
        let targetWeekStart = mondayStart(for: targetDate)

        let sourceClasses = (classes[gymId] ?? [:]).values.filter {
            $0.startTime >= sourceWeekStart && $0.startTime < sourceWeekEnd
        }

        for sourceClass in sourceClasses {
            let offset = sourceClass.startTime.timeIntervalSince(sourceWeekStart)
            let newClass = GymClass(
                title: sourceClass.title,
                coach: sourceClass.coach,
                startTime: targetWeekStart.addingTimeInterval(offset),
                durationMinutes: sourceClass.durationMinutes,
                capacity: sourceClass.capacity,
                currentAttendees: 0,
                waitlistCount: 0,
                classType: sourceClass.classType,
                isPremium: sourceClass.isPremium
            )
            classes[gymId, default: [:]][newClass.id] = newClass
        }
    }

    // MARK: - Booking

    func book(gymId: UUID, classId: UUID) async throws {
        try throwIfNeeded()
        guard let uid = signedInUID else { throw MockBackendError.notAuthenticated }
        try performBooking(gymId: gymId, classId: classId, userId: uid)
    }

    func cancelBooking(gymId: UUID, classId: UUID) async throws {
        try throwIfNeeded()
        guard let uid = signedInUID else { throw MockBackendError.notAuthenticated }
        performCancelBooking(gymId: gymId, classId: classId, userId: uid)
    }

    func cancelBooking(gymId: UUID, classId: UUID, onBehalfOf userId: String) async throws {
        try throwIfNeeded()
        performCancelBooking(gymId: gymId, classId: classId, userId: userId)
    }

    func fetchUserBookings(gymId: UUID) async throws -> Set<UUID> {
        try throwIfNeeded()
        guard let uid = signedInUID else { return [] }
        return Set((bookings[gymId] ?? []).filter { $0.userId == uid }.map(\.classId))
    }

    func isUserBooked(gymId: UUID, classId: UUID) async throws -> Bool {
        try throwIfNeeded()
        guard let uid = signedInUID else { return false }
        return (bookings[gymId] ?? []).contains { $0.userId == uid && $0.classId == classId }
    }

    private func performBooking(gymId: UUID, classId: UUID, userId: String) throws {
        guard var gymClass = classes[gymId]?[classId] else { throw MockBackendError.classNotFound }
        guard gymClass.startTime >= Date() else { throw MockBackendError.classInPast }

        let existing = bookings[gymId] ?? []
        guard !existing.contains(where: { $0.userId == userId && $0.classId == classId }) else { return }
        guard gymClass.currentAttendees < gymClass.capacity else { throw MockBackendError.classFull }

        let consumedActivePlanId = try validateAndConsumeMembership(gymId: gymId, userId: userId, gymClass: gymClass)

        gymClass.currentAttendees += 1
        classes[gymId]?[classId] = gymClass
        bookings[gymId, default: []].append(Booking(userId: userId, classId: classId, activePlanId: consumedActivePlanId))
    }

    /// Mirrors `FirebaseBackend.validateAndConsumeMembership` — platform admins bypass
    /// the wallet check regardless of gym role, then owners/coaches bypass it within
    /// their gym. Returns the consumed `ActivePlanItem` id (for refund-on-cancel), or
    /// nil if an unlimited item (or a bypass) authorized the booking.
    private func validateAndConsumeMembership(gymId: UUID, userId: String, gymClass: GymClass) throws -> String? {
        let items = activePlans[gymId]?[userId] ?? [:]
        let matching = items.values.filter { $0.matches(gymClass: gymClass) }

        if !matching.isEmpty {
            if matching.contains(where: { $0.type == .unlimited }) {
                return nil
            }

            let creditItems = matching.filter { $0.type == .credits && $0.availableCredits() > 0 }.sorted { $0.expiresAt < $1.expiresAt }
            guard let chosen = creditItems.first else { throw MockBackendError.insufficientCredits }

            var updated = chosen
            if chosen.resetPeriod == .monthly {
                let currentIndex = chosen.currentCycleIndex()
                if currentIndex != chosen.lastCycleIndex {
                    updated.cycleCreditsUsed = 1
                    updated.lastCycleIndex = currentIndex
                } else {
                    updated.cycleCreditsUsed += 1
                }
            } else {
                updated.remainingCredits -= 1
            }
            activePlans[gymId, default: [:]][userId, default: [:]][chosen.id] = updated

            return chosen.id
        }

        if (users[userId]?.role ?? .user) == .admin { return nil }

        let role = userRoles[gymId]?[userId] ?? .member
        if role.canManageClasses { return nil }

        throw MockBackendError.noActiveMembership
    }

    private func performCancelBooking(gymId: UUID, classId: UUID, userId: String) {
        let matchingBookings = (bookings[gymId] ?? []).filter { $0.userId == userId && $0.classId == classId }
        guard !matchingBookings.isEmpty else { return }

        bookings[gymId]?.removeAll { $0.userId == userId && $0.classId == classId }

        for booking in matchingBookings {
            guard let activePlanId = booking.activePlanId,
                  var item = activePlans[gymId]?[userId]?[activePlanId],
                  item.type == .credits else { continue }
            if item.resetPeriod == .monthly {
                if item.cycleCreditsUsed > 0 {
                    item.cycleCreditsUsed -= 1
                }
            } else {
                item.remainingCredits += 1
            }
            activePlans[gymId, default: [:]][userId, default: [:]][activePlanId] = item
        }

        guard var gymClass = classes[gymId]?[classId] else { return }

        let waitingEntries = (waitlist[gymId] ?? []).filter { $0.classId == classId }.sorted { $0.joinedAt < $1.joinedAt }

        if let first = waitingEntries.first {
            bookings[gymId, default: []].append(Booking(userId: first.userId, classId: classId))
            waitlist[gymId]?.removeAll { $0.userId == first.userId && $0.classId == classId }
            gymClass.waitlistCount = max(0, gymClass.waitlistCount - 1)
        } else {
            gymClass.currentAttendees = max(0, gymClass.currentAttendees - 1)
        }
        classes[gymId]?[classId] = gymClass
    }

    // MARK: - Waitlist

    func joinWaitlist(gymId: UUID, classId: UUID) async throws {
        try throwIfNeeded()
        guard let uid = signedInUID else { throw MockBackendError.notAuthenticated }
        guard let gymClass = classes[gymId]?[classId] else { throw MockBackendError.classNotFound }
        guard gymClass.startTime >= Date() else { throw MockBackendError.classInPast }

        let existing = waitlist[gymId] ?? []
        guard !existing.contains(where: { $0.userId == uid && $0.classId == classId }) else { return }

        waitlist[gymId, default: []].append(WaitlistEntry(userId: uid, classId: classId, joinedAt: Date()))
        var updatedClass = gymClass
        updatedClass.waitlistCount += 1
        classes[gymId]?[classId] = updatedClass
    }

    func leaveWaitlist(gymId: UUID, classId: UUID) async throws {
        try throwIfNeeded()
        guard let uid = signedInUID else { throw MockBackendError.notAuthenticated }

        let countBefore = waitlist[gymId]?.count ?? 0
        waitlist[gymId]?.removeAll { $0.userId == uid && $0.classId == classId }
        let countAfter = waitlist[gymId]?.count ?? 0

        if countAfter < countBefore, var gymClass = classes[gymId]?[classId] {
            gymClass.waitlistCount = max(0, gymClass.waitlistCount - 1)
            classes[gymId]?[classId] = gymClass
        }
    }

    func fetchUserWaitlist(gymId: UUID) async throws -> Set<UUID> {
        try throwIfNeeded()
        guard let uid = signedInUID else { return [] }
        return Set((waitlist[gymId] ?? []).filter { $0.userId == uid }.map(\.classId))
    }

    func fetchWaitlistPosition(gymId: UUID, classId: UUID) async throws -> (position: Int, total: Int)? {
        try throwIfNeeded()
        guard let uid = signedInUID else { return nil }

        let entries = (waitlist[gymId] ?? [])
            .filter { $0.classId == classId }
            .sorted { $0.joinedAt < $1.joinedAt }

        guard let index = entries.firstIndex(where: { $0.userId == uid }) else { return nil }
        return (position: index + 1, total: entries.count)
    }

    // MARK: - Attendees

    func fetchAttendees(gymId: UUID, classId: UUID) async throws -> [Member] {
        try throwIfNeeded()
        let matchingBookings = (bookings[gymId] ?? []).filter { $0.classId == classId }
        return matchingBookings.compactMap { booking in
            guard let user = users[booking.userId] else { return nil }
            return Member(
                id: booking.userId,
                name: user.fullName,
                email: user.email,
                profilePicBase64: user.profilePicBase64,
                isCheckedIn: booking.isCheckedIn,
                checkedInAt: booking.checkedInAt
            )
        }
    }

    func toggleAttendance(gymId: UUID, classId: UUID, userId: String, isCheckedIn: Bool) async throws {
        try throwIfNeeded()
        guard let index = bookings[gymId]?.firstIndex(where: { $0.classId == classId && $0.userId == userId }) else {
            throw MockBackendError.bookingNotFound
        }
        bookings[gymId]?[index].isCheckedIn = isCheckedIn
        bookings[gymId]?[index].checkedInAt = isCheckedIn ? Date() : nil
    }

    // MARK: - Members

    func fetchMembers(gymId: UUID) async throws -> [Member] { try throwIfNeeded(); return members[gymId] ?? [] }

    func fetchMemberBookings(gymId: UUID, userId: String) async throws -> [GymClass] {
        try throwIfNeeded()
        let classIds = (bookings[gymId] ?? []).filter { $0.userId == userId }.map(\.classId)
        return classIds.compactMap { classes[gymId]?[$0] }.sorted { $0.startTime > $1.startTime }
    }

    func addMember(gymId: UUID, firstName: String, lastName: String, email: String, password: String) async throws {
        try throwIfNeeded()
        let uid = UUID().uuidString
        members[gymId, default: []].append(Member(id: uid, name: "\(firstName) \(lastName)", email: email, joinedAt: Date()))
    }

    func removeMember(gymId: UUID, userId: String) async throws {
        try throwIfNeeded()
        members[gymId]?.removeAll { $0.id == userId }
        activePlans[gymId]?[userId] = nil
    }

    // MARK: - Membership Plans & Credit Wallet

    func createMembershipPlan(gymId: UUID, plan: MembershipPlan) async throws {
        try throwIfNeeded()
        membershipPlans[gymId, default: [:]][plan.id] = plan
    }

    func fetchMembershipPlans(gymId: UUID) async throws -> [MembershipPlan] {
        try throwIfNeeded()
        return Array((membershipPlans[gymId] ?? [:]).values)
    }

    func updateMembershipPlan(gymId: UUID, plan: MembershipPlan) async throws {
        try throwIfNeeded()
        membershipPlans[gymId, default: [:]][plan.id] = plan
    }

    func deleteMembershipPlan(gymId: UUID, planId: UUID) async throws {
        try throwIfNeeded()
        membershipPlans[gymId]?[planId] = nil
    }

    func grantPlanToMember(gymId: UUID, userId: String, plan: MembershipPlan, customExpiresAt: Date? = nil) async throws {
        try throwIfNeeded()
        let now = Date()
        for component in plan.components {
            let expiresAt = customExpiresAt ?? Calendar.current.date(
                byAdding: component.validityUnit.calendarComponent,
                value: component.validityValue,
                to: now
            ) ?? now

            let item = ActivePlanItem(
                id: UUID().uuidString,
                planName: plan.name,
                type: component.type,
                resetPeriod: component.resetPeriod,
                workoutType: component.workoutType,
                creditCount: component.creditCount,
                remainingCredits: component.resetPeriod == .none ? (component.type == .unlimited ? 0 : component.creditCount) : 0,
                cycleCreditsUsed: 0,
                cycleAnchorDate: now,
                lastCycleIndex: 0,
                expiresAt: expiresAt
            )
            activePlans[gymId, default: [:]][userId, default: [:]][item.id] = item
        }
    }

    /// Test convenience: grants an unlimited, non-expiring plan item. For
    /// tests that need booking to succeed but aren't testing billing itself
    /// — see `MembershipTests.swift` for actual wallet coverage.
    @discardableResult
    func grantUnlimitedForTesting(gymId: UUID, userId: String) -> ActivePlanItem {
        let item = ActivePlanItem(
            id: UUID().uuidString,
            planName: "Test Plan",
            type: .unlimited,
            resetPeriod: .none,
            workoutType: nil,
            creditCount: 0,
            remainingCredits: 0,
            cycleCreditsUsed: 0,
            cycleAnchorDate: Date(),
            lastCycleIndex: 0,
            expiresAt: Date().addingTimeInterval(86400 * 365)
        )
        activePlans[gymId, default: [:]][userId, default: [:]][item.id] = item
        return item
    }

    /// Test convenience: seeds a booking directly, bypassing `book()`'s gating
    /// (capacity, membership, past-class). For fixture setup that needs a
    /// pre-existing booking on an already-past class (e.g. testing past-booking
    /// display), where `book()` itself would now correctly reject the action.
    func seedBookingForTesting(gymId: UUID, classId: UUID, userId: String) {
        bookings[gymId, default: []].append(Booking(userId: userId, classId: classId))
    }

    func revokeActivePlan(gymId: UUID, userId: String, activePlanId: String) async throws {
        try throwIfNeeded()
        activePlans[gymId]?[userId]?[activePlanId] = nil
    }

    func fetchActivePlans(gymId: UUID, userId: String) async throws -> [ActivePlanItem] {
        try throwIfNeeded()
        return Array((activePlans[gymId]?[userId] ?? [:]).values)
    }

    // MARK: - Logbook

    func fetchWorkoutLogs(gymId: UUID) async throws -> [WorkoutLog] {
        try throwIfNeeded()
        guard let uid = signedInUID else { throw MockBackendError.notAuthenticated }
        return Array((workoutLogs[gymId]?[uid] ?? [:]).values)
    }

    func addWorkoutLog(gymId: UUID, log: WorkoutLog) async throws {
        try throwIfNeeded()
        guard let uid = signedInUID else { throw MockBackendError.notAuthenticated }
        workoutLogs[gymId, default: [:]][uid, default: [:]][log.id] = log
    }

    func updateWorkoutLog(gymId: UUID, log: WorkoutLog) async throws {
        try await addWorkoutLog(gymId: gymId, log: log)
    }

    func deleteWorkoutLog(gymId: UUID, logId: String) async throws {
        try throwIfNeeded()
        guard let uid = signedInUID else { throw MockBackendError.notAuthenticated }
        workoutLogs[gymId]?[uid]?[logId] = nil
    }
}
