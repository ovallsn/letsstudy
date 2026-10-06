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

    @Query("SELECT * FROM study_sessions WHERE id = :id LIMIT 1")
    suspend fun getSession(id: String): StudySessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: StudySessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveQuestions(questions: List<StudyQuestionEntity>)

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

    @Query("SELECT * FROM study_questions WHERE sessionId = :sessionId ORDER BY position ASC")
    fun observeQuestions(sessionId: String): Flow<List<StudyQuestionEntity>>

    @Query("SELECT * FROM study_questions WHERE sessionId = :sessionId ORDER BY position ASC")
    suspend fun getQuestions(sessionId: String): List<StudyQuestionEntity>

    @Query("SELECT * FROM study_questions WHERE id = :id LIMIT 1")
    suspend fun getQuestion(id: String): StudyQuestionEntity?

    @Query("UPDATE study_questions SET selectedOptionIndex = :choice WHERE id = :id")
    suspend fun updateChoice(id: String, choice: Int)

    @Query("DELETE FROM study_questions WHERE sessionId = :sessionId")
    suspend fun deleteQuestionsForSession(sessionId: String)

    @Query("DELETE FROM study_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    @Transaction
    suspend fun deleteSessionWithQuestions(sessionId: String) {
        deleteQuestionsForSession(sessionId)
        deleteSessionById(sessionId)
    }

    @Update
    suspend fun updateQuestion(question: StudyQuestionEntity)
}
