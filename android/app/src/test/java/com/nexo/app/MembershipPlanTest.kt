package com.nexo.app

import com.nexo.app.domain.model.PlanComponent
import com.nexo.app.domain.model.ValidityUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MembershipPlanTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun millis(year: Int, month: Int, day: Int): Long =
        ZonedDateTime.of(year, month, day, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun expiresAtMillis_addsDays() {
        val component = PlanComponent(validityValue = 10, validityUnit = ValidityUnit.DAYS)
        assertEquals(millis(2026, 1, 11), component.expiresAtMillis(millis(2026, 1, 1), zone))
    }

    @Test
    fun expiresAtMillis_addsWeeks() {
        val component = PlanComponent(validityValue = 2, validityUnit = ValidityUnit.WEEKS)
        assertEquals(millis(2026, 1, 15), component.expiresAtMillis(millis(2026, 1, 1), zone))
    }

    @Test
    fun expiresAtMillis_addsMonths() {
        val component = PlanComponent(validityValue = 3, validityUnit = ValidityUnit.MONTHS)
        assertEquals(millis(2026, 4, 1), component.expiresAtMillis(millis(2026, 1, 1), zone))
    }

    @Test
    fun expiresAtMillis_addsYears() {
        val component = PlanComponent(validityValue = 1, validityUnit = ValidityUnit.YEARS)
        assertEquals(millis(2027, 1, 1), component.expiresAtMillis(millis(2026, 1, 1), zone))
    }
}
