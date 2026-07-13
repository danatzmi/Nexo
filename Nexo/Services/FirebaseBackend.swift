//
//  FirebaseBackend.swift
//  Nexo
//
//  Created by Atzmi, Dan on 18/04/2026.
//

import Foundation
import FirebaseCore
import FirebaseAuth
import FirebaseFirestore

enum FBError: Error, LocalizedError {
    case notAuthenticated
    case classNotFound
    case classFull
    case userNotFound
    case unknown

    var errorDescription: String? {
        switch self {
        case .notAuthenticated: return "User not authenticated."
        case .classNotFound: return "Class not found."
        case .classFull: return "This class is full."
        case .userNotFound: return "User profile not found."
        case .unknown: return "Unknown error."
        }
    }
}

final class FirebaseBackend: BackendService {
    static let shared = FirebaseBackend()

    private let auth = Auth.auth()
    private let db = Firestore.firestore()

    private init() {}

    // MARK: - Authentication

    func signIn(email: String, password: String) async throws {
        try await auth.signIn(withEmail: email, password: password)
    }

    func signUp(email: String, password: String, firstName: String, lastName: String) async throws {
        let result = try await auth.createUser(withEmail: email, password: password)
        let uid = result.user.uid

        try await db.collection("users").document(uid).setData([
            "firstName": firstName,
            "lastName": lastName,
            "email": result.user.email ?? email,
            "role": PlatformRole.user.rawValue
        ])
    }

    func signOut() throws {
        try auth.signOut()
    }

    func currentUID() -> String? {
        auth.currentUser?.uid
    }

    func fetchPlatformRole() async throws -> PlatformRole {
        guard let uid = currentUID() else { throw FBError.notAuthenticated }
        let snap = try await db.collection("users").document(uid).getDocument()
        let roleStr = snap.data()?["role"] as? String ?? "user"
        return PlatformRole(rawValue: roleStr) ?? .user
    }

    func fetchUserProfile() async throws -> PlatformUser {
        guard let uid = currentUID() else { throw FBError.notAuthenticated }
        let snap = try await db.collection("users").document(uid).getDocument()
        guard let data = snap.data() else { throw FBError.userNotFound }

        let roleStr = data["role"] as? String ?? PlatformRole.user.rawValue
        return PlatformUser(
            id: uid,
            firstName: data["firstName"] as? String ?? "",
            lastName: data["lastName"] as? String ?? "",
            email: data["email"] as? String ?? "",
            role: PlatformRole(rawValue: roleStr) ?? .user
        )
    }

    // MARK: - Account Creation (secondary app avoids signing out current user)

    private func secondaryAuth() -> Auth {
        if FirebaseApp.app(name: "Secondary") == nil {
            FirebaseApp.configure(name: "Secondary", options: FirebaseApp.app()!.options)
        }
        return Auth.auth(app: FirebaseApp.app(name: "Secondary")!)
    }

    private func createUserAccount(email: String, password: String, firstName: String, lastName: String) async throws -> String {
        let result = try await secondaryAuth().createUser(withEmail: email, password: password)
        let uid = result.user.uid

        try await db.collection("users").document(uid).setData([
            "firstName": firstName,
            "lastName": lastName,
            "email": email,
            "role": PlatformRole.user.rawValue
        ])

        try? secondaryAuth().signOut()
        return uid
    }

    // MARK: - Platform (admin only)

    func fetchAllUsers() async throws -> [PlatformUser] {
        let snapshot = try await db.collection("users").getDocuments()
        return snapshot.documents.compactMap { doc in
            let data = doc.data()
            let roleStr = data["role"] as? String ?? PlatformRole.user.rawValue
            return PlatformUser(
                id: doc.documentID,
                firstName: data["firstName"] as? String ?? "",
                lastName: data["lastName"] as? String ?? "",
                email: data["email"] as? String ?? "",
                role: PlatformRole(rawValue: roleStr) ?? .user
            )
        }
    }

    func updatePlatformRole(uid: String, role: PlatformRole) async throws {
        try await db.collection("users").document(uid).updateData(["role": role.rawValue])
    }

    // MARK: - Gyms

