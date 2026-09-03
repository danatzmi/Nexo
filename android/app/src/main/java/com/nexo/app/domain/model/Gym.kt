package com.nexo.app.domain.model

/**
 * Mirrors `gyms/{gymId}` — see FIRESTORE_SCHEMA.md. `id` is a UUID string
 * (app-created entity, per CLAUDE.md's ID scheme), matching iOS `Gym.id`.
 *
 * NOTE: the Phase 1 task spec described an `emblemSymbol` field here, but
 * no such field exists on the iOS `Gym` model or in FIRESTORE_SCHEMA.md —
 * this mirrors the real document shape instead.
 */
data class Gym(
    val id: String,
    val name: String,
    val ownerUID: String,
    val workoutTypes: List<String> = DEFAULT_WORKOUT_TYPES,
    val city: String? = null
) {
    companion object {
        /** Mirrors `WorkoutCategory.defaults` on iOS. */
        val DEFAULT_WORKOUT_TYPES = listOf(
            "CrossFit WOD", "HIIT", "Strength Training", "Cardio", "Yoga", "Pilates"
        )
    }
}
