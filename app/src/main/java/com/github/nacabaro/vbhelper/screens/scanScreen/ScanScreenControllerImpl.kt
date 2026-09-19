package com.github.nacabaro.vbhelper.screens.scanScreen

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.NfcA
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.github.cfogrady.vbnfc.TagCommunicator
import com.github.cfogrady.vbnfc.be.BENfcCharacter
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.cfogrady.vbnfc.vb.VBNfcCharacter
import com.github.nacabaro.vbhelper.ActivityLifecycleListener
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.card.Card
import com.github.nacabaro.vbhelper.screens.scanScreen.converters.FromNfcConverter
import com.github.nacabaro.vbhelper.screens.scanScreen.converters.ToNfcConverter
import com.github.nacabaro.vbhelper.source.VitalWearCharacterExporter
import com.github.nacabaro.vbhelper.source.VitalWearCharacterImporter
import com.github.nacabaro.vbhelper.source.getCryptographicTransformerMap
import com.github.nacabaro.vbhelper.source.isMissingSecrets
import com.github.nacabaro.vbhelper.source.proto.Secrets
import com.github.nacabaro.vbhelper.transfer.hce.VitalWearHceReaderClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.domain.identity.IndividualIdentity
import com.github.nacabaro.vbhelper.domain.identity.WatchTransfer
import com.github.nacabaro.vbhelper.domain.identity.TransferFingerprint
import com.github.nacabaro.vbhelper.source.WatchTransferRepository
import com.github.nacabaro.vbhelper.di.VBHelper
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import androidx.room.withTransaction

