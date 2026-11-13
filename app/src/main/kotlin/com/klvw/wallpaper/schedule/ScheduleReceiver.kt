package com.klvw.wallpaper.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduleReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Ep {
        fun scheduleManager(): ScheduleManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ScheduleManager.ACTION_BOUNDARY &&
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) return

        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext, Ep::class.java
        )
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ep.scheduleManager().onBoundaryFired()
            } finally {
                pending.finish()
            }
        }
    }
}
