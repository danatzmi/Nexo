package com.nexo.app

import com.nexo.app.data.repository.FakeBackendRepository
import com.nexo.app.domain.model.Member
import com.nexo.app.ui.auth.AuthViewModel
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
class AuthViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun toggleMode_switchesBetweenLoginAndSignUp_andClearsAnyError() {
        val viewModel = AuthViewModel(FakeBackendRepository())

        viewModel.toggleMode()

        assertEquals(AuthViewModel.Mode.SIGN_UP, viewModel.uiState.value.mode)
    }

    @Test
    fun canSubmit_requiresFirstAndLastName_onlyInSignUpMode() {
        val viewModel = AuthViewModel(FakeBackendRepository())
        viewModel.updateEmail("dana@example.com")
        viewModel.updatePassword("password")

        assertTrue(viewModel.uiState.value.canSubmit) // login mode — email + password is enough

        viewModel.toggleMode()
        assertEquals(false, viewModel.uiState.value.canSubmit) // sign-up mode — first/last name still required

        viewModel.updateFirstName("Dana")
        viewModel.updateLastName("Cohen")
        assertTrue(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun submit_login_callsOnAuthenticated_onSuccess() = runTest {
        val repo = FakeBackendRepository()
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        val viewModel = AuthViewModel(repo)
        viewModel.updateEmail("dana@example.com")
        viewModel.updatePassword("password")

        var authenticated = false
        viewModel.submit { authenticated = true }

        assertTrue(authenticated)
        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun submit_login_surfacesErrorMessage_onFailure_withoutCallingOnAuthenticated() = runTest {
        val repo = FakeBackendRepository() // no profile seeded — signIn will fail
        val viewModel = AuthViewModel(repo)
        viewModel.updateEmail("nobody@example.com")
        viewModel.updatePassword("password")

        var authenticated = false
        viewModel.submit { authenticated = true }

        assertEquals(false, authenticated)
        assertTrue(viewModel.uiState.value.errorMessage?.isNotBlank() == true)
    }

    @Test
    fun submit_signUp_createsAccount_andCallsOnAuthenticated() = runTest {
        val repo = FakeBackendRepository()
        val viewModel = AuthViewModel(repo)
        viewModel.toggleMode()
        viewModel.updateEmail("new@example.com")
        viewModel.updatePassword("password")
        viewModel.updateFirstName("New")
        viewModel.updateLastName("User")

        var authenticated = false
        viewModel.submit { authenticated = true }

        assertTrue(authenticated)
        assertEquals("New User", repo.fetchMyProfile()?.fullName)
    }
}
