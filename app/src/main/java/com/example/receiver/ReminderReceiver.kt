package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Re-schedule alarm on boot
            NotificationHelper.scheduleDailyReminder(context)
        } else {
            // Show the reminder notification
            NotificationHelper.showReminderNotification(context)
        }
    }
}
