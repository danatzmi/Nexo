package com.nexo.app

import com.nexo.app.domain.model.PlatformRole
import com.nexo.app.domain.model.UserRole
import com.nexo.app.domain.model.canEditGymSettings
import com.nexo.app.domain.model.canManageGym
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the Manage tab's visibility/permission gating (`ManagementPermissions.kt`) — a role/permission matrix, exactly the kind of logic CLAUDE.md flags as easy to get subtly wrong. */
class ManagementPermissionsTest {

    @Test
    fun canManageGym_true_forOwner() {
        assertTrue(canManageGym(UserRole.OWNER, PlatformRole.USER))
    }

    @Test
    fun canManageGym_true_forCoach() {
        assertTrue(canManageGym(UserRole.COACH, PlatformRole.USER))
    }

    @Test
    fun canManageGym_false_forMember() {
        assertFalse(canManageGym(UserRole.MEMBER, PlatformRole.USER))
    }

    @Test
    fun canManageGym_true_forPlatformAdmin_regardlessOfGymRole() {
        assertTrue(canManageGym(UserRole.MEMBER, PlatformRole.ADMIN))
        assertTrue(canManageGym(null, PlatformRole.ADMIN))
    }

    @Test
    fun canManageGym_false_whenNoGymRoleAndNotPlatformAdmin() {
        assertFalse(canManageGym(null, PlatformRole.USER))
    }

    @Test
    fun canEditGymSettings_true_forOwner() {
        assertTrue(canEditGymSettings(UserRole.OWNER, PlatformRole.USER))
    }

    @Test
    fun canEditGymSettings_false_forCoach() {
        // A coach can manage members/team (canManageGym) but not rename the gym or edit class types.
        assertFalse(canEditGymSettings(UserRole.COACH, PlatformRole.USER))
    }

    @Test
    fun canEditGymSettings_true_forPlatformAdmin_regardlessOfGymRole() {
        assertTrue(canEditGymSettings(UserRole.MEMBER, PlatformRole.ADMIN))
    }
}
