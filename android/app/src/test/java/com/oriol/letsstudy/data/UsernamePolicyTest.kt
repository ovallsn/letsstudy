package com.oriol.letsstudy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernamePolicyTest {
    @Test
    fun normalizesValidUsernamesForCaseAndWhitespace() {
        assertEquals("study_with_me", UsernamePolicy.normalize("  Study_With_Me  "))
    }

    @Test
    fun rejectsRestrictedTermsWithSeparatorsAndNumberSubstitutions() {
        assertFalse(UsernamePolicy.isAllowed("s_h1t"))
        assertFalse(UsernamePolicy.isAllowed("f_u_c_k"))
        assertFalse(UsernamePolicy.isAllowed("n1gger"))
    }

    @Test
    fun rejectsReservedNamesAndKeepsOrdinaryNamesAvailable() {
        assertFalse(UsernamePolicy.isAllowed("admin123"))
        assertFalse(UsernamePolicy.isAllowed("admin_helper"))
        assertTrue(UsernamePolicy.isAllowed("oriol_reader"))
    }
}
