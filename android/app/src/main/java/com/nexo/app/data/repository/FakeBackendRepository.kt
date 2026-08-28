package com.nexo.app.data.repository

import com.nexo.app.domain.model.ActivePlanItem
import com.nexo.app.domain.model.Gym
import com.nexo.app.domain.model.GymClass
import com.nexo.app.domain.model.GymMember
import com.nexo.app.domain.model.Member
import com.nexo.app.domain.model.MembershipPlan
import com.nexo.app.domain.model.PlanComponentType
import com.nexo.app.domain.model.PlanResetPeriod
import com.nexo.app.domain.model.PlatformRole
import com.nexo.app.domain.model.PlatformUser
import com.nexo.app.domain.model.TeamMember
import com.nexo.app.domain.model.UserRole
import com.nexo.app.domain.model.WorkoutLog
import com.nexo.app.domain.model.applyTimeOfDay
import com.nexo.app.domain.model.generateJoinCode
import com.nexo.app.domain.model.mondayStartMillis
import com.nexo.app.domain.model.sanitizeJoinCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * In-memory [BackendRepository] — mirrors `MockBackendService` on iOS.
 * State is keyed per-gym the same way Firestore is, so it can back both
 * unit tests and offline Compose previews with no network dependency.
 */
class FakeBackendRepository : BackendRepository {
    private companion object {
        const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
    /** Test/preview hook — set before calling anything that reads [currentUID]. */
    var signedInUID: String? = null

    /** Test hook — when set, every suspend function throws this instead of doing its normal work. */
    var errorToThrow: Exception? = null

    private val gyms = mutableMapOf<String, Gym>()
    private val memberships = mutableMapOf<String, MutableMap<String, UserRole>>() // gymId -> (userId -> role)
    private val classes = mutableMapOf<String, MutableMap<String, GymClass>>() // gymId -> (classId -> class)
    private val bookings = mutableMapOf<String, MutableSet<Pair<String, String>>>() // gymId -> {(userId, classId)}
    private val attendance = mutableMapOf<String, MutableMap<Pair<String, String>, Pair<Boolean, Long?>>>() // gymId -> ((userId, classId) -> (isCheckedIn, checkedInAtMillis))
    private val workoutLogs = mutableMapOf<String, MutableMap<String, MutableMap<String, WorkoutLog>>>() // gymId -> (userId -> (logId -> log))
    private val profiles = mutableMapOf<String, Member>() // userId -> profile
    private val platformRoles = mutableMapOf<String, PlatformRole>() // userId -> role
    private val team = mutableMapOf<String, MutableMap<String, TeamMember>>() // gymId -> (uid -> TeamMember)
    private val gymMembers = mutableMapOf<String, MutableMap<String, GymMember>>() // gymId -> (uid -> GymMember)
    private val membershipPlans = mutableMapOf<String, MutableMap<String, MembershipPlan>>() // gymId -> (planId -> plan)
    private val waitlist = mutableMapOf<String, MutableList<Pair<String, String>>>() // gymId -> ordered [(userId, classId)], insertion order = join order
    /** Distinct from [platformRoles] (used by [fetchPlatformRole]/the admin branch of [fetchMyGyms]) — kept as its own store so existing [seedPlatformRole] call sites don't need to change; [updatePlatformRole] keeps both in sync going forward. */
    private val platformUsers = mutableMapOf<String, PlatformUser>() // uid -> full platform user record, for the Users tab
    private val gymJoinCodes = mutableMapOf<String, String>() // uppercase join code -> gymId
    private val activePlans = mutableMapOf<Pair<String, String>, MutableMap<String, ActivePlanItem>>() // (gymId, userId) -> (activePlanId -> item)
    private val bookingActivePlanIds = mutableMapOf<String, MutableMap<Pair<String, String>, String>>() // gymId -> ((userId, classId) -> activePlanId consumed to authorize it)
    private val classFlows = mutableMapOf<String, MutableStateFlow<List<GymClass>>>() // gymId -> live view backing observeClasses

    /** Pushes [gymId]'s current class list to any active [observeClasses] collector — called after every mutation to [classes]. A no-op if no one is observing that gym yet (the flow is lazily seeded with current state on first [observeClasses] call). */
    private fun notifyClassesChanged(gymId: String) {
        classFlows[gymId]?.value = classes[gymId]?.values?.toList() ?: emptyList()
    }

    // MARK: - Test/preview seeding

    fun seedGym(gym: Gym, role: UserRole, userId: String) {
        gyms[gym.id] = gym
        memberships.getOrPut(gym.id) { mutableMapOf() }[userId] = role
    }

    fun seedPlatformRole(userId: String, role: PlatformRole) {
        platformRoles[userId] = role
    }

    fun seedTeamMember(gymId: String, member: TeamMember) {
        team.getOrPut(gymId) { mutableMapOf() }[member.id] = member
    }

    fun seedGymMember(gymId: String, member: GymMember) {
        gymMembers.getOrPut(gymId) { mutableMapOf() }[member.id] = member
    }

    fun seedMembershipPlan(gymId: String, plan: MembershipPlan) {
        membershipPlans.getOrPut(gymId) { mutableMapOf() }[plan.id] = plan
    }

    fun seedPlatformUser(user: PlatformUser) {
        platformUsers[user.id] = user
    }

    fun seedClass(gymId: String, gymClass: GymClass) {
        classes.getOrPut(gymId) { mutableMapOf() }[gymClass.id] = gymClass
        notifyClassesChanged(gymId)
    }

    fun seedProfile(userId: String, member: Member) {
        profiles[userId] = member
    }

    /** Seeds a booking directly, bypassing `bookClass`'s capacity check — for setting up pre-existing state (e.g. a past booking) rather than exercising the booking action itself. */
    fun seedBooking(gymId: String, classId: String, userId: String) {
        bookings.getOrPut(gymId) { mutableSetOf() }.add(userId to classId)
    }

    /** Adds one item to [userId]'s credit wallet for [gymId] — for setting up pre-existing plan state rather than exercising `grantPlanToMember` (not yet ported to Android). */
    fun seedActivePlan(gymId: String, userId: String, item: ActivePlanItem) {
        activePlans.getOrPut(gymId to userId) { mutableMapOf() }[item.id] = item
    }

    /** Synchronous counterpart to [addWorkoutLog], for seeding demo/preview data without a coroutine scope. */
    fun seedWorkoutLog(gymId: String, userId: String, log: WorkoutLog) {
        workoutLogs.getOrPut(gymId) { mutableMapOf() }.getOrPut(userId) { mutableMapOf() }[log.id] = log
    }

    override fun currentUID(): String? = signedInUID

    override suspend fun signIn(email: String, password: String) {
        errorToThrow?.let { throw it }
        val uid = profiles.entries.firstOrNull { it.value.email == email }?.key
            ?: throw BackendException.NotAuthenticated
        signedInUID = uid
    }

    override suspend fun signUp(email: String, password: String, firstName: String, lastName: String) {
        errorToThrow?.let { throw it }
        val uid = "fake-${UUID.randomUUID()}"
        profiles[uid] = Member(id = uid, fullName = "$firstName $lastName".trim(), email = email)
        platformUsers[uid] = PlatformUser(id = uid, firstName = firstName, lastName = lastName, email = email)
        signedInUID = uid
    }

    override fun signOut() {
        signedInUID = null
    }

    override suspend fun sendPasswordReset(email: String) {
        errorToThrow?.let { throw it }
        if (profiles.values.none { it.email == email }) throw BackendException.UserNotFound
    }

    override suspend fun fetchMyProfile(): Member? {
        errorToThrow?.let { throw it }
        val uid = signedInUID ?: return null
        return profiles[uid]
    }

    override suspend fun fetchPlatformRole(): PlatformRole {
        errorToThrow?.let { throw it }
        val uid = signedInUID ?: return PlatformRole.USER
        return platformRoles[uid] ?: PlatformRole.USER
    }

    override suspend fun fetchAvailableGyms(): List<Gym> {
        errorToThrow?.let { throw it }
        return gyms.values.toList()
    }

    override suspend fun fetchMyGyms(): List<Pair<Gym, UserRole>> {
        errorToThrow?.let { throw it }
        val uid = signedInUID ?: return emptyList()

        if (fetchPlatformRole() == PlatformRole.ADMIN) {
            return fetchAvailableGyms().map { it to UserRole.OWNER }
        }

        val seenGymIds = mutableSetOf<String>()
        val results = mutableListOf<Pair<Gym, UserRole>>()

        memberships.forEach { (gymId, roles) ->
            val role = roles[uid]
            val gym = gyms[gymId]
            if (role != null && gym != null) {
                seenGymIds.add(gymId)
                results.add(gym to role)
            }
        }

        // Also include gyms where user is the owner
        gyms.values.filter { it.ownerUID == uid && it.id !in seenGymIds }.forEach { gym ->
            results.add(gym to UserRole.OWNER)
        }

        return results
    }

    override suspend fun fetchClasses(gymId: String): List<GymClass> {
        errorToThrow?.let { throw it }
        return classes[gymId]?.values?.toList() ?: emptyList()
    }

    override fun observeClasses(gymId: String): Flow<List<GymClass>> =
        classFlows.getOrPut(gymId) { MutableStateFlow(classes[gymId]?.values?.toList() ?: emptyList()) }.asStateFlow()

    override suspend fun fetchMyBookedClassIds(gymId: String): Set<String> {
        errorToThrow?.let { throw it }
        val uid = signedInUID ?: return emptySet()
        return (bookings[gymId] ?: emptySet()).filter { it.first == uid }.map { it.second }.toSet()
    }

    override suspend fun bookClass(gymId: String, classId: String) {
        errorToThrow?.let { throw it }
        val uid = signedInUID ?: throw BackendException.NotAuthenticated
        val gymClass = classes[gymId]?.get(classId) ?: throw BackendException.ClassNotFound

        val classBookings = bookings.getOrPut(gymId) { mutableSetOf() }
        // Already booked — no-op, matches `performBooking`'s idempotency on iOS.
        if (classBookings.contains(uid to classId)) return

        // Mirrors FirebaseBackendRepository's ordering: the wallet is
        // validated/consumed before the capacity check, since on Firebase
        // that check runs inside the atomic transaction that follows.
        val consumedActivePlanId = validateAndConsumeMembership(gymId, uid, gymClass)

        if (gymClass.currentAttendees >= gymClass.capacity) throw BackendException.ClassFull

        classes[gymId]?.set(classId, gymClass.copy(currentAttendees = gymClass.currentAttendees + 1))
        notifyClassesChanged(gymId)
        classBookings.add(uid to classId)
        consumedActivePlanId?.let {
            bookingActivePlanIds.getOrPut(gymId) { mutableMapOf() }[uid to classId] = it
        }
    }

    override suspend fun cancelBooking(gymId: String, classId: String) {
        errorToThrow?.let { throw it }
        val uid = signedInUID ?: throw BackendException.NotAuthenticated
        removeBooking(gymId, classId, uid)
    }

    override suspend fun cancelBooking(gymId: String, classId: String, onBehalfOf: String) {
        errorToThrow?.let { throw it }
        removeBooking(gymId, classId, onBehalfOf)
    }

    private fun removeBooking(gymId: String, classId: String, uid: String) {
        val classBookings = bookings[gymId] ?: return
        // No matching booking — no-op, matches the double-cancel guard on iOS
        // (`performCancelBooking`) so a duplicate cancel can't double-decrement.
        if (!classBookings.remove(uid to classId)) return

        val consumedActivePlanId = bookingActivePlanIds[gymId]?.remove(uid to classId)
        if (consumedActivePlanId != null) {
            val item = activePlans[gymId to uid]?.get(consumedActivePlanId)
            if (item != null && item.type == PlanComponentType.CREDITS) {
                val updated = if (item.resetPeriod == PlanResetPeriod.MONTHLY) {
                    if (item.cycleCreditsUsed > 0) item.copy(cycleCreditsUsed = item.cycleCreditsUsed - 1) else item
                } else {
                    item.copy(remainingCredits = item.remainingCredits + 1)
                }
                activePlans[gymId to uid]?.set(consumedActivePlanId, updated)
            }
        }

        val gymClass = classes[gymId]?.get(classId) ?: return
        val waitlistForGym = waitlist.getOrPut(gymId) { mutableListOf() }
        val firstWaiting = waitlistForGym.firstOrNull { it.second == classId }

        if (firstWaiting != null) {
            // Promote the first waiting user into the freed spot — attendance
            // stays full, only waitlistCount drops. Matches WaitlistTests.swift.
            // No wallet check on promotion, same as iOS's `removeBooking` —
            // the promoted user already committed to the class by waitlisting.
            waitlistForGym.remove(firstWaiting)
            classBookings.add(firstWaiting)
            classes[gymId]?.set(classId, gymClass.copy(waitlistCount = (gymClass.waitlistCount - 1).coerceAtLeast(0)))
        } else {
            classes[gymId]?.set(classId, gymClass.copy(currentAttendees = (gymClass.currentAttendees - 1).coerceAtLeast(0)))
        }
        notifyClassesChanged(gymId)
    }

    override suspend fun fetchActivePlans(gymId: String, userId: String): List<ActivePlanItem> {
        errorToThrow?.let { throw it }
        return activePlans[gymId to userId]?.values?.toList() ?: emptyList()
    }

    override suspend fun grantPlanToMember(gymId: String, userId: String, plan: MembershipPlan, customExpiresAtMillis: Long?) {
        errorToThrow?.let { throw it }
        val now = System.currentTimeMillis()
        for (component in plan.components) {
            val item = ActivePlanItem(
                id = UUID.randomUUID().toString(),
                planName = plan.name,
                type = component.type,
                resetPeriod = component.resetPeriod,
                workoutType = component.workoutType,
                creditCount = component.creditCount,
                remainingCredits = if (component.resetPeriod == PlanResetPeriod.NONE) (if (component.type == PlanComponentType.UNLIMITED) 0 else component.creditCount) else 0,
                cycleCreditsUsed = 0,
                cycleAnchorDateMillis = now,
                lastCycleIndex = 0,
                expiresAtMillis = customExpiresAtMillis ?: component.expiresAtMillis(now)
            )
            activePlans.getOrPut(gymId to userId) { mutableMapOf() }[item.id] = item
        }
    }

    override suspend fun revokeActivePlan(gymId: String, userId: String, activePlanId: String) {
        errorToThrow?.let { throw it }
        activePlans[gymId to userId]?.remove(activePlanId)
    }

    override suspend fun fetchMemberBookings(gymId: String, userId: String): List<GymClass> {
        errorToThrow?.let { throw it }
        val classIds = (bookings[gymId] ?: emptySet()).filter { it.first == userId }.map { it.second }.toSet()
        return classes[gymId]?.values?.filter { it.id in classIds } ?: emptyList()
    }

    /**
     * Enforces the member's plan wallet before a booking is created.
     * Returns the consumed [ActivePlanItem] id (for [cancelBooking] to
     * refund), or `null` if an unlimited item authorized it or the caller
     * is staff. Mirrors `FirebaseBackendRepository.validateAndConsumeMembership`.
     */
    private fun validateAndConsumeMembership(gymId: String, userId: String, gymClass: GymClass): String? {
        val wallet = activePlans[gymId to userId]?.values.orEmpty()
        val matching = wallet.filter { it.matches(gymClass) }

        if (matching.isNotEmpty()) {
            if (matching.any { it.type == PlanComponentType.UNLIMITED }) return null

            val chosen = matching.filter { it.type == PlanComponentType.CREDITS && it.availableCredits() > 0 }
                .minByOrNull { it.expiresAtMillis } ?: throw BackendException.InsufficientCredits

            val updated = if (chosen.resetPeriod == PlanResetPeriod.MONTHLY) {
                val currentIndex = chosen.currentCycleIndex()
                if (currentIndex != chosen.lastCycleIndex) {
                    chosen.copy(cycleCreditsUsed = 1, lastCycleIndex = currentIndex)
                } else {
                    chosen.copy(cycleCreditsUsed = chosen.cycleCreditsUsed + 1)
                }
            } else {
                chosen.copy(remainingCredits = chosen.remainingCredits - 1)
            }
            activePlans[gymId to userId]?.set(chosen.id, updated)
            return chosen.id
        }

        if ((platformRoles[userId] ?: PlatformRole.USER) == PlatformRole.ADMIN) return null
        val role = memberships[gymId]?.get(userId) ?: UserRole.MEMBER
        if (role.canManageClasses) return null

        throw BackendException.NoActiveMembership
    }

    override suspend fun fetchAttendees(gymId: String, classId: String): List<Member> {
        errorToThrow?.let { throw it }
        val userIds = (bookings[gymId] ?: emptySet()).filter { it.second == classId }.map { it.first }
        return userIds.mapNotNull { uid ->
            val profile = profiles[uid] ?: return@mapNotNull null
            val (checkedIn, checkedInAt) = attendance[gymId]?.get(uid to classId) ?: (false to null)
            profile.copy(isCheckedIn = checkedIn, checkedInAtMillis = checkedInAt)
        }
    }

    override suspend fun toggleAttendance(gymId: String, classId: String, userId: String, isCheckedIn: Boolean) {
        errorToThrow?.let { throw it }
        if (userId to classId !in (bookings[gymId] ?: emptySet())) return // no booking to check in
        attendance.getOrPut(gymId) { mutableMapOf() }[userId to classId] = isCheckedIn to (if (isCheckedIn) System.currentTimeMillis() else null)
    }

    override suspend fun updateProfilePicture(base64: String) {
        errorToThrow?.let { throw it }
        val uid = signedInUID ?: throw BackendException.NotAuthenticated
        profiles[uid]?.let { profiles[uid] = it.copy(profilePicBase64 = base64) }
        platformUsers[uid]?.let { platformUsers[uid] = it.copy(profilePicBase64 = base64) }
    }

    override suspend fun fetchWorkoutLogs(gymId: String): List<WorkoutLog> {
        errorToThrow?.let { throw it }
        val uid = signedInUID ?: throw BackendException.NotAuthenticated
        return workoutLogs[gymId]?.get(uid)?.values?.toList() ?: emptyList()
    }

    override suspend fun addWorkoutLog(gymId: String, log: WorkoutLog) {
        errorToThrow?.let { throw it }
        val uid = signedInUID ?: throw BackendException.NotAuthenticated
        workoutLogs.getOrPut(gymId) { mutableMapOf() }.getOrPut(uid) { mutableMapOf() }[log.id] = log
    }

    override suspend fun updateWorkoutLog(gymId: String, log: WorkoutLog) {
        // `setData`-style upsert — same as `FirebaseBackend.updateWorkoutLog`
        // delegating straight to `addWorkoutLog` on iOS.
        addWorkoutLog(gymId, log)
    }

    override suspend fun deleteWorkoutLog(gymId: String, logId: String) {
        errorToThrow?.let { throw it }
        val uid = signedInUID ?: throw BackendException.NotAuthenticated
        workoutLogs[gymId]?.get(uid)?.remove(logId)
    }

    override suspend fun fetchTeam(gymId: String): List<TeamMember> {
        errorToThrow?.let { throw it }
        return team[gymId]?.values?.toList() ?: emptyList()
    }

    override suspend fun addTeamMember(gymId: String, email: String, role: UserRole, name: String) {
        errorToThrow?.let { throw it }
        val existingUid = profiles.entries.firstOrNull { it.value.email == email }?.key
            ?: throw BackendException.UserNotFound
        val member = TeamMember(id = existingUid, fullName = name.ifBlank { profiles.getValue(existingUid).fullName }, email = email, role = role)
        team.getOrPut(gymId) { mutableMapOf() }[existingUid] = member
        memberships.getOrPut(gymId) { mutableMapOf() }[existingUid] = role
    }

    override suspend fun registerTeamMember(gymId: String, firstName: String, lastName: String, email: String, password: String, role: UserRole) {
        errorToThrow?.let { throw it }
        val uid = registerNewAccount(firstName, lastName, email)
        team.getOrPut(gymId) { mutableMapOf() }[uid] = TeamMember(id = uid, fullName = "$firstName $lastName".trim(), email = email, role = role)
        memberships.getOrPut(gymId) { mutableMapOf() }[uid] = role
    }

    override suspend fun updateTeamMemberRole(gymId: String, userId: String, role: UserRole) {
        errorToThrow?.let { throw it }
        team[gymId]?.get(userId)?.let { team.getValue(gymId)[userId] = it.copy(role = role) }
        memberships[gymId]?.set(userId, role)
    }

    override suspend fun removeTeamMember(gymId: String, memberId: String) {
        errorToThrow?.let { throw it }
        team[gymId]?.remove(memberId)
        memberships[gymId]?.remove(memberId)
    }

    override suspend fun fetchGymMembers(gymId: String): List<GymMember> {
        errorToThrow?.let { throw it }
        return gymMembers[gymId]?.values?.toList() ?: emptyList()
    }

    override suspend fun addMember(gymId: String, email: String) {
        errorToThrow?.let { throw it }
        val existingUid = profiles.entries.firstOrNull { it.value.email == email }?.key
            ?: throw BackendException.UserNotFound
        val profile = profiles.getValue(existingUid)
        memberships.getOrPut(gymId) { mutableMapOf() }[existingUid] = UserRole.MEMBER
        gymMembers.getOrPut(gymId) { mutableMapOf() }[existingUid] = GymMember(id = existingUid, fullName = profile.fullName, email = profile.email)
    }

    override suspend fun registerMember(gymId: String, firstName: String, lastName: String, email: String, password: String) {
        errorToThrow?.let { throw it }
        val uid = registerNewAccount(firstName, lastName, email)
        memberships.getOrPut(gymId) { mutableMapOf() }[uid] = UserRole.MEMBER
        gymMembers.getOrPut(gymId) { mutableMapOf() }[uid] = GymMember(id = uid, fullName = "$firstName $lastName".trim(), email = email)
    }

    /** Creates a new fake profile without changing [signedInUID] — mirrors the "doesn't sign out the caller" property of the secondary-`FirebaseApp` account creation on [FirebaseBackendRepository]. */
    private fun registerNewAccount(firstName: String, lastName: String, email: String): String {
        val uid = "fake-${UUID.randomUUID()}"
        val fullName = "$firstName $lastName".trim()
        profiles[uid] = Member(id = uid, fullName = fullName, email = email)
        platformUsers[uid] = PlatformUser(id = uid, firstName = firstName, lastName = lastName, email = email)
        return uid
    }

    override suspend fun removeMember(gymId: String, userId: String) {
        errorToThrow?.let { throw it }
        gymMembers[gymId]?.remove(userId)
        memberships[gymId]?.remove(userId)
        activePlans.remove(gymId to userId)
    }

    override suspend fun fetchMembershipPlans(gymId: String): List<MembershipPlan> {
        errorToThrow?.let { throw it }
        return membershipPlans[gymId]?.values?.toList() ?: emptyList()
    }

    override suspend fun createMembershipPlan(gymId: String, plan: MembershipPlan) {
        errorToThrow?.let { throw it }
        membershipPlans.getOrPut(gymId) { mutableMapOf() }[plan.id] = plan
    }

    override suspend fun updateMembershipPlan(gymId: String, plan: MembershipPlan) = createMembershipPlan(gymId, plan)

    override suspend fun deleteMembershipPlan(gymId: String, planId: String) {
        errorToThrow?.let { throw it }
        membershipPlans[gymId]?.remove(planId)
    }

    override suspend fun updateGymSettings(gymId: String, name: String, workoutTypes: List<String>) {
        errorToThrow?.let { throw it }
        val gym = gyms[gymId] ?: return
        gyms[gymId] = gym.copy(name = name, workoutTypes = workoutTypes)
    }

    override suspend fun createClass(gymId: String, gymClass: GymClass) {
        errorToThrow?.let { throw it }
        classes.getOrPut(gymId) { mutableMapOf() }[gymClass.id] = gymClass
        notifyClassesChanged(gymId)
    }

    override suspend fun createClasses(gymId: String, classes: List<GymClass>) {
        errorToThrow?.let { throw it }
        val store = this.classes.getOrPut(gymId) { mutableMapOf() }
        classes.forEach { store[it.id] = it }
        notifyClassesChanged(gymId)
    }

    override suspend fun updateClass(gymId: String, gymClass: GymClass) {
        errorToThrow?.let { throw it }
        classes.getOrPut(gymId) { mutableMapOf() }[gymClass.id] = gymClass
        notifyClassesChanged(gymId)
    }

    override suspend fun deleteClass(gymId: String, classId: String) {
        errorToThrow?.let { throw it }
        classes[gymId]?.remove(classId)
        notifyClassesChanged(gymId)
    }

    override suspend fun updateClassSeries(gymId: String, seriesId: String, fromDateMillis: Long, updatedTemplate: GymClass) {
        errorToThrow?.let { throw it }
        val store = classes[gymId] ?: return
        val toUpdate = store.values.filter { it.seriesId == seriesId && it.startTimeMillis >= fromDateMillis }
        for (occurrence in toUpdate) {
            store[occurrence.id] = updatedTemplate.copy(
                id = occurrence.id,
                startTimeMillis = applyTimeOfDay(occurrence.startTimeMillis, updatedTemplate.startTimeMillis),
                seriesId = seriesId,
                currentAttendees = occurrence.currentAttendees,
                waitlistCount = occurrence.waitlistCount
            )
        }
        notifyClassesChanged(gymId)
    }

    override suspend fun deleteClassSeries(gymId: String, seriesId: String, fromDateMillis: Long) {
        errorToThrow?.let { throw it }
        val store = classes[gymId] ?: return
        val toDelete = store.values.filter { it.seriesId == seriesId && it.startTimeMillis >= fromDateMillis }.map { it.id }
        toDelete.forEach { store.remove(it) }
        notifyClassesChanged(gymId)
    }

    override suspend fun copySchedule(gymId: String, fromWeekOfMillis: Long, toWeekOfMillis: Long) {
        errorToThrow?.let { throw it }
        val sourceWeekStart = mondayStartMillis(fromWeekOfMillis)
        val sourceWeekEnd = sourceWeekStart + WEEK_MILLIS
        val targetWeekStart = mondayStartMillis(toWeekOfMillis)

        val sourceClasses = classes[gymId]?.values
            ?.filter { it.startTimeMillis >= sourceWeekStart && it.startTimeMillis < sourceWeekEnd }
            ?: emptyList()
        if (sourceClasses.isEmpty()) return

        val store = classes.getOrPut(gymId) { mutableMapOf() }
        for (sourceClass in sourceClasses) {
            val offset = sourceClass.startTimeMillis - sourceWeekStart
            val newClass = sourceClass.copy(
                id = UUID.randomUUID().toString(),
                startTimeMillis = targetWeekStart + offset,
                currentAttendees = 0,
                waitlistCount = 0,
                seriesId = null
            )
            store[newClass.id] = newClass
        }
        notifyClassesChanged(gymId)
    }

    override suspend fun joinWaitlist(gymId: String, classId: String) {
        errorToThrow?.let { throw it }
        val uid = signedInUID ?: throw BackendException.NotAuthenticated
        val gymClass = classes[gymId]?.get(classId) ?: throw BackendException.ClassNotFound
        if (gymClass.startTimeMillis < System.currentTimeMillis()) throw BackendException.ClassInPast

        val list = waitlist.getOrPut(gymId) { mutableListOf() }
        if (list.any { it.first == uid && it.second == classId }) return // already waitlisted — idempotent

        list.add(uid to classId)
        classes[gymId]?.set(classId, gymClass.copy(waitlistCount = gymClass.waitlistCount + 1))
        notifyClassesChanged(gymId)
    }

    override suspend fun leaveWaitlist(gymId: String, classId: String) {
        errorToThrow?.let { throw it }
        val uid = signedInUID ?: throw BackendException.NotAuthenticated
        val list = waitlist[gymId] ?: return
        if (!list.remove(uid to classId)) return

        val gymClass = classes[gymId]?.get(classId) ?: return
        classes[gymId]?.set(classId, gymClass.copy(waitlistCount = (gymClass.waitlistCount - 1).coerceAtLeast(0)))
        notifyClassesChanged(gymId)
    }

    override suspend fun fetchMyWaitlistedClassIds(gymId: String): Set<String> {
        errorToThrow?.let { throw it }
        val uid = signedInUID ?: return emptySet()
        return (waitlist[gymId] ?: emptyList()).filter { it.first == uid }.map { it.second }.toSet()
    }

    override suspend fun fetchWaitlistPosition(gymId: String, classId: String): Int? {
        errorToThrow?.let { throw it }
        val uid = signedInUID ?: return null
        val entries = (waitlist[gymId] ?: emptyList()).filter { it.second == classId }
        val index = entries.indexOfFirst { it.first == uid }
        return if (index == -1) null else index + 1
    }

    override suspend fun fetchAllUsers(): List<PlatformUser> {
        errorToThrow?.let { throw it }
        return platformUsers.values.toList()
    }

    override suspend fun updatePlatformRole(uid: String, role: PlatformRole) {
        errorToThrow?.let { throw it }
        platformRoles[uid] = role
        platformUsers[uid]?.let { platformUsers[uid] = it.copy(role = role) }
    }

    override suspend fun deleteGym(gymId: String) {
        errorToThrow?.let { throw it }
        gyms[gymId]?.joinCode?.let { gymJoinCodes.remove(it) }
        gyms.remove(gymId)
        memberships.remove(gymId)
        classes.remove(gymId)
        bookings.remove(gymId)
        waitlist.remove(gymId)
        workoutLogs.remove(gymId)
        team.remove(gymId)
        gymMembers.remove(gymId)
        membershipPlans.remove(gymId)
        bookingActivePlanIds.remove(gymId)
        activePlans.keys.filter { it.first == gymId }.forEach { activePlans.remove(it) }
        notifyClassesChanged(gymId)
    }

    override suspend fun createGymForCurrentUser(name: String, city: String?, joinCode: String?, workoutTypes: List<String>): Gym {
        errorToThrow?.let { throw it }
        val uid = signedInUID ?: throw BackendException.NotAuthenticated

        val requested = joinCode?.let { sanitizeJoinCode(it) }?.takeIf { it.isNotBlank() } ?: generateJoinCode(name)
        var code = requested
        var attempt = 1
        while (gymJoinCodes.containsKey(code)) {
            code = "$requested$attempt"
            attempt++
        }

        val gym = Gym(id = UUID.randomUUID().toString(), name = name, ownerUID = uid, workoutTypes = workoutTypes, joinCode = code, city = city)
        gyms[gym.id] = gym
        gymJoinCodes[code] = gym.id
        memberships.getOrPut(gym.id) { mutableMapOf() }[uid] = UserRole.OWNER
        val profile = profiles[uid]
        team.getOrPut(gym.id) { mutableMapOf() }[uid] = TeamMember(
            id = uid,
            fullName = profile?.fullName.orEmpty(),
            email = profile?.email.orEmpty(),
            role = UserRole.OWNER
        )
        return gym
    }

    override suspend fun createGym(name: String, ownerFirstName: String, ownerLastName: String, ownerEmail: String, ownerPassword: String): Gym {
        errorToThrow?.let { throw it }
        signedInUID ?: throw BackendException.NotAuthenticated

        val existingUid = profiles.entries.firstOrNull { it.value.email == ownerEmail }?.key
        val ownerUID = existingUid ?: registerNewAccount(ownerFirstName, ownerLastName, ownerEmail)
        val ownerFullName = profiles[ownerUID]?.fullName ?: "$ownerFirstName $ownerLastName".trim()

        val requested = generateJoinCode(name)
        var code = requested
        var attempt = 1
        while (gymJoinCodes.containsKey(code)) {
            code = "$requested$attempt"
            attempt++
        }

        val gym = Gym(id = UUID.randomUUID().toString(), name = name, ownerUID = ownerUID, joinCode = code)
        gyms[gym.id] = gym
        gymJoinCodes[code] = gym.id
        memberships.getOrPut(gym.id) { mutableMapOf() }[ownerUID] = UserRole.OWNER
        team.getOrPut(gym.id) { mutableMapOf() }[ownerUID] = TeamMember(
            id = ownerUID,
            fullName = ownerFullName,
            email = ownerEmail,
            role = UserRole.OWNER
        )
        return gym
    }

    override suspend fun fetchGymByJoinCode(code: String): Gym? {
        errorToThrow?.let { throw it }
        val upper = code.uppercase()
        val gymId = gymJoinCodes[upper]
        if (gymId != null) return gyms[gymId]
        // Fallback for a gym seeded directly (via seedGym) rather than through
        // createGymForCurrentUser, which is the only path that registers
        // gymJoinCodes — mirrors FirebaseBackendRepository's own fallback
        // query for gyms predating the gymCodes lookup collection.
        return gyms.values.firstOrNull { it.joinCode?.uppercase() == upper }
    }

    override suspend fun joinGymByCode(code: String): Gym {
        errorToThrow?.let { throw it }
        val uid = signedInUID ?: throw BackendException.NotAuthenticated
        val gym = fetchGymByJoinCode(code) ?: throw BackendException.GymNotFound

        memberships.getOrPut(gym.id) { mutableMapOf() }[uid] = UserRole.MEMBER
        val profile = profiles[uid]
        gymMembers.getOrPut(gym.id) { mutableMapOf() }[uid] = GymMember(
            id = uid,
            fullName = profile?.fullName.orEmpty(),
            email = profile?.email.orEmpty()
        )
        return gym
    }
}