    func fetchMyGyms() async throws -> [(gym: Gym, role: UserRole)] {
        guard let uid = currentUID() else { throw FBError.notAuthenticated }

        let membershipsSnapshot = try await db.collection("users").document(uid)
            .collection("memberships").getDocuments()

        var results: [(gym: Gym, role: UserRole)] = []

        for membershipDoc in membershipsSnapshot.documents {
            let data = membershipDoc.data()
            guard let roleStr = data["role"] as? String,
                  let role = UserRole(rawValue: roleStr),
                  let gymId = UUID(uuidString: membershipDoc.documentID) else { continue }

            let gymDoc = try await db.collection("gyms").document(membershipDoc.documentID).getDocument()
            guard let gymData = gymDoc.data(),
                  let gymName = gymData["name"] as? String,
                  let ownerUID = gymData["ownerUID"] as? String else { continue }

            results.append((gym: Gym(id: gymId, name: gymName, ownerUID: ownerUID), role: role))
        }

        return results
    }

    func fetchAvailableGyms() async throws -> [Gym] {
        let snapshot = try await db.collection("gyms").getDocuments()

        return snapshot.documents.compactMap { doc in
            guard let name = doc.data()["name"] as? String,
                  let ownerUID = doc.data()["ownerUID"] as? String,
                  let id = UUID(uuidString: doc.documentID) else { return nil }
            return Gym(id: id, name: name, ownerUID: ownerUID)
        }
    }

    func createGym(name: String, ownerFirstName: String, ownerLastName: String, ownerEmail: String, ownerPassword: String) async throws -> Gym {
        guard currentUID() != nil else { throw FBError.notAuthenticated }

        let ownerUID = try await createUserAccount(
            email: ownerEmail,
            password: ownerPassword,
            firstName: ownerFirstName,
            lastName: ownerLastName
        )

        let gym = Gym(name: name, ownerUID: ownerUID)

        try await db.collection("gyms").document(gym.id.uuidString).setData([
            "name": gym.name,
            "ownerUID": ownerUID,
            "createdAt": Timestamp(date: Date())
        ])

        try await db.collection("users").document(ownerUID)
            .collection("memberships").document(gym.id.uuidString).setData([
                "role": UserRole.owner.rawValue,
                "joinedAt": Timestamp(date: Date())
            ])

        try await teamRef(gym.id).document(ownerUID).setData([
            "role": UserRole.owner.rawValue,
            "firstName": ownerFirstName,
            "lastName": ownerLastName,
            "email": ownerEmail,
            "addedAt": Timestamp(date: Date())
        ])

        return gym
    }

    func fetchTeam(gymId: UUID) async throws -> [TeamMember] {
        let snapshot = try await teamRef(gymId).getDocuments()

        return snapshot.documents.compactMap { doc in
            let data = doc.data()
            guard let roleStr = data["role"] as? String,
                  let role = UserRole(rawValue: roleStr) else { return nil }

            return TeamMember(
                id: doc.documentID,
                firstName: data["firstName"] as? String ?? "",
                lastName: data["lastName"] as? String ?? "",
                email: data["email"] as? String ?? "",
                role: role
            )
        }
    }

    func addCoach(gymId: UUID, firstName: String, lastName: String, email: String, password: String) async throws {
        guard currentUID() != nil else { throw FBError.notAuthenticated }

        let coachUID = try await createUserAccount(
            email: email,
            password: password,
            firstName: firstName,
            lastName: lastName
        )

        try await db.collection("users").document(coachUID)
            .collection("memberships").document(gymId.uuidString).setData([
                "role": UserRole.coach.rawValue,
                "joinedAt": Timestamp(date: Date())
            ])

        try await teamRef(gymId).document(coachUID).setData([
            "role": UserRole.coach.rawValue,
            "firstName": firstName,
            "lastName": lastName,
            "email": email,
            "addedAt": Timestamp(date: Date())
        ])
    }

    func joinGym(gymId: UUID) async throws {
        guard let uid = currentUID() else { throw FBError.notAuthenticated }

        let joinedAt = Timestamp(date: Date())

        try await db.collection("users").document(uid)
            .collection("memberships").document(gymId.uuidString).setData([
                "role": UserRole.member.rawValue,
                "joinedAt": joinedAt
            ])

        let userDoc = try await db.collection("users").document(uid).getDocument()
        let userData = userDoc.data() ?? [:]

        try await membersRef(gymId).document(uid).setData([
            "firstName": userData["firstName"] as? String ?? "",
            "lastName": userData["lastName"] as? String ?? "",
            "email": userData["email"] as? String ?? "",
            "role": UserRole.member.rawValue,
            "joinedAt": joinedAt
        ])
    }

