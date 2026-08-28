package com.nexo.app.data.repository

import com.nexo.app.domain.model.ActivePlanItem
import com.nexo.app.domain.model.Gym
import com.nexo.app.domain.model.GymClass
import com.nexo.app.domain.model.GymMember
import com.nexo.app.domain.model.Member
import com.nexo.app.domain.model.MembershipPlan
import com.nexo.app.domain.model.PlatformRole
import com.nexo.app.domain.model.PlatformUser
import com.nexo.app.domain.model.TeamMember
import com.nexo.app.domain.model.UserRole
import com.nexo.app.domain.model.WorkoutLog
import kotlinx.coroutines.flow.Flow

/**
 * Mirrors `BackendService` on iOS — the single abstraction over Firebase.
 * UI/state-holder code must never call Firebase directly, only through
 * this interface (CLAUDE.md's repository-pattern rule, mirrored for
 * Android). `FirebaseBackendRepository` (a later phase) is the real
 * implementation; [FakeBackendRepository] is the in-memory stand-in used
 * for unit tests and offline previews.
 */
interface BackendRepository {
    fun currentUID(): String?
    /** Mirrors iOS's `signIn(email:password:)`. */
    suspend fun signIn(email: String, password: String)
    /**
     * Mirrors iOS's `signUp(email:password:firstName:lastName:)` — creates
     * the Auth account, then writes the `users/{uid}` profile document.
     */
    suspend fun signUp(email: String, password: String, firstName: String, lastName: String)
    /** Mirrors iOS's `signOut()`. */
    fun signOut()
    /**
     * Sends a password-reset email — mirrors iOS's `sendPasswordReset(email:)`.
     * [FirebaseBackendRepository] doesn't check whether [email] belongs to a
     * registered account first (real Firebase Auth silently no-ops for an
     * unknown email, by design, to avoid leaking which emails are
     * registered); [FakeBackendRepository] throws
     * [BackendException.UserNotFound] for an unrecognized email so tests can
     * exercise that error path, mirroring iOS's `MockBackendService`.
     */
    suspend fun sendPasswordReset(email: String)
    /**
     * The signed-in user's own profile (name, photo) — mirrors iOS's
     * `fetchUserProfile()`. Not in the original Phase 1 interface, but
     * Phase 2's Home greeting and Profile screen need a display name from
     * somewhere other than the raw UID.
     */
    suspend fun fetchMyProfile(): Member?
    /** The signed-in user's platform-wide role (admin vs regular user) — mirrors iOS's `fetchPlatformRole()`. */
    suspend fun fetchPlatformRole(): PlatformRole
    /** All gyms registered on the platform — mirrors iOS's `fetchAvailableGyms()`. */
    suspend fun fetchAvailableGyms(): List<Gym>
    suspend fun fetchMyGyms(): List<Pair<Gym, UserRole>>
    suspend fun fetchClasses(gymId: String): List<GymClass>
    /**
     * A live view of every class in this gym — mirrors the spirit of
     * iOS's `observeClasses(gymId:for:)`, but scoped to the whole gym
     * rather than one day at a time: `ScheduleViewModel` already filters
     * the full list to the selected day client-side (see
     * `UiState.classesForSelectedDate`), so a day-scoped listener would
     * mean tearing down and resubscribing on every date/week navigation
     * for no real benefit — one gym-scoped listener, reused across day
     * changes, is simpler and just as live. Replaces the poll-on-resume
     * [fetchClasses]/`refresh()` pattern specifically in `ScheduleViewModel`;
     * every other screen (Home, Profile, Class Detail, Member Detail,
     * Manage tab) keeps using the one-shot suspend [fetchClasses], since
     * they don't need continuous updates.
     */
    fun observeClasses(gymId: String): Flow<List<GymClass>>
    /**
     * The signed-in user's booked class IDs in this gym — mirrors iOS's
     * `fetchUserBookings(gymId:)`. Not in the original Phase 1 interface,
     * but Phase 2's UI can't render booked/upcoming state (Book vs. Cancel
     * button, Upcoming Bookings, Activity Timeline) without it.
     */
    suspend fun fetchMyBookedClassIds(gymId: String): Set<String>
    suspend fun bookClass(gymId: String, classId: String)
    /**
     * Cancels the signed-in user's booking. If the class has a non-empty
     * waitlist, the first waitlisted user (by join order) is
     * automatically promoted into the freed spot — `currentAttendees`
     * stays the same and `waitlistCount` decrements by 1. Otherwise
     * `currentAttendees` decrements by 1. Mirrors iOS's `removeBooking`.
     */
    suspend fun cancelBooking(gymId: String, classId: String)
    /** Staff-initiated cancel of another member's booking — mirrors iOS's `cancelBooking(gymId:classId:onBehalfOf:)`. Same waitlist-promotion/refund behavior as the self-service overload above, just acting on [userId] instead of the signed-in user. */
    suspend fun cancelBooking(gymId: String, classId: String, onBehalfOf: String)
    /**
     * The signed-in-or-given user's credit wallet for this gym — mirrors
     * iOS's `fetchActivePlans(gymId:userId:)`, used by the Home screen's
     * "My Plans" card. `bookClass` enforces this wallet internally (see
     * `FirebaseBackendRepository.validateAndConsumeMembership`); this
     * method is read-only, for display.
     */
    suspend fun fetchActivePlans(gymId: String, userId: String): List<ActivePlanItem>
    /**
     * Grants [userId] one credit-wallet item per component in [plan]'s
     * template — mirrors iOS's `grantPlanToMember(gymId:userId:plan:customExpiresAt:)`.
     * `expiresAt` is computed from each component's `validityValue`/`validityUnit`
     * starting now, unless [customExpiresAtMillis] is given, in which case
     * every item created from this grant expires on that date instead.
     * `remainingCredits` is `0` for an unlimited component.
     */
    suspend fun grantPlanToMember(gymId: String, userId: String, plan: MembershipPlan, customExpiresAtMillis: Long? = null)
    /** Removes one item from [userId]'s credit wallet — mirrors iOS's `revokeActivePlan`. Does not refund/adjust anything else; it's an unconditional removal, distinct from the automatic credit refund `cancelBooking` performs. */
    suspend fun revokeActivePlan(gymId: String, userId: String, activePlanId: String)
    /** A member's own booked classes, past and upcoming — mirrors iOS's `fetchMemberBookings(gymId:userId:)`, for the Manage tab's Member Detail screen (staff only). */
    suspend fun fetchMemberBookings(gymId: String, userId: String): List<GymClass>
    /** The members currently booked into a class, with live `isCheckedIn`/`checkedInAtMillis` attendance — mirrors iOS's `fetchAttendees(gymId:classId:)`, for the Class Detail screen's Attendees card. */
    suspend fun fetchAttendees(gymId: String, classId: String): List<Member>
    /** Marks [userId]'s booking for [classId] as checked in (or undoes it) — Owner/Coach/Platform Admin only (gated in the UI layer). A no-op if [userId] has no booking for that class. */
    suspend fun toggleAttendance(gymId: String, classId: String, userId: String, isCheckedIn: Boolean)
    /** Updates the signed-in user's `users/{uid}.profilePicBase64` — [base64] is expected to already be a downscaled, compressed JPEG (see `ui/profile`'s photo-picker flow). */
    suspend fun updateProfilePicture(base64: String)
    suspend fun fetchWorkoutLogs(gymId: String): List<WorkoutLog>
    suspend fun addWorkoutLog(gymId: String, log: WorkoutLog)
    suspend fun updateWorkoutLog(gymId: String, log: WorkoutLog)
    suspend fun deleteWorkoutLog(gymId: String, logId: String)

