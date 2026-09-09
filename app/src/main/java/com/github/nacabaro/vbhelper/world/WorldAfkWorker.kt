package com.github.nacabaro.vbhelper.world

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import DefaultAppContainer
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

class WorldAfkWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = DefaultAppContainer(applicationContext)
        val location = runCatching {
            LocationServices.getFusedLocationProviderClient(applicationContext).lastLocation.await()
        }.getOrNull() ?: return Result.retry()
        container.worldRepository.ensureSpawns(location.latitude, location.longitude)
        return Result.success()
    }
}
