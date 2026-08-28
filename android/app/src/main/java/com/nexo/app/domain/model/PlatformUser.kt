package com.nexo.app.domain.model

/**
 * A platform-wide user record, as seen from the Platform Admin Dashboard's
 * Users tab — mirrors iOS's `PlatformUser`. `id` is the Firebase Auth UID,
 * matching `users/{uid}` — see FIRESTORE_SCHEMA.md.
 */
data class PlatformUser(
    val id: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val role: PlatformRole = PlatformRole.USER,
    val profilePicBase64: String? = null
) {
    val fullName: String get() = "$firstName $lastName".trim()
    val displayName: String get() = fullName.ifEmpty { email }
}
