package com.tamerslab

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.tamerslab.vitalwear.protos.Character
import com.tamerslab.navigation.AppNavigation
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.diagnostics.StartupTrace
import com.tamerslab.navigation.AppNavigationHandlers
import com.tamerslab.navigation.AppRoute
import com.tamerslab.navigation.appRouteFromUri
import com.tamerslab.chat.ui.ChatScreenControllerImpl
import com.tamerslab.home.ui.HomeScreenControllerImpl
import com.tamerslab.items.ui.ItemsScreenControllerImpl
import com.tamerslab.transfer.ui.scan.ScanScreenControllerImpl
import com.tamerslab.settings.ui.SettingsScreenControllerImpl
import com.tamerslab.adventure.ui.AdventureScreenControllerImpl
import com.tamerslab.species.ui.card.CardScreenControllerImpl
import com.tamerslab.species.ui.spriteviewer.SpriteViewerControllerImpl
import com.tamerslab.storage.ui.StorageScreenControllerImpl
import com.tamerslab.chat.ui.lorebook.LorebookScreenControllerImpl
import com.tamerslab.world.ui.WorldChatScreenControllerImpl
import com.tamerslab.transfer.data.VitalWearCharacterImporter
import com.tamerslab.ui.theme.TamersLabTheme
import com.tamerslab.settings.data.AppThemeSettings
import androidx.compose.ui.graphics.toArgb
import com.tamerslab.components.TechBackground
import com.tamerslab.components.showAppFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private val onActivityLifecycleListeners = HashMap<String, ActivityLifecycleListener>()
    private var initialDestination by mutableStateOf<AppRoute?>(null)

    private fun registerActivityLifecycleListener(key: String, activityLifecycleListener: ActivityLifecycleListener) {
        if( onActivityLifecycleListeners[key] != null) {
            throw IllegalStateException("Key is already in use")
        }
        onActivityLifecycleListeners[key] = activityLifecycleListener
    }

    private fun unregisterActivityLifecycleListener(key: String) {
        onActivityLifecycleListeners.remove(key)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val initialTheme = AppThemeSettings.readTheme(getSharedPreferences("app_preferences", 0))
        setTheme(initialTheme.nativeThemeResource)
        super.onCreate(savedInstanceState)
        applyDefaultLanguageIfUnset()
        StartupTrace.section("startup:activity-oncreate") {
        val application = applicationContext as TamersLabApplication
        val scanScreenController = ScanScreenControllerImpl(
            application.container.dataStoreSecretsRepository.secretsFlow,
            this,
            this::registerActivityLifecycleListener,
            this::unregisterActivityLifecycleListener,
            application.container.db,
        )
        val settingsScreenController = SettingsScreenControllerImpl(this)
        val itemsScreenController = ItemsScreenControllerImpl(this)
        val adventureScreenController = AdventureScreenControllerImpl(this)
        val storageScreenController = StorageScreenControllerImpl(this)
        val homeScreenController = HomeScreenControllerImpl(this)
        val spriteViewerController = SpriteViewerControllerImpl(this)
        val cardScreenController = CardScreenControllerImpl(this)
        val chatScreenController = ChatScreenControllerImpl(this)
        val lorebookScreenController = LorebookScreenControllerImpl(this)
        val worldChatScreenController = WorldChatScreenControllerImpl(this)

        val palette = settingsScreenController.currentTheme.value.palette
        val barColor = palette.background.toArgb()
        val barStyle = if (palette.isDark) SystemBarStyle.dark(barColor)
            else SystemBarStyle.light(barColor, barColor)
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)

        initialDestination = getInitialDestinationFromIntent(intent)

        setContent {
            val appFont by settingsScreenController.currentFont.collectAsStateWithLifecycle()
            val appTheme by settingsScreenController.currentTheme.collectAsStateWithLifecycle()
            TamersLabTheme(appFont = appFont, appTheme = appTheme) {
                TechBackground {
                    MainApplication(
                        scanScreenController = scanScreenController,
                        settingsScreenController = settingsScreenController,
                        itemsScreenController = itemsScreenController,
                        adventureScreenController = adventureScreenController,
                        homeScreenController = homeScreenController,
                        storageScreenController = storageScreenController,
                        spriteViewerController = spriteViewerController,
                        cardScreenController = cardScreenController,
                        chatScreenController = chatScreenController,
                        lorebookScreenController = lorebookScreenController,
                        worldChatScreenController = worldChatScreenController,
                        initialDestination = initialDestination
                    )
                }
            }
        }

        Log.i("MainActivity", "Activity onCreated")
        handleImportIntent(intent)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.i("MainActivity", "onPause")
        for(activityListener in onActivityLifecycleListeners) {
            activityListener.value.onPause()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i("MainActivity", "Resume")
        for(activityListener in onActivityLifecycleListeners) {
            activityListener.value.onResume()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialDestination = getInitialDestinationFromIntent(intent)
        handleImportIntent(intent)
    }

    private fun handleImportIntent(intent: Intent?) {
        val importUri = extractVitalWearImportUri(intent) ?: return
        val application = applicationContext as TamersLabApplication

        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                contentResolver.openInputStream(importUri)?.use { inputStream ->
                    val character = Character.parseFrom(inputStream)
                    VitalWearCharacterImporter(application.container.db).importCharacter(character)
                } ?: VitalWearCharacterImporter.ImportResult(
                    success = false,
                    message = getString(R.string.app_transfer_import_failed)
                )
            }.getOrElse {
                VitalWearCharacterImporter.ImportResult(
                    success = false,
                    message = getString(R.string.app_transfer_import_failed)
                )
            }

            application.feedback.transfer(result.message)
        }
    }

    private fun extractVitalWearImportUri(intent: Intent?): Uri? {
        if (intent == null) {
            return null
        }

        val isVitalWearImport = intent.type == VITALWEAR_CHARACTER_MIME
        if (!isVitalWearImport) {
            return null
        }

        return when (intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
    }

    private fun getInitialDestinationFromIntent(intent: Intent?): AppRoute? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        return appRouteFromUri(intent.data?.toString())
    }

    private fun applyDefaultLanguageIfUnset() {
        val preferences = getSharedPreferences("app_preferences", MODE_PRIVATE)
        if (!preferences.contains("language_tag")) {
            preferences.edit().putString("language_tag", "en").apply()
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        }
    }

    @Composable
    private fun MainApplication(
        scanScreenController: ScanScreenControllerImpl,
        settingsScreenController: SettingsScreenControllerImpl,
        itemsScreenController: ItemsScreenControllerImpl,
        adventureScreenController: AdventureScreenControllerImpl,
        storageScreenController: StorageScreenControllerImpl,
        homeScreenController: HomeScreenControllerImpl,
        spriteViewerController: SpriteViewerControllerImpl,
        cardScreenController: CardScreenControllerImpl,
        chatScreenController: ChatScreenControllerImpl,
        lorebookScreenController: LorebookScreenControllerImpl,
        worldChatScreenController: WorldChatScreenControllerImpl,
        initialDestination: AppRoute? = null
    ) {
        AppNavigation(
            applicationNavigationHandlers = AppNavigationHandlers(
                settingsScreenController,
                scanScreenController,
                itemsScreenController,
                adventureScreenController,
                storageScreenController,
                homeScreenController,
                spriteViewerController,
                cardScreenController,
                chatScreenController,
                lorebookScreenController,
                worldChatScreenController
            ),
            initialDestination = initialDestination
        )
    }

    companion object {
        private const val VITALWEAR_CHARACTER_MIME = "application/x-vitalwear-character"
    }
}


