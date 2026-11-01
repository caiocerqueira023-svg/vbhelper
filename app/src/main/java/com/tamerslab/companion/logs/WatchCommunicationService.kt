package com.tamerslab.companion.logs

import com.tamerslab.companion.common.ChannelTypes
import com.tamerslab.di.TamersLabApplication
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.WearableListenerService
import timber.log.Timber

class WatchCommunicationService : WearableListenerService() {
    override fun onChannelOpened(channel: ChannelClient.Channel) {
        super.onChannelOpened(channel)
        when (channel.path) {
            ChannelTypes.LOGS_DATA -> {
                val logService = (application as TamersLabApplication).companionLogService
                logService.receiveFile(this, channel)
            }
            else -> Timber.i("Unknown channel: ${channel.path}")
        }
    }
}

