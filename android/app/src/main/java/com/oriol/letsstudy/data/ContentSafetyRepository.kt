package com.oriol.letsstudy.data

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ContentReportDraft private constructor(
    val kind: String,
    val subjectId: String,
    val reason: String,
    val excerpt: String,
) {
    fun firestoreFields(): Map<String, String> = mapOf(
        "kind" to kind,
        "subjectId" to subjectId,
        "reason" to reason,
        "excerpt" to excerpt,
    )

    companion object {
        fun create(kind: String, subjectId: String, reason: String, excerpt: String): ContentReportDraft {
            require(kind in setOf("community", "generated"))
            require(reason in setOf("unsafe", "inappropriate", "incorrect", "other"))
            require(subjectId.isNotBlank())
            return ContentReportDraft(kind, subjectId.take(100), reason, excerpt.take(500))
        }
    }
}

object CommunityEntryVisibility {
    fun visible(entries: List<StudyLeaderboardEntry>, blockedIds: Set<String>): List<StudyLeaderboardEntry> =
        entries.filterNot { it.id in blockedIds }
}

class ContentSafetyRepository(
    context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val blocks = context.getSharedPreferences("letsstudy_community_blocks", Context.MODE_PRIVATE)

    fun blockedIds(): Set<String> = blocks.getStringSet("entry_ids", emptySet()).orEmpty().toSet()

    fun block(entryId: String): Set<String> {
        val updated = blockedIds() + entryId
        blocks.edit { putStringSet("entry_ids", updated) }
        return updated
    }

    fun unblock(entryId: String): Set<String> {
        val updated = blockedIds() - entryId
        blocks.edit { putStringSet("entry_ids", updated) }
        return updated
    }

    suspend fun report(kind: String, subjectId: String, reason: String, excerpt: String) {
        val report = ContentReportDraft.create(kind, subjectId, reason, excerpt)
        firestore.collection("contentReports").document(UUID.randomUUID().toString()).set(
            report.firestoreFields() + ("createdAt" to FieldValue.serverTimestamp()),
        ).await()
    }
}