    // MARK: - Manage tab (Owner/Coach/Platform Admin only — gated in the UI layer, per FEEDBACK.md Phase 6)

    suspend fun fetchTeam(gymId: String): List<TeamMember>
    /**
     * Attaches an *existing* platform user (found by [email]) to this
     * gym's team — mirrors iOS's `addExistingUserToGym`, i.e. `AddTeamMemberView`'s
     * "Search" mode. Throws [BackendException.UserNotFound] if no
     * `users/{uid}` document matches [email]. [name] is accepted to match
     * the task spec's signature but unused by [FirebaseBackendRepository]
     * — the existing user's own `firstName`/`lastName` are used instead,
     * same as iOS's "Search" mode.
     */
    suspend fun addTeamMember(gymId: String, email: String, role: UserRole, name: String)
    /**
     * Registers a brand-new Auth account for a coach/owner and attaches
     * them to this gym's team — mirrors iOS's `addTeamMember(gymId:firstName:
     * lastName:email:password:role:)`, i.e. `AddTeamMemberView`'s "New Account"
     * mode. [FirebaseBackendRepository] creates the account via a secondary
     * `FirebaseApp`/`FirebaseAuth` instance so it doesn't sign out the
     * caller's own session (same technique iOS uses with a secondary
     * `FirebaseApp`).
     */
    suspend fun registerTeamMember(gymId: String, firstName: String, lastName: String, email: String, password: String, role: UserRole)
    /** Updates [userId]'s role on both `gyms/{gymId}/team/{uid}` and `users/{uid}/memberships/{gymId}` — mirrors iOS's `updateTeamMemberRole`. */
    suspend fun updateTeamMemberRole(gymId: String, userId: String, role: UserRole)
    suspend fun removeTeamMember(gymId: String, memberId: String)
    suspend fun fetchGymMembers(gymId: String): List<GymMember>
    /** Attaches an *existing* platform user (found by [email]) to this gym as a member — search-existing-user mode only. Throws [BackendException.UserNotFound] if no `users/{uid}` document matches [email]. */
    suspend fun addMember(gymId: String, email: String)
    /** Registers a brand-new Auth account for a member and attaches them to this gym — mirrors iOS's `addMember(gymId:firstName:lastName:email:password:)`. Same secondary-`FirebaseApp` mechanism as [registerTeamMember]. */
    suspend fun registerMember(gymId: String, firstName: String, lastName: String, email: String, password: String)
    /** Removes the member from the gym entirely: their `gyms/{gymId}/members/{uid}` doc, their `users/{uid}/memberships/{gymId}` doc, and every item in their `activePlans` wallet for this gym — mirrors iOS's `removeMember`. Their platform-level `users/{uid}` account and any bookings they made are untouched (matches iOS's documented orphaning behavior). */
    suspend fun removeMember(gymId: String, userId: String)
    suspend fun fetchMembershipPlans(gymId: String): List<MembershipPlan>
    suspend fun createMembershipPlan(gymId: String, plan: MembershipPlan)
    /** Overwrites the plan at [plan]'s existing id — mirrors iOS's `updateMembershipPlan`, which (like `createMembershipPlan`) is a full `setData` at that document, not a partial field update. Does not touch any member's already-granted `activePlans` items (those are independent, denormalized copies — see `FIRESTORE_SCHEMA.md`'s `membershipPlans` note). */
    suspend fun updateMembershipPlan(gymId: String, plan: MembershipPlan)
    suspend fun deleteMembershipPlan(gymId: String, planId: String)
    suspend fun updateGymSettings(gymId: String, name: String, workoutTypes: List<String>)

