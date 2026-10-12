package com.oriol.letsstudy.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyLeaderboardEntryTest {
    @Test fun leaderboardEntryIncludesCurrentStreakDays() {
        val entry = leaderboardEntryFromData(
            "entry-1",
            mapOf("displayName" to "Oriol", "weeklyPoints" to 120L, "currentStreakDays" to 6L),
        )

        assertEquals(6, entry?.currentStreakDays)
    }

    @Test fun olderLeaderboardEntryDefaultsToZeroStreakDays() {
        val entry = leaderboardEntryFromData(
            "entry-2",
            mapOf("displayName" to "Learner", "weeklyPoints" to 30L),
        )

        assertEquals(0, entry?.currentStreakDays)
    }
}