    // MARK: - Classes

    func fetchClasses(gymId: UUID, for date: Date) async throws -> [GymClass] {
        let (start, end) = dayBounds(for: date)

        let snapshot = try await classesRef(gymId)
            .whereField("startTime", isGreaterThanOrEqualTo: Timestamp(date: start))
            .whereField("startTime", isLessThan: Timestamp(date: end))
            .getDocuments()

        return snapshot.documents.compactMap { parseClass(from: $0) }
    }

    func fetchAllClasses(gymId: UUID) async throws -> [GymClass] {
        let snapshot = try await classesRef(gymId)
            .order(by: "startTime")
            .getDocuments()
        return snapshot.documents.compactMap { parseClass(from: $0) }
    }

    func createClass(gymId: UUID, _ newClass: GymClass) async throws {
        try await classesRef(gymId).document(newClass.id.uuidString).setData(classData(for: newClass, currentAttendees: 0))
    }

    func createClasses(gymId: UUID, _ classes: [GymClass]) async throws {
        let batch = db.batch()
        for gymClass in classes {
            let ref = classesRef(gymId).document(gymClass.id.uuidString)
            batch.setData(classData(for: gymClass, currentAttendees: 0), forDocument: ref)
        }
        try await batch.commit()
    }

    func updateClass(gymId: UUID, _ gymClass: GymClass) async throws {
        try await classesRef(gymId).document(gymClass.id.uuidString).setData(classData(for: gymClass, currentAttendees: gymClass.currentAttendees))
    }

    func deleteClass(gymId: UUID, classId: UUID) async throws {
        try await classesRef(gymId).document(classId.uuidString).delete()
    }

    func deleteClassSeries(gymId: UUID, seriesId: UUID, from date: Date) async throws {
        let snapshot = try await classesRef(gymId)
            .whereField("seriesId", isEqualTo: seriesId.uuidString)
            .getDocuments()

        let toDelete = snapshot.documents.filter { doc in
            guard let ts = doc.data()["startTime"] as? Timestamp else { return false }
            return ts.dateValue() >= date
        }

        let batch = db.batch()
        toDelete.forEach { batch.deleteDocument($0.reference) }
        try await batch.commit()
    }

    func observeClasses(gymId: UUID, for date: Date, onChange: @escaping ([GymClass]) -> Void) -> () -> Void {
        let (start, end) = dayBounds(for: date)

        let listener = classesRef(gymId)
            .whereField("startTime", isGreaterThanOrEqualTo: Timestamp(date: start))
            .whereField("startTime", isLessThan: Timestamp(date: end))
            .addSnapshotListener { snapshot, error in
                if let error { print("observeClasses error: \(error)"); return }
                guard let snapshot else { return }
                let classes = snapshot.documents.compactMap { self.parseClass(from: $0) }
                DispatchQueue.main.async { onChange(classes) }
            }

        return { listener.remove() }
    }

    // MARK: - Booking

    func book(gymId: UUID, classId: UUID) async throws {
        guard let uid = currentUID() else { throw FBError.notAuthenticated }

        let existing = try await bookingsRef(gymId)
            .whereField("userId", isEqualTo: uid)
            .whereField("classId", isEqualTo: classId.uuidString)
            .getDocuments()

        guard existing.documents.isEmpty else { return }

        let classDoc = try await classesRef(gymId).document(classId.uuidString).getDocument()
        guard let classData = classDoc.data() else { throw FBError.classNotFound }
        let capacity = classData["capacity"] as? Int ?? 0
        let currentAttendees = classData["currentAttendees"] as? Int ?? 0
        guard currentAttendees < capacity else { throw FBError.classFull }

        _ = try await bookingsRef(gymId).addDocument(data: [
            "userId": uid,
            "classId": classId.uuidString,
            "bookedAt": Timestamp(date: Date())
        ])

        try await classesRef(gymId).document(classId.uuidString)
            .updateData(["currentAttendees": FieldValue.increment(Int64(1))])
    }

    func cancelBooking(gymId: UUID, classId: UUID) async throws {
        guard let uid = currentUID() else { throw FBError.notAuthenticated }
        try await removeBooking(gymId: gymId, classId: classId, userId: uid)
    }

    func cancelBooking(gymId: UUID, classId: UUID, onBehalfOf userId: String) async throws {
        try await removeBooking(gymId: gymId, classId: classId, userId: userId)
    }

