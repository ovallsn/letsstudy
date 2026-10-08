package com.oriol.letsstudy.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyDao {
    @Query("SELECT * FROM study_sessions ORDER BY createdAt DESC")
    fun observeSessions(): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions ORDER BY createdAt DESC")
    suspend fun getAllSessions(): List<StudySessionEntity>

    @Query("SELECT * FROM study_sessions WHERE id = :id LIMIT 1")
    suspend fun getSession(id: String): StudySessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSession(session: StudySessionEntity)

    @Transaction
    suspend fun saveSession(session: StudySessionEntity) {
        val existing = getSession(session.id)
        val timestamp = when {
            existing == null -> maxOf(session.updatedAt, session.createdAt)
            session.updatedAt <= existing.updatedAt -> maxOf(System.currentTimeMillis(), existing.updatedAt + 1)
            else -> session.updatedAt
        }
        putSession(session.copy(updatedAt = timestamp))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putQuestions(questions: List<StudyQuestionEntity>)

    @Transaction
    suspend fun saveQuestions(questions: List<StudyQuestionEntity>) {
        val now = System.currentTimeMillis()
        putQuestions(questions.map { question ->
            val existing = getQuestion(question.id)
            val timestamp = if (existing == null) maxOf(question.updatedAt, now)
            else if (question.updatedAt <= existing.updatedAt) maxOf(now, existing.updatedAt + 1)
            else question.updatedAt
            question.copy(updatedAt = timestamp)
        })
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRemoteSession(session: StudySessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRemoteQuestions(questions: List<StudyQuestionEntity>)

    @Transaction
    suspend fun saveCompleteSession(session: StudySessionEntity, questions: List<StudyQuestionEntity>) {
        saveSession(session)
        saveQuestions(questions)
    }

    @Transaction
    suspend fun addQuestionsAndUpdateSession(session: StudySessionEntity, questions: List<StudyQuestionEntity>) {
        saveSession(session)
        saveQuestions(questions)
    }

    @Query("SELECT * FROM study_questions WHERE sessionId = :sessionId ORDER BY position ASC, id ASC")
    fun observeQuestions(sessionId: String): Flow<List<StudyQuestionEntity>>

    @Query("SELECT * FROM study_questions ORDER BY sessionId ASC, position ASC, id ASC")
    fun observeAllQuestions(): Flow<List<StudyQuestionEntity>>

    @Query("SELECT * FROM study_questions ORDER BY sessionId ASC, position ASC, id ASC")
    suspend fun getAllQuestions(): List<StudyQuestionEntity>

    @Query("SELECT * FROM study_questions WHERE sessionId = :sessionId ORDER BY position ASC, id ASC")
    suspend fun getQuestions(sessionId: String): List<StudyQuestionEntity>

    @Query("SELECT * FROM study_questions WHERE id = :id LIMIT 1")
    suspend fun getQuestion(id: String): StudyQuestionEntity?

    @Query("UPDATE study_questions SET selectedOptionIndex = :choice, answeredAt = :answeredAt, updatedAt = MAX(updatedAt + 1, :updatedAt), score = CASE WHEN :choice < 0 THEN -1 WHEN :choice = correctOptionIndex THEN 100 ELSE 0 END WHERE id = :id")
    suspend fun updateChoice(id: String, choice: Int, answeredAt: Long = if (choice < 0) 0 else System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE study_questions SET markedForReview = NOT markedForReview, updatedAt = MAX(updatedAt + 1, :updatedAt) WHERE id = :id")
    suspend fun toggleSavedMark(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE study_questions SET markedForReview = 0, reviewSuggested = 0, updatedAt = MAX(updatedAt + 1, :updatedAt) WHERE id = :id")
    suspend fun removeSavedMark(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE study_sessions SET lastOpenedAt = :time, updatedAt = MAX(updatedAt + 1, :time) WHERE id = :id")
    suspend fun touchSession(id: String, time: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRemoteMessage(message: TutorMessageEntity)

    @Query("SELECT * FROM tutor_messages ORDER BY createdAt ASC")
    fun observeMessages(): Flow<List<TutorMessageEntity>>

    @Query("SELECT * FROM tutor_messages ORDER BY createdAt ASC")
    suspend fun getAllMessages(): List<TutorMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: TutorMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: StudyActivityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRemoteActivity(activity: StudyActivityEntity)

    @Query("SELECT * FROM study_activity ORDER BY startedAt DESC")
    fun observeActivity(): Flow<List<StudyActivityEntity>>

    @Query("SELECT * FROM study_activity ORDER BY startedAt DESC")
    suspend fun getAllActivity(): List<StudyActivityEntity>

    @Query("SELECT * FROM study_deletions ORDER BY deletedAt ASC")
    fun observeDeletionTombstones(): Flow<List<StudyDeletionEntity>>

    @Query("SELECT * FROM study_deletions ORDER BY deletedAt ASC")
    suspend fun getDeletionTombstones(): List<StudyDeletionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDeletionTombstone(deletion: StudyDeletionEntity)

    @Query("DELETE FROM tutor_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessages(sessionId: String)

    @Query("DELETE FROM study_activity WHERE sessionId = :sessionId")
    suspend fun deleteActivity(sessionId: String)

    @Query("DELETE FROM study_questions WHERE sessionId = :sessionId")
    suspend fun deleteQuestionsForSession(sessionId: String)

    @Query("DELETE FROM study_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Transaction
    suspend fun deleteSessionRecords(sessionId: String) {
        deleteMessages(sessionId)
        deleteActivity(sessionId)
        deleteQuestionsForSession(sessionId)
        deleteSessionById(sessionId)
    }

    @Transaction
    suspend fun deleteSessionWithQuestions(sessionId: String) {
        putDeletionTombstone(StudyDeletionEntity(sessionId, System.currentTimeMillis()))
        deleteSessionRecords(sessionId)
    }

    @Transaction
    suspend fun applyRemoteDeletion(deletion: StudyDeletionEntity) {
        val existing = getDeletionTombstones().firstOrNull { it.sessionId == deletion.sessionId }
        if (existing == null || deletion.deletedAt > existing.deletedAt) putDeletionTombstone(deletion)
        deleteSessionRecords(deletion.sessionId)
    }

    @Query("INSERT OR REPLACE INTO study_deletions(sessionId, deletedAt) SELECT id, :deletedAt FROM study_sessions")
    suspend fun markAllSessionsDeleted(deletedAt: Long)

    @Query("DELETE FROM tutor_messages")
    suspend fun clearMessages()

    @Query("DELETE FROM study_activity")
    suspend fun clearActivity()

    @Query("DELETE FROM study_questions")
    suspend fun clearQuestions()

    @Query("DELETE FROM study_sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM study_deletions")
    suspend fun clearDeletionTombstones()

    @Transaction
    suspend fun clearStudyData() {
        markAllSessionsDeleted(System.currentTimeMillis())
        clearMessages()
        clearActivity()
        clearQuestions()
        clearSessions()
    }

    @Transaction
    suspend fun clearAllStudyData() {
        clearMessages()
        clearActivity()
        clearQuestions()
        clearSessions()
        clearDeletionTombstones()
    }

    @Update
    suspend fun updateQuestionRecord(question: StudyQuestionEntity)

    @Transaction
    suspend fun updateQuestion(question: StudyQuestionEntity) {
        val existing = getQuestion(question.id) ?: return
        val updatedAt = maxOf(System.currentTimeMillis(), existing.updatedAt + 1)
        updateQuestionRecord(question.copy(updatedAt = updatedAt))
    }
}
