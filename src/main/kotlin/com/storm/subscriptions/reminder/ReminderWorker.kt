package io.github.folk97stormi.subtrack.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.folk97stormi.subtrack.data.AppDatabase
import io.github.folk97stormi.subtrack.util.AppLogger
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val subscriptionId = inputData.getLong(KEY_SUBSCRIPTION_ID, -1L)
        if (subscriptionId <= 0) return Result.success()
        AppLogger.d("ReminderWorker started for subscriptionId=$subscriptionId")

        val subscription = AppDatabase.create(applicationContext)
            .subscriptionDao()
            .getById(subscriptionId) ?: return Result.success()

        val billingDate = LocalDate.ofEpochDay(subscription.nextBillingEpochDay)
        val daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), billingDate)
        if (daysUntil !in 1..3) return Result.success()
        if (!canPostNotifications()) return Result.success()

        createChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Upcoming charge")
            .setContentText("${subscription.name} renews in $daysUntil day${if (daysUntil == 1L) "" else "s"}.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(subscription.id.toInt(), notification)
        AppLogger.d("Reminder notification shown for subscriptionId=$subscriptionId")

        return Result.success()
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Subscription reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val KEY_SUBSCRIPTION_ID = "subscription_id"
        const val CHANNEL_ID = "subscription_reminders"
    }
}