    // MARK: - Class creation, series management & waitlist (Phase 7)

    suspend fun createClass(gymId: String, gymClass: GymClass)
    suspend fun createClasses(gymId: String, classes: List<GymClass>)
    suspend fun updateClass(gymId: String, gymClass: GymClass)
    suspend fun deleteClass(gymId: String, classId: String)
    /** Batch-updates every occurrence sharing [seriesId] whose `startTimeMillis >= fromDateMillis` — mirrors iOS's `updateClassSeries`: each occurrence keeps its own date, only the time-of-day and other template fields change, and each keeps its own `currentAttendees`/`waitlistCount`. */
    suspend fun updateClassSeries(gymId: String, seriesId: String, fromDateMillis: Long, updatedTemplate: GymClass)
    /** Batch-deletes every occurrence sharing [seriesId] whose `startTimeMillis >= fromDateMillis`. */
    suspend fun deleteClassSeries(gymId: String, seriesId: String, fromDateMillis: Long)
    /**
     * Duplicates every class in the Monday-start week containing
     * [fromWeekOfMillis] into the Monday-start week containing
     * [toWeekOfMillis], preserving each class's time-of-day offset within
     * its week — mirrors iOS's `copySchedule(gymId:fromWeekOf:toWeekOf:)`.
     * Copies get fresh ids, `currentAttendees`/`waitlistCount` reset to 0,
     * and no `seriesId` (a copy is a standalone occurrence, matching
     * `FIRESTORE_SCHEMA.md`'s documented behavior). A no-op if the source
     * week has no classes. **Not yet wired to any Android UI** — iOS
     * itself has this fully implemented and tested on the backend but has
     * no UI entry point for it either; Android mirrors that exact state
     * rather than building ahead of iOS.
     */
    suspend fun copySchedule(gymId: String, fromWeekOfMillis: Long, toWeekOfMillis: Long)

