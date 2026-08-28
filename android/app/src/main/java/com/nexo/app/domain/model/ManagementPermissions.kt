package com.nexo.app.domain.model

/**
 * Whether a member with [userRole] in the currently selected gym (or
 * [platformRole] platform-wide) can see the Manage tab — mirrors iOS's
 * `AppState.canManageClasses` plus the platform-admin bypass. A pure
 * function (not inlined in `NexoApp`) so the gating rule is unit-testable
 * per CLAUDE.md's "business logic stays out of views" rule.
 */
fun canManageGym(userRole: UserRole?, platformRole: PlatformRole): Boolean =
    userRole == UserRole.OWNER || userRole == UserRole.COACH || platformRole == PlatformRole.ADMIN

/**
 * Narrower than [canManageGym] — only Owners and Platform Admins can edit
 * gym settings (name/class types), matching iOS's `AdminView` toolbar
 * gate (`appState.gymRole == .owner || appState.isAdmin`). Coaches can
 * manage members/team but not rename the gym or edit its class types.
 */
fun canEditGymSettings(userRole: UserRole?, platformRole: PlatformRole): Boolean =
    userRole == UserRole.OWNER || platformRole == PlatformRole.ADMIN