    private func removeBooking(gymId: UUID, classId: UUID, userId: String) async throws {
        let bookings = try await bookingsRef(gymId)
            .whereField("userId", isEqualTo: userId)
            .whereField("classId", isEqualTo: classId.uuidString)
            .getDocuments()

        for doc in bookings.documents {
            try await doc.reference.delete()
        }

        let waitlistSnapshot = try await waitlistRef(gymId)
            .whereField("classId", isEqualTo: classId.uuidString)
            .order(by: "joinedAt")
            .limit(to: 1)
            .getDocuments()

        if let firstWaiting = waitlistSnapshot.documents.first,
           let waitingUserId = firstWaiting.data()["userId"] as? String {
            _ = try await bookingsRef(gymId).addDocument(data: [
                "userId": waitingUserId,
                "classId": classId.uuidString,
                "bookedAt": Timestamp(date: Date())
            ])
            try await firstWaiting.reference.delete()
            try await classesRef(gymId).document(classId.uuidString)
                .updateData(["waitlistCount": FieldValue.increment(Int64(-1))])
        } else {
            try await classesRef(gymId).document(classId.uuidString)
                .updateData(["currentAttendees": FieldValue.increment(Int64(-1))])
        }
    }

    // MARK: - Waitlist

    func joinWaitlist(gymId: UUID, classId: UUID) async throws {
        guard let uid = currentUID() else { throw FBError.notAuthenticated }

        let existing = try await waitlistRef(gymId)
            .whereField("userId", isEqualTo: uid)
            .whereField("classId", isEqualTo: classId.uuidString)
            .getDocuments()

        guard existing.documents.isEmpty else { return }

        _ = try await waitlistRef(gymId).addDocument(data: [
            "userId": uid,
            "classId": classId.uuidString,
            "joinedAt": Timestamp(date: Date())
        ])

        try await classesRef(gymId).document(classId.uuidString)
            .updateData(["waitlistCount": FieldValue.increment(Int64(1))])
    }

    func leaveWaitlist(gymId: UUID, classId: UUID) async throws {
        guard let uid = currentUID() else { throw FBError.notAuthenticated }

        let entries = try await waitlistRef(gymId)
            .whereField("userId", isEqualTo: uid)
            .whereField("classId", isEqualTo: classId.uuidString)
            .getDocuments()

        for doc in entries.documents {
            try await doc.reference.delete()
        }

        if !entries.documents.isEmpty {
            try await classesRef(gymId).document(classId.uuidString)
                .updateData(["waitlistCount": FieldValue.increment(Int64(-1))])
        }
    }

    func fetchUserWaitlist(gymId: UUID) async throws -> Set<UUID> {
        guard let uid = currentUID() else { return [] }

        let snapshot = try await waitlistRef(gymId)
            .whereField("userId", isEqualTo: uid)
            .getDocuments()

        return Set(snapshot.documents.compactMap { doc -> UUID? in
            guard let classIdStr = doc.data()["classId"] as? String else { return nil }
            return UUID(uuidString: classIdStr)
        })
    }

    func fetchUserBookings(gymId: UUID) async throws -> Set<UUID> {
        guard let uid = currentUID() else { return [] }

        let snapshot = try await bookingsRef(gymId)
            .whereField("userId", isEqualTo: uid)
            .getDocuments()

        return Set(snapshot.documents.compactMap { doc -> UUID? in
            guard let classIdStr = doc.data()["classId"] as? String else { return nil }
            return UUID(uuidString: classIdStr)
        })
    }

    func isUserBooked(gymId: UUID, classId: UUID) async throws -> Bool {
        guard let uid = currentUID() else { return false }

        let bookings = try await bookingsRef(gymId)
            .whereField("userId", isEqualTo: uid)
            .whereField("classId", isEqualTo: classId.uuidString)
            .getDocuments()

        return !bookings.documents.isEmpty
    }

    // MARK: - Attendees

