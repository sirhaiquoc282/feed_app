package com.danghung.nhungapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OutOfQuotaPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class FeedScheduleReceiver : BroadcastReceiver() {

    companion object {
        private const val WORK_NAME_PREFIX = "feed_schedule_work_"
        private const val PREF_NAME = "feed_schedule_receiver"
        private const val DUPLICATE_WINDOW_MS = 30_000L
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != FeedScheduleAlarmManager.ACTION_FEED_SCHEDULE) return

        val scheduleId = intent.getLongExtra(FeedScheduleAlarmManager.EXTRA_SCHEDULE_ID, -1L)
        if (scheduleId <= 0L) return

        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val key = "last_handled_$scheduleId"
        val lastHandled = prefs.getLong(key, 0L)
        if (now - lastHandled < DUPLICATE_WINDOW_MS) {
            return
        }
        prefs.edit().putLong(key, now).apply()

        val inputData = Data.Builder()
            .putLong(FeedScheduleWorker.KEY_SCHEDULE_ID, scheduleId)
            .build()

        val immediateWork = OneTimeWorkRequestBuilder<FeedScheduleWorker>()
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        val workManager = WorkManager.getInstance(appContext)
        workManager.enqueueUniqueWork(
            "$WORK_NAME_PREFIX$scheduleId",
            ExistingWorkPolicy.REPLACE,
            immediateWork
        )
    }
}
