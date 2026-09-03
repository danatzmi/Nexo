package com.nexo.app.data.repository

import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.nexo.app.domain.model.ActivePlanItem
import com.nexo.app.domain.model.Gym
import com.nexo.app.domain.model.GymClass
import com.nexo.app.domain.model.GymMember
import com.nexo.app.domain.model.Member
import com.nexo.app.domain.model.MembershipPlan
import com.nexo.app.domain.model.PlanComponent
import com.nexo.app.domain.model.PlanComponentType
import com.nexo.app.domain.model.PlanResetPeriod
import com.nexo.app.domain.model.PlanType
import com.nexo.app.domain.model.PlatformRole
import com.nexo.app.domain.model.PlatformUser
import com.nexo.app.domain.model.TeamMember
import com.nexo.app.domain.model.UserRole
import com.nexo.app.domain.model.ValidityUnit
import com.nexo.app.domain.model.WorkoutLog
import com.nexo.app.domain.model.applyTimeOfDay
import com.nexo.app.domain.model.mondayStartMillis
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

/**
 * Real [BackendRepository] implementation over Firebase Auth + Firestore —
 * mirrors `FirebaseBackend` on iOS. Collection paths and field names must
 * match `FIRESTORE_SCHEMA.md` exactly, since Firestore is the shared,
 * platform-neutral contract between the iOS and Android clients.
 */
class FirebaseBackendRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : BackendRepository {

    private val functions: FirebaseFunctions = Firebase.functions("us-central1")

    private companion object {
        const val SECONDARY_APP_NAME = "Secondary"
        const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000
    }

    override fun currentUID(): String? = auth.currentUser?.uid

