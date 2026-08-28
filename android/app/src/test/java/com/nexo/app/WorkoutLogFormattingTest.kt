package com.nexo.app

import com.nexo.app.domain.model.WorkoutLog
import com.nexo.app.domain.model.formattedDetail
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutLogFormattingTest {

    @Test
    fun formattedDetail_showsSetsRepsAndScore_whenAllPresent() {
        val log = WorkoutLog(id = "1", movement = "Squat", score = 100.0, reps = 5, sets = 3, dateMillis = 0L)
        assertEquals("3 × 5 @ 100", log.formattedDetail)
    }

    @Test
    fun formattedDetail_showsOnlyScore_whenSetsOrRepsMissing() {
        val logScoreOnly = WorkoutLog(id = "1", movement = "Farmer Carry", score = 42.5, reps = null, sets = null, dateMillis = 0L)
        assertEquals("42.5", logScoreOnly.formattedDetail)

        val logScoreReps = WorkoutLog(id = "2", movement = "Push-ups", score = 50.0, reps = 20, sets = null, dateMillis = 0L)
        assertEquals("50", logScoreReps.formattedDetail)

        val logScoreSets = WorkoutLog(id = "3", movement = "Plank", score = 60.0, reps = null, sets = 3, dateMillis = 0L)
        assertEquals("60", logScoreSets.formattedDetail)
    }

    @Test
    fun formattedDetail_showsSetsAndReps_whenScoreIsNull() {
        val log = WorkoutLog(id = "1", movement = "Push-Ups", score = null, reps = 20, sets = 4, dateMillis = 0L)
        assertEquals("4 × 20", log.formattedDetail)
    }

    @Test
    fun formattedDetail_showsRepsOrSets_whenScoreIsNullAndOneMissing() {
        val logReps = WorkoutLog(id = "1", movement = "Push-Ups", score = null, reps = 20, sets = null, dateMillis = 0L)
        assertEquals("20 reps", logReps.formattedDetail)

        val logSets = WorkoutLog(id = "2", movement = "Plank", score = null, reps = null, sets = 3, dateMillis = 0L)
        assertEquals("3 sets", logSets.formattedDetail)
    }

    @Test
    fun formattedDetail_fallsBackToLogged_whenEverythingIsNull() {
        val log = WorkoutLog(id = "1", movement = "Rest Day Check-In", score = null, reps = null, sets = null, dateMillis = 0L)
        assertEquals("Logged", log.formattedDetail)
    }
}