    /** Throws [BackendException.ClassInPast] if the class has already started. */
    suspend fun joinWaitlist(gymId: String, classId: String)
    suspend fun leaveWaitlist(gymId: String, classId: String)
    suspend fun fetchMyWaitlistedClassIds(gymId: String): Set<String>
    /** This user's 1-based position on the class's waitlist, ordered by join time — `null` if not waitlisted. */
    suspend fun fetchWaitlistPosition(gymId: String, classId: String): Int?

    // MARK: - Platform Admin Dashboard & gym onboarding (Phase 8)

    /** All registered platform users — Platform Admin only. */
    suspend fun fetchAllUsers(): List<PlatformUser>
    suspend fun updatePlatformRole(uid: String, role: PlatformRole)
    /** Permanently deletes a gym and everything under it (classes, bookings, waitlist, team, members, membership plans, and every user's membership) — Platform Admin only. No undo. */
    suspend fun deleteGym(gymId: String)

    /** Creates a gym owned by the signed-in user, with a generated-or-sanitized join code — the self-serve "I am a Gym Owner" onboarding path. */
    suspend fun createGymForCurrentUser(name: String, city: String?, joinCode: String?, workoutTypes: List<String>): Gym
    /**
     * Platform Admin creates a gym on behalf of another owner — mirrors
     * iOS's `createGym(name:ownerFirstName:ownerLastName:ownerEmail:ownerPassword:)`,
     * distinct from the self-serve [createGymForCurrentUser]. If
     * [ownerEmail] already belongs to a platform user, that existing
     * account becomes the owner (their own name is used, not
     * [ownerFirstName]/[ownerLastName]); otherwise a brand-new account is
     * registered via the same secondary-`FirebaseApp` mechanism as
     * [registerTeamMember]/[registerMember]. The join code is always
     * auto-generated (no custom-code input on this admin path).
     */
    suspend fun createGym(name: String, ownerFirstName: String, ownerLastName: String, ownerEmail: String, ownerPassword: String): Gym
    suspend fun fetchGymByJoinCode(code: String): Gym?
    /** Resolves [code] to a gym and enrolls the signed-in user as a member. Throws [BackendException.GymNotFound] if the code doesn't match any gym. */
    suspend fun joinGymByCode(code: String): Gym
}

/**
 * Errors thrown by [BackendRepository] implementations — mirrors the
 * shape of `MockBackendError`/`FBError` on iOS (a fixed, typed error set
 * rather than raw strings/exceptions).
 */
sealed class BackendException(message: String) : Exception(message) {
    object NotAuthenticated : BackendException("Not authenticated")
    object ClassNotFound : BackendException("Class not found")
    object ClassFull : BackendException("Class is full")
    object UserNotFound : BackendException("No account found with that email — they need to sign up first.")
    object ClassInPast : BackendException("Cannot book or join waitlist for a class that has already started.")
    object GymNotFound : BackendException("No gym found with that join code.")
    object NoActiveMembership : BackendException("No active membership. Please contact your gym to purchase a plan.")
    object InsufficientCredits : BackendException("Insufficient credits. Please contact your gym to purchase more.")
}
