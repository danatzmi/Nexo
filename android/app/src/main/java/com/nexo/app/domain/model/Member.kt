package com.nexo.app.domain.model

/**
 * Mirrors `gyms/{gymId}/members/{uid}` — see FIRESTORE_SCHEMA.md. `id` is
 * the Firebase Auth UID (matches iOS `Member.id: String`); this collection
 * only ever holds `role == "member"` entries (owners/coaches live in
 * `gyms/{gymId}/team` instead), so there is no `role` field here.
 *
 * NOTE: the Phase 1 task spec described `userId`/`role`/`avatarUrl` fields,
 * but the real document has no separate `userId` (`id` already is the
 * UID), no `role`, and photos are inlined as Base64 on the document
 * (`profilePicBase64`) rather than hosted at a URL — there is no Storage
 * bucket in this project. This mirrors the real shape instead.
 */
data class Member(
    val id: String,
    val fullName: String,
    val email: String,
    val profilePicBase64: String? = null,
    /** Live attendance for a specific class — only populated by `fetchAttendees`, not `fetchMyProfile`/other Member reads. See `BackendRepository.toggleAttendance`. */
    val isCheckedIn: Boolean = false,
    val checkedInAtMillis: Long? = null
)
