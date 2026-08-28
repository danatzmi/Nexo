package com.nexo.app.domain.model

/**
 * Mirrors `gyms/{gymId}/team/{uid}` — see FIRESTORE_SCHEMA.md. `id` is the
 * Firebase Auth UID. Only ever holds `role == owner` or `role == coach`
 * entries — members live in `gyms/{gymId}/members` instead.
 *
 * NOTE: [profilePicBase64] isn't a field on the real `team` document (only
 * `role`/`firstName`/`lastName`/`email`/`addedAt` are, per
 * FIRESTORE_SCHEMA.md and iOS's `fetchTeam`) — it's always `null` when
 * parsed from Firestore. Kept as an optional field anyway so [AvatarView]
 * has one consistent signature across `Member`/`GymMember`/`TeamMember`.
 */
data class TeamMember(
    val id: String,
    val fullName: String,
    val email: String,
    val role: UserRole,
    val profilePicBase64: String? = null
)
