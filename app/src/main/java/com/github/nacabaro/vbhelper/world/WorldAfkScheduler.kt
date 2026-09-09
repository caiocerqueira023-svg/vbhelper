package com.github.nacabaro.vbhelper.world

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorldAfkScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WorldAfkWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "world-afk-interaction",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
