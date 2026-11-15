package com.oriol.letsstudy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ContentSafetyTest {
    @Test fun reportDraftAcceptsSupportedKindsAndReasons() {
        val report = ContentReportDraft.create("generated", "question-1", "incorrect", "An excerpt")
        assertEquals("generated", report.kind)
        assertEquals("question-1", report.subjectId)
        assertEquals("incorrect", report.reason)
        assertEquals("An excerpt", report.excerpt)
    }

    @Test fun reportDraftRejectsUnsupportedKindAndReason() {
        assertThrows(IllegalArgumentException::class.java) { ContentReportDraft.create("other", "entry-1", "unsafe", "text") }
        assertThrows(IllegalArgumentException::class.java) { ContentReportDraft.create("community", "entry-1", "spam", "text") }
    }

    @Test fun reportDraftBoundsUserSuppliedIdentifiersAndExcerpt() {
        val report = ContentReportDraft.create("generated", "x".repeat(150), "other", "y".repeat(900))
        assertEquals(100, report.subjectId.length)
        assertEquals(500, report.excerpt.length)
    }

    @Test fun blockedCommunityEntriesAreHiddenWithoutChangingOtherEntries() {
        val entries = listOf(
            StudyLeaderboardEntry("mine", "my_name", 30),
            StudyLeaderboardEntry("blocked", "blocked_name", 20),
            StudyLeaderboardEntry("visible", "visible_name", 10),
        )
        assertEquals(listOf("mine", "visible"), CommunityEntryVisibility.visible(entries, setOf("blocked")).map { it.id })
        assertEquals(entries, CommunityEntryVisibility.visible(entries, emptySet()))
    }
}
