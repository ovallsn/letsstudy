package com.oriol.letsstudy.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
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
    val avatarThumbnail: ByteArray? = null,
)

data class StudyLeaderboardState(
    val enabled: Boolean = false,
    val nickname: String = "",
    val photoShared: Boolean = false,
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
    val photoShared: Boolean = false,
    val avatarThumbnail: ByteArray? = null,
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
        val photoShared = settings.getBoolean("sharePhoto") == true
        if (enabled && !UsernamePolicy.isAllowed(nickname)) {
            removeEnrollment(uid)
            return StudyLeaderboardState(error = "Your previous board username no longer follows the community rules. Choose a new one to join again.")
        }
        val query = firestore.collection(PUBLIC_COLLECTION)
            .whereEqualTo("weekKey", currentWeekKey())
            .orderBy("weeklyPoints", Query.Direction.DESCENDING)
            .limit(25)
            .get()
            .await()
        return StudyLeaderboardState(
            enabled = enabled,
            nickname = nickname,
            photoShared = photoShared,
            myEntryId = entryId.takeIf { enabled },
            entries = query.documents.mapNotNull { document ->
                val displayName = document.getString("displayName").orEmpty()
                if (!UsernamePolicy.isAllowed(displayName)) return@mapNotNull null
                StudyLeaderboardEntry(
                    id = document.id,
                    displayName = displayName,
                    weeklyPoints = document.getLong("weeklyPoints")?.toInt() ?: 0,
                    avatarThumbnail = document.getBlob("avatarThumb")?.toBytes(),
                )
            },
        )
    }

    suspend fun optIn(displayName: String, weeklyPoints: Int, avatarThumbnail: ByteArray?) {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Sign in before joining the community board.")
        val nickname = safeDisplayName(displayName)
        val settings = settingsReference(uid).get().await()
        val entryId = settings.getString("entryId")?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString()
        val weekKey = currentWeekKey()
        val existingEntry = publicReference(entryId).get().await()
        val previousScore = existingEntry.getLong("weeklyPoints")?.toInt()
            ?.takeIf { existingEntry.getString("weekKey") == weekKey } ?: 0
        saveEnrollment(
            uid,
            LeaderboardEnrollment(
                entryId = entryId,
                displayName = nickname,
                weeklyPoints = maxOf(weeklyPoints.coerceIn(0, MAX_WEEKLY_POINTS), previousScore),
                weekKey = weekKey,
                photoShared = avatarThumbnail != null,
                avatarThumbnail = avatarThumbnail,
            ),
        )
    }

    suspend fun publishCurrentScore(weeklyPoints: Int) {
        val uid = auth.currentUser?.uid ?: return
        val settings = settingsReference(uid).get().await()
        if (settings.getBoolean("enabled") != true) return
        val entryId = settings.getString("entryId")?.takeIf(String::isNotBlank) ?: return
        val nickname = settings.getString("displayName")?.takeIf { UsernamePolicy.isAllowed(it) } ?: return
        val weekKey = currentWeekKey()
        val publicEntry = publicReference(entryId).get().await()
        val previousScore = publicEntry.getLong("weeklyPoints")?.toInt()
            ?.takeIf { publicEntry.getString("weekKey") == weekKey } ?: 0
        val score = maxOf(weeklyPoints.coerceIn(0, MAX_WEEKLY_POINTS), previousScore)
        val avatarThumbnail = publicEntry.getBlob("avatarThumb")?.toBytes()
        val photoShared = settings.getBoolean("sharePhoto") == true && avatarThumbnail != null
        if (publicEntry.getString("displayName") == nickname
            && publicEntry.getLong("weeklyPoints")?.toInt() == score
            && publicEntry.getString("weekKey") == weekKey
            && (publicEntry.getBlob("avatarThumb") != null) == photoShared
        ) return
        saveEnrollment(uid, LeaderboardEnrollment(entryId, nickname, score, weekKey, photoShared, avatarThumbnail))
    }

    suspend fun setPhotoSharing(enabled: Boolean, avatarThumbnail: ByteArray?, weeklyPoints: Int) {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Sign in to change your community board settings.")
        val settings = settingsReference(uid).get().await()
        if (settings.getBoolean("enabled") != true) throw IllegalStateException("Join the community board before sharing a profile photo.")
        val entryId = settings.getString("entryId")?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Your community board entry could not be found. Try refreshing the board.")
        val nickname = settings.getString("displayName")?.takeIf(UsernamePolicy::isAllowed)
            ?: throw IllegalStateException("Choose a valid board username before sharing a profile photo.")
        if (enabled && avatarThumbnail == null) throw IllegalStateException("Choose a profile photo in Settings before sharing it.")
        val current = publicReference(entryId).get().await()
        val weekKey = currentWeekKey()
        val currentScore = current.getLong("weeklyPoints")?.toInt()?.coerceIn(0, MAX_WEEKLY_POINTS) ?: 0
        val score = if (current.getString("weekKey") == weekKey) {
            maxOf(currentScore, weeklyPoints.coerceIn(0, MAX_WEEKLY_POINTS))
        } else {
            weeklyPoints.coerceIn(0, MAX_WEEKLY_POINTS)
        }
        saveEnrollment(
            uid,
            LeaderboardEnrollment(
                entryId = entryId,
                displayName = nickname,
                weeklyPoints = score,
                weekKey = weekKey,
                photoShared = enabled,
                avatarThumbnail = avatarThumbnail.takeIf { enabled },
            ),
        )
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
            photoShared = settings.getBoolean("sharePhoto") == true && current.getBlob("avatarThumb") != null,
            avatarThumbnail = current.getBlob("avatarThumb")?.toBytes(),
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
        val publicEntry = mutableMapOf<String, Any>(
            "displayName" to enrollment.displayName,
            "weeklyPoints" to enrollment.weeklyPoints,
            "weekKey" to enrollment.weekKey,
        )
        if (enrollment.photoShared && enrollment.avatarThumbnail != null) {
            publicEntry["avatarThumb"] = Blob.fromBytes(enrollment.avatarThumbnail)
        }
        val privateSettings = mapOf(
            "enabled" to true,
            "entryId" to enrollment.entryId,
            "displayName" to enrollment.displayName,
            "sharePhoto" to (enrollment.photoShared && enrollment.avatarThumbnail != null),
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
        return UsernamePolicy.normalize(value)
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
