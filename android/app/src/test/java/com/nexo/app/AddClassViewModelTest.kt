package com.nexo.app

import com.nexo.app.data.repository.FakeBackendRepository
import com.nexo.app.domain.model.GymClass
import com.nexo.app.domain.model.RecurrenceType
import com.nexo.app.domain.model.TeamMember
import com.nexo.app.domain.model.UserRole
import com.nexo.app.ui.schedule.AddClassViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddClassViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsCoaches_filteredToManagementRoles_fromFetchTeam() = runTest {
        val repo = FakeBackendRepository()
        repo.seedTeamMember("gym-1", TeamMember(id = "coach-1", fullName = "Alex Kim", email = "alex@example.com", role = UserRole.COACH))
        repo.seedTeamMember("gym-1", TeamMember(id = "owner-1", fullName = "Jordan Lee", email = "jordan@example.com", role = UserRole.OWNER))

        val viewModel = AddClassViewModel(repo, "gym-1", listOf("CrossFit WOD", "Yoga"), existingClass = null)

        assertEquals(listOf("Alex Kim", "Jordan Lee"), viewModel.uiState.value.availableCoaches)
    }

    @Test
    fun save_createSingleClass_whenRecurrenceIsNone() = runTest {
        val repo = FakeBackendRepository()
        val viewModel = AddClassViewModel(repo, "gym-1", listOf("CrossFit WOD"), existingClass = null)
        viewModel.updateCoach("Alex")

        viewModel.save(applyToSeries = false)

        val classes = repo.fetchClasses("gym-1")
        assertEquals(1, classes.size)
        assertEquals("Alex", classes.first().coach)
        assertEquals(null, classes.first().seriesId)
        assertTrue(viewModel.uiState.value.didSave)
    }

    @Test
    fun save_createsOneInstancePerRecurrenceDate_sharingASeriesId() = runTest {
        val repo = FakeBackendRepository()
        val viewModel = AddClassViewModel(repo, "gym-1", listOf("CrossFit WOD"), existingClass = null)
        val start = viewModel.uiState.value.startTimeMillis
        viewModel.updateRecurrenceType(RecurrenceType.DAILY)
        viewModel.updateRepeatEndMillis(start + 3 * 86_400_000L) // 4 days inclusive

        viewModel.save(applyToSeries = false)

        val classes = repo.fetchClasses("gym-1")
        assertEquals(4, classes.size)
        val seriesIds = classes.mapNotNull { it.seriesId }.toSet()
        assertEquals(1, seriesIds.size) // all instances share one seriesId
    }

    @Test
    fun handleSaveTapped_showsSeriesPrompt_whenEditingAClassThatBelongsToASeries() = runTest {
        val repo = FakeBackendRepository()
        val existing = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 12, currentAttendees = 0, seriesId = "series-1")
        val viewModel = AddClassViewModel(repo, "gym-1", listOf("CrossFit WOD"), existingClass = existing)

        viewModel.handleSaveTapped()

        assertTrue(viewModel.uiState.value.showSeriesPrompt)
    }

    @Test
    fun handleSaveTapped_savesImmediately_whenEditingANonSeriesClass() = runTest {
        val repo = FakeBackendRepository()
        val existing = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 12, currentAttendees = 0)
        repo.seedClass("gym-1", existing)
        val viewModel = AddClassViewModel(repo, "gym-1", listOf("CrossFit WOD"), existingClass = existing)

        viewModel.handleSaveTapped()

        assertEquals(false, viewModel.uiState.value.showSeriesPrompt)
        assertTrue(viewModel.uiState.value.didSave)
    }

    @Test
    fun save_editSingleOccurrence_leavesOtherSeriesOccurrencesUntouched() = runTest {
        val repo = FakeBackendRepository()
        val target = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 0L, capacity = 12, currentAttendees = 0, seriesId = "series-1")
        val sibling = GymClass(id = "class-2", title = "WOD", coach = "Alex", startTimeMillis = 86_400_000L, capacity = 12, currentAttendees = 0, seriesId = "series-1")
        repo.seedClass("gym-1", target)
        repo.seedClass("gym-1", sibling)
        val viewModel = AddClassViewModel(repo, "gym-1", listOf("CrossFit WOD"), existingClass = target)
        viewModel.updateCoach("Sam")

        viewModel.save(applyToSeries = false)

        val classes = repo.fetchClasses("gym-1").associateBy { it.id }
        assertEquals("Sam", classes.getValue("class-1").coach)
        assertEquals("Alex", classes.getValue("class-2").coach) // untouched — not applied to series
    }

    @Test
    fun save_editWholeSeries_updatesFutureOccurrencesToo() = runTest {
        val repo = FakeBackendRepository()
        val target = GymClass(id = "class-1", title = "WOD", coach = "Alex", startTimeMillis = 1_000_000L, capacity = 12, currentAttendees = 0, seriesId = "series-1")
        val future = GymClass(id = "class-2", title = "WOD", coach = "Alex", startTimeMillis = 2_000_000L, capacity = 12, currentAttendees = 0, seriesId = "series-1")
        repo.seedClass("gym-1", target)
        repo.seedClass("gym-1", future)
        val viewModel = AddClassViewModel(repo, "gym-1", listOf("CrossFit WOD"), existingClass = target)
        viewModel.updateCoach("Sam")

        viewModel.save(applyToSeries = true)

        val classes = repo.fetchClasses("gym-1").associateBy { it.id }
        assertEquals("Sam", classes.getValue("class-1").coach)
        assertEquals("Sam", classes.getValue("class-2").coach)
    }
}
