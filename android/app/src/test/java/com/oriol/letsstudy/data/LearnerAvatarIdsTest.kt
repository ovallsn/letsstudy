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
        assertEquals("AL", LearnerAvatarIds.initials("Alex Jordan Lee"))
        assertEquals("AM", LearnerAvatarIds.initials("Alex Morgan"))
    }

    @Test
    fun handlesSingleNamesAndBlankNames() {
        assertEquals("AL", LearnerAvatarIds.initials("Alex"))
        assertEquals("LS", LearnerAvatarIds.initials("  "))
    }
}
