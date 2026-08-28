package com.nexo.app.domain.model

/**
 * Per-gym role, stored as `role` on `users/{uid}/memberships/{gymId}` and
 * `gyms/{gymId}/team/{uid}` (owner/coach) or `gyms/{gymId}/members/{uid}`
 * (member) — see FIRESTORE_SCHEMA.md. Distinct from [PlatformRole], which
 * is platform-wide, not per-gym.
 */
enum class UserRole(val firestoreValue: String) {
    OWNER("owner"),
    COACH("coach"),
    MEMBER("member");

    val canManageClasses: Boolean
        get() = this == OWNER || this == COACH

    val displayName: String
        get() = when (this) {
            OWNER -> "Owner"
            COACH -> "Coach"
            MEMBER -> "Member"
        }

    companion object {
        fun fromFirestoreValue(value: String): UserRole =
            entries.firstOrNull { it.firestoreValue.equals(value, ignoreCase = true) } ?: MEMBER
    }
}
