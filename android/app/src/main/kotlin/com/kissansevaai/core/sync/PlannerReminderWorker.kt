package com.kissansevaai.core.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.kissansevaai.R
import com.kissansevaai.core.data.DataStoreManager
import com.kissansevaai.core.database.PlannerTaskDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlannerReminderEntryPoint {
    fun plannerTaskDao(): PlannerTaskDao
    fun dataStoreManager(): DataStoreManager
}

class PlannerReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val ep = EntryPointAccessors.fromApplication(
                applicationContext,
                PlannerReminderEntryPoint::class.java,
            )
            val userId = ep.dataStoreManager().userId.first().orEmpty()
            val pending = ep.plannerTaskDao().getPendingTasks(userId).first()
                .filter { it.reminderEnabled }

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val dueToday = pending.filter { it.scheduledDate <= today }

            if (dueToday.isNotEmpty()) {
                postReminderNotification(dueToday.size, dueToday.first().title)
            }
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Planner reminder skipped: ${e.message}")
            Result.success()
        }
    }

    private fun postReminderNotification(count: Int, topTaskTitle: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "KissanSevaAI Crop Tasks",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Daily reminders for crop irrigation, spraying, and field inspection tasks."
        }
        mgr.createNotificationChannel(channel)

        val summary = if (count == 1) topTaskTitle else "$topTaskTitle (+${count - 1} more)"
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("KissanSevaAI Farm Tasks ($count due)")
            .setContentText(summary)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val TAG = "PlannerReminderWorker"
        const val CHANNEL_ID = "kissan_planner_tasks"
        const val NOTIFICATION_ID = 4102
        const val UNIQUE_WORK_NAME = "kissan_planner_reminders"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<PlannerReminderWorker>(12, TimeUnit.HOURS)
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }
    }
}