    override suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    override suspend fun signUp(email: String, password: String, firstName: String, lastName: String) {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw BackendException.NotAuthenticated

        val data = mapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "email" to (result.user?.email ?: email),
            "role" to PlatformRole.USER.firestoreValue
        )
        firestore.collection("users").document(uid).set(data).await()
    }

    override fun signOut() {
        auth.signOut()
    }

    override suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    override suspend fun fetchMyProfile(): Member? {
        val uid = currentUID() ?: return null
        val doc = firestore.collection("users").document(uid).get().await()
        if (!doc.exists()) return null

        val firstName = doc.getString("firstName").orEmpty()
        val lastName = doc.getString("lastName").orEmpty()
        return Member(
            id = uid,
            fullName = "$firstName $lastName".trim(),
            email = doc.getString("email").orEmpty(),
            profilePicBase64 = doc.getString("profilePicBase64")
        )
    }

    override suspend fun fetchPlatformRole(): PlatformRole {
        val uid = currentUID() ?: return PlatformRole.USER
        val doc = firestore.collection("users").document(uid).get().await()
        val roleStr = doc.getString("role").orEmpty()
        return PlatformRole.fromFirestoreValue(roleStr)
    }

    override suspend fun fetchAvailableGyms(): List<Gym> {
        val snapshot = firestore.collection("gyms").get().await()
        return snapshot.documents.mapNotNull { parseGym(it) }
    }

    override suspend fun fetchMyGyms(): List<Pair<Gym, UserRole>> {
        val uid = currentUID() ?: return emptyList()

        val platformRole = fetchPlatformRole()
        if (platformRole == PlatformRole.ADMIN) {
            return fetchAvailableGyms().map { it to UserRole.OWNER }
        }

        val memberships = firestore.collection("users").document(uid)
            .collection("memberships").get().await()

        val results = mutableListOf<Pair<Gym, UserRole>>()
        val seenGymIds = mutableSetOf<String>()

        for (membershipDoc in memberships.documents) {
            val roleStr = membershipDoc.getString("role") ?: continue
            val gymId = membershipDoc.id

            val gymDoc = firestore.collection("gyms").document(gymId).get().await()
            val gym = parseGym(gymDoc) ?: continue

            seenGymIds.add(gymId)
            results.add(gym to UserRole.fromFirestoreValue(roleStr))
        }

        // Also include any gyms where this user is the owner (ownerUID == uid) if not already in memberships
        val ownedGymsSnapshot = firestore.collection("gyms").whereEqualTo("ownerUID", uid).get().await()
        for (gymDoc in ownedGymsSnapshot.documents) {
            if (gymDoc.id !in seenGymIds) {
                val gym = parseGym(gymDoc) ?: continue
                results.add(gym to UserRole.OWNER)
            }
        }

        return results
    }

    override suspend fun fetchClasses(gymId: String): List<GymClass> {
        val snapshot = classesRef(gymId).get().await()
        return snapshot.documents.mapNotNull { parseClass(it) }
    }

    override fun observeClasses(gymId: String): Flow<List<GymClass>> = callbackFlow {
        val registration = classesRef(gymId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                trySend(snapshot.documents.mapNotNull { parseClass(it) })
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun fetchMyBookedClassIds(gymId: String): Set<String> {
        val uid = currentUID() ?: return emptySet()
        val snapshot = bookingsRef(gymId).whereEqualTo("userId", uid).get().await()
        return snapshot.documents.mapNotNull { it.getString("classId") }.toSet()
    }

    override suspend fun bookClass(gymId: String, classId: String) {
        if (currentUID() == null) throw BackendException.NotAuthenticated
        try {
            functions.getHttpsCallable("bookClass").call(
                mapOf("gymId" to gymId, "classId" to classId)
            ).await()
        } catch (e: Exception) {
            throw parseFunctionsException(e)
        }
    }

    override suspend fun cancelBooking(gymId: String, classId: String) {
        val uid = currentUID() ?: throw BackendException.NotAuthenticated
        val existing = bookingsRef(gymId)
            .whereEqualTo("userId", uid)
            .whereEqualTo("classId", classId)
            .get().await()
        val bookingId = existing.documents.firstOrNull()?.id ?: throw BackendException.BookingNotFound
        try {
            functions.getHttpsCallable("cancelBooking").call(
                mapOf("gymId" to gymId, "classId" to classId, "bookingId" to bookingId)
            ).await()
        } catch (e: Exception) {
            throw parseFunctionsException(e)
        }
    }

    override suspend fun cancelBooking(gymId: String, classId: String, onBehalfOf: String) {
        val existing = bookingsRef(gymId)
            .whereEqualTo("userId", onBehalfOf)
            .whereEqualTo("classId", classId)
            .get().await()
        val bookingId = existing.documents.firstOrNull()?.id ?: throw BackendException.BookingNotFound
        try {
            functions.getHttpsCallable("cancelBooking").call(
                mapOf("gymId" to gymId, "classId" to classId, "bookingId" to bookingId, "onBehalfOfUserId" to onBehalfOf)
            ).await()
        } catch (e: Exception) {
            throw parseFunctionsException(e)
        }
    }

    override suspend fun fetchActivePlans(gymId: String, userId: String): List<ActivePlanItem> {
        val snapshot = activePlansRef(gymId, userId).get().await()
        return snapshot.documents.mapNotNull { parseActivePlanItem(it) }
    }

    override fun observeActivePlans(gymId: String, userId: String): Flow<List<ActivePlanItem>> = callbackFlow {
        val listener = activePlansRef(gymId, userId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val items = snapshot?.documents.orEmpty().mapNotNull { parseActivePlanItem(it) }
            trySend(items)
        }
        awaitClose { listener.remove() }
    }

    override suspend fun grantPlanToMember(gymId: String, userId: String, plan: MembershipPlan, customExpiresAtMillis: Long?) {
        val batch = firestore.batch()
        val now = System.currentTimeMillis()
        for (component in plan.components) {
            val expiresAt = customExpiresAtMillis ?: component.expiresAtMillis(now)
            val data = mutableMapOf<String, Any>(
                "planName" to plan.name,
                "type" to component.type.firestoreValue,
                "resetPeriod" to component.resetPeriod.firestoreValue,
                "creditCount" to component.creditCount,
                "remainingCredits" to (if (component.resetPeriod == PlanResetPeriod.NONE) (if (component.type == PlanComponentType.UNLIMITED) 0 else component.creditCount) else 0),
                "cycleCreditsUsed" to 0,
                "cycleAnchorDate" to Timestamp(Date(now)),
                "lastCycleIndex" to 0,
                "expiresAt" to Timestamp(Date(expiresAt))
            )
            component.workoutType?.let { data["workoutType"] = it }
            batch.set(activePlansRef(gymId, userId).document(), data)
        }
        batch.commit().await()
    }

    override suspend fun revokeActivePlan(gymId: String, userId: String, activePlanId: String) {
        activePlansRef(gymId, userId).document(activePlanId).delete().await()
    }

    override suspend fun fetchMemberBookings(gymId: String, userId: String): List<GymClass> {
        val bookingsSnapshot = bookingsRef(gymId).whereEqualTo("userId", userId).get().await()
        val classIds = bookingsSnapshot.documents.mapNotNull { it.getString("classId") }.toSet()
        if (classIds.isEmpty()) return emptyList()

        val classes = mutableListOf<GymClass>()
        classIds.chunked(30).forEach { chunk ->
            val snapshot = classesRef(gymId).whereIn(FieldPath.documentId(), chunk).get().await()
            classes.addAll(snapshot.documents.mapNotNull { parseClass(it) })
        }
        return classes
    }

    override suspend fun fetchAttendees(gymId: String, classId: String): List<Member> {
        val bookingsSnapshot = bookingsRef(gymId).whereEqualTo("classId", classId).get().await()
        // A user could in principle have more than one booking doc for the
        // same class (shouldn't happen given bookClass's idempotency guard,
        // but not enforced by a unique-document-per-(user,class) key) — last
        // one read wins, which is fine since they'd all describe the same
        // person's attendance.
        val attendanceByUser = bookingsSnapshot.documents.mapNotNull { doc ->
            val userId = doc.getString("userId") ?: return@mapNotNull null
            val checkedIn = doc.getBoolean("checkedIn") ?: false
            val checkedInAt = doc.getTimestamp("checkedInAt")?.toDate()?.time
            userId to (checkedIn to checkedInAt)
        }.toMap()
        val userIds = attendanceByUser.keys.toList()
        if (userIds.isEmpty()) return emptyList()

        val members = mutableListOf<Member>()
        // Firestore 'in' queries are limited to 30 values per call.
        userIds.chunked(30).forEach { chunk ->
            val usersSnapshot = firestore.collection("users")
                .whereIn(FieldPath.documentId(), chunk)
                .get().await()
            for (doc in usersSnapshot.documents) {
                val firstName = doc.getString("firstName").orEmpty()
                val lastName = doc.getString("lastName").orEmpty()
                val (checkedIn, checkedInAt) = attendanceByUser[doc.id] ?: (false to null)
                members.add(
                    Member(
                        id = doc.id,
                        fullName = "$firstName $lastName".trim(),
                        email = doc.getString("email").orEmpty(),
                        profilePicBase64 = doc.getString("profilePicBase64"),
                        isCheckedIn = checkedIn,
                        checkedInAtMillis = checkedInAt
                    )
                )
            }
        }
        return members
    }

    override suspend fun toggleAttendance(gymId: String, classId: String, userId: String, isCheckedIn: Boolean) {
        val bookingDoc = bookingsRef(gymId)
            .whereEqualTo("classId", classId)
            .whereEqualTo("userId", userId)
            .limit(1)
            .get().await()
            .documents
            .firstOrNull() ?: return // no booking to check in

        if (isCheckedIn) {
            bookingDoc.reference.update(mapOf("checkedIn" to true, "checkedInAt" to Timestamp.now())).await()
        } else {
            bookingDoc.reference.update(mapOf("checkedIn" to false, "checkedInAt" to FieldValue.delete())).await()
        }
    }

    override suspend fun updateProfilePicture(base64: String) {
        val uid = currentUID() ?: throw BackendException.NotAuthenticated
        firestore.collection("users").document(uid).update("profilePicBase64", base64).await()
    }

    override suspend fun fetchWorkoutLogs(gymId: String): List<WorkoutLog> {
        val uid = currentUID() ?: throw BackendException.NotAuthenticated
        val snapshot = workoutLogsRef(gymId, uid).get().await()
        return snapshot.documents.mapNotNull { doc ->
            val movement = doc.getString("movement") ?: return@mapNotNull null
            val dateMillis = doc.getTimestamp("date")?.toDate()?.time ?: return@mapNotNull null
            WorkoutLog(
                id = doc.id,
                movement = movement,
                score = doc.getDouble("score"),
                reps = doc.getLong("reps")?.toInt(),
                sets = doc.getLong("sets")?.toInt(),
                dateMillis = dateMillis
            )
        }
    }

    override suspend fun addWorkoutLog(gymId: String, log: WorkoutLog) {
        val uid = currentUID() ?: throw BackendException.NotAuthenticated
        // Full (non-merge) `set` — an edit that drops a previously-set
        // field (e.g. clearing Score) must actually remove it, matching
        // iOS's `addWorkoutLog`/`updateWorkoutLog` comment.
        val data = mutableMapOf<String, Any>(
            "movement" to log.movement,
            "date" to Timestamp(Date(log.dateMillis))
        )
        log.score?.let { data["score"] = it }
        log.reps?.let { data["reps"] = it }
        log.sets?.let { data["sets"] = it }
        workoutLogsRef(gymId, uid).document(log.id).set(data).await()
    }

    override suspend fun updateWorkoutLog(gymId: String, log: WorkoutLog) = addWorkoutLog(gymId, log)

    override suspend fun deleteWorkoutLog(gymId: String, logId: String) {
        val uid = currentUID() ?: throw BackendException.NotAuthenticated
        workoutLogsRef(gymId, uid).document(logId).delete().await()
    }

    override suspend fun fetchTeam(gymId: String): List<TeamMember> {
        val snapshot = teamRef(gymId).get().await()
        return snapshot.documents.mapNotNull { doc ->
            val roleStr = doc.getString("role") ?: return@mapNotNull null
            val firstName = doc.getString("firstName").orEmpty()
            val lastName = doc.getString("lastName").orEmpty()
            TeamMember(
                id = doc.id,
                fullName = "$firstName $lastName".trim(),
                email = doc.getString("email").orEmpty(),
                role = UserRole.fromFirestoreValue(roleStr)
            )
        }
    }

    override suspend fun addTeamMember(gymId: String, email: String, role: UserRole, name: String) {
        val existingUsers = firestore.collection("users").whereEqualTo("email", email).get().await()
        val userDoc = existingUsers.documents.firstOrNull() ?: throw BackendException.UserNotFound
        val uid = userDoc.id
        val firstName = userDoc.getString("firstName").orEmpty()
        val lastName = userDoc.getString("lastName").orEmpty()

        teamRef(gymId).document(uid).set(
            mapOf(
                "role" to role.firestoreValue,
                "firstName" to firstName,
                "lastName" to lastName,
                "email" to email,
                "addedAt" to Timestamp.now()
            )
        ).await()

        firestore.collection("users").document(uid).collection("memberships").document(gymId).set(
            mapOf("role" to role.firestoreValue, "joinedAt" to Timestamp.now())
        ).await()
    }

    override suspend fun registerTeamMember(gymId: String, firstName: String, lastName: String, email: String, password: String, role: UserRole) {
        val uid = createUserAccount(email, password, firstName, lastName)
        val joinedAt = Timestamp.now()

        teamRef(gymId).document(uid).set(
            mapOf(
                "role" to role.firestoreValue,
                "firstName" to firstName,
                "lastName" to lastName,
                "email" to email,
                "addedAt" to joinedAt
            )
        ).await()

        firestore.collection("users").document(uid).collection("memberships").document(gymId).set(
            mapOf("role" to role.firestoreValue, "joinedAt" to joinedAt)
        ).await()
    }

    override suspend fun updateTeamMemberRole(gymId: String, userId: String, role: UserRole) {
        firestore.collection("users").document(userId).collection("memberships").document(gymId)
            .update("role", role.firestoreValue).await()
        teamRef(gymId).document(userId).update("role", role.firestoreValue).await()
    }

    override suspend fun removeTeamMember(gymId: String, memberId: String) {
        teamRef(gymId).document(memberId).delete().await()
        firestore.collection("users").document(memberId).collection("memberships").document(gymId).delete().await()
    }

    override suspend fun fetchGymMembers(gymId: String): List<GymMember> {
        val snapshot = membersRef(gymId).get().await()
        return snapshot.documents.map { doc ->
            val firstName = doc.getString("firstName").orEmpty()
            val lastName = doc.getString("lastName").orEmpty()
            val activePlanName = firestore.collection("users").document(doc.id)
                .collection("memberships").document(gymId).collection("activePlans")
                .limit(1).get().await().documents.firstOrNull()?.getString("planName")

            GymMember(
                id = doc.id,
                fullName = "$firstName $lastName".trim(),
                email = doc.getString("email").orEmpty(),
                joinedAtMillis = doc.getTimestamp("joinedAt")?.toDate()?.time ?: 0L,
                activePlanName = activePlanName,
                profilePicBase64 = doc.getString("profilePicBase64")
            )
        }
    }

    override suspend fun addMember(gymId: String, email: String) {
        val existingUsers = firestore.collection("users").whereEqualTo("email", email).get().await()
        val userDoc = existingUsers.documents.firstOrNull() ?: throw BackendException.UserNotFound
        val uid = userDoc.id
        val firstName = userDoc.getString("firstName").orEmpty()
        val lastName = userDoc.getString("lastName").orEmpty()

        membersRef(gymId).document(uid).set(
            mapOf(
                "firstName" to firstName,
                "lastName" to lastName,
                "email" to email,
                "role" to UserRole.MEMBER.firestoreValue,
                "joinedAt" to Timestamp.now()
            )
        ).await()

        firestore.collection("users").document(uid).collection("memberships").document(gymId).set(
            mapOf("role" to UserRole.MEMBER.firestoreValue, "joinedAt" to Timestamp.now())
        ).await()
    }

    override suspend fun registerMember(gymId: String, firstName: String, lastName: String, email: String, password: String) {
        val uid = createUserAccount(email, password, firstName, lastName)
        val joinedAt = Timestamp.now()

        membersRef(gymId).document(uid).set(
            mapOf(
                "firstName" to firstName,
                "lastName" to lastName,
                "email" to email,
                "role" to UserRole.MEMBER.firestoreValue,
                "joinedAt" to joinedAt
            )
        ).await()

        firestore.collection("users").document(uid).collection("memberships").document(gymId).set(
            mapOf("role" to UserRole.MEMBER.firestoreValue, "joinedAt" to joinedAt)
        ).await()
    }

    /**
     * Creates a brand-new Auth account + `users/{uid}` profile via a lazily
     * created secondary `FirebaseApp`/`FirebaseAuth` instance, so the new
     * account doesn't sign in and replace the caller's own session — mirrors
     * iOS's `FirebaseBackend.createUserAccount`/`secondaryAuth()`. The
     * secondary Auth session is signed out immediately after, leaving no
     * trace beyond the new Firestore/Auth records.
     */
    private suspend fun createUserAccount(email: String, password: String, firstName: String, lastName: String): String {
        val result = secondaryAuth().createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw BackendException.NotAuthenticated

        firestore.collection("users").document(uid).set(
            mapOf("firstName" to firstName, "lastName" to lastName, "email" to email, "role" to PlatformRole.USER.firestoreValue)
        ).await()

        secondaryAuth().signOut()
        return uid
    }

    private fun secondaryAuth(): FirebaseAuth {
        val context = FirebaseApp.getInstance().applicationContext
        val secondaryApp = FirebaseApp.getApps(context).firstOrNull { it.name == SECONDARY_APP_NAME }
            ?: FirebaseApp.initializeApp(context, FirebaseApp.getInstance().options, SECONDARY_APP_NAME)
        return FirebaseAuth.getInstance(secondaryApp)
    }

    override suspend fun removeMember(gymId: String, userId: String) {
        val activePlansSnapshot = activePlansRef(gymId, userId).get().await()

        val batch = firestore.batch()
        activePlansSnapshot.documents.forEach { batch.delete(it.reference) }
        batch.delete(firestore.collection("users").document(userId).collection("memberships").document(gymId))
        batch.delete(membersRef(gymId).document(userId))
        batch.commit().await()
    }

    override suspend fun fetchMembershipPlans(gymId: String): List<MembershipPlan> {
        val snapshot = membershipPlansRef(gymId).get().await()
        return snapshot.documents.mapNotNull { parsePlan(it) }
    }

    override suspend fun createMembershipPlan(gymId: String, plan: MembershipPlan) {
        membershipPlansRef(gymId).document(plan.id).set(planData(plan)).await()
    }

    override suspend fun updateMembershipPlan(gymId: String, plan: MembershipPlan) = createMembershipPlan(gymId, plan)

    override suspend fun deleteMembershipPlan(gymId: String, planId: String) {
        membershipPlansRef(gymId).document(planId).delete().await()
    }

    override suspend fun updateGymSettings(gymId: String, name: String, workoutTypes: List<String>) {
        gymRef(gymId).update(mapOf("name" to name, "workoutTypes" to workoutTypes)).await()
    }

    override suspend fun createClass(gymId: String, gymClass: GymClass) {
        classesRef(gymId).document(gymClass.id).set(classData(gymClass, gymClass.currentAttendees)).await()
    }

    override suspend fun createClasses(gymId: String, classes: List<GymClass>) {
        val batch = firestore.batch()
        classes.forEach { gymClass ->
            batch.set(classesRef(gymId).document(gymClass.id), classData(gymClass, gymClass.currentAttendees))
        }
        batch.commit().await()
    }

    override suspend fun updateClass(gymId: String, gymClass: GymClass) {
        classesRef(gymId).document(gymClass.id).set(classData(gymClass, gymClass.currentAttendees)).await()
    }

    override suspend fun deleteClass(gymId: String, classId: String) {
        classesRef(gymId).document(classId).delete().await()
    }

    override suspend fun updateClassSeries(gymId: String, seriesId: String, fromDateMillis: Long, updatedTemplate: GymClass) {
        val snapshot = classesRef(gymId).whereEqualTo("seriesId", seriesId).get().await()
        val toUpdate = snapshot.documents.filter { doc ->
            val startTime = doc.getTimestamp("startTime")?.toDate()?.time ?: return@filter false
            startTime >= fromDateMillis
        }
        if (toUpdate.isEmpty()) return

        val batch = firestore.batch()
        for (doc in toUpdate) {
            val originalStart = doc.getTimestamp("startTime")?.toDate()?.time ?: continue
            val currentAttendees = doc.getLong("currentAttendees")?.toInt() ?: 0
            val waitlistCount = doc.getLong("waitlistCount")?.toInt() ?: 0
            val occurrence = updatedTemplate.copy(
                id = doc.id,
                startTimeMillis = applyTimeOfDay(originalStart, updatedTemplate.startTimeMillis),
                seriesId = seriesId,
                currentAttendees = currentAttendees,
                waitlistCount = waitlistCount
            )
            batch.set(doc.reference, classData(occurrence, currentAttendees))
        }
        batch.commit().await()
    }

    override suspend fun deleteClassSeries(gymId: String, seriesId: String, fromDateMillis: Long) {
        val snapshot = classesRef(gymId).whereEqualTo("seriesId", seriesId).get().await()
        val toDelete = snapshot.documents.filter { doc ->
            val startTime = doc.getTimestamp("startTime")?.toDate()?.time ?: return@filter false
            startTime >= fromDateMillis
        }
        if (toDelete.isEmpty()) return

        val batch = firestore.batch()
        toDelete.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    override suspend fun copySchedule(gymId: String, fromWeekOfMillis: Long, toWeekOfMillis: Long) {
        val sourceWeekStart = mondayStartMillis(fromWeekOfMillis)
        val sourceWeekEnd = sourceWeekStart + WEEK_MILLIS
        val targetWeekStart = mondayStartMillis(toWeekOfMillis)

        val snapshot = classesRef(gymId)
            .whereGreaterThanOrEqualTo("startTime", Timestamp(Date(sourceWeekStart)))
            .whereLessThan("startTime", Timestamp(Date(sourceWeekEnd)))
            .get().await()
        val sourceClasses = snapshot.documents.mapNotNull { parseClass(it) }
        if (sourceClasses.isEmpty()) return

        val batch = firestore.batch()
        for (sourceClass in sourceClasses) {
            val offset = sourceClass.startTimeMillis - sourceWeekStart
            val newClass = sourceClass.copy(
                id = UUID.randomUUID().toString(),
                startTimeMillis = targetWeekStart + offset,
                currentAttendees = 0,
                waitlistCount = 0,
                seriesId = null
            )
            batch.set(classesRef(gymId).document(newClass.id), classData(newClass, 0))
        }
        batch.commit().await()
    }

    override suspend fun joinWaitlist(gymId: String, classId: String) {
        if (currentUID() == null) throw BackendException.NotAuthenticated
        try {
            functions.getHttpsCallable("joinWaitlist").call(
                mapOf("gymId" to gymId, "classId" to classId)
            ).await()
        } catch (e: Exception) {
            throw parseFunctionsException(e)
        }
    }

    override suspend fun leaveWaitlist(gymId: String, classId: String) {
        if (currentUID() == null) throw BackendException.NotAuthenticated
        try {
            functions.getHttpsCallable("leaveWaitlist").call(
                mapOf("gymId" to gymId, "classId" to classId)
            ).await()
        } catch (e: Exception) {
            throw parseFunctionsException(e)
        }
    }

    private fun parseFunctionsException(e: Exception): Throwable {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("CLASS_FULL") || msg.contains("Class is full") -> BackendException.ClassFull
            msg.contains("INSUFFICIENT_CREDITS") || msg.contains("No available credits") -> BackendException.InsufficientCredits
            msg.contains("NO_ACTIVE_PLAN") || msg.contains("No active membership") -> BackendException.NoActiveMembership
            msg.contains("CLASS_IN_PAST") || msg.contains("already started") -> BackendException.ClassInPast
            msg.contains("CLASS_NOT_FOUND") || msg.contains("Class not found") -> BackendException.ClassNotFound
            msg.contains("BOOKING_NOT_FOUND") || msg.contains("Booking not found") -> BackendException.BookingNotFound
            else -> e
        }
    }

    override suspend fun fetchMyWaitlistedClassIds(gymId: String): Set<String> {
        val uid = currentUID() ?: return emptySet()
        val snapshot = waitlistRef(gymId).whereEqualTo("userId", uid).get().await()
        return snapshot.documents.mapNotNull { it.getString("classId") }.toSet()
    }

    override suspend fun fetchWaitlistPosition(gymId: String, classId: String): Int? {
        val uid = currentUID() ?: return null
        val snapshot = waitlistRef(gymId).whereEqualTo("classId", classId).get().await()
        val sorted = snapshot.documents.sortedBy { it.getTimestamp("joinedAt")?.toDate()?.time ?: Long.MAX_VALUE }
        val index = sorted.indexOfFirst { it.getString("userId") == uid }
        return if (index == -1) null else index + 1
    }

    /** Transactionally adjusts an Int counter field, clamped to a floor of 0 — mirrors iOS's `adjustClassCounter`. */
    private suspend fun adjustCounter(gymId: String, classId: String, field: String, delta: Int) {
        val ref = classesRef(gymId).document(classId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            val current = snapshot.getLong(field)?.toInt() ?: 0
            transaction.update(ref, field, (current + delta).coerceAtLeast(0))
            null
        }.await()
    }

    override suspend fun fetchAllUsers(): List<PlatformUser> {
        val snapshot = firestore.collection("users").get().await()
        return snapshot.documents.map { doc ->
            PlatformUser(
                id = doc.id,
                firstName = doc.getString("firstName").orEmpty(),
                lastName = doc.getString("lastName").orEmpty(),
                email = doc.getString("email").orEmpty(),
                role = PlatformRole.fromFirestoreValue(doc.getString("role").orEmpty()),
                profilePicBase64 = doc.getString("profilePicBase64")
            )
        }
    }

    override suspend fun updatePlatformRole(uid: String, role: PlatformRole) {
        firestore.collection("users").document(uid).update("role", role.firestoreValue).await()
    }

    /** Permanently deletes a gym and everything under it — mirrors iOS's `deleteGym`. No undo; the caller confirms with the user first. */
    override suspend fun deleteGym(gymId: String) {
        val users = fetchAllUsers()
        for (user in users) {
            val membershipRef = firestore.collection("users").document(user.id).collection("memberships").document(gymId)
            deleteAllDocuments(membershipRef.collection("activePlans"))
            membershipRef.delete().await()
        }

        for (subcollection in listOf("classes", "bookings", "waitlist", "team", "members", "membershipPlans")) {
            deleteAllDocuments(gymRef(gymId).collection(subcollection))
        }

        gymRef(gymId).delete().await()
    }

    /** Firestore batches are capped at 500 writes — chunks so this holds up for any collection size. */
    private suspend fun deleteAllDocuments(collection: CollectionReference) {
        val documents = collection.get().await().documents
        var index = 0
        while (index < documents.size) {
            val chunk = documents.subList(index, minOf(index + 400, documents.size))
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
            index += 400
        }
    }

    override suspend fun createGym(name: String, city: String?, workoutTypes: List<String>, ownerFirstName: String, ownerLastName: String, ownerEmail: String, ownerPassword: String): Gym {
        currentUID() ?: throw BackendException.NotAuthenticated

        val existingUsers = firestore.collection("users").whereEqualTo("email", ownerEmail).get().await()
        val existingDoc = existingUsers.documents.firstOrNull()

        val ownerUID: String
        val resolvedFirstName: String
        val resolvedLastName: String
        if (existingDoc != null) {
            ownerUID = existingDoc.id
            resolvedFirstName = existingDoc.getString("firstName") ?: ownerFirstName
            resolvedLastName = existingDoc.getString("lastName") ?: ownerLastName
        } else {
            ownerUID = createUserAccount(ownerEmail, ownerPassword, ownerFirstName, ownerLastName)
            resolvedFirstName = ownerFirstName
            resolvedLastName = ownerLastName
        }

        val resolvedWorkoutTypes = workoutTypes.ifEmpty { Gym.DEFAULT_WORKOUT_TYPES }
        val gymId = UUID.randomUUID().toString()
        val gymData = mutableMapOf<String, Any>(
            "name" to name,
            "ownerUID" to ownerUID,
            "workoutTypes" to resolvedWorkoutTypes,
            "createdAt" to Timestamp.now()
        )
        city?.let { gymData["city"] = it }
        gymRef(gymId).set(gymData).await()

        val joinedAt = Timestamp.now()
        firestore.collection("users").document(ownerUID).collection("memberships").document(gymId).set(
            mapOf("role" to UserRole.OWNER.firestoreValue, "joinedAt" to joinedAt)
        ).await()

        teamRef(gymId).document(ownerUID).set(
            mapOf(
                "role" to UserRole.OWNER.firestoreValue,
                "firstName" to resolvedFirstName,
                "lastName" to resolvedLastName,
                "email" to ownerEmail,
                "addedAt" to joinedAt
            )
        ).await()

        return Gym(id = gymId, name = name, ownerUID = ownerUID, workoutTypes = resolvedWorkoutTypes, city = city)
    }

    private fun parseGym(doc: DocumentSnapshot): Gym? {
        val name = doc.getString("name") ?: return null
        val ownerUID = doc.getString("ownerUID") ?: return null
        @Suppress("UNCHECKED_CAST")
        val workoutTypes = doc.get("workoutTypes") as? List<String> ?: Gym.DEFAULT_WORKOUT_TYPES
        return Gym(
            id = doc.id,
            name = name,
            ownerUID = ownerUID,
            workoutTypes = workoutTypes,
            city = doc.getString("city")
        )
    }

    // MARK: - Refs

    private fun gymRef(gymId: String) = firestore.collection("gyms").document(gymId)
    private fun classesRef(gymId: String) = gymRef(gymId).collection("classes")
    private fun bookingsRef(gymId: String) = gymRef(gymId).collection("bookings")
    private fun waitlistRef(gymId: String) = gymRef(gymId).collection("waitlist")
    private fun teamRef(gymId: String) = gymRef(gymId).collection("team")
    private fun membersRef(gymId: String) = gymRef(gymId).collection("members")
    private fun membershipPlansRef(gymId: String) = gymRef(gymId).collection("membershipPlans")
    private fun workoutLogsRef(gymId: String, uid: String) =
        membersRef(gymId).document(uid).collection("workoutLogs")
    private fun activePlansRef(gymId: String, userId: String) =
        firestore.collection("users").document(userId).collection("memberships").document(gymId).collection("activePlans")

    private fun classData(gymClass: GymClass, currentAttendees: Int): Map<String, Any> {
        val data = mutableMapOf<String, Any>(
            "title" to gymClass.title,
            "coach" to gymClass.coach,
            "startTime" to Timestamp(Date(gymClass.startTimeMillis)),
            "durationMinutes" to gymClass.durationMinutes,
            "capacity" to gymClass.capacity,
            "currentAttendees" to currentAttendees,
            "waitlistCount" to gymClass.waitlistCount,
            "classType" to gymClass.classType,
            "isPremium" to gymClass.isPremium,
            "description" to gymClass.description
        )
        gymClass.seriesId?.let { data["seriesId"] = it }
        return data
    }

    private fun parsePlan(doc: DocumentSnapshot): MembershipPlan? {
        val name = doc.getString("name") ?: return null
        val type = PlanType.fromFirestoreValue(doc.getString("type") ?: PlanType.MONTHLY.firestoreValue)
        val price = doc.getDouble("price") ?: 0.0
        @Suppress("UNCHECKED_CAST")
        val rawComponents = doc.get("components") as? List<Map<String, Any>> ?: emptyList()
        val components = rawComponents.mapNotNull { dict ->
            val typeStr = dict["type"] as? String ?: return@mapNotNull null
            PlanComponent(
                id = dict["id"] as? String ?: java.util.UUID.randomUUID().toString(),
                type = PlanComponentType.fromFirestoreValue(typeStr),
                resetPeriod = PlanResetPeriod.fromFirestoreValue(dict["resetPeriod"] as? String ?: PlanResetPeriod.NONE.firestoreValue),
                workoutType = dict["workoutType"] as? String,
                creditCount = (dict["creditCount"] as? Long)?.toInt() ?: 0,
                validityValue = (dict["validityValue"] as? Long)?.toInt() ?: 1,
                validityUnit = (dict["validityUnit"] as? String)?.let { ValidityUnit.fromFirestoreValue(it) } ?: ValidityUnit.MONTHS
            )
        }

        return MembershipPlan(id = doc.id, name = name, type = type, price = price, components = components)
    }

    private fun planData(plan: MembershipPlan): Map<String, Any> {
        val componentsData = plan.components.map { component ->
            val data = mutableMapOf<String, Any>(
                "id" to component.id,
                "type" to component.type.firestoreValue,
                "resetPeriod" to component.resetPeriod.firestoreValue,
                "creditCount" to component.creditCount,
                "validityValue" to component.validityValue,
                "validityUnit" to component.validityUnit.firestoreValue
            )
            component.workoutType?.let { data["workoutType"] = it }
            data
        }
        return mapOf(
            "name" to plan.name,
            "type" to plan.type.firestoreValue,
            "price" to plan.price,
            "components" to componentsData
        )
    }

    private fun parseActivePlanItem(doc: DocumentSnapshot): ActivePlanItem? {
        val planName = doc.getString("planName") ?: return null
        val type = doc.getString("type")?.let { PlanComponentType.fromFirestoreValue(it) } ?: return null
        val expiresAtMillis = doc.getTimestamp("expiresAt")?.toDate()?.time ?: return null
        return ActivePlanItem(
            id = doc.id,
            planName = planName,
            type = type,
            resetPeriod = PlanResetPeriod.fromFirestoreValue(doc.getString("resetPeriod") ?: PlanResetPeriod.NONE.firestoreValue),
            workoutType = doc.getString("workoutType"),
            creditCount = doc.getLong("creditCount")?.toInt() ?: 0,
            remainingCredits = doc.getLong("remainingCredits")?.toInt() ?: 0,
            cycleCreditsUsed = doc.getLong("cycleCreditsUsed")?.toInt() ?: 0,
            cycleAnchorDateMillis = doc.getTimestamp("cycleAnchorDate")?.toDate()?.time ?: expiresAtMillis,
            lastCycleIndex = doc.getLong("lastCycleIndex")?.toInt() ?: 0,
            expiresAtMillis = expiresAtMillis
        )
    }

    private fun parseClass(doc: DocumentSnapshot): GymClass? {
        val title = doc.getString("title") ?: return null
        val startTimeMillis = doc.getTimestamp("startTime")?.toDate()?.time ?: return null
        return GymClass(
            id = doc.id,
            title = title,
            coach = doc.getString("coach").orEmpty(),
            startTimeMillis = startTimeMillis,
            capacity = doc.getLong("capacity")?.toInt() ?: 0,
            currentAttendees = doc.getLong("currentAttendees")?.toInt() ?: 0,
            waitlistCount = doc.getLong("waitlistCount")?.toInt() ?: 0,
            durationMinutes = doc.getLong("durationMinutes")?.toInt() ?: 60,
            description = doc.getString("description").orEmpty(),
            classType = doc.getString("classType") ?: "CrossFit WOD",
            isPremium = doc.getBoolean("isPremium") ?: false,
            seriesId = doc.getString("seriesId")
        )
    }
}
