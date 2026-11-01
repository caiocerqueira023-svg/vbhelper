package com.tamerslab.transfer.data.gameSave

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

/** One normal file selection requests the provider access needed for later explicit moves. */
class OpenGameSaveDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent = super.createIntent(context, input).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }
}
