package com.oriol.letsstudy

import android.content.Context
import androidx.core.content.edit

class LearnerAgeStore(context: Context) {
    private val preferences = context.getSharedPreferences("letsstudy_age_route", Context.MODE_PRIVATE)

    fun read(): LearnerAgeGroup? = LearnerAgeGroup.fromStoredValue(preferences.getString("age_group", null))

    fun save(group: LearnerAgeGroup) {
        preferences.edit { putString("age_group", group.name) }
    }
}

class YouthProgressStore(context: Context, group: LearnerAgeGroup) {
    private val preferences = context.getSharedPreferences("letsstudy_youth_progress", Context.MODE_PRIVATE)
    private val key = "answers_${group.name}"
    private val knownIds = YouthCourseCatalog.forGroup(group).flatMap { it.questions }.mapTo(mutableSetOf()) { it.id }

    fun read(): YouthProgress = YouthProgress.fromStoredValues(preferences.getStringSet(key, emptySet()).orEmpty(), knownIds)

    fun save(progress: YouthProgress) {
        preferences.edit { putStringSet(key, progress.storedValues()) }
    }

    fun clear() {
        preferences.edit { remove(key) }
    }
}