    func fetchAttendees(gymId: UUID, classId: UUID) async throws -> [Member] {
        let bookingsSnapshot = try await bookingsRef(gymId)
            .whereField("classId", isEqualTo: classId.uuidString)
            .getDocuments()

        let userIds = bookingsSnapshot.documents.compactMap { $0.data()["userId"] as? String }
        guard !userIds.isEmpty else { return [] }

        var members: [Member] = []

        // Firestore 'in' queries are limited to 30 values per call
        let chunks = stride(from: 0, to: userIds.count, by: 30).map {
            Array(userIds[$0..<min($0 + 30, userIds.count)])
        }

        for chunk in chunks {
            let usersSnapshot = try await db.collection("users")
                .whereField(FieldPath.documentID(), in: chunk)
                .getDocuments()

            for userDoc in usersSnapshot.documents {
                let data = userDoc.data()
                let firstName = data["firstName"] as? String ?? ""
                let lastName = data["lastName"] as? String ?? ""
                let email = data["email"] as? String ?? ""

                members.append(Member(
                    id: userDoc.documentID,
                    name: "\(firstName) \(lastName)",
                    email: email
                ))
            }
        }

        return members
    }

    // MARK: - Members

    func fetchMembers(gymId: UUID) async throws -> [Member] {
        let snapshot = try await membersRef(gymId).getDocuments()

        return snapshot.documents.map { doc in
            let data = doc.data()
            let firstName = data["firstName"] as? String ?? ""
            let lastName = data["lastName"] as? String ?? ""

            return Member(
                id: doc.documentID,
                name: "\(firstName) \(lastName)".trimmingCharacters(in: .whitespaces),
                email: data["email"] as? String ?? "",
                joinedAt: (data["joinedAt"] as? Timestamp)?.dateValue()
            )
        }
    }

    func fetchMemberBookings(gymId: UUID, userId: String) async throws -> [GymClass] {
        let bookingsSnapshot = try await bookingsRef(gymId)
            .whereField("userId", isEqualTo: userId)
            .getDocuments()

        let classIds = bookingsSnapshot.documents.compactMap { $0.data()["classId"] as? String }
        guard !classIds.isEmpty else { return [] }

        var classes: [GymClass] = []

        // Firestore 'in' queries are limited to 30 values per call
        let chunks = stride(from: 0, to: classIds.count, by: 30).map {
            Array(classIds[$0..<min($0 + 30, classIds.count)])
        }

        for chunk in chunks {
            let snapshot = try await classesRef(gymId)
                .whereField(FieldPath.documentID(), in: chunk)
                .getDocuments()
            classes.append(contentsOf: snapshot.documents.compactMap { parseClass(from: $0) })
        }

        return classes.sorted { $0.startTime > $1.startTime }
    }

    func addMember(gymId: UUID, firstName: String, lastName: String, email: String, password: String) async throws {
        guard currentUID() != nil else { throw FBError.notAuthenticated }

        let memberUID = try await createUserAccount(
            email: email,
            password: password,
            firstName: firstName,
            lastName: lastName
        )

        let joinedAt = Timestamp(date: Date())

        try await db.collection("users").document(memberUID)
            .collection("memberships").document(gymId.uuidString).setData([
                "role": UserRole.member.rawValue,
                "joinedAt": joinedAt
            ])

        try await membersRef(gymId).document(memberUID).setData([
            "firstName": firstName,
            "lastName": lastName,
            "email": email,
            "role": UserRole.member.rawValue,
            "joinedAt": joinedAt
        ])
    }

    // MARK: - Workouts

    func fetchWorkouts(gymId: UUID) async throws -> [Workout] {
        let snapshot = try await workoutsRef(gymId).getDocuments()
        return snapshot.documents.compactMap { parseWorkout(from: $0) }
    }

    func fetchWorkout(gymId: UUID, id: UUID) async throws -> Workout? {
        let doc = try await workoutsRef(gymId).document(id.uuidString).getDocument()
        guard doc.exists else { return nil }
        return parseWorkout(from: doc)
    }

    func createWorkout(gymId: UUID, _ workout: Workout) async throws {
        let exercisesData = workout.exercises.map { exercise in
            [
                "id": exercise.id.uuidString,
                "name": exercise.name,
                "reps": exercise.reps as Any,
                "duration": exercise.duration as Any,
                "notes": exercise.notes as Any
            ] as [String: Any]
        }

        let data: [String: Any] = [
            "name": workout.name,
            "type": workout.type.rawValue,
            "description": workout.description,
            "exercises": exercisesData,
            "durationMinutes": workout.durationMinutes,
            "difficulty": workout.difficulty.rawValue
        ]

        try await workoutsRef(gymId).document(workout.id.uuidString).setData(data)
    }

    func updateWorkout(gymId: UUID, _ workout: Workout) async throws {
        try await createWorkout(gymId: gymId, workout)
    }

