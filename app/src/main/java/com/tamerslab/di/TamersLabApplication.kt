package com.tamerslab.di

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tamerslab.companion.validation.ValidatedCardManager
import com.tamerslab.companion.logs.CompanionLogService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.tamerslab.storage.data.EvolutionHistoryRepository
import com.tamerslab.world.WorldAfkScheduler
import com.tamerslab.storage.data.StorageRepository
import androidx.room.withTransaction
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class TamersLabApplication : Application() {
    lateinit var container: DefaultAppContainer
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val feedback = com.tamerslab.components.AppFeedback()

    val validatedCardManager: ValidatedCardManager
        get() = container.validatedCardManager

    val companionLogService: CompanionLogService
        get() = container.companionLogService

    override fun onCreate() {
        super.onCreate()
        val languageTag = getSharedPreferences("app_preferences", MODE_PRIVATE)
            .getString("language_tag", null)
        if (!languageTag.isNullOrBlank()) {
            AppCompatDelegate.setApplicationLocales(
                if (languageTag == "system") {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(languageTag)
                }
            )
        }
        startKoin {
            androidContext(this@TamersLabApplication)
            modules(appModules)
        }
        container = GlobalContext.get().get()
        applicationScope.launch {
            try { container.linkedGameSaves.refreshAll() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { Log.e("LinkedGameSave", "Could not refresh linked saves at startup", failure) }
        }
        applicationScope.launch {
            val gate = requireNotNull(container.db.participation)
            gate.observe().collect {
                try { container.db.withTransaction { gate.reconcileLocked() } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { Log.e("PartnerParticipation", "Could not reconcile collection participation", failure) }
            }
        }
        applicationScope.launch {
            container.db.invalidationTracker.createFlow("CardCharacter", "SpeciesProfile", "SpeciesCatalogEntry", "Sprite")
                .collect {
                    try {
                        container.db.expandedCatalog?.refreshImportedBindings()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { Log.e("ExpandedCatalog", "Could not refresh imported Dot bindings", failure) }
                }
        }
        applicationScope.launch {
            try {
                // Reassign legacy or missing personality rows before any chat
                // screen can build a prompt from the old system.
                StorageRepository(container.db).ensureAllPersonalities()
            } catch (failure: Exception) {
                Log.e("DigimonPersonality", "Could not migrate stored personalities", failure)
            }
            try {
                // Attach a permanent species reference to individuals created before
                // the binding existed. Idempotent; never rewrites an existing identity.
                com.tamerslab.species.SpeciesBindingRepository(
                    container.db,
                    catalogLoader = { container.dotSpriteLibrary.catalog() },
                ).backfillMissingBindings()
            } catch (failure: Exception) {
                Log.e("SpeciesBinding", "Could not backfill stored species bindings", failure)
            }
            try {
                // Gameplay definitions are available independently of sprite priority.
                container.db.expandedCatalog?.ensureReadyDefinitions()
                container.nativeMaterialization.materializeRetainedMappings()
            } catch (failure: Exception) {
                Log.e("ExpandedCatalog", "Could not prepare catalog definitions", failure)
            }
            val histories = EvolutionHistoryRepository(container.db)
            // Initial emission repairs existing storage; later emissions also cover
            // imports, degeneration, restored backups and edited evolution routes.
            container.db.invalidationTracker.createFlow(
                "UserCharacter", "TransformationHistory", "CardCharacter",
                "PossibleTransformations", "CardFusions",
            ).collect {
                try {
                    histories.repairAll()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.e("EvolutionHistory", "Could not repair stored evolution histories", failure)
                }
            }
        }
        WorldAfkScheduler.schedule(applicationContext)
    }
}
