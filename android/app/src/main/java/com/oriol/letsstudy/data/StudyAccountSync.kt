package com.oriol.letsstudy.data

import com.google.gson.Gson
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.Locale

enum class CloudSyncStatus { SIGNED_OUT, CONNECTING, SYNCING, SYNCED, OFFLINE, ERROR }

data class StudyAccountState(
    val email: String? = null,
    val displayName: String? = null,
    val username: String? = null,
    val profileComplete: Boolean = false,
    val isProfileLoading: Boolean = false,
    val status: CloudSyncStatus = CloudSyncStatus.SIGNED_OUT,
    val isBusy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

private class UsernameTakenException : IllegalStateException("That username is already taken. Choose another one.")

class StudyAccountSync(private val dao: StudyDao) {
    private val gson = Gson()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(StudyAccountState())
    val state = _state.asStateFlow()
    private val authEvents = Channel<FirebaseUser?>(Channel.CONFLATED)
    private val syncRequests = Channel<Unit>(Channel.CONFLATED)
    private val syncMutex = Mutex()
    private val profileMutex = Mutex()
    @Volatile private var activeUid: String? = null
    @Volatile private var remoteReady = false
    @Volatile private var remoteRecords: Map<String, Map<String, Any?>> = emptyMap()
    private var serverReady = CompletableDeferred<Unit>()
    private var listener: ListenerRegistration? = null
    private val authListener = FirebaseAuth.AuthStateListener { instance -> authEvents.trySend(instance.currentUser) }

    init {
        scope.launch { for (user in authEvents) switchAccount(user) }
        scope.launch { for (ignored in syncRequests) reconcileSafely() }
        scope.launch { dao.observeSessions().collect { requestReconcile() } }
        scope.launch { dao.observeAllQuestions().collect { requestReconcile() } }
        scope.launch { dao.observeMessages().collect { requestReconcile() } }
        scope.launch { dao.observeActivity().collect { requestReconcile() } }
        scope.launch { dao.observeDeletionTombstones().collect { requestReconcile() } }
        auth.addAuthStateListener(authListener)
    }

    fun createAccount(displayName: String, username: String, email: String, password: String) = runAccountAction {
        val safeDisplayName = validateDisplayName(displayName)
        val safeUsername = normalizeUsername(username)
        validateCredentials(email, password)
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: auth.currentUser ?: throw IllegalStateException("Firebase did not return the new account.")
        saveProfile(user, safeDisplayName, safeUsername)
        "Your account and learner profile are ready. Your studies will sync when Firebase is ready."
    }

    fun completeProfile(displayName: String, username: String) = runAccountAction {
        val user = auth.currentUser ?: throw IllegalStateException("Sign in before completing your profile.")
        saveProfile(user, validateDisplayName(displayName), normalizeUsername(username))
        "Your learner profile is saved."
    }

    fun updateProfile(displayName: String, username: String) = runAccountAction {
        val user = auth.currentUser ?: throw IllegalStateException("Sign in before editing your profile.")
        saveProfile(user, validateDisplayName(displayName), normalizeUsername(username))
        "Your learner profile is updated."
    }

    fun signIn(email: String, password: String) = runAccountAction {
        require(email.trim().contains('@')) { "Enter a valid email address." }
        require(password.isNotBlank()) { "Enter your password." }
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        "Signed in. Preparing your studies…"
    }

    fun sendPasswordReset(email: String) = runAccountAction {
        require(email.trim().contains('@')) { "Enter the email address for your account." }
        auth.sendPasswordResetEmail(email.trim()).await()
        "If an account exists for that address, Firebase has sent a password reset email."
    }

    fun syncNow() = runAccountAction {
        synchronizeNow()
        "Your studies are up to date."
    }

    fun signOut() = runAccountAction {
        synchronizeNow()
        syncMutex.withLock {
            stopRemoteSync()
            dao.clearAllStudyData()
            activeUid = null
            auth.signOut()
            _state.value = StudyAccountState(message = "Signed out. Your synced studies will return when you sign in again.")
        }
        ""
    }

    fun deleteAccount(password: String) = runAccountAction {
        require(password.isNotBlank()) { "Enter your password to confirm account deletion." }
        val user = auth.currentUser ?: throw IllegalStateException("Sign in again before deleting your account.")
        val email = user.email ?: throw IllegalStateException("This account has no email address for password confirmation.")
        user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
        synchronizeNow()
        syncMutex.withLock {
            stopRemoteSync()
            activeUid = null
        }
        var removedProfile: Map<String, Any?>? = null
        try {
            withTimeout(ACCOUNT_DELETE_TIMEOUT_MILLIS) {
                deleteCloudRecords(user.uid)
                removedProfile = deleteProfile(user.uid)
                user.delete().await()
            }
        } catch (error: Exception) {
            if (auth.currentUser?.uid == user.uid) {
                val profileRestoreFailed = removedProfile?.let { profile -> runCatching { restoreProfile(user.uid, profile) }.isFailure } == true
                switchAccount(user)
                if (profileRestoreFailed) {
                    _state.update { it.copy(message = "Account deletion did not finish, and the username could not be restored. Complete your profile again in Settings.") }
                }
            }
            throw error
        }
        dao.clearAllStudyData()
        auth.signOut()
        _state.update { StudyAccountState(message = "Your account and synced study data were deleted.") }
        ""
    }

    suspend fun synchronizeNow() {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Sign in to synchronize your studies.")
        if (activeUid != uid) switchAccount(auth.currentUser)
        withTimeout(SYNC_TIMEOUT_MILLIS) {
            if (!remoteReady) serverReady.await()
            syncMutex.withLock { reconcile() }
        }
    }

    fun close() {
        auth.removeAuthStateListener(authListener)
        stopRemoteSync()
        authEvents.close()
        syncRequests.close()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private suspend fun switchAccount(user: FirebaseUser?) {
        var loadProfile = false
        syncMutex.withLock {
            val nextUid = user?.uid
            val previousUid = activeUid
            if (nextUid == previousUid) {
                if (nextUid != null) _state.update { it.copy(email = user.email) }
                loadProfile = nextUid != null && !_state.value.profileComplete && !_state.value.isProfileLoading
                return@withLock
            }
            stopRemoteSync()
            if (previousUid != null) dao.clearAllStudyData()
            activeUid = nextUid
            if (nextUid == null) {
                _state.value = StudyAccountState()
                return@withLock
            }
            remoteRecords = emptyMap()
            remoteReady = false
            serverReady = CompletableDeferred()
            _state.value = StudyAccountState(email = user.email, isProfileLoading = true, status = CloudSyncStatus.CONNECTING)
            loadProfile = true
            listener = firestore.collection("users").document(nextUid).collection("studyData")
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (activeUid != nextUid) return@addSnapshotListener
                    if (error != null) {
                        _state.update { it.copy(status = if (error.code == FirebaseFirestoreException.Code.UNAVAILABLE) CloudSyncStatus.OFFLINE else CloudSyncStatus.ERROR, error = friendlyError(error)) }
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener
                    remoteRecords = snapshot.documents.mapNotNull { document ->
                        document.data?.let { document.id to it }
                    }.toMap()
                    if (!snapshot.metadata.isFromCache) {
                        remoteReady = true
                        serverReady.complete(Unit)
                        _state.update { it.copy(status = CloudSyncStatus.SYNCING, error = null) }
                    } else if (!remoteReady) {
                        _state.update { it.copy(status = CloudSyncStatus.OFFLINE) }
                    }
                    requestReconcile()
                }
        }
        if (loadProfile && user != null) loadAccountProfile(user)
    }

    private suspend fun loadAccountProfile(user: FirebaseUser) = profileMutex.withLock {
        try {
            val snapshot = withTimeout(PROFILE_TIMEOUT_MILLIS) {
                profileReference(user.uid).get().await()
            }
            val name = snapshot.getString("displayName")?.trim()?.takeIf(String::isNotEmpty)
            val username = snapshot.getString("username")?.trim()?.lowercase(Locale.ROOT)
                ?.takeIf { USERNAME_PATTERN.matches(it) }
            val complete = name != null && username != null
            var authNameUpdated = true
            if (complete && user.displayName != name) {
                try {
                    user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (_: Exception) {
                    authNameUpdated = false
                }
            }
            if (auth.currentUser?.uid == user.uid) {
                _state.update {
                    it.copy(
                        displayName = name,
                        username = username,
                        profileComplete = complete,
                        isProfileLoading = false,
                        error = if (!authNameUpdated) "Your profile is saved. Firebase will refresh the account display name when the connection allows it." else it.error,
                    )
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            if (auth.currentUser?.uid == user.uid) {
                _state.update { it.copy(isProfileLoading = false, error = friendlyError(error)) }
            }
        }
    }

    private suspend fun saveProfile(user: FirebaseUser, displayName: String, username: String) = profileMutex.withLock {
        val profile = profileReference(user.uid)
        val reservation = usernameReference(username)
        firestore.runTransaction { transaction ->
            val current = transaction.get(profile)
            val previousUsername = current.getString("username")?.trim()?.lowercase(Locale.ROOT).orEmpty()
            val nextReservation = transaction.get(reservation)
            val previousReservation = if (previousUsername.isNotBlank() && previousUsername != username) {
                transaction.get(usernameReference(previousUsername))
            } else null

            if (previousUsername != username && nextReservation.exists()) throw UsernameTakenException()
            if (previousUsername == username && nextReservation.exists() && nextReservation.getString("username") != username) {
                throw IllegalStateException("The username reservation needs attention. Contact the project maintainer.")
            }
            if (previousUsername.isNotBlank() && previousUsername != username && previousReservation?.exists() == true && previousReservation.getString("username") != previousUsername) {
                throw IllegalStateException("The current username reservation needs attention. Contact the project maintainer.")
            }

            transaction.set(profile, mapOf(
                "displayName" to displayName,
                "username" to username,
                "updatedAt" to FieldValue.serverTimestamp(),
            ))
            if (previousUsername != username) {
                transaction.set(reservation, mapOf("username" to username))
                if (previousReservation?.exists() == true) transaction.delete(usernameReference(previousUsername))
            } else if (!nextReservation.exists()) {
                transaction.set(reservation, mapOf("username" to username))
            }
        }.await()

        var authNameUpdated = true
        try {
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(displayName).build()).await()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            authNameUpdated = false
        }
        if (auth.currentUser?.uid == user.uid) {
            _state.update {
                it.copy(
                    email = user.email,
                    displayName = displayName,
                    username = username,
                    profileComplete = true,
                    isProfileLoading = false,
                    error = if (authNameUpdated) null else "Your profile is saved. Firebase will refresh the account display name when the connection allows it.",
                )
            }
        }
    }

    private suspend fun deleteProfile(uid: String): Map<String, Any?>? {
        val profile = profileReference(uid)
        return firestore.runTransaction { transaction ->
            val snapshot = transaction.get(profile)
            if (!snapshot.exists()) return@runTransaction null
            val data = snapshot.data.orEmpty()
            val username = data["username"] as? String
            val reservation = username?.takeIf(String::isNotBlank)?.let { transaction.get(usernameReference(it)) }
            if (reservation?.exists() == true && reservation.getString("username") != username) {
                throw IllegalStateException("The username reservation needs attention. Account deletion was stopped.")
            }
            transaction.delete(profile)
            if (reservation?.exists() == true) transaction.delete(reservation.reference)
            data
        }.await()
    }

    private suspend fun restoreProfile(uid: String, data: Map<String, Any?>) = profileMutex.withLock {
        val username = (data["username"] as? String)?.trim()?.lowercase(Locale.ROOT)
            ?: throw IllegalStateException("The saved account profile could not be restored.")
        val profile = profileReference(uid)
        val reservation = usernameReference(username)
        firestore.runTransaction { transaction ->
            val currentReservation = transaction.get(reservation)
            if (currentReservation.exists()) throw UsernameTakenException()
            transaction.set(profile, data)
            transaction.set(reservation, mapOf("username" to username))
        }.await()
    }

    private fun profileReference(uid: String) = firestore.collection("users").document(uid)
        .collection("profile").document("account")

    private fun usernameReference(username: String) = firestore.collection("usernameReservations").document(username)

    private fun validateDisplayName(value: String): String {
        val displayName = value.trim().replace(Regex("\\s+"), " ")
        require(displayName.length in 1..80) { "Enter a display name between 1 and 80 characters." }
        require(displayName.none(Char::isISOControl)) { "The display name contains an unsupported character." }
        return displayName
    }

    private fun normalizeUsername(value: String): String {
        val username = value.trim()
        require(USERNAME_PATTERN.matches(username)) { "Use 3–20 letters, numbers or underscores for your username." }
        return username.lowercase(Locale.ROOT)
    }

    private fun requestReconcile() {
        if (activeUid != null && remoteReady) syncRequests.trySend(Unit)
    }

    private suspend fun reconcileSafely() {
        try {
            syncMutex.withLock { reconcile() }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.update { it.copy(status = if (error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.UNAVAILABLE) CloudSyncStatus.OFFLINE else CloudSyncStatus.ERROR, error = error.message ?: "Cloud sync could not finish.") }
        }
    }

    private suspend fun reconcile() {
        val uid = activeUid ?: return
        if (!remoteReady) return
        _state.update { it.copy(status = CloudSyncStatus.SYNCING, error = null) }
        val remote = remoteRecords
        val remoteDeletions = remote.values.mapNotNull(::decodeDeletion).associateBy { it.sessionId }
        remoteDeletions.values.forEach { dao.applyRemoteDeletion(it) }
        val localDeletions = dao.getDeletionTombstones().associateBy { it.sessionId }
        val deletedSessionIds = localDeletions.keys + remoteDeletions.keys
        val writes = linkedMapOf<String, Map<String, Any?>>()
        val deletes = remote.filter { (_, record) ->
            if (record["type"] == "deletion") false
            else {
                val sessionId = if (record["type"] == "session") record["id"] as? String else record["sessionId"] as? String
                sessionId != null && sessionId in deletedSessionIds
            }
        }.keys

        localDeletions.values.forEach { deletion ->
            val key = deletionKey(deletion.sessionId)
            if (key !in remote) writes[key] = deletion.toCloudRecord()
        }

        val localSessions = dao.getAllSessions().associateBy { it.id }
        val remoteSessions = remote.values.mapNotNull(::decodeSession).associateBy { it.id }
        remoteSessions.values.filter { it.id !in deletedSessionIds }.forEach { cloud ->
            val local = localSessions[cloud.id]
            if (local == null) {
                dao.upsertRemoteSession(cloud)
            } else {
                val localRecord = local.toCloudRecord()
                val cloudRecord = remote[sessionKey(cloud.id)] ?: cloud.toCloudRecord()
                when (compareVersions(local.updatedAt, localRecord, cloud.updatedAt, cloudRecord)) {
                    in Int.MIN_VALUE..-1 -> dao.upsertRemoteSession(cloud)
                    in 1..Int.MAX_VALUE -> writes[sessionKey(local.id)] = localRecord
                }
            }
        }
        localSessions.values.filter { it.id !in deletedSessionIds }.forEach { local ->
            if (local.id !in remoteSessions) writes[sessionKey(local.id)] = local.toCloudRecord()
        }

        val sessionIds = (localSessions.keys + remoteSessions.keys) - deletedSessionIds
        val localQuestions = dao.getAllQuestions().filter { it.sessionId in sessionIds }.associateBy { it.id }
        val remoteQuestions = remote.values.mapNotNull(::decodeQuestion).filter { it.sessionId in sessionIds }.associateBy { it.id }
        remoteQuestions.values.forEach { cloud ->
            val local = localQuestions[cloud.id]
            if (local == null) {
                dao.upsertRemoteQuestions(listOf(cloud))
            } else {
                val localRecord = local.toCloudRecord()
                val cloudRecord = remote[questionKey(cloud.id)] ?: cloud.toCloudRecord()
                when (compareVersions(local.updatedAt, localRecord, cloud.updatedAt, cloudRecord)) {
                    in Int.MIN_VALUE..-1 -> dao.upsertRemoteQuestions(listOf(cloud))
                    in 1..Int.MAX_VALUE -> writes[questionKey(local.id)] = localRecord
                }
            }
        }
        localQuestions.values.forEach { local ->
            if (local.id !in remoteQuestions) writes[questionKey(local.id)] = local.toCloudRecord()
        }

        val localMessages = dao.getAllMessages().filter { it.sessionId in sessionIds }.associateBy { it.id }
        val remoteMessages = remote.values.mapNotNull(::decodeMessage).filter { it.sessionId in sessionIds }.associateBy { it.id }
        remoteMessages.values.filter { it.id !in localMessages }.forEach { dao.putRemoteMessage(it) }
        localMessages.values.filter { it.id !in remoteMessages }.forEach { writes[messageKey(it.id)] = it.toCloudRecord() }

        val localActivity = dao.getAllActivity().filter { it.sessionId in sessionIds }.associateBy { it.id }
        val remoteActivity = remote.values.mapNotNull(::decodeActivity).filter { it.sessionId in sessionIds }.associateBy { it.id }
        remoteActivity.values.filter { it.id !in localActivity }.forEach { dao.putRemoteActivity(it) }
        localActivity.values.filter { it.id !in remoteActivity }.forEach { writes[activityKey(it.id)] = it.toCloudRecord() }

        (writes.keys + deletes).distinct().chunked(FIRESTORE_BATCH_SIZE).forEach { documentIds ->
            val batch = firestore.batch()
            documentIds.forEach { documentId ->
                val reference = firestore.collection("users").document(uid).collection("studyData").document(documentId)
                val record = writes[documentId]
                if (record == null) batch.delete(reference) else batch.set(reference, record)
            }
            batch.commit().await()
            if (activeUid == uid) remoteRecords = (remoteRecords - documentIds.filter { it in deletes }.toSet()) + documentIds.mapNotNull { id -> writes[id]?.let { id to it } }.toMap()
        }
        if (activeUid == uid) _state.update { it.copy(status = CloudSyncStatus.SYNCED, error = null) }
    }

    private suspend fun deleteCloudRecords(uid: String) {
        val collection = firestore.collection("users").document(uid).collection("studyData")
        while (true) {
            val page = collection.limit(FIRESTORE_BATCH_SIZE.toLong()).get().await()
            if (page.isEmpty) return
            val batch = firestore.batch()
            page.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    private fun stopRemoteSync() {
        listener?.remove()
        listener = null
        remoteReady = false
        remoteRecords = emptyMap()
    }

    private fun runAccountAction(action: suspend () -> String) {
        scope.launch {
            _state.update { it.copy(isBusy = true, error = null, message = null) }
            try {
                val message = action()
                if (message.isNotBlank()) _state.update { it.copy(message = message) }
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                _state.update { it.copy(error = friendlyError(error)) }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(error = friendlyError(error)) }
            } finally {
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun validateCredentials(email: String, password: String) {
        require(email.trim().contains('@')) { "Enter a valid email address." }
        require(password.length >= MIN_PASSWORD_LENGTH) { "Use a password with at least 8 characters." }
        require(password.length <= 1024) { "That password is too long." }
    }

    private fun friendlyError(error: Exception): String = when (error) {
        is UsernameTakenException -> error.message ?: "That username is already taken. Choose another one."
        is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> "An account already uses this email. Sign in instead."
            "ERROR_INVALID_EMAIL" -> "Enter a valid email address."
            "ERROR_WEAK_PASSWORD" -> "Use a stronger password with at least 8 characters."
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" -> "The email or password is incorrect."
            "ERROR_NETWORK_REQUEST_FAILED" -> "Check your internet connection and try again."
            else -> "Firebase could not complete this account request. Check the Authentication setup and try again."
        }
        is FirebaseFirestoreException -> when (error.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Firebase denied cloud sync. Publish the Firestore security rules from the project README."
            FirebaseFirestoreException.Code.UNAVAILABLE -> "Cloud sync is offline. Your study data remains on this phone."
            else -> "Cloud sync could not finish. Your study data remains on this phone."
        }
        is kotlinx.coroutines.TimeoutCancellationException -> "Cloud sync is taking too long. Check the connection and try again."
        is IllegalArgumentException, is IllegalStateException -> error.message ?: "Check the information and try again."
        else -> "The request could not be completed. Check your connection and Firebase setup."
    }

    private fun deletionKey(sessionId: String) = "deletion_$sessionId"
    private fun sessionKey(id: String) = "session_$id"
    private fun questionKey(id: String) = "question_$id"
    private fun messageKey(id: String) = "message_$id"
    private fun activityKey(id: String) = "activity_$id"

    private fun compareVersions(localTime: Long, local: Map<String, Any?>, cloudTime: Long, cloud: Map<String, Any?>): Int {
        val timeOrder = localTime.compareTo(cloudTime)
        return if (timeOrder != 0) timeOrder else gson.toJson(local.toSortedMap()).compareTo(gson.toJson(cloud.toSortedMap()))
    }

    private fun StudySessionEntity.toCloudRecord() = mapOf(
        "type" to "session", "id" to id, "title" to title, "sourceLabel" to sourceLabel,
        "summary" to summary, "sourceContext" to sourceContext, "practiceLanguage" to practiceLanguage,
        "coveredTopicsSummary" to coveredTopicsSummary, "createdAt" to createdAt,
        "generationMode" to generationMode, "studyKind" to studyKind, "goal" to goal,
        "level" to level, "intensity" to intensity, "target" to target, "modulesJson" to modulesJson,
        "lastOpenedAt" to lastOpenedAt, "updatedAt" to updatedAt,
    )

    private fun StudyQuestionEntity.toCloudRecord() = mapOf(
        "type" to "question", "id" to id, "sessionId" to sessionId, "position" to position,
        "category" to category, "prompt" to prompt, "topic" to topic,
        "evaluationCriteria" to evaluationCriteria, "referenceAnswer" to referenceAnswer,
        "sourceBasis" to sourceBasis, "learnerAnswer" to learnerAnswer, "strengths" to strengths,
        "missingPoints" to missingPoints, "reasoningFeedback" to reasoningFeedback,
        "improvedReferenceAnswer" to improvedReferenceAnswer, "reviewSuggested" to reviewSuggested,
        "markedForReview" to markedForReview, "optionsJson" to optionsJson,
        "correctOptionIndex" to correctOptionIndex, "explanation" to explanation,
        "selectedOptionIndex" to selectedOptionIndex, "conceptLessonJson" to conceptLessonJson,
        "format" to format, "moduleId" to moduleId, "alternativesJson" to alternativesJson,
        "answeredAt" to answeredAt, "score" to score, "updatedAt" to updatedAt,
    )

    private fun TutorMessageEntity.toCloudRecord() = mapOf(
        "type" to "message", "id" to id, "sessionId" to sessionId, "questionId" to questionId,
        "role" to role, "text" to text, "createdAt" to createdAt,
    )

    private fun StudyActivityEntity.toCloudRecord() = mapOf(
        "type" to "activity", "id" to id, "sessionId" to sessionId,
        "startedAt" to startedAt, "durationSeconds" to durationSeconds,
    )

    private fun StudyDeletionEntity.toCloudRecord() = mapOf(
        "type" to "deletion", "sessionId" to sessionId, "deletedAt" to deletedAt,
    )

    private fun decodeSession(data: Map<String, Any?>): StudySessionEntity? = if (data["type"] != "session") null else runCatching {
        val createdAt = data.long("createdAt")
        StudySessionEntity(
            id = data.string("id"), title = data.string("title"), sourceLabel = data.string("sourceLabel"),
            summary = data.string("summary"), sourceContext = data.string("sourceContext"),
            practiceLanguage = data.string("practiceLanguage"), coveredTopicsSummary = data.string("coveredTopicsSummary"),
            createdAt = createdAt, generationMode = data.string("generationMode", "OFFLINE"),
            studyKind = data.string("studyKind", "JOB"), goal = data.string("goal"),
            level = data.string("level", "Beginner"), intensity = data.string("intensity", "Balanced"),
            target = data.string("target"), modulesJson = data.string("modulesJson", "[]"),
            lastOpenedAt = data.long("lastOpenedAt"), updatedAt = data.long("updatedAt", createdAt),
        ).takeIf { it.id.isNotBlank() }
    }.getOrNull()

    private fun decodeQuestion(data: Map<String, Any?>): StudyQuestionEntity? = if (data["type"] != "question") null else runCatching {
        StudyQuestionEntity(
            id = data.string("id"), sessionId = data.string("sessionId"), position = data.int("position"),
            category = data.string("category"), prompt = data.string("prompt"), topic = data.string("topic"),
            evaluationCriteria = data.string("evaluationCriteria"), referenceAnswer = data.string("referenceAnswer"),
            sourceBasis = data.string("sourceBasis"), learnerAnswer = data.string("learnerAnswer"),
            strengths = data.string("strengths"), missingPoints = data.string("missingPoints"),
            reasoningFeedback = data.string("reasoningFeedback"), improvedReferenceAnswer = data.string("improvedReferenceAnswer"),
            reviewSuggested = data.boolean("reviewSuggested"), markedForReview = data.boolean("markedForReview"),
            optionsJson = data.string("optionsJson", "[]"), correctOptionIndex = data.int("correctOptionIndex", -1),
            explanation = data.string("explanation"), selectedOptionIndex = data.int("selectedOptionIndex", -1),
            conceptLessonJson = data.string("conceptLessonJson"), format = data.string("format", "MULTIPLE_CHOICE"),
            moduleId = data.string("moduleId"), alternativesJson = data.string("alternativesJson", "[]"),
            answeredAt = data.long("answeredAt"), score = data.int("score", -1), updatedAt = data.long("updatedAt"),
        ).takeIf { it.id.isNotBlank() && it.sessionId.isNotBlank() }
    }.getOrNull()

    private fun decodeMessage(data: Map<String, Any?>): TutorMessageEntity? = if (data["type"] != "message") null else runCatching {
        TutorMessageEntity(data.string("id"), data.string("sessionId"), data.string("questionId"), data.string("role"), data.string("text"), data.long("createdAt"))
            .takeIf { it.id.isNotBlank() && it.sessionId.isNotBlank() }
    }.getOrNull()

    private fun decodeActivity(data: Map<String, Any?>): StudyActivityEntity? = if (data["type"] != "activity") null else runCatching {
        StudyActivityEntity(data.string("id"), data.string("sessionId"), data.long("startedAt"), data.long("durationSeconds"))
            .takeIf { it.id.isNotBlank() && it.sessionId.isNotBlank() }
    }.getOrNull()

    private fun decodeDeletion(data: Map<String, Any?>): StudyDeletionEntity? = if (data["type"] != "deletion") null else runCatching {
        StudyDeletionEntity(data.string("sessionId"), data.long("deletedAt")).takeIf { it.sessionId.isNotBlank() }
    }.getOrNull()

    private fun Map<String, Any?>.string(key: String, fallback: String = "") = this[key] as? String ?: fallback
    private fun Map<String, Any?>.long(key: String, fallback: Long = 0) = (this[key] as? Number)?.toLong() ?: fallback
    private fun Map<String, Any?>.int(key: String, fallback: Int = 0) = (this[key] as? Number)?.toInt() ?: fallback
    private fun Map<String, Any?>.boolean(key: String, fallback: Boolean = false) = this[key] as? Boolean ?: fallback

    companion object {
        private const val MIN_PASSWORD_LENGTH = 8
        private const val PROFILE_TIMEOUT_MILLIS = 20_000L
        private const val FIRESTORE_BATCH_SIZE = 400
        private const val SYNC_TIMEOUT_MILLIS = 25_000L
        private const val ACCOUNT_DELETE_TIMEOUT_MILLIS = 180_000L
        private val USERNAME_PATTERN = Regex("[A-Za-z0-9_]{3,20}")
    }
}
