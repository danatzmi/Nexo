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
import FirebaseFunctions

enum FBError: Error, LocalizedError {
    case notAuthenticated
    case classNotFound
    case classFull
    case userNotFound
    case bookingNotFound
    case noActiveMembership
    case insufficientCredits
    case classInPast
    case unknown

    var errorDescription: String? {
        switch self {
        case .notAuthenticated: return "User not authenticated."
        case .classNotFound: return "Class not found."
        case .classFull: return "This class is full."
        case .userNotFound: return "User profile not found."
        case .bookingNotFound: return "Booking not found."
        case .noActiveMembership: return "No active membership. Please contact your gym to purchase a plan."
        case .insufficientCredits: return "Insufficient credits. Please contact your gym to purchase more."
        case .classInPast: return "Cannot book or join waitlist for a class that has already started."
        case .unknown: return "Unknown error."
        }
    }
}

@MainActor
final class FirebaseBackend: BackendService {
    static let shared = FirebaseBackend()

    private let auth = Auth.auth()
    private let db = Firestore.firestore()
    private lazy var functions = Functions.functions(region: "us-central1")

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

    func sendPasswordReset(email: String) async throws {
        try await auth.sendPasswordReset(withEmail: email)
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
            role: PlatformRole(rawValue: roleStr) ?? .user,
            profilePicBase64: data["profilePicBase64"] as? String
        )
    }

    func updateProfilePicture(base64String: String) async throws {
        guard let uid = currentUID() else { throw FBError.notAuthenticated }
        try await db.collection("users").document(uid).updateData(["profilePicBase64": base64String])
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
                role: PlatformRole(rawValue: roleStr) ?? .user,
                profilePicBase64: data["profilePicBase64"] as? String
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
            let workoutTypes = gymData["workoutTypes"] as? [String] ?? WorkoutCategory.defaults
            let city = gymData["city"] as? String

            results.append((gym: Gym(id: gymId, name: gymName, ownerUID: ownerUID, workoutTypes: workoutTypes, city: city), role: role))
        }

        return results
    }

    func fetchAvailableGyms() async throws -> [Gym] {
        let snapshot = try await db.collection("gyms").getDocuments()

        return snapshot.documents.compactMap { doc in
            guard let name = doc.data()["name"] as? String,
                  let ownerUID = doc.data()["ownerUID"] as? String,
                  let id = UUID(uuidString: doc.documentID) else { return nil }
            let workoutTypes = doc.data()["workoutTypes"] as? [String] ?? WorkoutCategory.defaults
            let city = doc.data()["city"] as? String
            return Gym(id: id, name: name, ownerUID: ownerUID, workoutTypes: workoutTypes, city: city)
        }
    }

    func createGym(name: String, city: String?, workoutTypes: [String], ownerFirstName: String, ownerLastName: String, ownerEmail: String, ownerPassword: String) async throws -> Gym {
        guard currentUID() != nil else { throw FBError.notAuthenticated }

        let ownerUID: String
        let resolvedFirstName: String
        let resolvedLastName: String

        let existingUsers = try await db.collection("users")
            .whereField("email", isEqualTo: ownerEmail)
            .getDocuments()

        if let existingDoc = existingUsers.documents.first {
            ownerUID = existingDoc.documentID
            let data = existingDoc.data()
            resolvedFirstName = data["firstName"] as? String ?? ownerFirstName
            resolvedLastName = data["lastName"] as? String ?? ownerLastName
        } else {
            ownerUID = try await createUserAccount(
                email: ownerEmail,
                password: ownerPassword,
                firstName: ownerFirstName,
                lastName: ownerLastName
            )
            resolvedFirstName = ownerFirstName
            resolvedLastName = ownerLastName
        }

        let resolvedWorkoutTypes = workoutTypes.isEmpty ? WorkoutCategory.defaults : workoutTypes
        let trimmedCity = city?.trimmingCharacters(in: .whitespacesAndNewlines)
        let gym = Gym(name: name, ownerUID: ownerUID, workoutTypes: resolvedWorkoutTypes, city: (trimmedCity?.isEmpty ?? true) ? nil : trimmedCity)

        var gymData: [String: Any] = [
            "name": gym.name,
            "ownerUID": ownerUID,
            "workoutTypes": gym.workoutTypes,
            "createdAt": Timestamp(date: Date())
        ]
        if let city = gym.city {
            gymData["city"] = city
        }

        try await db.collection("gyms").document(gym.id.uuidString).setData(gymData)

        try await db.collection("users").document(ownerUID)
            .collection("memberships").document(gym.id.uuidString).setData([
                "role": UserRole.owner.rawValue,
                "joinedAt": Timestamp(date: Date())
            ])

        try await teamRef(gym.id).document(ownerUID).setData([
            "role": UserRole.owner.rawValue,
            "firstName": resolvedFirstName,
            "lastName": resolvedLastName,
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

    func addTeamMember(gymId: UUID, firstName: String, lastName: String, email: String, password: String, role: UserRole) async throws {
        guard currentUID() != nil else { throw FBError.notAuthenticated }

        let memberUID = try await createUserAccount(
            email: email,
            password: password,
            firstName: firstName,
            lastName: lastName
        )

        try await db.collection("users").document(memberUID)
            .collection("memberships").document(gymId.uuidString).setData([
                "role": role.rawValue,
                "joinedAt": Timestamp(date: Date())
            ])

        try await teamRef(gymId).document(memberUID).setData([
            "role": role.rawValue,
            "firstName": firstName,
            "lastName": lastName,
            "email": email,
            "addedAt": Timestamp(date: Date())
        ])
    }

    func updateTeamMemberRole(gymId: UUID, userId: String, role: UserRole) async throws {
        try await membershipRef(gymId: gymId, userId: userId).updateData(["role": role.rawValue])
        try await teamRef(gymId).document(userId).updateData(["role": role.rawValue])
    }

    func removeTeamMember(gymId: UUID, userId: String) async throws {
        try await membershipRef(gymId: gymId, userId: userId).delete()
        try await teamRef(gymId).document(userId).delete()
    }

    func addExistingUserToGym(gymId: UUID, userId: String, role: UserRole) async throws {
        let userDoc = try await db.collection("users").document(userId).getDocument()
        guard let data = userDoc.data() else { throw FBError.userNotFound }
        let firstName = data["firstName"] as? String ?? ""
        let lastName = data["lastName"] as? String ?? ""
        let email = data["email"] as? String ?? ""

        try await db.collection("users").document(userId)
            .collection("memberships").document(gymId.uuidString).setData([
                "role": role.rawValue,
                "joinedAt": Timestamp(date: Date())
            ])

        if role == .member {
            try await membersRef(gymId).document(userId).setData([
                "firstName": firstName,
                "lastName": lastName,
                "email": email,
                "role": role.rawValue,
                "joinedAt": Timestamp(date: Date())
            ])
        } else {
            try await teamRef(gymId).document(userId).setData([
                "role": role.rawValue,
                "firstName": firstName,
                "lastName": lastName,
                "email": email,
                "addedAt": Timestamp(date: Date())
            ])
        }
    }

    func updateGymSettings(gymId: UUID, name: String, workoutTypes: [String]) async throws {
        try await db.collection("gyms").document(gymId.uuidString).updateData([
            "name": name,
            "workoutTypes": workoutTypes
        ])
    }

    /// Permanently deletes a gym and everything under it: every user's
    /// membership (and credit wallet) for this gym, every gym subcollection,
    /// and the gym document itself. There is no undo — the caller is
    /// responsible for confirming with the user first.
    func deleteGym(gymId: UUID) async throws {
        let users = try await fetchAllUsers()
        for user in users {
            let membershipRef = db.collection("users").document(user.id)
                .collection("memberships").document(gymId.uuidString)
            try await deleteAllDocuments(in: membershipRef.collection("activePlans"))
            try await membershipRef.delete()
        }

        for subcollection in ["classes", "classSeries", "membershipPlans", "bookings", "waitlist", "team", "members"] {
            try await deleteAllDocuments(in: gymRef(gymId).collection(subcollection))
        }

        try await gymRef(gymId).delete()
    }

    /// Firestore batches are capped at 500 writes — chunks so this holds up
    /// for any collection size, not just today's small test-scale gyms.
    private func deleteAllDocuments(in collection: CollectionReference) async throws {
        let documents = try await collection.getDocuments().documents
        var index = 0
        while index < documents.count {
            let chunk = documents[index..<min(index + 400, documents.count)]
            let batch = db.batch()
            chunk.forEach { batch.deleteDocument($0.reference) }
            try await batch.commit()
            index += 400
        }
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

    /// Applies `updatedTemplate`'s fields to every occurrence in the series
    /// on or after `startTime`, preserving each occurrence's own date —
    /// only the time-of-day shifts, taken from `updatedTemplate.startTime`.
    /// Each occurrence keeps its own `currentAttendees`/`waitlistCount`
    /// (read fresh per-document, not copied from the template) since those
    /// are per-occurrence roster state, not part of the edited template.
    func updateClassSeries(gymId: UUID, seriesId: UUID, from startTime: Date, updatedTemplate: GymClass) async throws {
        let snapshot = try await classesRef(gymId)
            .whereField("seriesId", isEqualTo: seriesId.uuidString)
            .getDocuments()

        let toUpdate = snapshot.documents.filter { doc in
            guard let ts = doc.data()["startTime"] as? Timestamp else { return false }
            return ts.dateValue() >= startTime
        }
        guard !toUpdate.isEmpty else { return }

        let calendar = Calendar.current
        let templateTime = calendar.dateComponents([.hour, .minute], from: updatedTemplate.startTime)

        let batch = db.batch()
        for doc in toUpdate {
            guard let originalStart = (doc.data()["startTime"] as? Timestamp)?.dateValue() else { continue }

            var dateComponents = calendar.dateComponents([.year, .month, .day], from: originalStart)
            dateComponents.hour = templateTime.hour
            dateComponents.minute = templateTime.minute
            let newStart = calendar.date(from: dateComponents) ?? originalStart

            var occurrence = updatedTemplate
            occurrence.startTime = newStart
            occurrence.seriesId = seriesId
            occurrence.currentAttendees = doc.data()["currentAttendees"] as? Int ?? 0
            occurrence.waitlistCount = doc.data()["waitlistCount"] as? Int ?? 0

            batch.setData(classData(for: occurrence, currentAttendees: occurrence.currentAttendees), forDocument: doc.reference)
        }
        try await batch.commit()
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

    func copySchedule(gymId: UUID, fromWeekOf sourceDate: Date, toWeekOf targetDate: Date) async throws {
        let sourceWeekStart = mondayStart(for: sourceDate)
        let sourceWeekEnd = Calendar.current.date(byAdding: .day, value: 7, to: sourceWeekStart)!
        let targetWeekStart = mondayStart(for: targetDate)

        let snapshot = try await classesRef(gymId)
            .whereField("startTime", isGreaterThanOrEqualTo: Timestamp(date: sourceWeekStart))
            .whereField("startTime", isLessThan: Timestamp(date: sourceWeekEnd))
            .getDocuments()

        let sourceClasses = snapshot.documents.compactMap { parseClass(from: $0) }
        guard !sourceClasses.isEmpty else { return }

        let batch = db.batch()
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
            let ref = classesRef(gymId).document(newClass.id.uuidString)
            batch.setData(classData(for: newClass, currentAttendees: 0), forDocument: ref)
        }
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

    /// Atomically adjusts an Int counter field (`currentAttendees`/`waitlistCount`)
    /// on a class document, clamped to a floor of 0. `FieldValue.increment` alone
    /// can't express this floor since it blindly applies a delta without ever
    /// looking at the field's current value — if the counter is ever already at 0
    /// (a race between concurrent cancels, a stale/duplicate call, or any other
    /// drift) a plain `increment(-1)` drives it negative, and the live
    /// `observeClasses` listener then faithfully pushes that bad value back down
    /// to every client, overwriting any client-side clamp. Reading and writing
    /// inside a transaction closes this at the source.
    private func adjustClassCounter(gymId: UUID, classId: UUID, field: String, by delta: Int) async throws {
        let ref = classesRef(gymId).document(classId.uuidString)
        _ = try await db.runTransaction { transaction, errorPointer in
            let snapshot: DocumentSnapshot
            do {
                snapshot = try transaction.getDocument(ref)
            } catch let error as NSError {
                errorPointer?.pointee = error
                return nil
            }
            let current = snapshot.data()?[field] as? Int ?? 0
            let updated = max(0, current + delta)
            transaction.updateData([field: updated], forDocument: ref)
            return nil
        }
    }

    // MARK: - Booking & Concurrency (Authoritative Cloud Functions)

    func book(gymId: UUID, classId: UUID) async throws {
        guard currentUID() != nil else { throw FBError.notAuthenticated }
        do {
            _ = try await functions.httpsCallable("bookClass").call([
                "gymId": gymId.uuidString,
                "classId": classId.uuidString
            ])
        } catch {
            throw parseFunctionsError(error)
        }
    }

    func cancelBooking(gymId: UUID, classId: UUID) async throws {
        guard let uid = currentUID() else { throw FBError.notAuthenticated }
        let snapshot = try await bookingsRef(gymId)
            .whereField("userId", isEqualTo: uid)
            .whereField("classId", isEqualTo: classId.uuidString)
            .getDocuments()
        guard let bookingDoc = snapshot.documents.first else { throw FBError.bookingNotFound }

        do {
            _ = try await functions.httpsCallable("cancelBooking").call([
                "gymId": gymId.uuidString,
                "classId": classId.uuidString,
                "bookingId": bookingDoc.documentID
            ])
        } catch {
            throw parseFunctionsError(error)
        }
    }

    func cancelBooking(gymId: UUID, classId: UUID, onBehalfOf userId: String) async throws {
        let snapshot = try await bookingsRef(gymId)
            .whereField("userId", isEqualTo: userId)
            .whereField("classId", isEqualTo: classId.uuidString)
            .getDocuments()
        guard let bookingDoc = snapshot.documents.first else { throw FBError.bookingNotFound }

        do {
            _ = try await functions.httpsCallable("cancelBooking").call([
                "gymId": gymId.uuidString,
                "classId": classId.uuidString,
                "bookingId": bookingDoc.documentID,
                "onBehalfOfUserId": userId
            ])
        } catch {
            throw parseFunctionsError(error)
        }
    }

    // MARK: - Waitlist (Authoritative Cloud Functions)

    func joinWaitlist(gymId: UUID, classId: UUID) async throws {
        guard currentUID() != nil else { throw FBError.notAuthenticated }
        do {
            _ = try await functions.httpsCallable("joinWaitlist").call([
                "gymId": gymId.uuidString,
                "classId": classId.uuidString
            ])
        } catch {
            throw parseFunctionsError(error)
        }
    }

    func leaveWaitlist(gymId: UUID, classId: UUID) async throws {
        guard currentUID() != nil else { throw FBError.notAuthenticated }
        do {
            _ = try await functions.httpsCallable("leaveWaitlist").call([
                "gymId": gymId.uuidString,
                "classId": classId.uuidString
            ])
        } catch {
            throw parseFunctionsError(error)
        }
    }

    private func parseFunctionsError(_ error: Error) -> Error {
        let errorString = "\(error)"
        if errorString.contains("CLASS_FULL") || errorString.contains("Class is full") {
            return FBError.classFull
        }
        if errorString.contains("INSUFFICIENT_CREDITS") || errorString.contains("No available credits") {
            return FBError.insufficientCredits
        }
        if errorString.contains("NO_ACTIVE_PLAN") || errorString.contains("No active membership") {
            return FBError.noActiveMembership
        }
        if errorString.contains("CLASS_IN_PAST") || errorString.contains("already started") {
            return FBError.classInPast
        }
        if errorString.contains("CLASS_NOT_FOUND") || errorString.contains("Class not found") {
            return FBError.classNotFound
        }
        if errorString.contains("BOOKING_NOT_FOUND") || errorString.contains("Booking not found") {
            return FBError.bookingNotFound
        }
        return error
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

    func fetchWaitlistPosition(gymId: UUID, classId: UUID) async throws -> (position: Int, total: Int)? {
        guard let uid = currentUID() else { return nil }

        let snapshot = try await waitlistRef(gymId)
            .whereField("classId", isEqualTo: classId.uuidString)
            .getDocuments()

        let sorted = snapshot.documents.sorted { lhs, rhs in
            let lhsJoined = (lhs.data()["joinedAt"] as? Timestamp)?.dateValue() ?? .distantPast
            let rhsJoined = (rhs.data()["joinedAt"] as? Timestamp)?.dateValue() ?? .distantPast
            return lhsJoined < rhsJoined
        }

        guard let index = sorted.firstIndex(where: { $0.data()["userId"] as? String == uid }) else { return nil }
        return (position: index + 1, total: sorted.count)
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

        var checkInStatusByUserId: [String: (isCheckedIn: Bool, checkedInAt: Date?)] = [:]
        for doc in bookingsSnapshot.documents {
            let data = doc.data()
            if let userId = data["userId"] as? String {
                let isCheckedIn = data["checkedIn"] as? Bool ?? false
                let checkedInAt = (data["checkedInAt"] as? Timestamp)?.dateValue()
                checkInStatusByUserId[userId] = (isCheckedIn, checkedInAt)
            }
        }

        let userIds = Array(checkInStatusByUserId.keys)
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
                let status = checkInStatusByUserId[userDoc.documentID]

                members.append(Member(
                    id: userDoc.documentID,
                    name: "\(firstName) \(lastName)".trimmingCharacters(in: .whitespaces),
                    email: email,
                    profilePicBase64: data["profilePicBase64"] as? String,
                    isCheckedIn: status?.isCheckedIn ?? false,
                    checkedInAt: status?.checkedInAt
                ))
            }
        }

        return members
    }

    func toggleAttendance(gymId: UUID, classId: UUID, userId: String, isCheckedIn: Bool) async throws {
        let snapshot = try await bookingsRef(gymId)
            .whereField("classId", isEqualTo: classId.uuidString)
            .whereField("userId", isEqualTo: userId)
            .getDocuments()

        guard let bookingDoc = snapshot.documents.first else {
            throw FBError.bookingNotFound
        }

        var updateData: [String: Any] = [
            "checkedIn": isCheckedIn
        ]
        if isCheckedIn {
            updateData["checkedInAt"] = Timestamp(date: Date())
        } else {
            updateData["checkedInAt"] = FieldValue.delete()
        }

        try await bookingDoc.reference.updateData(updateData)
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
                joinedAt: (data["joinedAt"] as? Timestamp)?.dateValue(),
                profilePicBase64: data["profilePicBase64"] as? String
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

    func removeMember(gymId: UUID, userId: String) async throws {
        let activePlansSnapshot = try await activePlansRef(gymId: gymId, userId: userId).getDocuments()

        let batch = db.batch()
        for doc in activePlansSnapshot.documents {
            batch.deleteDocument(doc.reference)
        }
        batch.deleteDocument(membershipRef(gymId: gymId, userId: userId))
        batch.deleteDocument(membersRef(gymId).document(userId))
        try await batch.commit()
    }

    // MARK: - Logbook

    func fetchWorkoutLogs(gymId: UUID) async throws -> [WorkoutLog] {
        guard let uid = currentUID() else { throw FBError.notAuthenticated }

        let snapshot = try await workoutLogsRef(gymId: gymId, userId: uid).getDocuments()
        return snapshot.documents.compactMap { doc in
            let data = doc.data()
            guard let movement = data["movement"] as? String,
                  let date = (data["date"] as? Timestamp)?.dateValue() else { return nil }
            return WorkoutLog(
                id: doc.documentID,
                movement: movement,
                score: data["score"] as? Double,
                reps: data["reps"] as? Int,
                sets: data["sets"] as? Int,
                date: date
            )
        }
    }

    func addWorkoutLog(gymId: UUID, log: WorkoutLog) async throws {
        guard let uid = currentUID() else { throw FBError.notAuthenticated }
        // A full `setData` (no merge) so editing a log that drops a
        // previously-set field (e.g. clearing Score) actually removes it,
        // rather than leaving the old value stuck.
        var data: [String: Any] = [
            "movement": log.movement,
            "date": Timestamp(date: log.date)
        ]
        if let score = log.score { data["score"] = score }
        if let reps = log.reps { data["reps"] = reps }
        if let sets = log.sets { data["sets"] = sets }
        try await workoutLogsRef(gymId: gymId, userId: uid).document(log.id).setData(data)
    }

    /// `setData` on an existing document ID is already an upsert, so an
    /// edit is just re-running the same write `addWorkoutLog` does.
    func updateWorkoutLog(gymId: UUID, log: WorkoutLog) async throws {
        try await addWorkoutLog(gymId: gymId, log: log)
    }

    func deleteWorkoutLog(gymId: UUID, logId: String) async throws {
        guard let uid = currentUID() else { throw FBError.notAuthenticated }
        try await workoutLogsRef(gymId: gymId, userId: uid).document(logId).delete()
    }

    // MARK: - Membership Plans & Credit Wallet

    func createMembershipPlan(gymId: UUID, plan: MembershipPlan) async throws {
        try await membershipPlansRef(gymId).document(plan.id.uuidString).setData(membershipPlanData(for: plan))
    }

    func fetchMembershipPlans(gymId: UUID) async throws -> [MembershipPlan] {
        let snapshot = try await membershipPlansRef(gymId).getDocuments()
        return snapshot.documents.compactMap { membershipPlan(from: $0) }
    }

    func updateMembershipPlan(gymId: UUID, plan: MembershipPlan) async throws {
        try await membershipPlansRef(gymId).document(plan.id.uuidString).setData(membershipPlanData(for: plan))
    }

    func deleteMembershipPlan(gymId: UUID, planId: UUID) async throws {
        try await membershipPlansRef(gymId).document(planId.uuidString).delete()
    }

    func grantPlanToMember(gymId: UUID, userId: String, plan: MembershipPlan, customExpiresAt: Date? = nil) async throws {
        let batch = db.batch()
        let now = Date()
        for component in plan.components {
            let expiresAt = customExpiresAt ?? Calendar.current.date(
                byAdding: component.validityUnit.calendarComponent,
                value: component.validityValue,
                to: now
            ) ?? now

            var data: [String: Any] = [
                "planName": plan.name,
                "type": component.type.rawValue,
                "resetPeriod": component.resetPeriod.rawValue,
                "creditCount": component.creditCount,
                "remainingCredits": component.resetPeriod == .none ? (component.type == .unlimited ? 0 : component.creditCount) : 0,
                "cycleCreditsUsed": 0,
                "cycleAnchorDate": Timestamp(date: now),
                "lastCycleIndex": 0,
                "expiresAt": Timestamp(date: expiresAt)
            ]
            if let workoutType = component.workoutType {
                data["workoutType"] = workoutType
            }
            batch.setData(data, forDocument: activePlansRef(gymId: gymId, userId: userId).document())
        }
        try await batch.commit()
    }

    func revokeActivePlan(gymId: UUID, userId: String, activePlanId: String) async throws {
        try await activePlansRef(gymId: gymId, userId: userId).document(activePlanId).delete()
    }

    func fetchActivePlans(gymId: UUID, userId: String) async throws -> [ActivePlanItem] {
        let snapshot = try await activePlansRef(gymId: gymId, userId: userId).getDocuments()
        return snapshot.documents.compactMap { activePlanItem(from: $0) }
    }

    // MARK: - Private Helpers

    private func gymRef(_ gymId: UUID) -> DocumentReference {
        db.collection("gyms").document(gymId.uuidString)
    }

    private func classesRef(_ gymId: UUID) -> CollectionReference {
        gymRef(gymId).collection("classes")
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

    private func workoutLogsRef(gymId: UUID, userId: String) -> CollectionReference {
        membersRef(gymId).document(userId).collection("workoutLogs")
    }

    private func membershipRef(gymId: UUID, userId: String) -> DocumentReference {
        db.collection("users").document(userId).collection("memberships").document(gymId.uuidString)
    }

    private func membershipPlansRef(_ gymId: UUID) -> CollectionReference {
        gymRef(gymId).collection("membershipPlans")
    }

    private func activePlansRef(gymId: UUID, userId: String) -> CollectionReference {
        membershipRef(gymId: gymId, userId: userId).collection("activePlans")
    }

    private func membershipPlanData(for plan: MembershipPlan) -> [String: Any] {
        var componentsData: [[String: Any]] = []
        for component in plan.components {
            var data: [String: Any] = [
                "id": component.id.uuidString,
                "type": component.type.rawValue,
                "resetPeriod": component.resetPeriod.rawValue,
                "creditCount": component.creditCount,
                "validityValue": component.validityValue,
                "validityUnit": component.validityUnit.rawValue
            ]
            if let workoutType = component.workoutType {
                data["workoutType"] = workoutType
            }
            componentsData.append(data)
        }
        return [
            "name": plan.name,
            "type": plan.type.rawValue,
            "price": plan.price,
            "components": componentsData
        ]
    }

    private func membershipPlan(from doc: QueryDocumentSnapshot) -> MembershipPlan? {
        let data = doc.data()
        guard let name = data["name"] as? String,
              let planId = UUID(uuidString: doc.documentID) else { return nil }

        let typeStr = data["type"] as? String ?? "monthly"
        let type = PlanType(rawValue: typeStr) ?? .monthly
        let price = data["price"] as? Double ?? 0
        let rawComponents = data["components"] as? [Any] ?? []
        let components: [PlanComponent] = rawComponents.compactMap { element in
            guard let dict = element as? [String: Any],
                  let compTypeStr = dict["type"] as? String,
                  let compType = PlanComponentType(rawValue: compTypeStr) else { return nil }

            let id = UUID(uuidString: dict["id"] as? String ?? "") ?? UUID()
            let resetPeriodStr = dict["resetPeriod"] as? String ?? PlanResetPeriod.none.rawValue
            let resetPeriod = PlanResetPeriod(rawValue: resetPeriodStr) ?? .none
            let workoutType = dict["workoutType"] as? String
            let creditCount = dict["creditCount"] as? Int ?? 0
            let validityValue = dict["validityValue"] as? Int ?? 1
            let validityUnit = ValidityUnit(rawValue: dict["validityUnit"] as? String ?? "") ?? .months
            return PlanComponent(
                id: id,
                type: compType,
                resetPeriod: resetPeriod,
                workoutType: workoutType,
                creditCount: creditCount,
                validityValue: validityValue,
                validityUnit: validityUnit
            )
        }
        return MembershipPlan(id: planId, name: name, type: type, price: price, components: components)
    }

    private func activePlanItem(from doc: QueryDocumentSnapshot) -> ActivePlanItem? {
        let data = doc.data()
        guard let planName = data["planName"] as? String,
              let typeStr = data["type"] as? String,
              let type = PlanComponentType(rawValue: typeStr),
              let expiresAt = (data["expiresAt"] as? Timestamp)?.dateValue() else { return nil }

        let resetPeriodStr = data["resetPeriod"] as? String ?? PlanResetPeriod.none.rawValue
        let resetPeriod = PlanResetPeriod(rawValue: resetPeriodStr) ?? .none
        let workoutType = data["workoutType"] as? String
        let creditCount = data["creditCount"] as? Int ?? 0
        let remainingCredits = data["remainingCredits"] as? Int ?? 0
        let cycleCreditsUsed = data["cycleCreditsUsed"] as? Int ?? 0
        let cycleAnchorDate = (data["cycleAnchorDate"] as? Timestamp)?.dateValue() ?? expiresAt
        let lastCycleIndex = data["lastCycleIndex"] as? Int ?? 0

        return ActivePlanItem(
            id: doc.documentID,
            planName: planName,
            type: type,
            resetPeriod: resetPeriod,
            workoutType: workoutType,
            creditCount: creditCount,
            remainingCredits: remainingCredits,
            cycleCreditsUsed: cycleCreditsUsed,
            cycleAnchorDate: cycleAnchorDate,
            lastCycleIndex: lastCycleIndex,
            expiresAt: expiresAt
        )
    }

    private func dayBounds(for date: Date) -> (start: Date, end: Date) {
        let calendar = Calendar.current
        let start = calendar.startOfDay(for: date)
        let end = calendar.date(byAdding: .day, value: 1, to: start)!
        return (start, end)
    }

    /// Midnight on the Monday of the week containing `date`, independent of the
    /// device's locale/first-weekday setting — schedule copying needs a fixed,
    /// deterministic week boundary, not a locale-dependent one.
    private func mondayStart(for date: Date) -> Date {
        let calendar = Calendar.current
        let startOfDay = calendar.startOfDay(for: date)
        let weekday = calendar.component(.weekday, from: startOfDay) // 1 = Sunday ... 7 = Saturday, fixed regardless of locale
        let daysSinceMonday = (weekday + 5) % 7
        return calendar.date(byAdding: .day, value: -daysSinceMonday, to: startOfDay)!
    }

    private func classData(for gymClass: GymClass, currentAttendees: Int) -> [String: Any] {
        var data: [String: Any] = [
            "title": gymClass.title,
            "coach": gymClass.coach,
            "startTime": Timestamp(date: gymClass.startTime),
            "durationMinutes": gymClass.durationMinutes,
            "capacity": gymClass.capacity,
            "currentAttendees": currentAttendees,
            "waitlistCount": gymClass.waitlistCount,
            "classType": gymClass.classType,
            "isPremium": gymClass.isPremium,
            "description": gymClass.description
        ]
        if let seriesId = gymClass.seriesId { data["seriesId"] = seriesId.uuidString }
        return data
    }

    private func parseClass(id: String, data: [String: Any]) -> GymClass? {
        guard let title = data["title"] as? String else { return nil }

        let coach = data["coach"] as? String ?? ""
        let startTime = (data["startTime"] as? Timestamp)?.dateValue() ?? Date()
        let duration = data["durationMinutes"] as? Int ?? 60
        let capacity = data["capacity"] as? Int ?? 12
        let currentAttendees = data["currentAttendees"] as? Int ?? 0
        let waitlistCount = data["waitlistCount"] as? Int ?? 0
        let seriesId = (data["seriesId"] as? String).flatMap { UUID(uuidString: $0) }
        let classType = data["classType"] as? String ?? "CrossFit WOD"
        let isPremium = data["isPremium"] as? Bool ?? false
        let description = data["description"] as? String ?? ""

        return GymClass(
            id: UUID(uuidString: id) ?? UUID(),
            title: title,
            coach: coach,
            startTime: startTime,
            durationMinutes: duration,
            capacity: capacity,
            currentAttendees: currentAttendees,
            waitlistCount: waitlistCount,
            attendees: [],
            seriesId: seriesId,
            classType: classType,
            isPremium: isPremium,
            description: description
        )
    }

    private func parseClass(from doc: QueryDocumentSnapshot) -> GymClass? {
        parseClass(id: doc.documentID, data: doc.data())
    }

    private func parseClass(from doc: DocumentSnapshot) -> GymClass? {
        guard let data = doc.data() else { return nil }
        return parseClass(id: doc.documentID, data: data)
    }

}
