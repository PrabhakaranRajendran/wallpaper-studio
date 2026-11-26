package com.prabhu.wallpaperstudio.wallpaper

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.prabhu.wallpaperstudio.data.Store
import com.prabhu.wallpaperstudio.data.Target
import com.prabhu.wallpaperstudio.render.LockChanger
import java.util.concurrent.TimeUnit

/**
 * Changes the lock screen on a timer, even when Wallpaper Studio isn't the
 * Home screen wallpaper. Android runs this at most every 15 minutes; when the
 * Home wallpaper is ours it also changes the lock screen each time the screen turns off.
 */
class LockWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        Store.init(applicationContext)
        if (Store.lockMode() == "slideshow" && !Battery.stopAll(applicationContext)) {
            val interval = Store.interval(Target.LOCK)
            val due = if (interval <= 0) {
                // "Every screen off" needs our Home wallpaper; without it, change every 15 min.
                System.currentTimeMillis() - Store.long(com.prabhu.wallpaperstudio.data.Keys.last(Target.LOCK), 0L) > 14 * 60_000L
            } else {
                LockChanger.isDue()
            }
            if (due) LockChanger.change(applicationContext, "timer")
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "lock_slideshow"

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            if (Store.lockMode() == "slideshow") {
                val request = PeriodicWorkRequestBuilder<LockWorker>(15, TimeUnit.MINUTES).build()
                wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
            } else {
                wm.cancelUniqueWork(NAME)
            }
        }
    }
}
