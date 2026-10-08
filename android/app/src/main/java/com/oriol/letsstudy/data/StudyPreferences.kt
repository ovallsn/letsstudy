package com.oriol.letsstudy.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import com.oriol.letsstudy.R

class StudyPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("learner_settings", Context.MODE_PRIVATE)
    fun read() = LearnerSettings(prefs.getString("name", "").orEmpty(), prefs.getString("language", "English").orEmpty(),
        prefs.getString("mode", "ONLINE").orEmpty(), prefs.getBoolean("daily", false), prefs.getBoolean("weekly", false),
        prefs.getInt("hour", 19), prefs.getInt("minute", 0), prefs.getBoolean("online_disclosure", false),
        LearnerAvatarIds.safe(prefs.getString("avatar", LearnerAvatarIds.DEFAULT)), prefs.getString("profile_photo_path", "").orEmpty())
    fun save(value: LearnerSettings) { prefs.edit().putString("name", value.name).putString("language", value.language)
        .putString("mode", value.mode).putBoolean("daily", value.dailyReminder).putBoolean("weekly", value.weeklySummary)
        .putInt("hour", value.reminderHour).putInt("minute", value.reminderMinute).putBoolean("online_disclosure", value.onlineDisclosureAccepted)
        .putString("avatar", LearnerAvatarIds.safe(value.avatarId)).putString("profile_photo_path", value.photoPath).apply() }
}

object StudyReminders {
    fun schedule(context: Context, settings: LearnerSettings) {
        val manager = WorkManager.getInstance(context)
        listOf("daily" to settings.dailyReminder, "weekly" to settings.weeklySummary).forEach { (kind, enabled) ->
            val name = "study-reminder-$kind"
            if (!enabled) manager.cancelUniqueWork(name)
            else {
                val next = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, settings.reminderHour); set(Calendar.MINUTE, settings.reminderMinute); set(Calendar.SECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DATE, 1)
                }
                val request = PeriodicWorkRequestBuilder<StudyReminderWorker>(if (kind == "daily") 1L else 7L, TimeUnit.DAYS)
                    .setInitialDelay(next.timeInMillis - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf("kind" to kind)).build()
                manager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
            }
        }
    }
}

class StudyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= 33 && applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("study-reminders", "Study reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val weekly = inputData.getString("kind") == "weekly"
        val settings = StudyPreferences(applicationContext).read()
        if ((weekly && !settings.weeklySummary) || (!weekly && !settings.dailyReminder)) return Result.success()
        val dao = StudyDatabase.get(applicationContext).studyDao()
        val questions = dao.observeAllQuestions().first()
        val answered = questions.count { it.answeredAt >= System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7) }
        val intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pending = intent?.let { android.app.PendingIntent.getActivity(applicationContext, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT) }
        val notification = NotificationCompat.Builder(applicationContext, "study-reminders")
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle(if (weekly) "Your week of learning" else "A little practice goes a long way")
            .setContentText(if (weekly) "$answered questions answered this week. Keep your momentum going." else "Your saved studies are ready. Make a little time for yourself.")
            .setContentIntent(pending).setAutoCancel(true).build()
        manager.notify(if (weekly) 102 else 101, notification)
        return Result.success()
    }
}
