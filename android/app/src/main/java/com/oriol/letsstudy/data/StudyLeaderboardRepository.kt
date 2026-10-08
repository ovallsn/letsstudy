package com.oriol.letsstudy.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

data class StudyLeaderboardEntry(
    val id: String,
    val displayName: String,
    val weeklyPoints: Int,
)

data class StudyLeaderboardState(
    val enabled: Boolean = false,
    val nickname: String = "",
    val myEntryId: String? = null,
    val entries: List<StudyLeaderboardEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class LeaderboardEnrollment(
    val entryId: String,
    val displayName: String,
    val weeklyPoints: Int,
    val weekKey: String,
)

class StudyLeaderboardRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    suspend fun load(): StudyLeaderboardState {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Sign in to view the community board.")
        val settings = settingsReference(uid).get().await()
        val entryId = settings.getString("entryId").orEmpty()
        val enabled = settings.getBoolean("enabled") == true && entryId.isNotBlank()
        val nickname = settings.getString("displayName").orEmpty()
        val query = firestore.collection(PUBLIC_COLLECTION)
            .whereEqualTo("weekKey", currentWeekKey())
            .orderBy("weeklyPoints", Query.Direction.DESCENDING)
            .limit(25)
            .get()
            .await()
        return StudyLeaderboardState(
            enabled = enabled,
            nickname = nickname,
            myEntryId = entryId.takeIf { enabled },
            entries = query.documents.map { document ->
                StudyLeaderboardEntry(document.id, document.getString("displayName").orEmpty(), document.getLong("weeklyPoints")?.toInt() ?: 0)
            },
        )
    }

    suspend fun optIn(displayName: String, weeklyPoints: Int) {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Sign in before joining the community board.")
        val nickname = safeDisplayName(displayName)
        val settings = settingsReference(uid).get().await()
        val entryId = settings.getString("entryId")?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString()
        saveEnrollment(uid, LeaderboardEnrollment(entryId, nickname, weeklyPoints.coerceIn(0, MAX_WEEKLY_POINTS), currentWeekKey()))
    }

    suspend fun publishCurrentScore(weeklyPoints: Int) {
        val uid = auth.currentUser?.uid ?: return
        val settings = settingsReference(uid).get().await()
        if (settings.getBoolean("enabled") != true) return
        val entryId = settings.getString("entryId")?.takeIf(String::isNotBlank) ?: return
        val nickname = settings.getString("displayName")?.takeIf(String::isNotBlank) ?: return
        val score = weeklyPoints.coerceIn(0, MAX_WEEKLY_POINTS)
        val weekKey = currentWeekKey()
        val publicEntry = publicReference(entryId).get().await()
        if (publicEntry.getString("displayName") == nickname
            && publicEntry.getLong("weeklyPoints")?.toInt() == score
            && publicEntry.getString("weekKey") == weekKey
        ) return
        saveEnrollment(uid, LeaderboardEnrollment(entryId, nickname, score, weekKey))
    }

    suspend fun optOut(): LeaderboardEnrollment? {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Sign in to change your community board settings.")
        return removeEnrollment(uid)
    }

    suspend fun removeForAccountDeletion(uid: String): LeaderboardEnrollment? = try {
        removeEnrollment(uid)
    } catch (error: FirebaseFirestoreException) {
        if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) null else throw error
    }

    suspend fun restoreAfterAccountDeletionFailure(uid: String, enrollment: LeaderboardEnrollment) {
        saveEnrollment(uid, enrollment)
    }

    private suspend fun removeEnrollment(uid: String): LeaderboardEnrollment? {
        val settingsRef = settingsReference(uid)
        val settings = settingsRef.get().await()
        if (settings.getBoolean("enabled") != true) return null
        val entryId = settings.getString("entryId")?.takeIf(String::isNotBlank) ?: return null
        val publicRef = publicReference(entryId)
        val current = publicRef.get().await()
        val enrollment = LeaderboardEnrollment(
            entryId = entryId,
            displayName = settings.getString("displayName")?.takeIf(String::isNotBlank) ?: "Learner",
            weeklyPoints = current.getLong("weeklyPoints")?.toInt()?.coerceIn(0, MAX_WEEKLY_POINTS) ?: 0,
            weekKey = current.getString("weekKey")?.takeIf(String::isNotBlank) ?: currentWeekKey(),
        )
        firestore.batch().apply {
            delete(publicRef)
            delete(ownerReference(entryId))
            delete(settingsRef)
        }.commit().await()
        return enrollment
    }

    private suspend fun saveEnrollment(uid: String, enrollment: LeaderboardEnrollment) {
        val owner = mapOf(
            "ownerUid" to uid,
            "entryId" to enrollment.entryId,
            "displayName" to enrollment.displayName,
            "weekKey" to enrollment.weekKey,
        )
        val publicEntry = mapOf(
            "displayName" to enrollment.displayName,
            "weeklyPoints" to enrollment.weeklyPoints,
            "weekKey" to enrollment.weekKey,
        )
        val privateSettings = mapOf(
            "enabled" to true,
            "entryId" to enrollment.entryId,
            "displayName" to enrollment.displayName,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        firestore.batch().apply {
            set(ownerReference(enrollment.entryId), owner)
            set(publicReference(enrollment.entryId), publicEntry)
            set(settingsReference(uid), privateSettings)
        }.commit().await()
    }

    private fun settingsReference(uid: String) = firestore.collection("users").document(uid)
        .collection("leaderboard").document("settings")

    private fun ownerReference(entryId: String) = firestore.collection(OWNER_COLLECTION).document(entryId)

    private fun publicReference(entryId: String) = firestore.collection(PUBLIC_COLLECTION).document(entryId)

    private fun safeDisplayName(value: String): String {
        val clean = value.trim().replace(Regex("\\s+"), " ")
        require(clean.length in 2..24) { "Choose a board nickname between 2 and 24 characters." }
        require(clean.none(Char::isISOControl) && !clean.contains('@')) { "Use a nickname, not an email address." }
        return clean
    }

    private fun currentWeekKey(): String {
        val date = LocalDate.now(ZoneOffset.UTC)
        val week = date.get(WeekFields.ISO.weekOfWeekBasedYear())
        val year = date.get(WeekFields.ISO.weekBasedYear())
        return "%04d-W%02d".format(Locale.ROOT, year, week)
    }

    companion object {
        const val MAX_WEEKLY_POINTS = 500
        private const val OWNER_COLLECTION = "leaderboardOwners"
        private const val PUBLIC_COLLECTION = "publicLeaderboard"
    }
}
