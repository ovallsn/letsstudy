package com.oriol.letsstudy.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LearnerAvatarIdsTest {
    @Test
    fun defaultsToInitialsAndMigratesTheFormerDefaultAvatar() {
        assertEquals("initials", LearnerAvatarIds.DEFAULT)
        assertEquals("initials", LearnerAvatarIds.safe("sprout"))
    }

    @Test
    fun preservesOtherSupportedAvatars() {
        assertEquals("book", LearnerAvatarIds.safe("book"))
        assertEquals("initials", LearnerAvatarIds.safe("unknown"))
    }

    @Test
    fun createsInitialsFromTheFirstAndLastName() {
        assertEquals("ON", LearnerAvatarIds.initials("Oriol Baiz Núñez"))
        assertEquals("OB", LearnerAvatarIds.initials("Oriol Baiz"))
    }

    @Test
    fun handlesSingleNamesAndBlankNames() {
        assertEquals("OR", LearnerAvatarIds.initials("Oriol"))
        assertEquals("LS", LearnerAvatarIds.initials("  "))
    }
}
