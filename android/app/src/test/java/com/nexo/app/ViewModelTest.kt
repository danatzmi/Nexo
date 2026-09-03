package com.nexo.app

import com.nexo.app.data.repository.FakeBackendRepository
import com.nexo.app.domain.model.ActivePlanItem
import com.nexo.app.domain.model.Gym
import com.nexo.app.domain.model.GymClass
import com.nexo.app.domain.model.Member
import com.nexo.app.domain.model.PlanComponentType
import com.nexo.app.domain.model.UserRole
import com.nexo.app.ui.home.GymHomeViewModel
import com.nexo.app.ui.logbook.LogbookViewModel
import com.nexo.app.ui.profile.ProfileViewModel
import com.nexo.app.ui.schedule.ScheduleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Covers the date/time filtering logic in the Phase 2 screen ViewModels —
 * the kind of thing CLAUDE.md flags as easy to get subtly wrong (which
 * classes count as "upcoming" vs. "past" relative to `now`), and which
 * [FakeBackendRepositoryTest] doesn't exercise since it lives one layer
 * up, in the ViewModels rather than the repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val now = System.currentTimeMillis()
    private val pastClass = GymClass(id = "past", title = "Yesterday's WOD", coach = "Alex", startTimeMillis = now - 100_000, capacity = 10, currentAttendees = 1)
    private val futureNear = GymClass(id = "future-near", title = "Tomorrow AM", coach = "Alex", startTimeMillis = now + 100_000, capacity = 10, currentAttendees = 1)
    private val futureFar = GymClass(id = "future-far", title = "Next Week", coach = "Sam", startTimeMillis = now + 500_000, capacity = 10, currentAttendees = 1)
    private val unbookedFuture = GymClass(id = "unbooked", title = "Unbooked Class", coach = "Sam", startTimeMillis = now + 200_000, capacity = 10, currentAttendees = 0)

    private fun seedStandardGym(repo: FakeBackendRepository) {
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.seedActivePlan("gym-1", "member-1", ActivePlanItem(id = "plan-member-1", planName = "Unlimited", type = PlanComponentType.UNLIMITED, expiresAtMillis = Long.MAX_VALUE / 2))
        listOf(pastClass, futureNear, futureFar, unbookedFuture).forEach { repo.seedClass("gym-1", it) }
        repo.seedBooking("gym-1", pastClass.id, "member-1")
        repo.seedBooking("gym-1", futureNear.id, "member-1")
        repo.seedBooking("gym-1", futureFar.id, "member-1")
        repo.signedInUID = "member-1"
    }

    @Test
    fun gymHomeViewModel_nextBookedClass_isTheSoonestFutureBookedClass() = runTest {
        val repo = FakeBackendRepository()
        seedStandardGym(repo)

        val viewModel = GymHomeViewModel(repo, "gym-1")

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals("future-near", state.nextBookedClass?.id)
        assertEquals("Dana Cohen", state.userDisplayName)
        assertEquals("Iron Temple", state.gymName)
    }

    @Test
    fun gymHomeViewModel_activePlans_populatesFromRepository_withRemainingCredits() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.seedActivePlan("gym-1", "member-1", ActivePlanItem(id = "plan-1", planName = "10-Class Pack", type = PlanComponentType.CREDITS, remainingCredits = 4, expiresAtMillis = Long.MAX_VALUE / 2))
        repo.signedInUID = "member-1"

        val viewModel = GymHomeViewModel(repo, "gym-1")

        val plans = viewModel.uiState.value.activePlans
        assertEquals(listOf("plan-1"), plans.map { it.id })
        assertEquals(4, plans.first().remainingCredits)
    }

    @Test
    fun gymHomeViewModel_activePlans_isEmpty_whenTheMemberHasNoWallet() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.signedInUID = "member-1"

        val viewModel = GymHomeViewModel(repo, "gym-1")

        assertTrue(viewModel.uiState.value.activePlans.isEmpty())
    }

    @Test
    fun gymHomeViewModel_nextBookedClass_isNull_whenNoFutureBookings() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.seedClass("gym-1", pastClass)
        repo.seedBooking("gym-1", pastClass.id, "member-1")
        repo.signedInUID = "member-1"

        val viewModel = GymHomeViewModel(repo, "gym-1")

        assertNull(viewModel.uiState.value.nextBookedClass)
    }

    @Test
    fun profileViewModel_upcomingBookings_excludesPastAndUnbookedClasses_sortedBySoonestFirst() = runTest {
        val repo = FakeBackendRepository()
        seedStandardGym(repo)

        val viewModel = ProfileViewModel(repo, "gym-1")

        val upcoming = viewModel.uiState.value.upcomingBookings
        assertEquals(listOf("future-near", "future-far"), upcoming.map { it.id })
    }

    @Test
    fun logbookViewModel_activityTimeline_includesOnlyPastBookedClasses_sortedMostRecentFirst() = runTest {
        val repo = FakeBackendRepository()
        seedStandardGym(repo)
        val olderPast = pastClass.copy(id = "older-past", startTimeMillis = now - 500_000)
        repo.seedClass("gym-1", olderPast)
        repo.seedBooking("gym-1", olderPast.id, "member-1")

        val viewModel = LogbookViewModel(repo, "gym-1")

        val timeline = viewModel.uiState.value.activityTimeline
        assertEquals(listOf("past", "older-past"), timeline.map { it.id })
        assertEquals(2, viewModel.uiState.value.totalWorkouts)
    }

    @Test
    fun scheduleViewModel_book_movesClassIntoBookedIds_andShowsASuccessMessage() = runTest {
        val repo = FakeBackendRepository()
        seedStandardGym(repo)

        val viewModel = ScheduleViewModel(repo, "gym-1")
        viewModel.book(unbookedFuture.id)

        val state = viewModel.uiState.value
        assertEquals(true, unbookedFuture.id in state.bookedClassIds)
        assertEquals(1, state.allClasses.first { it.id == unbookedFuture.id }.currentAttendees)
        assertEquals("Booked!", state.successMessage?.title)
        assertEquals(false, state.successMessage?.isWaitlist)
    }

    @Test
    fun scheduleViewModel_cancel_removesClassFromBookedIds_andDecrementsAttendees() = runTest {
        val repo = FakeBackendRepository()
        seedStandardGym(repo)

        val viewModel = ScheduleViewModel(repo, "gym-1")
        viewModel.cancel(futureNear.id)

        val state = viewModel.uiState.value
        assertEquals(false, futureNear.id in state.bookedClassIds)
        assertEquals(0, state.allClasses.first { it.id == futureNear.id }.currentAttendees)
    }

    @Test
    fun scheduleViewModel_book_surfacesErrorMessage_whenClassIsFull() = runTest {
        val repo = FakeBackendRepository()
        val fullClass = GymClass(id = "full", title = "Packed WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 1, currentAttendees = 1)
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.seedClass("gym-1", fullClass)
        repo.signedInUID = "member-2"

        val viewModel = ScheduleViewModel(repo, "gym-1")
        viewModel.book("full")

        val state = viewModel.uiState.value
        assertEquals(true, state.errorMessage?.isNotBlank())
        // Never optimistic — a failed booking must never have shown as booked.
        assertEquals(false, "full" in state.bookedClassIds)
        assertEquals(1, state.allClasses.first { it.id == "full" }.currentAttendees)
        assertNull(state.successMessage)
    }

    @Test
    fun scheduleViewModel_book_rollsBackOptimisticUpdate_andClearsSuccessMessage_whenRepositoryThrows() = runTest {
        val repo = FakeBackendRepository()
        seedStandardGym(repo)

        val viewModel = ScheduleViewModel(repo, "gym-1")
        repo.errorToThrow = RuntimeException("network error")
        viewModel.book(unbookedFuture.id)

        val state = viewModel.uiState.value
        assertEquals(false, unbookedFuture.id in state.bookedClassIds)
        assertEquals(0, state.allClasses.first { it.id == unbookedFuture.id }.currentAttendees)
        assertNull(state.successMessage)
        assertEquals(true, state.errorMessage?.isNotBlank())
    }

    @Test
    fun scheduleViewModel_cancel_rollsBackOptimisticUpdate_whenRepositoryThrows() = runTest {
        val repo = FakeBackendRepository()
        seedStandardGym(repo)

        val viewModel = ScheduleViewModel(repo, "gym-1")
        repo.errorToThrow = RuntimeException("network error")
        viewModel.cancel(futureNear.id)

        val state = viewModel.uiState.value
        assertEquals(true, futureNear.id in state.bookedClassIds)
        assertEquals(1, state.allClasses.first { it.id == futureNear.id }.currentAttendees)
        assertEquals(true, state.errorMessage?.isNotBlank())
    }

    @Test
    fun scheduleViewModel_joinWaitlist_updatesWaitlistedIdsAndCount_andShowsASuccessMessage() = runTest {
        val repo = FakeBackendRepository()
        val fullClass = GymClass(id = "full", title = "Packed WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 1, currentAttendees = 1)
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.seedClass("gym-1", fullClass)
        repo.signedInUID = "member-1"

        val viewModel = ScheduleViewModel(repo, "gym-1")
        viewModel.joinWaitlist("full")

        val state = viewModel.uiState.value
        assertEquals(true, "full" in state.waitlistedClassIds)
        assertEquals(1, state.allClasses.first { it.id == "full" }.waitlistCount)
        assertEquals("Waitlisted!", state.successMessage?.title)
        assertEquals(true, state.successMessage?.isWaitlist)
    }

    @Test
    fun scheduleViewModel_joinWaitlist_rollsBackOptimisticUpdate_andClearsSuccessMessage_whenRepositoryThrows() = runTest {
        val repo = FakeBackendRepository()
        val fullClass = GymClass(id = "full", title = "Packed WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 1, currentAttendees = 1)
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.seedClass("gym-1", fullClass)
        repo.signedInUID = "member-1"

        val viewModel = ScheduleViewModel(repo, "gym-1")
        repo.errorToThrow = RuntimeException("network error")
        viewModel.joinWaitlist("full")

        val state = viewModel.uiState.value
        assertEquals(false, "full" in state.waitlistedClassIds)
        assertEquals(0, state.allClasses.first { it.id == "full" }.waitlistCount)
        assertNull(state.successMessage)
        assertEquals(true, state.errorMessage?.isNotBlank())
    }

    @Test
    fun scheduleViewModel_leaveWaitlist_clearsWaitlistedIdAndDecrementsCount() = runTest {
        val repo = FakeBackendRepository()
        val fullClass = GymClass(id = "full", title = "Packed WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 1, currentAttendees = 1)
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.seedClass("gym-1", fullClass)
        repo.signedInUID = "member-1"
        repo.joinWaitlist("gym-1", "full")

        val viewModel = ScheduleViewModel(repo, "gym-1")
        viewModel.leaveWaitlist("full")

        val state = viewModel.uiState.value
        assertEquals(false, "full" in state.waitlistedClassIds)
        assertEquals(0, state.allClasses.first { it.id == "full" }.waitlistCount)
    }

    @Test
    fun scheduleViewModel_leaveWaitlist_rollsBackOptimisticUpdate_whenRepositoryThrows() = runTest {
        val repo = FakeBackendRepository()
        val fullClass = GymClass(id = "full", title = "Packed WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 1, currentAttendees = 1)
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.seedClass("gym-1", fullClass)
        repo.signedInUID = "member-1"
        repo.joinWaitlist("gym-1", "full")

        val viewModel = ScheduleViewModel(repo, "gym-1")
        repo.errorToThrow = RuntimeException("network error")
        viewModel.leaveWaitlist("full")

        val state = viewModel.uiState.value
        assertEquals(true, "full" in state.waitlistedClassIds)
        assertEquals(1, state.allClasses.first { it.id == "full" }.waitlistCount)
        assertEquals(true, state.errorMessage?.isNotBlank())
    }

    // MARK: - Proactive plan/credit dimming

    @Test
    fun scheduleViewModel_bookingBlockedReason_isNull_withACoveringUnlimitedPlan() = runTest {
        val repo = FakeBackendRepository()
        seedStandardGym(repo)

        val viewModel = ScheduleViewModel(repo, "gym-1")

        assertNull(viewModel.uiState.value.bookingBlockedReason(unbookedFuture))
    }

    @Test
    fun scheduleViewModel_bookingBlockedReason_isNoActivePlan_withNoPlansAtAll() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.seedClass("gym-1", unbookedFuture)
        repo.signedInUID = "member-1"

        val viewModel = ScheduleViewModel(repo, "gym-1")

        assertEquals("No active plan", viewModel.uiState.value.bookingBlockedReason(unbookedFuture))
    }

    @Test
    fun scheduleViewModel_bookingBlockedReason_isNoCreditsRemaining_withAMatchingButExhaustedCreditPlan() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.seedClass("gym-1", unbookedFuture)
        repo.seedActivePlan(
            "gym-1", "member-1",
            ActivePlanItem(id = "plan-exhausted", planName = "10-Class Pass", type = PlanComponentType.CREDITS, creditCount = 10, remainingCredits = 0, expiresAtMillis = now + 1_000_000_000)
        )
        repo.signedInUID = "member-1"

        val viewModel = ScheduleViewModel(repo, "gym-1")

        assertEquals("No credits remaining", viewModel.uiState.value.bookingBlockedReason(unbookedFuture))
    }

    @Test
    fun scheduleViewModel_classesForSelectedDate_filtersToOnlyTheSelectedDay() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.signedInUID = "member-1"

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val classToday = GymClass(id = "today", title = "Today WOD", coach = "Alex", startTimeMillis = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(), capacity = 10, currentAttendees = 0)
        val classTomorrow = GymClass(id = "tomorrow", title = "Tomorrow WOD", coach = "Alex", startTimeMillis = today.plusDays(1).atTime(10, 0).atZone(zone).toInstant().toEpochMilli(), capacity = 10, currentAttendees = 0)
        repo.seedClass("gym-1", classToday)
        repo.seedClass("gym-1", classTomorrow)

        val viewModel = ScheduleViewModel(repo, "gym-1")

        assertEquals(listOf("today"), viewModel.uiState.value.classesForSelectedDate.map { it.id })

        viewModel.selectDate(today.plusDays(1))
        assertEquals(listOf("tomorrow"), viewModel.uiState.value.classesForSelectedDate.map { it.id })
    }

    @Test
    fun scheduleViewModel_weekDates_containsSelectedDate() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.signedInUID = "member-1"

        val viewModel = ScheduleViewModel(repo, "gym-1")

        assertTrue(viewModel.uiState.value.weekDates.contains(viewModel.uiState.value.selectedDate))
    }

    @Test
    fun scheduleViewModel_shiftWeek_movesSelectedDateBySevenDays() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.signedInUID = "member-1"

        val viewModel = ScheduleViewModel(repo, "gym-1")
        val initialDate = viewModel.uiState.value.selectedDate

        viewModel.shiftWeek(1)
        assertEquals(initialDate.plusWeeks(1), viewModel.uiState.value.selectedDate)

        viewModel.shiftWeek(-2)
        assertEquals(initialDate.minusWeeks(1), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun scheduleViewModel_refresh_updatesBookedStateFromExternalMutation() = runTest {
        val repo = FakeBackendRepository()
        seedStandardGym(repo)

        val viewModel = ScheduleViewModel(repo, "gym-1")
        assertEquals(false, unbookedFuture.id in viewModel.uiState.value.bookedClassIds)

        // Simulate booking from ClassDetailScreen in the background
        repo.bookClass("gym-1", unbookedFuture.id)

        // On resume back to ScheduleScreen — only bookedClassIds needs this;
        // allClasses is already live via observeClasses (see the dedicated
        // test below), refresh() just re-syncs this user's own booking status.
        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals(true, unbookedFuture.id in state.bookedClassIds)
        assertEquals(1, state.allClasses.first { it.id == unbookedFuture.id }.currentAttendees)
    }

    @Test
    fun scheduleViewModel_allClasses_updatesLive_withoutCallingRefresh() = runTest {
        val repo = FakeBackendRepository()
        seedStandardGym(repo)

        val viewModel = ScheduleViewModel(repo, "gym-1")
        assertEquals(0, viewModel.uiState.value.allClasses.first { it.id == unbookedFuture.id }.currentAttendees)

        // Simulate another client (e.g. a different device) booking this class —
        // no call to viewModel.refresh() here at all.
        repo.signedInUID = "other-member"
        repo.seedActivePlan("gym-1", "other-member", ActivePlanItem(id = "plan-other", planName = "Unlimited", type = PlanComponentType.UNLIMITED, expiresAtMillis = Long.MAX_VALUE / 2))
        repo.bookClass("gym-1", unbookedFuture.id)

        val updated = viewModel.uiState.value.allClasses.first { it.id == unbookedFuture.id }
        assertEquals(1, updated.currentAttendees)
    }

    @Test
    fun gymHomeViewModel_refresh_updatesNextBookingFromExternalMutation() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.seedActivePlan("gym-1", "member-1", ActivePlanItem(id = "plan-member-1", planName = "Unlimited", type = PlanComponentType.UNLIMITED, expiresAtMillis = Long.MAX_VALUE / 2))
        repo.seedClass("gym-1", unbookedFuture)
        repo.signedInUID = "member-1"

        val viewModel = GymHomeViewModel(repo, "gym-1")
        assertNull(viewModel.uiState.value.nextBookedClass)

        // Simulate booking from ClassDetailScreen
        repo.bookClass("gym-1", unbookedFuture.id)

        // On resume back to HomeScreen
        viewModel.refresh()

        assertEquals(unbookedFuture.id, viewModel.uiState.value.nextBookedClass?.id)
    }

    @Test
    fun profileViewModel_refresh_updatesUpcomingBookingsFromExternalMutation() = runTest {
        val repo = FakeBackendRepository()
        repo.seedGym(Gym(id = "gym-1", name = "Iron Temple", ownerUID = "owner-1"), UserRole.MEMBER, "member-1")
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.seedActivePlan("gym-1", "member-1", ActivePlanItem(id = "plan-member-1", planName = "Unlimited", type = PlanComponentType.UNLIMITED, expiresAtMillis = Long.MAX_VALUE / 2))
        repo.seedClass("gym-1", unbookedFuture)
        repo.signedInUID = "member-1"

        val viewModel = ProfileViewModel(repo, "gym-1")
        assertEquals(0, viewModel.uiState.value.upcomingBookings.size)

        // Simulate booking from ClassDetailScreen
        repo.bookClass("gym-1", unbookedFuture.id)

        // On resume back to ProfileScreen
        viewModel.refresh()

        assertEquals(listOf(unbookedFuture.id), viewModel.uiState.value.upcomingBookings.map { it.id })
    }
}
