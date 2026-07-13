//
//  BackendService.swift
//  Nexo
//
//  Created by Atzmi, Dan on 20/04/2026.
//

import Foundation

protocol BackendService {
    // MARK: - Authentication
    func signIn(email: String, password: String) async throws
    func signUp(email: String, password: String, firstName: String, lastName: String) async throws
    func signOut() throws
    func currentUID() -> String?
    func fetchPlatformRole() async throws -> PlatformRole
    func fetchUserProfile() async throws -> PlatformUser

    // MARK: - Platform (admin only)
    func fetchAllUsers() async throws -> [PlatformUser]
    func updatePlatformRole(uid: String, role: PlatformRole) async throws

    // MARK: - Gyms
    func fetchMyGyms() async throws -> [(gym: Gym, role: UserRole)]
    func fetchAvailableGyms() async throws -> [Gym]
    func createGym(name: String, ownerFirstName: String, ownerLastName: String, ownerEmail: String, ownerPassword: String) async throws -> Gym
    func joinGym(gymId: UUID) async throws

    // MARK: - Team
    func fetchTeam(gymId: UUID) async throws -> [TeamMember]
    func addCoach(gymId: UUID, firstName: String, lastName: String, email: String, password: String) async throws

    // MARK: - Classes
    func fetchClasses(gymId: UUID, for date: Date) async throws -> [GymClass]
    func fetchAllClasses(gymId: UUID) async throws -> [GymClass]
    func createClass(gymId: UUID, _ newClass: GymClass) async throws
    func createClasses(gymId: UUID, _ classes: [GymClass]) async throws
    func updateClass(gymId: UUID, _ gymClass: GymClass) async throws
    func deleteClass(gymId: UUID, classId: UUID) async throws
    func deleteClassSeries(gymId: UUID, seriesId: UUID, from date: Date) async throws
    func observeClasses(gymId: UUID, for date: Date, onChange: @escaping ([GymClass]) -> Void) -> () -> Void

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

    // MARK: - Attendees
    func fetchAttendees(gymId: UUID, classId: UUID) async throws -> [Member]

    // MARK: - Members
    func fetchMembers(gymId: UUID) async throws -> [Member]
    func fetchMemberBookings(gymId: UUID, userId: String) async throws -> [GymClass]
    func addMember(gymId: UUID, firstName: String, lastName: String, email: String, password: String) async throws

    // MARK: - Workouts
    func fetchWorkouts(gymId: UUID) async throws -> [Workout]
    func fetchWorkout(gymId: UUID, id: UUID) async throws -> Workout?
    func createWorkout(gymId: UUID, _ workout: Workout) async throws
    func updateWorkout(gymId: UUID, _ workout: Workout) async throws
    func deleteWorkout(gymId: UUID, id: UUID) async throws
}
