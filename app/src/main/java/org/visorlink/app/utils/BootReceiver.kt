package org.visorlink.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.visorlink.app.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class BootReceiver : BroadcastReceiver(), KoinComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            val pendingResult = goAsync()
            scope.launch {
                try {
                    val userRepository: UserRepository = get()
                    val reminderManager: DiaryReminderManager = get()
                    
                    val profile = userRepository.currentUserFlow().firstOrNull()
                    if (profile?.diaryEnabled == true && profile.diaryRemindersEnabled) {
                        reminderManager.scheduleReminder(profile.diaryReminderTime)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BootReceiver", "Failed to reschedule reminders on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
