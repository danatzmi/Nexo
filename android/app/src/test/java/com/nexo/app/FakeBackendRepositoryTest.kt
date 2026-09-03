package com.nexo.app

import com.nexo.app.data.repository.BackendException
import com.nexo.app.data.repository.FakeBackendRepository
import com.nexo.app.domain.model.ActivePlanItem
import com.nexo.app.domain.model.Gym
import com.nexo.app.domain.model.GymClass
import com.nexo.app.domain.model.GymMember
import com.nexo.app.domain.model.Member
import com.nexo.app.domain.model.MembershipPlan
import com.nexo.app.domain.model.PlanComponent
import com.nexo.app.domain.model.PlanComponentType
import com.nexo.app.domain.model.PlanResetPeriod
import com.nexo.app.domain.model.PlatformRole
import com.nexo.app.domain.model.PlatformUser
import com.nexo.app.domain.model.TeamMember
import com.nexo.app.domain.model.UserRole
import com.nexo.app.domain.model.ValidityUnit
import com.nexo.app.domain.model.WorkoutLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FakeBackendRepositoryTest {

    /** Grants [userId] an unlimited, never-expiring plan so `bookClass` in these tests exercises booking mechanics without also having to seed a full credit-wallet story. */
    private fun FakeBackendRepository.grantUnlimitedPlan(gymId: String, userId: String) {
        seedActivePlan(gymId, userId, ActivePlanItem(id = "plan-$userId-$gymId", planName = "Test Plan", type = PlanComponentType.UNLIMITED, expiresAtMillis = Long.MAX_VALUE / 2))
    }

    // MARK: - bookClass / capacity & waitlist limits

    @Test
    fun bookClass_incrementsCurrentAttendees() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"
        repo.grantUnlimitedPlan("gym-1", "member-1")
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 2, currentAttendees = 0)
        repo.seedClass("gym-1", gymClass)

        repo.bookClass("gym-1", "class-1")

        val updated = repo.fetchClasses("gym-1").first { it.id == "class-1" }
        assertEquals(1, updated.currentAttendees)
    }

    @Test
    fun bookClass_isIdempotent_whenAlreadyBookedBySameUser() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"
        repo.grantUnlimitedPlan("gym-1", "member-1")
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0)
        repo.seedClass("gym-1", gymClass)

        repo.bookClass("gym-1", "class-1")
        repo.bookClass("gym-1", "class-1")

        val updated = repo.fetchClasses("gym-1").first { it.id == "class-1" }
        assertEquals(1, updated.currentAttendees)
    }

    @Test
    fun bookClass_throwsClassFull_whenAtCapacity() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 1, currentAttendees = 1)
        repo.seedClass("gym-1", gymClass)
        repo.signedInUID = "member-2" // a different member than whoever fills the existing spot
        repo.grantUnlimitedPlan("gym-1", "member-2")

        try {
            repo.bookClass("gym-1", "class-1")
            fail("Expected BackendException.ClassFull")
        } catch (e: BackendException.ClassFull) {
            // expected
        }

        val updated = repo.fetchClasses("gym-1").first { it.id == "class-1" }
        assertEquals(1, updated.currentAttendees) // unchanged — the rejected booking must not have been counted
    }

    @Test
    fun bookClass_throwsClassNotFound_forUnknownClass() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"

        try {
            repo.bookClass("gym-1", "does-not-exist")
            fail("Expected BackendException.ClassNotFound")
        } catch (e: BackendException.ClassNotFound) {
            // expected
        }
    }

    @Test
    fun cancelBooking_decrementsCurrentAttendees() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"
        repo.grantUnlimitedPlan("gym-1", "member-1")
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0)
        repo.seedClass("gym-1", gymClass)
        repo.bookClass("gym-1", "class-1")

        repo.cancelBooking("gym-1", "class-1")

        val updated = repo.fetchClasses("gym-1").first { it.id == "class-1" }
        assertEquals(0, updated.currentAttendees)
    }

    @Test
    fun cancelBooking_isIdempotent_onDoubleCancel() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"
        repo.grantUnlimitedPlan("gym-1", "member-1")
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0)
        repo.seedClass("gym-1", gymClass)
        repo.bookClass("gym-1", "class-1")

        repo.cancelBooking("gym-1", "class-1")
        repo.cancelBooking("gym-1", "class-1") // must not drive attendees negative

        val updated = repo.fetchClasses("gym-1").first { it.id == "class-1" }
        assertEquals(0, updated.currentAttendees)
    }

    // MARK: - fetchMyBookedClassIds / fetchMyProfile

    @Test
    fun fetchMyBookedClassIds_returnsOnlyTheSignedInUsersBookings() = runTest {
        val repo = FakeBackendRepository()
        val classA = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0)
        val classB = GymClass(id = "class-2", title = "Yoga", coach = "Sam", startTimeMillis = 0L, capacity = 5, currentAttendees = 0)
        repo.seedClass("gym-1", classA)
        repo.seedClass("gym-1", classB)

        repo.signedInUID = "member-1"
        repo.grantUnlimitedPlan("gym-1", "member-1")
        repo.bookClass("gym-1", "class-1")
        repo.signedInUID = "member-2"
        repo.grantUnlimitedPlan("gym-1", "member-2")
        repo.bookClass("gym-1", "class-2")

        repo.signedInUID = "member-1"
        assertEquals(setOf("class-1"), repo.fetchMyBookedClassIds("gym-1"))
    }

    @Test
    fun fetchMyProfile_returnsTheSeededProfileForTheSignedInUser() = runTest {
        val repo = FakeBackendRepository()
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.signedInUID = "member-1"

        assertEquals("Dana Cohen", repo.fetchMyProfile()?.fullName)
    }

    @Test
    fun fetchMyProfile_returnsNull_whenNoProfileWasSeededForThatUser() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"

        assertNull(repo.fetchMyProfile())
    }

    // MARK: - fetchAttendees

    @Test
    fun fetchAttendees_returnsProfilesOfEveryoneBookedIntoThatClass() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0)
        repo.seedClass("gym-1", gymClass)
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.seedProfile("member-2", Member(id = "member-2", fullName = "Sam Lee", email = "sam@example.com"))

        repo.signedInUID = "member-1"
        repo.grantUnlimitedPlan("gym-1", "member-1")
        repo.bookClass("gym-1", "class-1")
        repo.signedInUID = "member-2"
        repo.grantUnlimitedPlan("gym-1", "member-2")
        repo.bookClass("gym-1", "class-1")

        val attendees = repo.fetchAttendees("gym-1", "class-1")

        assertEquals(setOf("Dana Cohen", "Sam Lee"), attendees.map { it.fullName }.toSet())
    }

    @Test
    fun fetchAttendees_excludesBookingsForOtherClasses() = runTest {
        val repo = FakeBackendRepository()
        val classA = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0)
        val classB = GymClass(id = "class-2", title = "Yoga", coach = "Sam", startTimeMillis = 0L, capacity = 5, currentAttendees = 0)
        repo.seedClass("gym-1", classA)
        repo.seedClass("gym-1", classB)
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.signedInUID = "member-1"
        repo.grantUnlimitedPlan("gym-1", "member-1")
        repo.bookClass("gym-1", "class-2")

        assertTrue(repo.fetchAttendees("gym-1", "class-1").isEmpty())
    }

    @Test
    fun fetchAttendees_returnsEmptyList_whenNoOneIsBooked() = runTest {
        val repo = FakeBackendRepository()

        assertTrue(repo.fetchAttendees("gym-1", "class-1").isEmpty())
    }

    // MARK: - toggleAttendance

    @Test
    fun toggleAttendance_markingCheckedIn_isReflectedInFetchAttendees() = runTest {
        val repo = FakeBackendRepository()
        repo.seedClass("gym-1", GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0))
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.signedInUID = "member-1"
        repo.grantUnlimitedPlan("gym-1", "member-1")
        repo.bookClass("gym-1", "class-1")

        repo.toggleAttendance("gym-1", "class-1", "member-1", isCheckedIn = true)

        val attendee = repo.fetchAttendees("gym-1", "class-1").first()
        assertTrue(attendee.isCheckedIn)
        assertTrue(attendee.checkedInAtMillis != null)
    }

    @Test
    fun toggleAttendance_uncheckingIn_clearsCheckedInAtMillis() = runTest {
        val repo = FakeBackendRepository()
        repo.seedClass("gym-1", GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0))
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.signedInUID = "member-1"
        repo.grantUnlimitedPlan("gym-1", "member-1")
        repo.bookClass("gym-1", "class-1")
        repo.toggleAttendance("gym-1", "class-1", "member-1", isCheckedIn = true)

        repo.toggleAttendance("gym-1", "class-1", "member-1", isCheckedIn = false)

        val attendee = repo.fetchAttendees("gym-1", "class-1").first()
        assertEquals(false, attendee.isCheckedIn)
        assertNull(attendee.checkedInAtMillis)
    }

    @Test
    fun toggleAttendance_isANoOp_whenTheUserHasNoBookingForThatClass() = runTest {
        val repo = FakeBackendRepository()
        repo.seedClass("gym-1", GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0))
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))

        repo.toggleAttendance("gym-1", "class-1", "member-1", isCheckedIn = true)

        assertTrue(repo.fetchAttendees("gym-1", "class-1").isEmpty())
    }

    // MARK: - Credit wallet enforcement (bookClass / cancelBooking)

    @Test
    fun bookClass_withOneCredit_succeeds_andDecrementsRemainingCreditsToZero() = runTest {
        val repo = FakeBackendRepository()
        repo.seedClass("gym-1", GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0))
        repo.seedActivePlan("gym-1", "member-1", ActivePlanItem(id = "plan-1", planName = "10-Class Pack", type = PlanComponentType.CREDITS, remainingCredits = 1, expiresAtMillis = Long.MAX_VALUE / 2))
        repo.signedInUID = "member-1"

        repo.bookClass("gym-1", "class-1")

        assertEquals(0, repo.fetchActivePlans("gym-1", "member-1").first { it.id == "plan-1" }.remainingCredits)
    }

    @Test
    fun bookClass_withZeroRemainingCredits_throwsInsufficientCredits() = runTest {
        val repo = FakeBackendRepository()
        repo.seedClass("gym-1", GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0))
        repo.seedActivePlan("gym-1", "member-1", ActivePlanItem(id = "plan-1", planName = "10-Class Pack", type = PlanComponentType.CREDITS, remainingCredits = 0, expiresAtMillis = Long.MAX_VALUE / 2))
        repo.signedInUID = "member-1"

        try {
            repo.bookClass("gym-1", "class-1")
            fail("Expected BackendException.InsufficientCredits")
        } catch (e: BackendException.InsufficientCredits) {
            // expected
        }
    }

    @Test
    fun bookClass_withNoActivePlans_throwsNoActiveMembership() = runTest {
        val repo = FakeBackendRepository()
        repo.seedClass("gym-1", GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0))
        repo.signedInUID = "member-1"

        try {
            repo.bookClass("gym-1", "class-1")
            fail("Expected BackendException.NoActiveMembership")
        } catch (e: BackendException.NoActiveMembership) {
            // expected
        }
    }

    @Test
    fun bookClass_ownerOrCoach_bypassesWalletCheck_evenWithNoActivePlans() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.COACH, "coach-1")
        repo.seedClass("gym-1", GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0))
        repo.signedInUID = "coach-1"

        repo.bookClass("gym-1", "class-1")

        assertTrue(repo.fetchMyBookedClassIds("gym-1").contains("class-1"))
    }

    @Test
    fun bookClass_platformAdmin_bypassesWalletCheck_evenWithNoActivePlans() = runTest {
        val repo = FakeBackendRepository()
        repo.seedClass("gym-1", GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0))
        repo.seedPlatformRole("admin-1", PlatformRole.ADMIN)
        repo.signedInUID = "admin-1"

        repo.bookClass("gym-1", "class-1")

        assertTrue(repo.fetchMyBookedClassIds("gym-1").contains("class-1"))
    }

    @Test
    fun bookClass_withUnlimitedPlan_doesNotConsumeCredits() = runTest {
        val repo = FakeBackendRepository()
        repo.seedClass("gym-1", GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0))
        repo.seedActivePlan("gym-1", "member-1", ActivePlanItem(id = "plan-1", planName = "Unlimited", type = PlanComponentType.UNLIMITED, expiresAtMillis = Long.MAX_VALUE / 2))
        repo.signedInUID = "member-1"

        repo.bookClass("gym-1", "class-1")

        assertEquals(0, repo.fetchActivePlans("gym-1", "member-1").first().remainingCredits)
    }

    @Test
    fun cancelBooking_ofACreditBasedBooking_refundsOneCredit() = runTest {
        val repo = FakeBackendRepository()
        repo.seedClass("gym-1", GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = System.currentTimeMillis() + 3_600_000, capacity = 5, currentAttendees = 0))
        repo.seedActivePlan("gym-1", "member-1", ActivePlanItem(id = "plan-1", planName = "10-Class Pack", type = PlanComponentType.CREDITS, remainingCredits = 1, expiresAtMillis = Long.MAX_VALUE / 2))
        repo.signedInUID = "member-1"
        repo.bookClass("gym-1", "class-1")

        repo.cancelBooking("gym-1", "class-1")

        assertEquals(1, repo.fetchActivePlans("gym-1", "member-1").first { it.id == "plan-1" }.remainingCredits)
    }

    @Test
    fun fetchActivePlans_returnsTheSeededWallet() = runTest {
        val repo = FakeBackendRepository()
        repo.seedActivePlan("gym-1", "member-1", ActivePlanItem(id = "plan-1", planName = "10-Class Pack", type = PlanComponentType.CREDITS, remainingCredits = 4, expiresAtMillis = Long.MAX_VALUE / 2))

        val plans = repo.fetchActivePlans("gym-1", "member-1")

        assertEquals(listOf("plan-1"), plans.map { it.id })
    }

    // MARK: - updateProfilePicture

    @Test
    fun updateProfilePicture_updatesTheSignedInUsersProfile() = runTest {
        val repo = FakeBackendRepository()
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.signedInUID = "member-1"

        repo.updateProfilePicture("base64-jpeg-data")

        assertEquals("base64-jpeg-data", repo.fetchMyProfile()?.profilePicBase64)
    }

    // MARK: - workout log CRUD, scoped per user

    @Test
    fun addWorkoutLog_thenFetchWorkoutLogs_returnsSavedLog() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"
        val log = WorkoutLog(id = "log-1", movement = "Squat", score = 100.0, reps = 5, sets = 3, dateMillis = 0L)

        repo.addWorkoutLog("gym-1", log)
        val fetched = repo.fetchWorkoutLogs("gym-1")

        assertEquals(listOf(log), fetched)
    }

    @Test
    fun fetchWorkoutLogs_isScopedPerUser() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"
        repo.addWorkoutLog("gym-1", WorkoutLog(id = "log-1", movement = "Squat", score = 100.0, reps = 5, sets = 3, dateMillis = 0L))

        repo.signedInUID = "member-2"
        val fetched = repo.fetchWorkoutLogs("gym-1")

        assertTrue(fetched.isEmpty())
    }

    @Test
    fun updateWorkoutLog_overwritesTheExistingEntry() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"
        val original = WorkoutLog(id = "log-1", movement = "Squat", score = 100.0, reps = 5, sets = 3, dateMillis = 0L)
        repo.addWorkoutLog("gym-1", original)

        val updated = original.copy(movement = "Front Squat", score = 90.0)
        repo.updateWorkoutLog("gym-1", updated)

        val fetched = repo.fetchWorkoutLogs("gym-1")
        assertEquals(listOf(updated), fetched)
    }

    @Test
    fun deleteWorkoutLog_removesExactlyThatLog() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"
        val logA = WorkoutLog(id = "log-1", movement = "Squat", score = 100.0, reps = 5, sets = 3, dateMillis = 0L)
        val logB = WorkoutLog(id = "log-2", movement = "Deadlift", score = 150.0, reps = 5, sets = 3, dateMillis = 0L)
        repo.addWorkoutLog("gym-1", logA)
        repo.addWorkoutLog("gym-1", logB)

        repo.deleteWorkoutLog("gym-1", "log-1")

        assertEquals(listOf(logB), repo.fetchWorkoutLogs("gym-1"))
    }

    // MARK: - signIn / signUp / signOut

    @Test
    fun signIn_setsSignedInUID_forAMatchingSeededProfile() = runTest {
        val repo = FakeBackendRepository()
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))

        repo.signIn("dana@example.com", "irrelevant")

        assertEquals("member-1", repo.currentUID())
    }

    @Test
    fun signIn_throwsNotAuthenticated_whenNoProfileMatchesTheEmail() = runTest {
        val repo = FakeBackendRepository()

        try {
            repo.signIn("nobody@example.com", "irrelevant")
            fail("Expected BackendException.NotAuthenticated")
        } catch (e: BackendException.NotAuthenticated) {
            // expected
        }
        assertNull(repo.currentUID())
    }

    @Test
    fun signUp_createsAProfile_andSignsIn() = runTest {
        val repo = FakeBackendRepository()

        repo.signUp("new@example.com", "password", "New", "User")

        val uid = repo.currentUID()
        assertTrue(uid != null)
        assertEquals("New User", repo.fetchMyProfile()?.fullName)
    }

    @Test
    fun signOut_clearsSignedInUID() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"

        repo.signOut()

        assertNull(repo.currentUID())
    }

    @Test
    fun sendPasswordReset_succeeds_forARegisteredEmail() = runTest {
        val repo = FakeBackendRepository()
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))

        repo.sendPasswordReset("dana@example.com")
        // No exception — success.
    }

    @Test
    fun sendPasswordReset_throwsUserNotFound_forAnUnregisteredEmail() = runTest {
        val repo = FakeBackendRepository()

        try {
            repo.sendPasswordReset("nobody@example.com")
            fail("Expected BackendException.UserNotFound")
        } catch (e: BackendException.UserNotFound) {
            // expected
        }
    }

    @Test
    fun workoutLogMethods_throwNotAuthenticated_whenNoUserSignedIn() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = null

        try {
            repo.fetchWorkoutLogs("gym-1")
            fail("Expected BackendException.NotAuthenticated")
        } catch (e: BackendException.NotAuthenticated) {
            // expected
        }
        assertNull(repo.currentUID())
    }

    @Test
    fun fetchPlatformRole_defaultsToUser() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "user-1"

        assertEquals(PlatformRole.USER, repo.fetchPlatformRole())
    }

    @Test
    fun fetchMyGyms_returnsAllGymsAsOwner_whenUserIsPlatformAdmin() = runTest {
        val repo = FakeBackendRepository()
        val gym1 = Gym(id = "gym-1", name = "Iron Temple", ownerUID = "other-owner")
        val gym2 = Gym(id = "gym-2", name = "CrossFit Example", ownerUID = "other-owner-2")
        repo.seedGym(gym1, UserRole.MEMBER, "other-user")
        repo.seedGym(gym2, UserRole.MEMBER, "other-user")

        repo.signedInUID = "admin-1"
        repo.seedPlatformRole("admin-1", PlatformRole.ADMIN)

        val gyms = repo.fetchMyGyms()
        assertEquals(2, gyms.size)
        assertTrue(gyms.all { it.second == UserRole.OWNER })
    }

    @Test
    fun fetchMyGyms_returnsOwnedGyms_whenUserIsOwnerEvenWithoutMembershipDoc() = runTest {
        val repo = FakeBackendRepository()
        val gym1 = Gym(id = "gym-1", name = "My Own Gym", ownerUID = "owner-1")
        // Seed gym into repo directly without membership doc
        repo.seedGym(gym1, UserRole.MEMBER, "some-other-user")

        repo.signedInUID = "owner-1"
        val gyms = repo.fetchMyGyms()

        assertEquals(1, gyms.size)
        assertEquals("gym-1", gyms[0].first.id)
        assertEquals(UserRole.OWNER, gyms[0].second)
    }

    // MARK: - Manage tab: team

    @Test
    fun addTeamMember_thenFetchTeam_returnsTheNewMember() = runTest {
        val repo = FakeBackendRepository()
        repo.seedProfile("coach-1", Member(id = "coach-1", fullName = "Sam Lee", email = "sam@example.com"))

        repo.addTeamMember("gym-1", "sam@example.com", UserRole.COACH, "Sam Lee")

        val team = repo.fetchTeam("gym-1")
        assertEquals(listOf(TeamMember(id = "coach-1", fullName = "Sam Lee", email = "sam@example.com", role = UserRole.COACH)), team)
    }

    @Test
    fun addTeamMember_throwsUserNotFound_whenNoAccountMatchesTheEmail() = runTest {
        val repo = FakeBackendRepository()

        try {
            repo.addTeamMember("gym-1", "nobody@example.com", UserRole.COACH, "Nobody")
            fail("Expected BackendException.UserNotFound")
        } catch (e: BackendException.UserNotFound) {
            // expected
        }
    }

    @Test
    fun removeTeamMember_removesThemFromFetchTeam() = runTest {
        val repo = FakeBackendRepository()
        repo.seedTeamMember("gym-1", TeamMember(id = "coach-1", fullName = "Sam Lee", email = "sam@example.com", role = UserRole.COACH))

        repo.removeTeamMember("gym-1", "coach-1")

        assertTrue(repo.fetchTeam("gym-1").isEmpty())
    }

    @Test
    fun updateTeamMemberRole_updatesTheirRoleInFetchTeam() = runTest {
        val repo = FakeBackendRepository()
        repo.seedTeamMember("gym-1", TeamMember(id = "coach-1", fullName = "Sam Lee", email = "sam@example.com", role = UserRole.COACH))
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.COACH, "coach-1")

        repo.updateTeamMemberRole("gym-1", "coach-1", UserRole.OWNER)

        assertEquals(UserRole.OWNER, repo.fetchTeam("gym-1").first { it.id == "coach-1" }.role)
        repo.signedInUID = "coach-1"
        assertEquals(UserRole.OWNER, repo.fetchMyGyms().first { it.first.id == "gym-1" }.second)
    }

    @Test
    fun registerTeamMember_createsANewAccount_andAddsThemToTheTeam() = runTest {
        val repo = FakeBackendRepository()

        repo.registerTeamMember("gym-1", "Sam", "Lee", "sam@example.com", "password123", UserRole.COACH)

        val team = repo.fetchTeam("gym-1")
        assertEquals(listOf("Sam Lee"), team.map { it.fullName })
        assertEquals(UserRole.COACH, team.first().role)
    }

    @Test
    fun registerMember_createsANewAccount_andAddsThemAsAMember() = runTest {
        val repo = FakeBackendRepository()

        repo.registerMember("gym-1", "Dana", "Cohen", "dana@example.com", "password123")

        val members = repo.fetchGymMembers("gym-1")
        assertEquals(listOf("Dana Cohen"), members.map { it.fullName })
    }

    // MARK: - Manage tab: gym members

    @Test
    fun fetchGymMembers_returnsSeededMembers() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGymMember("gym-1", GymMember(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))

        val members = repo.fetchGymMembers("gym-1")

        assertEquals(1, members.size)
        assertEquals("Dana Cohen", members[0].fullName)
    }

    @Test
    fun addMember_attachesAnExistingPlatformUserAsAMember() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.OWNER, "owner-1")
        repo.seedProfile("user-1", Member(id = "user-1", fullName = "Dana Cohen", email = "dana@example.com"))

        repo.addMember("gym-1", "dana@example.com")

        val members = repo.fetchGymMembers("gym-1")
        assertEquals(listOf("Dana Cohen"), members.map { it.fullName })

        repo.signedInUID = "user-1"
        assertEquals(UserRole.MEMBER, repo.fetchMyGyms().first { it.first.id == "gym-1" }.second)
    }

    @Test
    fun addMember_throwsUserNotFound_whenNoAccountMatchesTheEmail() = runTest {
        val repo = FakeBackendRepository()

        try {
            repo.addMember("gym-1", "nobody@example.com")
            fail("Expected BackendException.UserNotFound")
        } catch (e: BackendException.UserNotFound) {
            // expected
        }
    }

    @Test
    fun removeMember_removesMembershipAndWallet() = runTest {
        val repo = FakeBackendRepository()
        repo.seedProfile("user-1", Member(id = "user-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.addMember("gym-1", "dana@example.com")
        repo.seedActivePlan("gym-1", "user-1", ActivePlanItem(id = "plan-1", planName = "Unlimited", type = PlanComponentType.UNLIMITED, expiresAtMillis = Long.MAX_VALUE / 2))

        repo.removeMember("gym-1", "user-1")

        assertTrue(repo.fetchGymMembers("gym-1").isEmpty())
        assertTrue(repo.fetchActivePlans("gym-1", "user-1").isEmpty())
    }

    // MARK: - Credit wallet grant/revoke (Manage tab: member detail)

    @Test
    fun grantPlanToMember_addsAnActivePlanItem_withComputedExpiry() = runTest {
        val repo = FakeBackendRepository()
        val plan = MembershipPlan(id = "plan-1", name = "10 Credits", price = 50.0, components = listOf(PlanComponent(type = PlanComponentType.CREDITS, creditCount = 10)))

        repo.grantPlanToMember("gym-1", "member-1", plan)

        val items = repo.fetchActivePlans("gym-1", "member-1")
        assertEquals(1, items.size)
        assertEquals("10 Credits", items.first().planName)
        assertEquals(10, items.first().remainingCredits)
        assertTrue(items.first().expiresAtMillis > System.currentTimeMillis())
    }

    @Test
    fun grantPlanToMember_unlimitedPlan_hasZeroRemainingCredits() = runTest {
        val repo = FakeBackendRepository()
        val plan = MembershipPlan(id = "plan-1", name = "Unlimited", price = 99.0, components = listOf(PlanComponent(type = PlanComponentType.UNLIMITED)))

        repo.grantPlanToMember("gym-1", "member-1", plan)

        assertEquals(0, repo.fetchActivePlans("gym-1", "member-1").first().remainingCredits)
    }

    @Test
    fun grantPlanToMember_multiComponentPlan_addsOneWalletItemPerComponent() = runTest {
        val repo = FakeBackendRepository()
        val plan = MembershipPlan(
            id = "plan-1",
            name = "Gold Membership",
            price = 150.0,
            components = listOf(
                PlanComponent(type = PlanComponentType.UNLIMITED, workoutType = "CrossFit WOD"),
                PlanComponent(type = PlanComponentType.CREDITS, workoutType = "Pilates", creditCount = 4)
            )
        )

        repo.grantPlanToMember("gym-1", "member-1", plan)

        val items = repo.fetchActivePlans("gym-1", "member-1")
        assertEquals(2, items.size)
        val unlimitedItem = items.first { it.type == PlanComponentType.UNLIMITED }
        assertEquals("CrossFit WOD", unlimitedItem.workoutType)
        val creditsItem = items.first { it.type == PlanComponentType.CREDITS }
        assertEquals("Pilates", creditsItem.workoutType)
        assertEquals(4, creditsItem.remainingCredits)
    }

    @Test
    fun grantPlanToMember_withCustomExpiresAtMillis_overridesComputedExpiryForEveryComponent() = runTest {
        val repo = FakeBackendRepository()
        val plan = MembershipPlan(
            id = "plan-1",
            name = "Gold Membership",
            price = 150.0,
            components = listOf(
                PlanComponent(type = PlanComponentType.UNLIMITED, validityValue = 1, validityUnit = ValidityUnit.MONTHS),
                PlanComponent(type = PlanComponentType.CREDITS, creditCount = 4, validityValue = 2, validityUnit = ValidityUnit.WEEKS)
            )
        )
        val customExpiresAt = System.currentTimeMillis() + 86_400_000L * 60

        repo.grantPlanToMember("gym-1", "member-1", plan, customExpiresAtMillis = customExpiresAt)

        val items = repo.fetchActivePlans("gym-1", "member-1")
        assertEquals(2, items.size)
        items.forEach { assertEquals(customExpiresAt, it.expiresAtMillis) }
    }

    @Test
    fun grantPlanToMember_monthlyResetComponent_hasZeroRemainingCredits_andFullCreditCount() = runTest {
        val repo = FakeBackendRepository()
        val plan = MembershipPlan(
            id = "plan-1",
            name = "Gold Membership",
            price = 150.0,
            components = listOf(PlanComponent(type = PlanComponentType.CREDITS, resetPeriod = PlanResetPeriod.MONTHLY, creditCount = 12))
        )

        repo.grantPlanToMember("gym-1", "member-1", plan)

        val item = repo.fetchActivePlans("gym-1", "member-1").first()
        assertEquals(PlanResetPeriod.MONTHLY, item.resetPeriod)
        assertEquals(12, item.creditCount)
        assertEquals(0, item.remainingCredits)
        assertEquals(0, item.cycleCreditsUsed)
        assertEquals(0, item.lastCycleIndex)
    }

    @Test
    fun bookClass_withMonthlyResetCredits_incrementsCycleCreditsUsed_notRemainingCredits() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0)
        repo.seedClass("gym-1", gymClass)
        repo.seedActivePlan(
            "gym-1", "member-1",
            ActivePlanItem(id = "plan-1", planName = "Gold", type = PlanComponentType.CREDITS, resetPeriod = PlanResetPeriod.MONTHLY, creditCount = 12, expiresAtMillis = Long.MAX_VALUE / 2)
        )

        repo.bookClass("gym-1", "class-1")

        val item = repo.fetchActivePlans("gym-1", "member-1").first()
        assertEquals(1, item.cycleCreditsUsed)
        assertEquals(0, item.remainingCredits)
        assertEquals(11, item.availableCredits())
    }

    @Test
    fun bookClass_withMonthlyResetCredits_throwsInsufficientCredits_onceFullyUsedInTheCurrentCycle() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"
        val class1 = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0)
        val class2 = GymClass(id = "class-2", title = "WOD 2", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0)
        repo.seedClass("gym-1", class1)
        repo.seedClass("gym-1", class2)
        repo.seedActivePlan(
            "gym-1", "member-1",
            ActivePlanItem(id = "plan-1", planName = "Gold", type = PlanComponentType.CREDITS, resetPeriod = PlanResetPeriod.MONTHLY, creditCount = 1, expiresAtMillis = Long.MAX_VALUE / 2)
        )

        repo.bookClass("gym-1", "class-1")

        try {
            repo.bookClass("gym-1", "class-2")
            fail("Expected BackendException.InsufficientCredits")
        } catch (e: BackendException.InsufficientCredits) {
            // expected
        }
    }

    @Test
    fun cancelBooking_withMonthlyResetCredits_decrementsCycleCreditsUsed() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0)
        repo.seedClass("gym-1", gymClass)
        repo.seedActivePlan(
            "gym-1", "member-1",
            ActivePlanItem(id = "plan-1", planName = "Gold", type = PlanComponentType.CREDITS, resetPeriod = PlanResetPeriod.MONTHLY, creditCount = 12, expiresAtMillis = Long.MAX_VALUE / 2)
        )
        repo.bookClass("gym-1", "class-1")

        repo.cancelBooking("gym-1", "class-1")

        val item = repo.fetchActivePlans("gym-1", "member-1").first()
        assertEquals(0, item.cycleCreditsUsed)
        assertEquals(12, item.availableCredits())
    }

    @Test
    fun revokeActivePlan_removesTheItemFromTheWallet() = runTest {
        val repo = FakeBackendRepository()
        repo.seedActivePlan("gym-1", "member-1", ActivePlanItem(id = "plan-1", planName = "Unlimited", type = PlanComponentType.UNLIMITED, expiresAtMillis = Long.MAX_VALUE / 2))

        repo.revokeActivePlan("gym-1", "member-1", "plan-1")

        assertTrue(repo.fetchActivePlans("gym-1", "member-1").isEmpty())
    }

    // MARK: - Staff-initiated booking management

    @Test
    fun fetchMemberBookings_returnsTheGivenUsersBookedClasses() = runTest {
        val repo = FakeBackendRepository()
        val classA = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0)
        val classB = GymClass(id = "class-2", title = "Yoga", coach = "Sam", startTimeMillis = 0L, capacity = 5, currentAttendees = 0)
        repo.seedClass("gym-1", classA)
        repo.seedClass("gym-1", classB)
        repo.seedBooking("gym-1", "class-1", "member-1")
        repo.seedBooking("gym-1", "class-2", "member-2")

        val bookings = repo.fetchMemberBookings("gym-1", "member-1")

        assertEquals(listOf("class-1"), bookings.map { it.id })
    }

    @Test
    fun cancelBooking_onBehalfOf_cancelsTheGivenUsersBooking() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 1)
        repo.seedClass("gym-1", gymClass)
        repo.seedBooking("gym-1", "class-1", "member-1")

        repo.cancelBooking("gym-1", "class-1", onBehalfOf = "member-1")

        assertEquals(emptyList<GymClass>(), repo.fetchMemberBookings("gym-1", "member-1"))
        assertEquals(0, repo.fetchClasses("gym-1").first { it.id == "class-1" }.currentAttendees)
    }

    // MARK: - Manage tab: membership plans

    @Test
    fun createMembershipPlan_thenFetchMembershipPlans_returnsIt() = runTest {
        val repo = FakeBackendRepository()
        val plan = MembershipPlan(id = "plan-1", name = "Unlimited Monthly", price = 99.0, components = listOf(PlanComponent(type = PlanComponentType.UNLIMITED)))

        repo.createMembershipPlan("gym-1", plan)

        assertEquals(listOf(plan), repo.fetchMembershipPlans("gym-1"))
    }

    @Test
    fun deleteMembershipPlan_removesItFromFetchMembershipPlans() = runTest {
        val repo = FakeBackendRepository()
        val plan = MembershipPlan(id = "plan-1", name = "10 Credits", price = 50.0, components = listOf(PlanComponent(type = PlanComponentType.CREDITS, creditCount = 10)))
        repo.seedMembershipPlan("gym-1", plan)

        repo.deleteMembershipPlan("gym-1", "plan-1")

        assertTrue(repo.fetchMembershipPlans("gym-1").isEmpty())
    }

    @Test
    fun updateMembershipPlan_overwritesTheExistingPlan_keepingItsId() = runTest {
        val repo = FakeBackendRepository()
        val original = MembershipPlan(id = "plan-1", name = "10 Credits", price = 50.0, components = listOf(PlanComponent(type = PlanComponentType.CREDITS, creditCount = 10)))
        repo.seedMembershipPlan("gym-1", original)

        val updated = original.copy(name = "20 Credits", price = 90.0, components = listOf(original.components.first().copy(creditCount = 20)))
        repo.updateMembershipPlan("gym-1", updated)

        val plans = repo.fetchMembershipPlans("gym-1")
        assertEquals(listOf("plan-1"), plans.map { it.id })
        assertEquals(updated, plans.first())
    }

    // MARK: - Manage tab: gym settings

    @Test
    fun updateGymSettings_updatesNameAndWorkoutTypes() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Old Name", ownerUID = "owner-1"), UserRole.OWNER, "owner-1")
        repo.signedInUID = "owner-1"

        repo.updateGymSettings("gym-1", "New Name", listOf("Yoga", "Pilates"))

        val gym = repo.fetchMyGyms().first { it.first.id == "gym-1" }.first
        assertEquals("New Name", gym.name)
        assertEquals(listOf("Yoga", "Pilates"), gym.workoutTypes)
    }

    // MARK: - Class creation, editing, deletion

    @Test
    fun createClass_thenFetchClasses_returnsIt() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "CrossFit WOD", coach = "Alex", startTimeMillis = 0L, capacity = 12, currentAttendees = 0)

        repo.createClass("gym-1", gymClass)

        assertEquals(listOf(gymClass), repo.fetchClasses("gym-1"))
    }

    @Test
    fun createClasses_createsEveryInstance() = runTest {
        val repo = FakeBackendRepository()
        val instances = listOf(
            GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 12, currentAttendees = 0, seriesId = "series-1"),
            GymClass(id = "class-2", title = "WOD", coach = "Alex", startTimeMillis = 86_400_000L, capacity = 12, currentAttendees = 0, seriesId = "series-1")
        )

        repo.createClasses("gym-1", instances)

        assertEquals(2, repo.fetchClasses("gym-1").size)
    }

    @Test
    fun updateClass_overwritesTheExistingClass() = runTest {
        val repo = FakeBackendRepository()
        val original = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 12, currentAttendees = 0)
        repo.seedClass("gym-1", original)

        repo.updateClass("gym-1", original.copy(coach = "Sam", capacity = 20))

        val updated = repo.fetchClasses("gym-1").first { it.id == "class-1" }
        assertEquals("Sam", updated.coach)
        assertEquals(20, updated.capacity)
    }

    @Test
    fun deleteClass_removesIt() = runTest {
        val repo = FakeBackendRepository()
        repo.seedClass("gym-1", GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 12, currentAttendees = 0))

        repo.deleteClass("gym-1", "class-1")

        assertTrue(repo.fetchClasses("gym-1").isEmpty())
    }

    @Test
    fun updateClassSeries_updatesOnlyOccurrencesOnOrAfterFromDate_keepsEachOccurrencesOwnDate() = runTest {
        val repo = FakeBackendRepository()
        val zone = java.time.ZoneId.systemDefault()
        fun millisAt(day: Int, hour: Int) = java.time.LocalDate.of(2026, 8, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

        val past = GymClass(id = "past", title = "WOD", coach = "Alex", startTimeMillis = millisAt(1, 9), capacity = 12, currentAttendees = 3, seriesId = "series-1")
        val futureA = GymClass(id = "future-a", title = "WOD", coach = "Alex", startTimeMillis = millisAt(10, 9), capacity = 12, currentAttendees = 5, seriesId = "series-1")
        val futureB = GymClass(id = "future-b", title = "WOD", coach = "Alex", startTimeMillis = millisAt(17, 9), capacity = 12, currentAttendees = 0, seriesId = "series-1")
        listOf(past, futureA, futureB).forEach { repo.seedClass("gym-1", it) }

        // Same day as future-a, but a different time (18:00 instead of 9:00) — the
        // edited time-of-day should carry over to future-b too, while future-b keeps its own date (Aug 17).
        val template = futureA.copy(coach = "Jordan", capacity = 25, startTimeMillis = millisAt(10, 18))
        repo.updateClassSeries("gym-1", "series-1", fromDateMillis = millisAt(10, 0), updatedTemplate = template)

        val classes = repo.fetchClasses("gym-1").associateBy { it.id }
        assertEquals("Alex", classes.getValue("past").coach) // before fromDateMillis — untouched
        assertEquals("Jordan", classes.getValue("future-a").coach)
        assertEquals("Jordan", classes.getValue("future-b").coach)
        assertEquals(25, classes.getValue("future-a").capacity)

        // Each occurrence keeps its own date; only the time-of-day shifts to the template's.
        val futureBStart = java.time.Instant.ofEpochMilli(classes.getValue("future-b").startTimeMillis).atZone(zone)
        assertEquals(java.time.LocalDate.of(2026, 8, 17), futureBStart.toLocalDate())
        assertEquals(18, futureBStart.hour)

        // currentAttendees stays per-occurrence roster state, not copied from the template.
        assertEquals(5, classes.getValue("future-a").currentAttendees)
        assertEquals(0, classes.getValue("future-b").currentAttendees)
    }

    @Test
    fun deleteClassSeries_deletesOnlyOccurrencesOnOrAfterFromDate() = runTest {
        val repo = FakeBackendRepository()
        val past = GymClass(id = "past", title = "WOD", coach = "Alex", startTimeMillis = 1_000_000L, capacity = 12, currentAttendees = 0, seriesId = "series-1")
        val future = GymClass(id = "future", title = "WOD", coach = "Alex", startTimeMillis = 2_000_000L, capacity = 12, currentAttendees = 0, seriesId = "series-1")
        listOf(past, future).forEach { repo.seedClass("gym-1", it) }

        repo.deleteClassSeries("gym-1", "series-1", fromDateMillis = 2_000_000L)

        val remaining = repo.fetchClasses("gym-1").map { it.id }
        assertEquals(listOf("past"), remaining)
    }

    // MARK: - observeClasses (live updates)

    @Test
    fun observeClasses_emitsTheCurrentlySeededClasses_immediately() = runTest {
        val repo = FakeBackendRepository()
        repo.seedClass("gym-1", GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0))

        val emitted = repo.observeClasses("gym-1").first()

        assertEquals(listOf("class-1"), emitted.map { it.id })
    }

    @Test
    fun observeClasses_isEmpty_forAGymWithNoClasses() = runTest {
        val repo = FakeBackendRepository()

        assertTrue(repo.observeClasses("gym-1").first().isEmpty())
    }

    @Test
    fun observeClasses_reflectsABookingsAttendeeCountChange_live() = runTest {
        val repo = FakeBackendRepository()
        repo.seedClass("gym-1", GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0))
        repo.seedActivePlan("gym-1", "member-1", ActivePlanItem(id = "plan-1", planName = "Unlimited", type = PlanComponentType.UNLIMITED, expiresAtMillis = Long.MAX_VALUE / 2))
        repo.signedInUID = "member-1"
        assertEquals(0, repo.observeClasses("gym-1").first().first { it.id == "class-1" }.currentAttendees)

        repo.bookClass("gym-1", "class-1")

        assertEquals(1, repo.observeClasses("gym-1").first().first { it.id == "class-1" }.currentAttendees)
    }

    @Test
    fun observeClasses_includesAClassCreatedAfterTheFirstSubscription() = runTest {
        val repo = FakeBackendRepository()
        assertTrue(repo.observeClasses("gym-1").first().isEmpty())

        repo.createClass("gym-1", GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0))

        assertEquals(listOf("class-1"), repo.observeClasses("gym-1").first().map { it.id })
    }

    @Test
    fun observeClasses_dropsAClassDeletedAfterTheFirstSubscription() = runTest {
        val repo = FakeBackendRepository()
        repo.seedClass("gym-1", GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 5, currentAttendees = 0))
        assertEquals(listOf("class-1"), repo.observeClasses("gym-1").first().map { it.id })

        repo.deleteClass("gym-1", "class-1")

        assertTrue(repo.observeClasses("gym-1").first().isEmpty())
    }

    // MARK: - Copy Schedule

    @Test
    fun copySchedule_duplicatesEachClass_atTheSameOffsetInTheTargetWeek() = runTest {
        val repo = FakeBackendRepository()
        val zone = ZoneId.systemDefault()
        // Source week: Monday Aug 17 2026. Tuesday 09:00 WOD.
        val sourceStart = LocalDate.of(2026, 8, 18).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val source = GymClass(id = "source-1", title = "WOD", coach = "Alex", startTimeMillis = sourceStart, capacity = 12, currentAttendees = 5, waitlistCount = 2, seriesId = "series-1")
        repo.seedClass("gym-1", source)

        // Copy to the week of Monday Aug 24 2026 (any day within that week works).
        val targetWeekOf = LocalDate.of(2026, 8, 26).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        repo.copySchedule("gym-1", fromWeekOfMillis = sourceStart, toWeekOfMillis = targetWeekOf)

        val copies = repo.fetchClasses("gym-1").filter { it.id != "source-1" }
        assertEquals(1, copies.size)
        val copy = copies.first()
        assertEquals("WOD", copy.title)
        assertEquals(12, copy.capacity)
        assertEquals(0, copy.currentAttendees) // reset, not carried over
        assertEquals(0, copy.waitlistCount) // reset, not carried over
        assertNull(copy.seriesId) // a copy is a standalone occurrence

        val expectedCopyStart = LocalDate.of(2026, 8, 25).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expectedCopyStart, copy.startTimeMillis)
    }

    @Test
    fun copySchedule_isANoOp_whenTheSourceWeekHasNoClasses() = runTest {
        val repo = FakeBackendRepository()
        val zone = ZoneId.systemDefault()
        val sourceWeekOf = LocalDate.of(2026, 8, 18).atStartOfDay(zone).toInstant().toEpochMilli()
        val targetWeekOf = LocalDate.of(2026, 8, 25).atStartOfDay(zone).toInstant().toEpochMilli()

        repo.copySchedule("gym-1", fromWeekOfMillis = sourceWeekOf, toWeekOfMillis = targetWeekOf)

        assertTrue(repo.fetchClasses("gym-1").isEmpty())
    }

    @Test
    fun copySchedule_ignoresClassesOutsideTheSourceWeek() = runTest {
        val repo = FakeBackendRepository()
        val zone = ZoneId.systemDefault()
        // Monday Aug 17 2026 is the source week; seed a class the week before and after it.
        val before = GymClass(id = "before", title = "WOD", coach = "Alex", startTimeMillis = LocalDate.of(2026, 8, 10).atTime(9, 0).atZone(zone).toInstant().toEpochMilli(), capacity = 12, currentAttendees = 0)
        val after = GymClass(id = "after", title = "WOD", coach = "Alex", startTimeMillis = LocalDate.of(2026, 8, 24).atTime(9, 0).atZone(zone).toInstant().toEpochMilli(), capacity = 12, currentAttendees = 0)
        repo.seedClass("gym-1", before)
        repo.seedClass("gym-1", after)

        val sourceWeekOf = LocalDate.of(2026, 8, 18).atStartOfDay(zone).toInstant().toEpochMilli()
        val targetWeekOf = LocalDate.of(2026, 9, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        repo.copySchedule("gym-1", fromWeekOfMillis = sourceWeekOf, toWeekOfMillis = targetWeekOf)

        assertEquals(setOf("before", "after"), repo.fetchClasses("gym-1").map { it.id }.toSet())
    }

    // MARK: - Waitlist

    @Test
    fun joinWaitlist_incrementsWaitlistCount_andIsIdempotent() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = System.currentTimeMillis() + 3_600_000, capacity = 1, currentAttendees = 1)
        repo.seedClass("gym-1", gymClass)
        repo.signedInUID = "waiting-user"

        repo.joinWaitlist("gym-1", "class-1")
        repo.joinWaitlist("gym-1", "class-1") // double-join must not double-count

        val updated = repo.fetchClasses("gym-1").first { it.id == "class-1" }
        assertEquals(1, updated.waitlistCount)
        assertTrue(repo.fetchMyWaitlistedClassIds("gym-1").contains("class-1"))
    }

    @Test
    fun joinWaitlist_throwsClassInPast_forAClassThatAlreadyStarted() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = System.currentTimeMillis() - 3_600_000, capacity = 1, currentAttendees = 1)
        repo.seedClass("gym-1", gymClass)
        repo.signedInUID = "waiting-user"

        try {
            repo.joinWaitlist("gym-1", "class-1")
            fail("Expected BackendException.ClassInPast")
        } catch (e: BackendException.ClassInPast) {
            // expected
        }
    }

    @Test
    fun leaveWaitlist_decrementsWaitlistCount() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = System.currentTimeMillis() + 3_600_000, capacity = 1, currentAttendees = 1)
        repo.seedClass("gym-1", gymClass)
        repo.signedInUID = "waiting-user"
        repo.joinWaitlist("gym-1", "class-1")

        repo.leaveWaitlist("gym-1", "class-1")

        val updated = repo.fetchClasses("gym-1").first { it.id == "class-1" }
        assertEquals(0, updated.waitlistCount)
        assertTrue(repo.fetchMyWaitlistedClassIds("gym-1").isEmpty())
    }

    @Test
    fun fetchWaitlistPosition_reportsOneBasedPosition_orderedByJoinTime() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = System.currentTimeMillis() + 3_600_000, capacity = 1, currentAttendees = 1)
        repo.seedClass("gym-1", gymClass)

        repo.signedInUID = "waiting-user-1"
        repo.joinWaitlist("gym-1", "class-1")
        repo.signedInUID = "waiting-user-2"
        repo.joinWaitlist("gym-1", "class-1")
        repo.signedInUID = "waiting-user-3"
        repo.joinWaitlist("gym-1", "class-1")

        repo.signedInUID = "waiting-user-2"
        assertEquals(2, repo.fetchWaitlistPosition("gym-1", "class-1"))
    }

    @Test
    fun fetchWaitlistPosition_returnsNull_whenNotWaitlisted() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = System.currentTimeMillis() + 3_600_000, capacity = 1, currentAttendees = 1)
        repo.seedClass("gym-1", gymClass)
        repo.signedInUID = "someone-else"

        assertNull(repo.fetchWaitlistPosition("gym-1", "class-1"))
    }

    @Test
    fun cancelBooking_promotesFirstWaitingUser_andKeepsAttendanceFull() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "Morning HIIT", coach = "Alex", startTimeMillis = System.currentTimeMillis() + 3_600_000, capacity = 1, currentAttendees = 0)
        repo.seedClass("gym-1", gymClass)

        repo.signedInUID = "attendee-1"
        repo.grantUnlimitedPlan("gym-1", "attendee-1")
        repo.bookClass("gym-1", "class-1") // fills the only spot

        repo.signedInUID = "waiting-user-1"
        repo.joinWaitlist("gym-1", "class-1")
        repo.signedInUID = "waiting-user-2"
        repo.joinWaitlist("gym-1", "class-1")

        // The original attendee cancels, which should promote waiting-user-1 (first in line).
        repo.signedInUID = "attendee-1"
        repo.cancelBooking("gym-1", "class-1")

        val updated = repo.fetchClasses("gym-1").first { it.id == "class-1" }
        assertEquals(1, updated.waitlistCount) // only waiting-user-2 remains
        assertEquals(1, updated.currentAttendees) // promoted user takes the freed spot — attendance stays full

        assertTrue(repo.fetchMyBookedClassIds("gym-1").isEmpty()) // attendee-1 no longer booked (still signed in as them)

        repo.signedInUID = "waiting-user-1"
        assertTrue(repo.fetchMyBookedClassIds("gym-1").contains("class-1"))

        repo.signedInUID = "waiting-user-2"
        assertTrue(repo.fetchMyWaitlistedClassIds("gym-1").contains("class-1"))
    }

    @Test
    fun cancelBooking_withNoWaitlist_justDecrementsCurrentAttendees() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = System.currentTimeMillis() + 3_600_000, capacity = 5, currentAttendees = 0)
        repo.seedClass("gym-1", gymClass)
        repo.signedInUID = "attendee-1"
        repo.grantUnlimitedPlan("gym-1", "attendee-1")
        repo.bookClass("gym-1", "class-1")

        repo.cancelBooking("gym-1", "class-1")

        val updated = repo.fetchClasses("gym-1").first { it.id == "class-1" }
        assertEquals(0, updated.currentAttendees)
        assertEquals(0, updated.waitlistCount)
    }

    // MARK: - Platform Admin Dashboard

    @Test
    fun fetchAllUsers_returnsSeededPlatformUsers() = runTest {
        val repo = FakeBackendRepository()
        repo.seedPlatformUser(PlatformUser(id = "user-1", firstName = "Dana", lastName = "Cohen", email = "dana@example.com"))

        assertEquals(1, repo.fetchAllUsers().size)
    }

    @Test
    fun updatePlatformRole_updatesTheUsersRole() = runTest {
        val repo = FakeBackendRepository()
        repo.seedPlatformUser(PlatformUser(id = "user-1", firstName = "Dana", lastName = "Cohen", email = "dana@example.com"))

        repo.updatePlatformRole("user-1", PlatformRole.ADMIN)

        assertEquals(PlatformRole.ADMIN, repo.fetchAllUsers().first { it.id == "user-1" }.role)
    }

    @Test
    fun deleteGym_removesTheGymAndItsClasses() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.OWNER, "owner-1")
        repo.seedClass("gym-1", GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 12, currentAttendees = 0))

        repo.deleteGym("gym-1")

        repo.signedInUID = "owner-1"
        assertTrue(repo.fetchMyGyms().isEmpty())
        assertTrue(repo.fetchClasses("gym-1").isEmpty())
    }

    // MARK: - Admin-only gym creation & owner assignment

    @Test
    fun createGym_registersANewAccount_andMakesThemOwner_whenTheEmailIsUnregistered() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "admin-1"

        val gym = repo.createGym("Iron Temple", "Tel Aviv", listOf("CrossFit"), "Dana", "Cohen", "dana@example.com", "password123")

        assertEquals(UserRole.OWNER, repo.fetchTeam(gym.id).first().role)
        assertEquals("Dana Cohen", repo.fetchTeam(gym.id).first().fullName)
        assertEquals("Tel Aviv", gym.city)
        assertEquals(listOf("CrossFit"), gym.workoutTypes)
    }

    @Test
    fun createGym_reusesAnExistingAccount_insteadOfRegisteringADuplicate() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "admin-1"
        repo.seedProfile("existing-owner", Member(id = "existing-owner", fullName = "Sam Lee", email = "sam@example.com"))

        val gym = repo.createGym("Iron Temple", null, emptyList(), "Ignored", "Name", "sam@example.com", "password123")

        assertEquals("existing-owner", gym.ownerUID)
        assertEquals("Sam Lee", repo.fetchTeam(gym.id).first().fullName)
    }

    @Test
    fun createGym_isImmediatelyVisibleAndJoinableByTheAssignedOwner() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "admin-1"

        val gym = repo.createGym("Iron Temple", null, emptyList(), "Dana", "Cohen", "dana@example.com", "password123")

        assertTrue(repo.fetchAvailableGyms().any { it.id == gym.id })
        repo.signedInUID = gym.ownerUID
        assertEquals(UserRole.OWNER, repo.fetchMyGyms().first { it.first.id == gym.id }.second)
    }
}