class ScanScreenControllerImpl(
    override val secretsFlow: Flow<Secrets>,
    private val componentActivity: ComponentActivity,
    private val registerActivityLifecycleListener: (String, ActivityLifecycleListener)->Unit,
    private val unregisterActivityLifecycleListener: (String)->Unit,
    private val database: AppDatabase,
): ScanScreenController {
    private var lastScannedCharacter: NfcCharacter? = null
    private var pendingExportCharacterId: Long? = null
    private var pendingWatchTransfer: WatchTransfer? = null
    private var selectedImportCardId: Long? = null
    private val handlingTag = AtomicBoolean(false)
    private val nfcAdapter: NfcAdapter

    init {
        val maybeNfcAdapter = NfcAdapter.getDefaultAdapter(componentActivity)
        if (maybeNfcAdapter == null) {
            Toast.makeText(componentActivity,  componentActivity.getString(R.string.scan_no_nfc_on_device), Toast.LENGTH_SHORT).show()
        }
        nfcAdapter = maybeNfcAdapter
        checkSecrets()
    }

    override fun onClickRead(secrets: Secrets, onComplete: ()->Unit, onMultipleCards: (List<Card>) -> Unit) {
        handleTag(
            secrets,
            handlerFunc = { tagCommunicator ->
                try {
                    var resultMessage = componentActivity.getString(R.string.scan_error_generic)
                    var saved = false
                    // Commit the entire import before the watch is told to remove its copy.
                    tagCommunicator.receiveCharacter { character ->
                        val converter = FromNfcConverter(componentActivity, tagCommunicator.deviceKey)
                        resultMessage = selectedImportCardId?.let { converter.addCharacterUsingCard(character, it) }
                            ?: converter.addCharacter(character) { cards, nfcCharacter ->
                                lastScannedCharacter = nfcCharacter
                                componentActivity.runOnUiThread { onMultipleCards(cards) }
                            }
                        saved = resultMessage == "Done reading character!"
                        saved
                    }
                    if (saved) {
                        selectedImportCardId = null
                        cancelRead()
                        componentActivity.runOnUiThread { onComplete() }
                    }
                    resultMessage
                } catch (e: Exception) {
                    Log.e("NFC_READ", "Error reading character from NFC", e)
                    componentActivity.runOnUiThread {
                        Toast.makeText(componentActivity, componentActivity.getString(R.string.scan_error_generic) + ": " + (e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show()
                    }
                    componentActivity.getString(R.string.scan_error_generic)
                }
            },
            hceHandler = { isoDep ->
                try {
                    val client = VitalWearHceReaderClient(isoDep)
                    val importer = VitalWearCharacterImporter(database)
                    val received = client.moveCharacterFromWatch { character ->
                        val result = importer.importCharacter(character)
                        componentActivity.runOnUiThread {
                            Toast.makeText(componentActivity, result.message, Toast.LENGTH_SHORT).show()
                        }
                        result.success
                    }
                    if (received) {
                        cancelRead()
                        componentActivity.runOnUiThread { onComplete() }
                    }
                } catch (e: Exception) {
                    Log.e("NFC_READ", "Error reading character from VitalWear HCE", e)
                    componentActivity.runOnUiThread {
                        Toast.makeText(componentActivity, componentActivity.getString(R.string.scan_error_generic) + ": " + (e.message ?: e.javaClass.simpleName), Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    override fun cancelRead() {
        if(nfcAdapter.isEnabled) {
            nfcAdapter.disableReaderMode(componentActivity)
        }
    }

    override fun registerActivityLifecycleListener(
        key: String,
        activityLifecycleListener: ActivityLifecycleListener
    ) {
        registerActivityLifecycleListener.invoke(key, activityLifecycleListener)
    }

    override fun unregisterActivityLifecycleListener(key: String) {
        unregisterActivityLifecycleListener.invoke(key)
    }

    private fun handleTag(
        secrets: Secrets,
        handlerFunc: (TagCommunicator) -> String,
        hceHandler: ((IsoDep) -> Unit)? = null,
    ) {
        if (!nfcAdapter.isEnabled) {
            showWirelessSettings()
        } else {
            val options = Bundle()
            // Work around for some broken Nfc firmware implementations that poll the card too fast
            options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
            nfcAdapter.enableReaderMode(
                componentActivity,
                buildOnReadTag(secrets, handlerFunc, hceHandler),
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                options
            )
        }
    }

    private fun buildOnReadTag(
        secrets: Secrets,
        handlerFunc: (TagCommunicator) -> String,
        hceHandler: ((IsoDep) -> Unit)? = null,
    ): (Tag) -> Unit {
        return handler@{ tag ->
            if (!handlingTag.compareAndSet(false, true)) return@handler
            try {
            val isoDep = IsoDep.get(tag)
            if (isoDep != null && hceHandler != null) {
                // VitalWear watch via HCE / ISO-DEP
                isoDep.connect()
                isoDep.use { hceHandler(isoDep) }
            } else {
                // Physical Vital Bracelet via raw NFC-A
                val nfcData = NfcA.get(tag)
                if (nfcData == null) {
                    componentActivity.runOnUiThread {
                        Toast.makeText(componentActivity, componentActivity.getString(R.string.scan_tag_not_vb), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    nfcData.connect()
                    nfcData.use {
                        val tagCommunicator = TagCommunicator.getInstance(nfcData, secrets.getCryptographicTransformerMap())
                        val successText = handlerFunc(tagCommunicator)
                        componentActivity.runOnUiThread {
                            Toast.makeText(componentActivity, successText, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            } catch (failure: Exception) {
                Log.e("NFC_TRANSFER", "Could not complete NFC operation", failure)
                componentActivity.runOnUiThread {
                    Toast.makeText(componentActivity, failure.message ?: componentActivity.getString(R.string.scan_error_generic), Toast.LENGTH_LONG).show()
                }
            } finally {
                handlingTag.set(false)
            }
        }
    }

    private fun checkSecrets() {
        componentActivity.lifecycleScope.launch(Dispatchers.IO) {
            if(secretsFlow.stateIn(componentActivity.lifecycleScope).value.isMissingSecrets()) {
                componentActivity.runOnUiThread {
                    Toast.makeText(componentActivity, componentActivity.getString(R.string.scan_missing_secrets), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onClickWrite(
        secrets: Secrets,
        nfcCharacter: NfcCharacter,
        onComplete: () -> Unit
    ) {
        handleTag(
            secrets,
            handlerFunc = { tagCommunicator ->
                try {
                    val transfer = requireNotNull(pendingWatchTransfer) { "No prepared individual transfer." }
                        .copy(deviceKey = tagCommunicator.deviceKey)
                    check(IndividualIdentity.decode(nfcCharacter.appReserved1) == transfer.token) { "Transfer token changed." }
                    val transfers = WatchTransferRepository(database)
                    transfers.prepare(transfer)
                    if (nfcCharacter is VBNfcCharacter) {
                        Log.d("SendCharacter", "VBNfcCharacter")
                        tagCommunicator.sendCharacter(nfcCharacter)
                    } else if (nfcCharacter is BENfcCharacter) {
                        Log.d("SendCharacter", "BENfcCharacter")
                        tagCommunicator.sendCharacter(nfcCharacter)
                    } else error("Unsupported watch character type")
                    transfers.complete(transfer)
                    cancelRead()
                    componentActivity.runOnUiThread { onComplete() }
                    componentActivity.getString(R.string.scan_sent_character_success)
                } catch (e: Throwable) {
                    Log.e("TAG", e.stackTraceToString())
                    e.message ?: componentActivity.getString(R.string.scan_error_generic)
                }
            },
            hceHandler = { isoDep ->
                try {
                    val characterId = requireNotNull(pendingExportCharacterId)
                    val source = requireNotNull(database.userCharacterDao().getCharacterSync(characterId))
                    val proto = VitalWearCharacterExporter(componentActivity, database).buildCharacterProto(characterId)
                    check(VitalWearHceReaderClient(isoDep).sendCharacterToWatchAndConfirm(proto)) {
                        "VitalWear did not confirm import. The storage copy has been preserved."
                    }
                    database.runInTransaction {
                        check(database.userCharacterDao().getCharacterSync(characterId) == source) { "The storage copy changed during transfer." }
                        database.userCharacterDao().deleteCharacterById(characterId)
                    }
                    cancelRead()
                    componentActivity.runOnUiThread { onComplete() }
                } catch (failure: Exception) {
                    Log.e("NFC_WRITE", "VitalWear send failed; source retained", failure)
                    componentActivity.runOnUiThread {
                        Toast.makeText(componentActivity, componentActivity.getString(R.string.scan_error_generic), Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    override fun onClickCheckCard(
        secrets: Secrets,
        nfcCharacter: NfcCharacter,
        onComplete: () -> Unit
    ) {
        handleTag(
            secrets,
            handlerFunc = { tagCommunicator ->
                tagCommunicator.prepareDIMForCharacter(nfcCharacter.dimId)
                componentActivity.runOnUiThread { onComplete() }
                componentActivity.getString(R.string.scan_sent_dim_success)
            },
            hceHandler = { _ ->
                // HCE has no separate physical DIM check. Send only in the write step.
                componentActivity.runOnUiThread { onComplete() }
            }
        )
    }

    // EXTRACTED DIRECTLY FROM EXAMPLE APP
    private fun showWirelessSettings() {
        Toast.makeText(componentActivity,  componentActivity.getString(R.string.scan_nfc_must_be_enabled), Toast.LENGTH_SHORT).show()
        componentActivity.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
    }

    override fun characterFromNfc(
        nfcCharacter: NfcCharacter,
        onMultipleCards: (List<Card>, NfcCharacter) -> Unit
    ): String {
        val nfcConverter = FromNfcConverter(
            componentActivity = componentActivity
        )
        return nfcConverter.addCharacter(nfcCharacter, onMultipleCards)
    }

    override suspend fun characterToNfc(characterId: Long): NfcCharacter = withContext(Dispatchers.IO) {
        database.withTransaction {
        pendingExportCharacterId = characterId
        val nfcGenerator = ToNfcConverter(componentActivity = componentActivity)
        val character = nfcGenerator.characterToNfc(characterId)
        val source = database.userCharacterDao().getCharacter(characterId)
        val individualId = source.individualId
        val fingerprint = TransferFingerprint.of(source)
        val previous = database.watchTransferDao().getByIndividualId(individualId)
        check(previous == null || (previous.sourceCharacterId == characterId && previous.sourceFingerprint == fingerprint)) {
            "An unfinished transfer exists with different data. Receive the Digimon from the watch to recover it."
        }
        val token = previous?.token ?: IndividualIdentity.generate()
        character.appReserved1 = IndividualIdentity.encode(token)
        pendingWatchTransfer = WatchTransfer.capture(token, individualId, character).copy(
            sourceCharacterId = characterId,
            cardId = database.cardDao().getCardByCharacterIdSync(characterId)?.id,
            sourceFingerprint = fingerprint,
            deviceKey = previous?.deviceKey.orEmpty(),
        )
        (componentActivity.applicationContext as VBHelper).container.reactionRepository.snapshotBeforeSendingToWatch(characterId)
        character
        }
    }

    override fun flushCharacter(cardId: Long) {
        // Nothing was removed from the watch while the DIM was ambiguous.
        // Read it again with this selection, persisting before acknowledging transfer.
        selectedImportCardId = cardId
        lastScannedCharacter = null
        Toast.makeText(componentActivity, R.string.scan_selected_card_rescan, Toast.LENGTH_LONG).show()
    }
}
