package com.nexo.app.domain.model

/**
 * Mirrors `gyms/{gymId}/members/{uid}` — see FIRESTORE_SCHEMA.md. `id` is
 * the Firebase Auth UID. [activePlanName] is a best-effort read of this
 * member's `activePlans` wallet subcollection (just the plan name, for
 * the Manage tab's badge) — not the full credit-wallet/consumption system,
 * which `CLAUDE.md`'s roadmap gates as its own feature.
 */
data class GymMember(
    val id: String,
    val fullName: String,
    val email: String,
    val joinedAtMillis: Long = System.currentTimeMillis(),
    val activePlanName: String? = null,
    val profilePicBase64: String? = null
)