    func deleteWorkout(gymId: UUID, id: UUID) async throws {
        try await workoutsRef(gymId).document(id.uuidString).delete()
    }

    // MARK: - Private Helpers

    private func gymRef(_ gymId: UUID) -> DocumentReference {
        db.collection("gyms").document(gymId.uuidString)
    }

    private func classesRef(_ gymId: UUID) -> CollectionReference {
        gymRef(gymId).collection("classes")
    }

    private func workoutsRef(_ gymId: UUID) -> CollectionReference {
        gymRef(gymId).collection("workouts")
    }

    private func bookingsRef(_ gymId: UUID) -> CollectionReference {
        gymRef(gymId).collection("bookings")
    }

    private func waitlistRef(_ gymId: UUID) -> CollectionReference {
        gymRef(gymId).collection("waitlist")
    }

    private func teamRef(_ gymId: UUID) -> CollectionReference {
        gymRef(gymId).collection("team")
    }

    private func membersRef(_ gymId: UUID) -> CollectionReference {
        gymRef(gymId).collection("members")
    }

    private func dayBounds(for date: Date) -> (start: Date, end: Date) {
        let calendar = Calendar.current
        let start = calendar.startOfDay(for: date)
        let end = calendar.date(byAdding: .day, value: 1, to: start)!
        return (start, end)
    }

    private func classData(for gymClass: GymClass, currentAttendees: Int) -> [String: Any] {
        var data: [String: Any] = [
            "title": gymClass.title,
            "coach": gymClass.coach,
            "startTime": Timestamp(date: gymClass.startTime),
            "durationMinutes": gymClass.durationMinutes,
            "capacity": gymClass.capacity,
            "currentAttendees": currentAttendees,
            "waitlistCount": gymClass.waitlistCount
        ]
        if let workoutId = gymClass.workoutId { data["workoutId"] = workoutId.uuidString }
        if let seriesId = gymClass.seriesId { data["seriesId"] = seriesId.uuidString }
        return data
    }

    private func parseClass(from doc: QueryDocumentSnapshot) -> GymClass? {
        let data = doc.data()
        guard let title = data["title"] as? String else { return nil }

        let coach = data["coach"] as? String ?? ""
        let startTime = (data["startTime"] as? Timestamp)?.dateValue() ?? Date()
        let duration = data["durationMinutes"] as? Int ?? 60
        let capacity = data["capacity"] as? Int ?? 12
        let currentAttendees = data["currentAttendees"] as? Int ?? 0
        let waitlistCount = data["waitlistCount"] as? Int ?? 0
        let workoutId = (data["workoutId"] as? String).flatMap { UUID(uuidString: $0) }
        let seriesId = (data["seriesId"] as? String).flatMap { UUID(uuidString: $0) }

        return GymClass(
            id: UUID(uuidString: doc.documentID) ?? UUID(),
            title: title,
            coach: coach,
            startTime: startTime,
            durationMinutes: duration,
            capacity: capacity,
            currentAttendees: currentAttendees,
            waitlistCount: waitlistCount,
            attendees: [],
            workoutId: workoutId,
            seriesId: seriesId
        )
    }

    private func parseWorkout(from doc: DocumentSnapshot) -> Workout? {
        guard let data = doc.data(),
              let name = data["name"] as? String,
              let typeStr = data["type"] as? String,
              let type = WorkoutType(rawValue: typeStr),
              let description = data["description"] as? String else { return nil }

        let durationMinutes = data["durationMinutes"] as? Int ?? 60
        let difficulty = Difficulty(rawValue: data["difficulty"] as? String ?? "") ?? .intermediate

        var exercises: [Exercise] = []
        if let exercisesData = data["exercises"] as? [[String: Any]] {
            exercises = exercisesData.compactMap { dict in
                guard let name = dict["name"] as? String else { return nil }
                let id = UUID(uuidString: dict["id"] as? String ?? "") ?? UUID()
                return Exercise(
                    id: id,
                    name: name,
                    reps: dict["reps"] as? String,
                    duration: dict["duration"] as? String,
                    notes: dict["notes"] as? String
                )
            }
        }

        return Workout(
            id: UUID(uuidString: doc.documentID) ?? UUID(),
            name: name,
            type: type,
            description: description,
            exercises: exercises,
            durationMinutes: durationMinutes,
            difficulty: difficulty
        )
    }
}
