package com.danghung.nhungapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.danghung.nhungapp.App
import com.danghung.nhungapp.R
import com.danghung.nhungapp.data.local.entity.HistoryEntity
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object FeedScheduleExecutionEngine {

    const val ACTION_SCHEDULE_UPDATED = "com.danghung.nhungapp.ACTION_SCHEDULE_UPDATED"
    const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
    const val EXTRA_SCHEDULE_STATUS = "extra_schedule_status"

    private const val CHANNEL_ID = "schedule_feed_notifications"
    private const val FEED_ACK_TIMEOUT_SECONDS = 20L

    fun markScheduleDoneByTimeout(context: Context, scheduleId: Long, message: String) {
        val appContext = context.applicationContext
        val dao = App.instance.appDatabase.scheduleDao()
        val schedule = runBlocking { dao.getById(scheduleId) } ?: return
        if (schedule.status) return

        runBlocking { dao.updateStatusById(scheduleId, true) }
        notifyScheduleUpdated(appContext, scheduleId, true)
        showSystemNotification(appContext, "Lịch cho ăn", message)
        
        val dateTimeStr = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy | HH:mm:ss", java.util.Locale.forLanguageTag("vi-VN")))
        runBlocking { 
            App.instance.appDatabase.historyDao().insertHistory(
                HistoryEntity(
                    dateTime = dateTimeStr,
                    type = "TỰ ĐỘNG (Lịch #$scheduleId)"
                )
            )
        }
    }

    fun executeAndNotify(context: Context, scheduleId: Long, source: String): Boolean {
        val appContext = context.applicationContext
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "nhungapp:FeedScheduleExecution"
        )
        runCatching { wakeLock.acquire(TimeUnit.MINUTES.toMillis(2)) }

        try {
        val dao = App.instance.appDatabase.scheduleDao()
        val schedule = runBlocking { dao.getById(scheduleId) }

        if (schedule == null) {
            showSystemNotification(appContext, "Lịch cho ăn", "Không tìm thấy lịch #$scheduleId")
            return false
        }
        if (schedule.status) return true

        val claimedRows = runBlocking { dao.claimPendingSchedule(scheduleId) }
        if (claimedRows == 0) return true

        val ackLatch = CountDownLatch(1)
        var feedAckReceived = false
        var connectError: String? = null
        var feedCommandSent = false
        val mqttClient = PetFeederMqttClient(appContext)
        mqttClient.setListener(object : PetFeederMqttClient.Listener {
            override fun onConnected() {
                if (feedCommandSent) return
                feedCommandSent = true
                mqttClient.publishFeedNow()
            }

            override fun onFoodDistance(distanceCm: Int) {
                // no-op
            }

            override fun onFeedDoneReport() {
                feedAckReceived = true
                ackLatch.countDown()
            }

            override fun onError(message: String) {
                connectError = message
                ackLatch.countDown()
            }
        })

        return try {
            mqttClient.connect()
            ackLatch.await(FEED_ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            mqttClient.disconnect()

            if (feedAckReceived) {
                notifyScheduleUpdated(appContext, scheduleId, true)
                showSystemNotification(
                    appContext,
                    "Lịch cho ăn",
                    "Đã cho ăn theo lịch #$scheduleId"
                )
                
                val dateTimeStr = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy | HH:mm:ss", java.util.Locale.forLanguageTag("vi-VN")))
                runBlocking { 
                    App.instance.appDatabase.historyDao().insertHistory(
                        HistoryEntity(
                            dateTime = dateTimeStr,
                            type = "TỰ ĐỘNG (Lịch #$scheduleId)"
                        )
                    )
                }
                
                true
            } else {
                runBlocking { dao.updateStatusById(scheduleId, false) }
                val reason = connectError ?: "thiết bị chưa xác nhận OPEN_FED"
                showSystemNotification(
                    appContext,
                    "Lịch cho ăn",
                    "Cho ăn theo lịch #$scheduleId thất bại: $reason"
                )
                false
            }
        } catch (t: Throwable) {
            runBlocking { dao.updateStatusById(scheduleId, false) }
            showSystemNotification(
                appContext,
                "Lịch cho ăn",
                "Cho ăn theo lịch #$scheduleId thất bại: ${t.message ?: "lỗi không xác định"}"
            )
            false
        }
        } finally {
            if (wakeLock.isHeld) {
                runCatching { wakeLock.release() }
            }
        }
    }

    private fun notifyScheduleUpdated(context: Context, scheduleId: Long, status: Boolean) {
        val intent = Intent(ACTION_SCHEDULE_UPDATED).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_SCHEDULE_STATUS, status)
        }
        context.sendBroadcast(intent)
    }

    private fun showSystemNotification(context: Context, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        createChannelIfNeeded(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pet)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Schedule Feed",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Thong bao ket qua cho an theo lich"
        }
        manager.createNotificationChannel(channel)
    }
}
