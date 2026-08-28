package com.nexo.app.domain.model

/**
 * Platform-wide role, stored as `role` on `users/{uid}` — see
 * FIRESTORE_SCHEMA.md. Distinct from [UserRole], which is per-gym.
 * Platform admins bypass gym-level role/wallet checks entirely
 * (`AppState.canManageClasses` on iOS).
 */
enum class PlatformRole(val firestoreValue: String) {
    ADMIN("admin"),
    USER("user");

    companion object {
        fun fromFirestoreValue(value: String): PlatformRole =
            entries.firstOrNull { it.firestoreValue.equals(value, ignoreCase = true) } ?: USER
    }
}
