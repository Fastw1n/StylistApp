package com.example.app1

object BodyProfileFormatter {
    const val TOTAL_FIELDS = 7

    fun filledCount(profile: BodyProfileDto): Int {
        return listOf(
            profile.skin_tone?.takeIf { it.isNotBlank() },
            profile.eye_color?.takeIf { it.isNotBlank() },
            profile.hair_color?.takeIf { it.isNotBlank() },
            profile.height_cm,
            profile.weight_kg,
            profile.chest_cm,
            profile.waist_cm
        ).count { it != null }
    }

    fun summary(profile: BodyProfileDto): String {
        val filled = filledCount(profile)
        return if (filled == 0) {
            "Параметры не заполнены"
        } else {
            "Заполнено $filled из $TOTAL_FIELDS"
        }
    }
}
