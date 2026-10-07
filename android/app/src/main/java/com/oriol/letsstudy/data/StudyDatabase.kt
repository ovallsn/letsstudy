package com.oriol.letsstudy.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [StudySessionEntity::class, StudyQuestionEntity::class], version = 3, exportSchema = false)
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

        fun get(context: Context): StudyDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                StudyDatabase::class.java,
                "roleready.db",
            ).addMigrations(migration1To2, migration2To3).build().also { instance = it }
        }
    }
}
