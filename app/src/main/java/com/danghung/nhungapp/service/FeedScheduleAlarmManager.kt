package com.danghung.nhungapp.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.danghung.nhungapp.App
import com.danghung.nhungapp.view.activity.HomeActivity
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

object FeedScheduleAlarmManager {
    const val ACTION_FEED_SCHEDULE = "com.danghung.nhungapp.ACTION_FEED_SCHEDULE"
    const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
    private const val WORK_NAME_PREFIX = "feed_schedule_work_"
    private const val WORK_BACKUP_DELAY_MS = 5_000L

    private val dateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy | HH:mm", Locale.forLanguageTag("vi-VN"))

    @SuppressLint("ScheduleExactAlarm")
    fun schedule(context: Context, scheduleId: Long, dateTime: String) {
        val triggerAtMillis = parseDateTimeToEpochMillis(dateTime) ?: return
        if (triggerAtMillis <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createPendingIntent(context, scheduleId)

        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exactAllowed) {
            val exactSet = runCatching {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }.isSuccess

            if (!exactSet) {
                scheduleInexact(alarmManager, triggerAtMillis, pendingIntent)
            }
        } else {
            val alarmClockSet = runCatching {
                val showIntent = PendingIntent.getActivity(
                    context,
                    (scheduleId % Int.MAX_VALUE).toInt() + 1,
                    Intent(context, HomeActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            }.isSuccess

            if (!alarmClockSet) {
                scheduleInexact(alarmManager, triggerAtMillis, pendingIntent)
            }
        }

        enqueueBackupWork(context.applicationContext, scheduleId, triggerAtMillis)
    }

    private fun scheduleInexact(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
    }

    fun cancel(context: Context, scheduleId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createPendingIntent(context, scheduleId)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(workName(scheduleId))
    }

    fun rescheduleAllPending(context: Context) {
        val appContext = context.applicationContext
        val dao = App.instance.appDatabase.scheduleDao()
        val now = System.currentTimeMillis()
        runBlocking {
            dao.getPendingSchedules().forEach { entity ->
                val triggerAtMillis = parseDateTimeToEpochMillis(entity.dateTime)
                if (triggerAtMillis == null) {
                    FeedScheduleExecutionEngine.markScheduleDoneByTimeout(
                        appContext,
                        entity.id,
                        "Lịch #${entity.id} sai định dạng thời gian, đã tự cập nhật Đã ăn"
                    )
                    return@forEach
                }

                if (triggerAtMillis <= now) {
                    FeedScheduleExecutionEngine.markScheduleDoneByTimeout(
                        appContext,
                        entity.id,
                        "Lịch #${entity.id} đã quá giờ, tự động cập nhật Đã ăn"
                    )
                } else {
                    schedule(appContext, entity.id, entity.dateTime)
                }
            }
        }
    }

    private fun createPendingIntent(context: Context, scheduleId: Long): PendingIntent {
        val intent = Intent(context, FeedScheduleReceiver::class.java).apply {
            action = ACTION_FEED_SCHEDULE
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
        }

        val requestCode = (scheduleId % Int.MAX_VALUE).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun parseDateTimeToEpochMillis(dateTime: String): Long? {
        val localDateTime = runCatching {
            LocalDateTime.parse(dateTime, dateTimeFormatter)
        }.getOrNull() ?: return null

        return localDateTime
            .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
            .toInstant()
            .toEpochMilli()
    }

    private fun enqueueBackupWork(context: Context, scheduleId: Long, triggerAtMillis: Long) {
        val now = System.currentTimeMillis()
        val delay = (triggerAtMillis - now + WORK_BACKUP_DELAY_MS).coerceAtLeast(1_000L)

        val input = Data.Builder()
            .putLong(FeedScheduleWorker.KEY_SCHEDULE_ID, scheduleId)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<FeedScheduleWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(input)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(scheduleId), ExistingWorkPolicy.REPLACE, workRequest)
    }

    private fun workName(scheduleId: Long): String = "$WORK_NAME_PREFIX$scheduleId"
}
