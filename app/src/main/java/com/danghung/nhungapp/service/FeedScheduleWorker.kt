package com.danghung.nhungapp.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.danghung.nhungapp.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FeedScheduleWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_SCHEDULE_ID = "key_schedule_id"
    }

    override suspend fun doWork(): Result {
        val scheduleId = inputData.getLong(KEY_SCHEDULE_ID, -1L)
        if (scheduleId <= 0L) return Result.failure()

        return runCatching {
            val executeSuccess = FeedScheduleExecutionEngine.executeAndNotify(
                applicationContext,
                scheduleId,
                "Worker"
            )

            val isDone = withContext(Dispatchers.IO) {
                App.instance.appDatabase.scheduleDao().getById(scheduleId)?.status == true
            }

            if (executeSuccess || isDone) Result.success() else Result.retry()
        }.getOrElse {
            Result.retry()
        }
    }
}
