package com.tamerslab.companion.card

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tamerslab.hardware.dim.adventure.AdventureLevels
import com.tamerslab.hardware.dim.adventure.AdventureLevels.AdventureLevel
import com.tamerslab.hardware.dim.card.Card
import com.tamerslab.hardware.dim.card.DimReader
import com.tamerslab.hardware.dim.card.DimWriter
import com.tamerslab.hardware.dim.character.CharacterStats
import com.tamerslab.hardware.dim.character.CharacterStats.CharacterStatsEntry
import com.tamerslab.hardware.dim.fusion.AttributeFusions
import com.tamerslab.hardware.dim.fusion.SpecificFusions
import com.tamerslab.hardware.dim.fusion.SpecificFusions.SpecificFusionEntry
import com.tamerslab.hardware.dim.header.DimHeader
import com.tamerslab.hardware.dim.transformation.TransformationRequirements
import com.tamerslab.hardware.dim.transformation.TransformationRequirements.TransformationRequirementsEntry
import com.tamerslab.R
import com.tamerslab.companion.card.CompanionValidateCardActivity
import com.tamerslab.companion.common.ChannelTypes
import com.tamerslab.companion.ui.CompanionLoading
import com.tamerslab.components.VitalButton
import com.tamerslab.companion.validation.ValidatedCardManager
import com.tamerslab.di.TamersLabApplication
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.Charset

class CompanionImportCardActivity : ComponentActivity() {
    enum class ImportState {
        PickFile,
        NameOrUnique,
        LoadFile,
        UnlockCard,
        ImportCard,
        Success,
    }

    private lateinit var filePickLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var validationLauncher: ActivityResultLauncher<Intent>
    private var uri: Uri? = null
    private val importState = MutableStateFlow(ImportState.PickFile)
    private val cardName = MutableStateFlow("")
    private val uniqueSprites = MutableStateFlow(false)
    private val convertToBem = MutableStateFlow(false)
    private val importPercent = MutableStateFlow(0)
    private val importStatus = MutableStateFlow("Preparing transfer")
    private lateinit var card: Card<out DimHeader, out CharacterStats<out CharacterStatsEntry>, out TransformationRequirements<out TransformationRequirementsEntry>, out AdventureLevels<out AdventureLevel>, out AttributeFusions, out SpecificFusions<out SpecificFusionEntry>>

