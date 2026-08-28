package com.nexo.app

import com.nexo.app.data.repository.FakeBackendRepository
import com.nexo.app.domain.model.Member
import com.nexo.app.ui.auth.ForgotPasswordViewModel
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
class ForgotPasswordViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun isValid_isFalseUntilTheEmailLooksValid() = runTest {
        val repo = FakeBackendRepository()
        val viewModel = ForgotPasswordViewModel(repo)

        assertEquals(false, viewModel.uiState.value.isValid)

        viewModel.updateEmail("not-an-email")
        assertEquals(false, viewModel.uiState.value.isValid)

        viewModel.updateEmail("dana@example.com")
        assertTrue(viewModel.uiState.value.isValid)
    }

    @Test
    fun sendResetLink_succeeds_setsDidSucceed_forARegisteredEmail() = runTest {
        val repo = FakeBackendRepository()
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        val viewModel = ForgotPasswordViewModel(repo)
        viewModel.updateEmail("dana@example.com")

        viewModel.sendResetLink()

        val state = viewModel.uiState.value
        assertTrue(state.didSucceed)
        assertEquals(false, state.isLoading)
        assertEquals(null, state.errorMessage)
    }

    @Test
    fun sendResetLink_surfacesErrorMessage_forAnUnregisteredEmail() = runTest {
        val repo = FakeBackendRepository()
        val viewModel = ForgotPasswordViewModel(repo)
        viewModel.updateEmail("nobody@example.com")

        viewModel.sendResetLink()

        val state = viewModel.uiState.value
        assertEquals(false, state.didSucceed)
        assertTrue(state.errorMessage?.isNotBlank() == true)
    }

    @Test
    fun sendResetLink_isANoOp_whenTheEmailIsInvalid() = runTest {
        val repo = FakeBackendRepository()
        val viewModel = ForgotPasswordViewModel(repo)
        viewModel.updateEmail("not-an-email")

        viewModel.sendResetLink()

        val state = viewModel.uiState.value
        assertEquals(false, state.didSucceed)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun updateEmail_clearsDidSucceed_soASecondAttemptDoesntShowStaleSuccess() = runTest {
        val repo = FakeBackendRepository()
        repo.seedProfile("member-1", Member(id = "member-1", fullName = "Dana Cohen", email = "dana@example.com"))
        val viewModel = ForgotPasswordViewModel(repo)
        viewModel.updateEmail("dana@example.com")
        viewModel.sendResetLink()
        assertTrue(viewModel.uiState.value.didSucceed)

        viewModel.updateEmail("dana2@example.com")

        assertEquals(false, viewModel.uiState.value.didSucceed)
    }
}
