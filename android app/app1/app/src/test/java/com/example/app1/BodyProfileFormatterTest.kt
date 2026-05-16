package com.example.app1

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyProfileFormatterTest {
    @Test
    fun filledCount_emptyProfile_returnsZero() {
        val profile = BodyProfileDto()

        assertEquals(0, BodyProfileFormatter.filledCount(profile))
    }

    @Test
    fun filledCount_fullProfile_countsAllSupportedFields() {
        val profile = BodyProfileDto(
            skin_tone = "warm",
            eye_color = "brown",
            hair_color = "black",
            height_cm = 171,
            weight_kg = 62,
            chest_cm = 88,
            waist_cm = 70
        )

        assertEquals(BodyProfileFormatter.TOTAL_FIELDS, BodyProfileFormatter.filledCount(profile))
    }

    @Test
    fun filledCount_blankTextFields_areNotCounted() {
        val profile = BodyProfileDto(
            skin_tone = " ",
            eye_color = "",
            hair_color = "black",
            height_cm = 171
        )

        assertEquals(2, BodyProfileFormatter.filledCount(profile))
    }

    @Test
    fun summary_fullProfile_containsFilledCounter() {
        val profile = BodyProfileDto(
            skin_tone = "warm",
            eye_color = "brown",
            hair_color = "black",
            height_cm = 171,
            weight_kg = 62,
            chest_cm = 88,
            waist_cm = 70
        )

        val summary = BodyProfileFormatter.summary(profile)

        assertTrue(summary.contains("7"))
        assertTrue(summary.contains(BodyProfileFormatter.TOTAL_FIELDS.toString()))
    }
}
