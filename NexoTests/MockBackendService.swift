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
    case injected
}

/// In-memory `BackendService` conformance for unit tests — no network, no Firebase,
/// runs in milliseconds. State is scoped per gym the same way Firestore is
/// (`gyms/{gymId}/...`), so tests can set up exactly the fixtures they need.
final class MockBackendService: BackendService {
    var signedInUID: String?
    var platformRole: PlatformRole = .user

    var users: [String: PlatformUser] = [:]
    var gyms: [UUID: Gym] = [:]
    var myGymsList: [(gym: Gym, role: UserRole)] = []
    var team: [UUID: [TeamMember]] = [:]
    var members: [UUID: [Member]] = [:]
    var classes: [UUID: [UUID: GymClass]] = [:]
    var workouts: [UUID: [UUID: Workout]] = [:]

    /// When set, every throwing method throws this instead of doing its normal work.
    /// Lets tests exercise failure paths (loading errors, action failures) without
    /// needing a real network failure.
    var errorToThrow: Error?

    private struct Booking {
        let userId: String
        let classId: UUID
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
    func fetchPlatformRole() async throws -> PlatformRole { try throwIfNeeded(); return platformRole }

    func fetchUserProfile() async throws -> PlatformUser {
        try throwIfNeeded()
        guard let uid = signedInUID, let user = users[uid] else { throw MockBackendError.notAuthenticated }
        return user
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
        let ownerUID = UUID().uuidString
        let gym = Gym(name: name, ownerUID: ownerUID)
        gyms[gym.id] = gym
        users[ownerUID] = PlatformUser(id: ownerUID, firstName: ownerFirstName, lastName: ownerLastName, email: ownerEmail, role: .user)
        team[gym.id, default: []].append(TeamMember(id: ownerUID, firstName: ownerFirstName, lastName: ownerLastName, email: ownerEmail, role: .owner))
        return gym
    }

    func joinGym(gymId: UUID) async throws {
        try throwIfNeeded()
        guard let uid = signedInUID, let user = users[uid] else { throw MockBackendError.notAuthenticated }
        members[gymId, default: []].append(Member(id: uid, name: user.fullName, email: user.email, joinedAt: Date()))
    }

    // MARK: - Team

    func fetchTeam(gymId: UUID) async throws -> [TeamMember] { try throwIfNeeded(); return team[gymId] ?? [] }

    func addCoach(gymId: UUID, firstName: String, lastName: String, email: String, password: String) async throws {
        try throwIfNeeded()
        let uid = UUID().uuidString
        team[gymId, default: []].append(TeamMember(id: uid, firstName: firstName, lastName: lastName, email: email, role: .coach))
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

    func observeClasses(gymId: UUID, for date: Date, onChange: @escaping ([GymClass]) -> Void) -> () -> Void {
        onChange((classes[gymId] ?? [:]).values.filter { Calendar.current.isDate($0.startTime, inSameDayAs: date) })
        return {}
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

        let existing = bookings[gymId] ?? []
        guard !existing.contains(where: { $0.userId == userId && $0.classId == classId }) else { return }
        guard gymClass.currentAttendees < gymClass.capacity else { throw MockBackendError.classFull }

        gymClass.currentAttendees += 1
        classes[gymId]?[classId] = gymClass
        bookings[gymId, default: []].append(Booking(userId: userId, classId: classId))
    }

    private func performCancelBooking(gymId: UUID, classId: UUID, userId: String) {
        bookings[gymId]?.removeAll { $0.userId == userId && $0.classId == classId }

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

        let existing = waitlist[gymId] ?? []
        guard !existing.contains(where: { $0.userId == uid && $0.classId == classId }) else { return }

        waitlist[gymId, default: []].append(WaitlistEntry(userId: uid, classId: classId, joinedAt: Date()))
        if var gymClass = classes[gymId]?[classId] {
            gymClass.waitlistCount += 1
            classes[gymId]?[classId] = gymClass
        }
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

    // MARK: - Attendees

    func fetchAttendees(gymId: UUID, classId: UUID) async throws -> [Member] {
        try throwIfNeeded()
        let userIds = (bookings[gymId] ?? []).filter { $0.classId == classId }.map(\.userId)
        return userIds.compactMap { uid in
            guard let user = users[uid] else { return nil }
            return Member(id: uid, name: user.fullName, email: user.email)
        }
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

    // MARK: - Workouts

    func fetchWorkouts(gymId: UUID) async throws -> [Workout] { try throwIfNeeded(); return Array((workouts[gymId] ?? [:]).values) }
    func fetchWorkout(gymId: UUID, id: UUID) async throws -> Workout? { try throwIfNeeded(); return workouts[gymId]?[id] }

    func createWorkout(gymId: UUID, _ workout: Workout) async throws {
        try throwIfNeeded()
        workouts[gymId, default: [:]][workout.id] = workout
    }

    func updateWorkout(gymId: UUID, _ workout: Workout) async throws {
        try throwIfNeeded()
        workouts[gymId, default: [:]][workout.id] = workout
    }

    func deleteWorkout(gymId: UUID, id: UUID) async throws {
        try throwIfNeeded()
        workouts[gymId]?[id] = nil
    }
}
