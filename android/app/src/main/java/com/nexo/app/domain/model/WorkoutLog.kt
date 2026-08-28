package com.nexo.app.domain.model

/**
 * Mirrors `gyms/{gymId}/members/{uid}/workoutLogs/{logId}` — see
 * FIRESTORE_SCHEMA.md. `id` is a client-generated UUID string, matching
 * iOS `WorkoutLog.id`. `movement` is fully generic/gym-agnostic free
 * text — there is no fixed baseline list on either client.
 *
 * NOTE: the Phase 1 task spec described a non-optional `weight: Double`
 * field, but the current schema (as of the "generic Score field" cycle on
 * iOS) has `score`/`reps`/`sets` all optional — a member can log an
 * activity with none, some, or all of them set. This mirrors the current
 * schema instead of the stale spec.
 */
data class WorkoutLog(
    val id: String,
    val movement: String,
    val score: Double? = null,
    val reps: Int? = null,
    val sets: Int? = null,
    val dateMillis: Long
)

/**
 * The value line for this entry — whichever of [WorkoutLog.score]/[WorkoutLog.reps]/
 * [WorkoutLog.sets] were actually logged, omitting labels for fields left
 * blank. Falls back to "Logged" when none were entered (a date-only
 * session). Mirrors `WorkoutLog.formattedDetail` on iOS.
 */
val WorkoutLog.formattedDetail: String
    get() {
        return when {
            sets != null && reps != null -> {
                if (score != null) "$sets × $reps @ ${score.formatCompact()}" else "$sets × $reps"
            }
            score != null -> score.formatCompact()
            reps != null -> "$reps reps"
            sets != null -> "$sets sets"
            else -> "Logged"
        }
    }

/** Drops a trailing ".0" for whole numbers (100.0 -> "100"), same as Swift's `Double.formatted()`. */
private fun Double.formatCompact(): String =
    if (this == this.toLong().toDouble()) this.toLong().toString() else this.toString()
