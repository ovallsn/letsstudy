package com.oriol.letsstudy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [StudySessionEntity::class, StudyQuestionEntity::class, TutorMessageEntity::class, StudyActivityEntity::class, StudyPlacementAttemptEntity::class, StudyDeletionEntity::class], version = 9, exportSchema = false)
abstract class StudyDatabase : RoomDatabase() {
    abstract fun studyDao(): StudyDao

    companion object {
        @Volatile private var instance: StudyDatabase? = null
        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE study_sessions ADD COLUMN generationMode TEXT NOT NULL DEFAULT 'OFFLINE'")
                db.execSQL("ALTER TABLE study_questions ADD COLUMN optionsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE study_questions ADD COLUMN correctOptionIndex INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE study_questions ADD COLUMN explanation TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE study_questions ADD COLUMN selectedOptionIndex INTEGER NOT NULL DEFAULT -1")
            }
        }
        private val migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE study_questions ADD COLUMN conceptLessonJson TEXT NOT NULL DEFAULT ''")
            }
        }

        val migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("studyKind TEXT NOT NULL DEFAULT 'JOB'", "goal TEXT NOT NULL DEFAULT ''", "level TEXT NOT NULL DEFAULT 'Beginner'", "intensity TEXT NOT NULL DEFAULT 'Balanced'", "target TEXT NOT NULL DEFAULT ''", "modulesJson TEXT NOT NULL DEFAULT '[]'", "lastOpenedAt INTEGER NOT NULL DEFAULT 0").forEach {
                    db.execSQL("ALTER TABLE study_sessions ADD COLUMN $it")
                }
                listOf("format TEXT NOT NULL DEFAULT 'MULTIPLE_CHOICE'", "moduleId TEXT NOT NULL DEFAULT ''", "alternativesJson TEXT NOT NULL DEFAULT '[]'", "answeredAt INTEGER NOT NULL DEFAULT 0", "score INTEGER NOT NULL DEFAULT -1").forEach {
                    db.execSQL("ALTER TABLE study_questions ADD COLUMN $it")
                }
                db.execSQL("CREATE TABLE IF NOT EXISTS tutor_messages (id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, questionId TEXT NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tutor_messages_sessionId ON tutor_messages(sessionId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS study_activity (id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, startedAt INTEGER NOT NULL, durationSeconds INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_activity_sessionId ON study_activity(sessionId)")
            }
        }

        private val migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE study_sessions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE study_sessions SET updatedAt = MAX(createdAt, lastOpenedAt)")
                db.execSQL("ALTER TABLE study_questions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE study_questions SET updatedAt = MAX(answeredAt, COALESCE((SELECT createdAt FROM study_sessions WHERE study_sessions.id = study_questions.sessionId), 0))")
                db.execSQL("CREATE TABLE IF NOT EXISTS study_deletions (sessionId TEXT NOT NULL PRIMARY KEY, deletedAt INTEGER NOT NULL)")
            }
        }

        private val migration5To6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_study_questions_sessionId_position")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_study_questions_sessionId_position ON study_questions(sessionId, position)")
            }
        }

        private val migration6To7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE study_questions ADD COLUMN reviewIntervalDays INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE study_questions ADD COLUMN reviewStreak INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE study_questions ADD COLUMN nextReviewAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE study_questions ADD COLUMN lastReviewedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val migration7To8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS language_placement_attempts (id TEXT NOT NULL, language TEXT NOT NULL, level TEXT NOT NULL, estimatedRange TEXT NOT NULL, startingLevel TEXT NOT NULL, correct INTEGER NOT NULL, total INTEGER NOT NULL, nextFocus TEXT NOT NULL, answersCsv TEXT NOT NULL, completedAt INTEGER NOT NULL, deletedAt INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_language_placement_attempts_completedAt ON language_placement_attempts(completedAt)")
            }
        }

        private val migration8To9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE language_placement_attempts ADD COLUMN attemptType TEXT NOT NULL DEFAULT 'PLACEMENT'")
                db.execSQL("ALTER TABLE language_placement_attempts ADD COLUMN bankVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE language_placement_attempts ADD COLUMN previousLevel TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE language_placement_attempts ADD COLUMN progressLevel TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): StudyDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                StudyDatabase::class.java,
                "roleready.db",
            ).addMigrations(migration1To2, migration2To3, migration3To4, migration4To5, migration5To6, migration6To7, migration7To8, migration8To9).build().also { instance = it }
        }
    }
}