    private val validatedCardManager by lazy {
        (application as TamersLabApplication).validatedCardManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filePickLauncher = buildFilePickLauncher()
        validationLauncher = buildValidationLauncher()
        setContent {
            val state by importState.collectAsStateWithLifecycle()
            when (state) {
                ImportState.PickFile -> {
                    LaunchedEffect(true) {
                        filePickLauncher.launch(arrayOf("*/*"))
                    }
                    CompanionLoading(loadingText = stringResource(R.string.settings_companion_import_card_image_title))
                }
                ImportState.NameOrUnique -> NameOrUnique()
                ImportState.LoadFile -> {
                    CompanionLoading(loadingText = "Loading Card Image")
                    LaunchedEffect(true) {
                        runCatching {
                            loadCard()
                        }.onFailure { error ->
                            Timber.e(error, "Failed to load card image")
                            Toast.makeText(
                                this@CompanionImportCardActivity,
                                "Card load failed: ${error.message ?: "unknown error"}",
                                Toast.LENGTH_LONG,
                            ).show()
                            importState.value = ImportState.NameOrUnique
                        }
                    }
                }
                ImportState.UnlockCard -> {
                    CompanionLoading(loadingText = stringResource(R.string.companion_validation_connect_vb))
                    LaunchedEffect(true) {
                        validationLauncher.launch(
                            Intent(applicationContext, CompanionValidateCardActivity::class.java).apply {
                                putExtra(CompanionValidateCardActivity.CARD_VALIDATED_KEY, card.header.dimId)
                            }
                        )
                    }
                }
                ImportState.ImportCard -> {
                    val percent by importPercent.collectAsStateWithLifecycle()
                    val status by importStatus.collectAsStateWithLifecycle()
                    CompanionLoading(loadingText = "$status ($percent%)")
                    LaunchedEffect(true) {
                        withContext(Dispatchers.IO) {
                            try {
                                val transferred = importCard()
                                importState.value = if (transferred) ImportState.Success else ImportState.NameOrUnique
                            } catch (error: Exception) {
                                Timber.e(error, "Card transfer failed")
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        this@CompanionImportCardActivity,
                                        "Card transfer failed: ${error.message ?: "unknown error"}",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                                importState.value = ImportState.NameOrUnique
                            }
                        }
                    }
                }
                ImportState.Success -> {
                    LaunchedEffect(true) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            finish()
                        }, 1000)
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(R.string.companion_card_import_success))
                    }
                }
            }
        }
    }

    @Composable
    private fun NameOrUnique() {
        val selectedUri = uri
        val filePath = selectedUri?.path ?: stringResource(R.string.app_file)
        val name by cardName.collectAsStateWithLifecycle()
        val unique by uniqueSprites.collectAsStateWithLifecycle()
        val convert by convertToBem.collectAsStateWithLifecycle()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VitalButton(modifier = Modifier.padding(end = 10.dp), onClick = { importState.value = ImportState.PickFile }) {
                    Text(stringResource(R.string.app_file))
                }
                Text(text = filePath)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.app_field_name), modifier = Modifier.padding(end = 10.dp))
                TextField(value = name, onValueChange = { cardName.value = it })
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.app_unique_sprites))
                Checkbox(checked = unique, onCheckedChange = { uniqueSprites.value = it })
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.app_convert_to_bem))
                Checkbox(checked = convert, onCheckedChange = { convertToBem.value = it })
            }
            VitalButton(
                style = com.tamerslab.components.VitalButtonStyle.PRIMARY,
                modifier = Modifier.padding(top = 16.dp),
                enabled = selectedUri != null,
                onClick = { importState.value = ImportState.LoadFile },
            ) {
                Text(stringResource(R.string.app_import))
            }
        }
    }

    private fun buildFilePickLauncher(): ActivityResultLauncher<Array<String>> {
        return registerForActivityResult(ActivityResultContracts.OpenDocument()) {
            if (it == null) {
                Toast.makeText(
                    this,
                    "Card file selection cancelled.",
                    Toast.LENGTH_SHORT,
                ).show()
                importState.value = ImportState.NameOrUnique
            } else {
                uri = it
                val path = it.path ?: ""
                var name = path.substringAfterLast("/")
                if (name.contains('.')) {
                    name = name.substringBeforeLast('.')
                }
                cardName.value = name
                importState.value = ImportState.NameOrUnique
            }
        }
    }

    private fun buildValidationLauncher(): ActivityResultLauncher<Intent> {
        return registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val validatedCardId = result.data?.extras?.getInt(CompanionValidateCardActivity.CARD_VALIDATED_KEY, -1)
            if (result.resultCode != RESULT_OK || validatedCardId == null || validatedCardId == -1) {
                Toast.makeText(
                    this,
                    "Card validation was cancelled.",
                    Toast.LENGTH_SHORT,
                ).show()
                importState.value = ImportState.NameOrUnique
            } else {
                lifecycleScope.launch(Dispatchers.IO) {
                    validatedCardManager.addValidatedCard(validatedCardId)
                    importState.value = ImportState.ImportCard
                }
            }
        }
    }

    private suspend fun loadCard() {
        val selectedUri = uri
        if (selectedUri == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@CompanionImportCardActivity,
                    "No card file selected.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            importState.value = ImportState.NameOrUnique
            return
        }
        val dimReader = DimReader()
        val inputStream = contentResolver.openInputStream(selectedUri)
        if (inputStream == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@CompanionImportCardActivity,
                    "Unable to open selected card file.",
                    Toast.LENGTH_LONG,
                ).show()
            }
            importState.value = ImportState.NameOrUnique
            return
        }
        inputStream.use {
            card = dimReader.readCard(it, false)
            if (validatedCardManager.isValidatedCard(card.header.dimId)) {
                importState.value = ImportState.ImportCard
            } else {
                importState.value = ImportState.UnlockCard
            }
        }
    }

    private suspend fun importCard(): Boolean {
        importPercent.value = 0
        importStatus.value = "Preparing card"
        val channelClient = Wearable.getChannelClient(this)
        val nodes = Wearable.getNodeClient(this).connectedNodes.await()
        Timber.i("Card transfer start: connectedNodes=${nodes.size}, ids=${nodes.joinToString { it.id }}")
        if (nodes.isEmpty()) {
            importStatus.value = getString(R.string.companion_firmware_no_watch)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@CompanionImportCardActivity,
                    getString(R.string.companion_firmware_no_watch),
                    Toast.LENGTH_LONG,
                ).show()
            }
            return false
        }

        val cardWriter = DimWriter()
        val cardPayload = ByteArrayOutputStream().use { outputStream ->
            cardWriter.writeCard(card, outputStream)
            outputStream.toByteArray()
        }
        val cardNameBytes = cardName.value.toByteArray(Charset.defaultCharset())
        val metadataLength = cardNameBytes.size + 1 + 1 + 1 + 4
        val totalBytes = (metadataLength + cardPayload.size).toLong() * nodes.size
        var transferredBytes = 0L
        var successfulTransfers = 0
        val failedNodes = mutableListOf<String>()
        for (node in nodes) {
            val nodeName = node.displayName.takeIf { it.isNotBlank() } ?: node.id
            importStatus.value = "Connecting to $nodeName"
            try {
                Timber.i("Opening card channel for nodeId=${node.id}, displayName=${node.displayName}")
                val channel = channelClient.openChannel(node.id, ChannelTypes.CARD_DATA).await()
                channelClient.getOutputStream(channel).await().use { os ->
                    val output = DataOutputStream(os)
                    output.write(cardNameBytes)
                    output.writeByte(0)
                    output.writeByte(if (uniqueSprites.value) 1 else 0)
                    output.writeByte(if (convertToBem.value) 1 else 0)
                    output.writeInt(cardPayload.size)
                    transferredBytes += metadataLength
                    val metadataPercent = if (totalBytes == 0L) 0 else ((transferredBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                    importStatus.value = "Sending card to $nodeName"
                    importPercent.value = metadataPercent

                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    cardPayload.inputStream().use { payloadInput ->
                        while (payloadInput.read(buffer).also { bytesRead = it } >= 0) {
                            output.write(buffer, 0, bytesRead)
                            transferredBytes += bytesRead
                            val transferPercent = if (totalBytes == 0L) 0 else ((transferredBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            importPercent.value = transferPercent
                        }
                    }
                    output.flush()
                }
                channelClient.close(channel).await()
                successfulTransfers++
                Timber.i("Card transfer complete for nodeId=${node.id}, card=${cardName.value}, payloadBytes=${cardPayload.size}")
            } catch (error: Exception) {
                failedNodes.add("$nodeName: ${error.message ?: "unknown error"}")
                Timber.e(error, "Card transfer failed for nodeId=${node.id}, displayName=${node.displayName}")
            }
        }

        if (successfulTransfers == 0) {
            importStatus.value = "Transfer failed"
            importPercent.value = 0
            withContext(Dispatchers.Main) {
                val reason = failedNodes.firstOrNull() ?: "No watch accepted the transfer"
                Toast.makeText(this@CompanionImportCardActivity, getString(R.string.ui_card_transfer_failed, reason), Toast.LENGTH_LONG).show()
            }
            return false
        }

        importStatus.value = "Transfer complete"
        importPercent.value = 100
        if (failedNodes.isNotEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@CompanionImportCardActivity,
                    "Sent to $successfulTransfers watch(es); ${failedNodes.size} failed.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

        Timber.i("Companion-style card import sent to watch for ${cardName.value}; success=$successfulTransfers failures=${failedNodes.size}")
        return true
    }
}

