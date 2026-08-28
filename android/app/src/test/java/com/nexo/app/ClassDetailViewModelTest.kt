package com.nexo.app

import com.nexo.app.data.repository.FakeBackendRepository
import com.nexo.app.domain.model.ActivePlanItem
import com.nexo.app.domain.model.GymClass
import com.nexo.app.domain.model.Member
import com.nexo.app.domain.model.PlanComponentType
import com.nexo.app.ui.schedule.ClassDetailViewModel
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

@OptIn(ExperimentalCoroutinesApi::class)
class ClassDetailViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val now = System.currentTimeMillis()

    @Test
    fun loadsGymClass_andBookingStatus_onInit() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 10, currentAttendees = 1)
        repo.seedClass("gym-1", gymClass)
        repo.seedBooking("gym-1", "class-1", "member-1")
        repo.signedInUID = "member-1"

        val viewModel = ClassDetailViewModel(repo, "gym-1", "class-1")

        assertEquals("class-1", viewModel.uiState.value.gymClass?.id)
        assertTrue(viewModel.uiState.value.isBooked)
    }

    @Test
    fun isBooked_isFalse_whenTheSignedInUserHasNotBookedThisClass() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 10, currentAttendees = 0)
        repo.seedClass("gym-1", gymClass)
        repo.signedInUID = "member-1"

        val viewModel = ClassDetailViewModel(repo, "gym-1", "class-1")

        assertEquals(false, viewModel.uiState.value.isBooked)
    }

    @Test
    fun loadAttendees_populatesAttendees_andClearsLoadingFlag() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 10, currentAttendees = 1)
        repo.seedClass("gym-1", gymClass)
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.seedBooking("gym-1", "class-1", "member-1")
        repo.signedInUID = "member-1"

        val viewModel = ClassDetailViewModel(repo, "gym-1", "class-1")

        assertEquals(false, viewModel.uiState.value.isLoadingAttendees)
        assertEquals(listOf("Dana Cohen"), viewModel.uiState.value.attendees.map { it.fullName })
    }

    @Test
    fun book_optimisticallyMarksBooked_andIncrementsCurrentAttendees() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 10, currentAttendees = 0)
        repo.seedClass("gym-1", gymClass)
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.signedInUID = "member-1"
        repo.seedActivePlan("gym-1", "member-1", ActivePlanItem(id = "plan-1", planName = "Unlimited", type = PlanComponentType.UNLIMITED, expiresAtMillis = Long.MAX_VALUE / 2))

        val viewModel = ClassDetailViewModel(repo, "gym-1", "class-1")
        viewModel.book()

        val state = viewModel.uiState.value
        assertTrue(state.isBooked)
        assertEquals(1, state.gymClass?.currentAttendees)
        assertEquals(false, state.isActionInProgress)
    }

    @Test
    fun book_rollsBackOptimisticUpdate_whenRepositoryThrows() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 10, currentAttendees = 0)
        repo.seedClass("gym-1", gymClass)
        repo.signedInUID = "member-1"

        val viewModel = ClassDetailViewModel(repo, "gym-1", "class-1")
        repo.errorToThrow = RuntimeException("network error")
        viewModel.book()

        val state = viewModel.uiState.value
        assertEquals(false, state.isBooked)
        assertEquals(0, state.gymClass?.currentAttendees)
        assertTrue(state.errorMessage?.isNotBlank() == true)
    }

    @Test
    fun cancel_optimisticallyMarksNotBooked_andDecrementsCurrentAttendees() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 10, currentAttendees = 1)
        repo.seedClass("gym-1", gymClass)
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.seedBooking("gym-1", "class-1", "member-1")
        repo.signedInUID = "member-1"

        val viewModel = ClassDetailViewModel(repo, "gym-1", "class-1")
        viewModel.cancel()

        val state = viewModel.uiState.value
        assertEquals(false, state.isBooked)
        assertEquals(0, state.gymClass?.currentAttendees)
    }

    @Test
    fun cancel_rollsBackOptimisticUpdate_whenRepositoryThrows() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 10, currentAttendees = 1)
        repo.seedClass("gym-1", gymClass)
        repo.seedBooking("gym-1", "class-1", "member-1")
        repo.signedInUID = "member-1"

        val viewModel = ClassDetailViewModel(repo, "gym-1", "class-1")
        repo.errorToThrow = RuntimeException("network error")
        viewModel.cancel()

        val state = viewModel.uiState.value
        assertTrue(state.isBooked)
        assertEquals(1, state.gymClass?.currentAttendees)
        assertTrue(state.errorMessage?.isNotBlank() == true)
    }

    @Test
    fun book_surfacesErrorMessage_whenClassIsFull() = runTest {
        val repo = FakeBackendRepository()
        val fullClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 1, currentAttendees = 1)
        repo.seedClass("gym-1", fullClass)
        repo.signedInUID = "member-2"

        val viewModel = ClassDetailViewModel(repo, "gym-1", "class-1")
        viewModel.book()

        assertTrue(viewModel.uiState.value.errorMessage?.isNotBlank() == true)
        assertEquals(false, viewModel.uiState.value.isBooked)
    }

    @Test
    fun gymClass_isNull_whenTheClassIdDoesNotExist() = runTest {
        val repo = FakeBackendRepository()
        repo.signedInUID = "member-1"

        val viewModel = ClassDetailViewModel(repo, "gym-1", "does-not-exist")

        assertNull(viewModel.uiState.value.gymClass)
    }

    // MARK: - Waitlist

    @Test
    fun isWaitlisted_andPosition_reflectTheSignedInUsersWaitlistStatus() = runTest {
        val repo = FakeBackendRepository()
        val fullClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 1, currentAttendees = 1)
        repo.seedClass("gym-1", fullClass)
        repo.signedInUID = "waiting-user-1"
        repo.joinWaitlist("gym-1", "class-1")
        repo.signedInUID = "waiting-user-2"
        repo.joinWaitlist("gym-1", "class-1")

        val viewModel = ClassDetailViewModel(repo, "gym-1", "class-1")

        assertTrue(viewModel.uiState.value.isWaitlisted)
        assertEquals(2, viewModel.uiState.value.waitlistPosition)
    }

    @Test
    fun joinWaitlist_marksWaitlisted() = runTest {
        val repo = FakeBackendRepository()
        val fullClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 1, currentAttendees = 1)
        repo.seedClass("gym-1", fullClass)
        repo.signedInUID = "waiting-user"

        val viewModel = ClassDetailViewModel(repo, "gym-1", "class-1")
        viewModel.joinWaitlist()

        assertTrue(viewModel.uiState.value.isWaitlisted)
        assertEquals(1, viewModel.uiState.value.gymClass?.waitlistCount)
    }

    @Test
    fun joinWaitlist_rollsBackOptimisticUpdate_whenRepositoryThrows() = runTest {
        val repo = FakeBackendRepository()
        val fullClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 1, currentAttendees = 1)
        repo.seedClass("gym-1", fullClass)
        repo.signedInUID = "waiting-user"

        val viewModel = ClassDetailViewModel(repo, "gym-1", "class-1")
        repo.errorToThrow = RuntimeException("network error")
        viewModel.joinWaitlist()

        val state = viewModel.uiState.value
        assertEquals(false, state.isWaitlisted)
        assertEquals(0, state.gymClass?.waitlistCount)
        assertTrue(state.errorMessage?.isNotBlank() == true)
    }

    @Test
    fun leaveWaitlist_clearsWaitlistedState() = runTest {
        val repo = FakeBackendRepository()
        val fullClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 1, currentAttendees = 1)
        repo.seedClass("gym-1", fullClass)
        repo.signedInUID = "waiting-user"
        repo.joinWaitlist("gym-1", "class-1")

        val viewModel = ClassDetailViewModel(repo, "gym-1", "class-1")
        viewModel.leaveWaitlist()

        assertEquals(false, viewModel.uiState.value.isWaitlisted)
        assertEquals(0, viewModel.uiState.value.gymClass?.waitlistCount)
    }

    @Test
    fun leaveWaitlist_rollsBackOptimisticUpdate_whenRepositoryThrows() = runTest {
        val repo = FakeBackendRepository()
        val fullClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 1, currentAttendees = 1)
        repo.seedClass("gym-1", fullClass)
        repo.signedInUID = "waiting-user"
        repo.joinWaitlist("gym-1", "class-1")

        val viewModel = ClassDetailViewModel(repo, "gym-1", "class-1")
        repo.errorToThrow = RuntimeException("network error")
        viewModel.leaveWaitlist()

        val state = viewModel.uiState.value
        assertTrue(state.isWaitlisted)
        assertEquals(1, state.gymClass?.waitlistCount)
        assertTrue(state.errorMessage?.isNotBlank() == true)
    }

    // MARK: - Attendance check-in

    @Test
    fun toggleAttendance_marksMemberCheckedIn_optimistically() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 10, currentAttendees = 1)
        repo.seedClass("gym-1", gymClass)
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.seedBooking("gym-1", "class-1", "member-1")
        repo.signedInUID = "coach-1"

        val viewModel = ClassDetailViewModel(repo, "gym-1", "class-1")
        val member = viewModel.uiState.value.attendees.first()
        viewModel.toggleAttendance(member)

        val state = viewModel.uiState.value
        assertTrue(state.attendees.first().isCheckedIn)
        assertEquals(1, state.checkedInCount)
    }

    @Test
    fun toggleAttendance_undoesCheckIn_whenMemberIsAlreadyCheckedIn() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 10, currentAttendees = 1)
        repo.seedClass("gym-1", gymClass)
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.seedBooking("gym-1", "class-1", "member-1")
        repo.toggleAttendance("gym-1", "class-1", "member-1", isCheckedIn = true)
        repo.signedInUID = "coach-1"

        val viewModel = ClassDetailViewModel(repo, "gym-1", "class-1")
        val member = viewModel.uiState.value.attendees.first()
        viewModel.toggleAttendance(member)

        val state = viewModel.uiState.value
        assertEquals(false, state.attendees.first().isCheckedIn)
        assertEquals(0, state.checkedInCount)
    }

    @Test
    fun toggleAttendance_rollsBackOptimisticUpdate_whenRepositoryThrows() = runTest {
        val repo = FakeBackendRepository()
        val gymClass = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = now + 100_000, capacity = 10, currentAttendees = 1)
        repo.seedClass("gym-1", gymClass)
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        repo.seedBooking("gym-1", "class-1", "member-1")
        repo.signedInUID = "coach-1"

        val viewModel = ClassDetailViewModel(repo, "gym-1", "class-1")
        val member = viewModel.uiState.value.attendees.first()
        repo.errorToThrow = RuntimeException("network error")
        viewModel.toggleAttendance(member)

        val state = viewModel.uiState.value
        assertEquals(false, state.attendees.first().isCheckedIn)
        assertTrue(state.errorMessage?.isNotBlank() == true)
    }

    // MARK: - Delete

    @Test
    fun deleteClass_singleOccurrence_setsDidDelete() = runTest {
        val repo = FakeBackendRepository()
        repo.seedClass("gym-1", GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = now, capacity = 12, currentAttendees = 0))

        val viewModel = ClassDetailViewModel(repo, "gym-1", "class-1")
        viewModel.deleteClass(applyToSeries = false)

        assertTrue(viewModel.uiState.value.didDelete)
        assertTrue(repo.fetchClasses("gym-1").isEmpty())
    }

    @Test
    fun deleteClass_applyToSeries_deletesFutureSeriesOccurrencesToo() = runTest {
        val repo = FakeBackendRepository()
        val target = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 1_000_000L, capacity = 12, currentAttendees = 0, seriesId = "series-1")
        val future = GymClass(id = "class-2", title = "WOD", coach = "Alex", startTimeMillis = 2_000_000L, capacity = 12, currentAttendees = 0, seriesId = "series-1")
        repo.seedClass("gym-1", target)
        repo.seedClass("gym-1", future)

        val viewModel = ClassDetailViewModel(repo, "gym-1", "class-1")
        viewModel.deleteClass(applyToSeries = true)

        assertTrue(viewModel.uiState.value.didDelete)
        assertTrue(repo.fetchClasses("gym-1").isEmpty())
    }
}
