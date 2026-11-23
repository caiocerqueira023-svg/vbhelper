package com.tamerslab.home.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.tamerslab.components.rememberPartnerParticipation
import com.tamerslab.storage.data.AppHomeStats
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.domain.device_data.BECharacterData
import com.tamerslab.domain.device_data.SpecialMissions
import com.tamerslab.domain.device_data.VBCharacterData
import com.tamerslab.domain.device_data.VitalsHistory
import com.tamerslab.domain.species.SpeciesProfile
import com.tamerslab.dtos.CardDtos
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.storage.ui.StorageScreenController
import com.tamerslab.species.data.CardRepository
import com.tamerslab.species.data.DexRepository
import com.tamerslab.storage.data.StorageRepository
import com.tamerslab.security.isMissingSecrets
import com.tamerslab.utils.BitmapData
import com.tamerslab.utils.DeviceType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Everything the partner overview reads, owned once.
 *
 * The legacy Home page and the farm partner deck render the same modules from the same data, so the
 * flow wiring (collection, card icons, per-device payloads, evolution history, missions, vitals
 * history, latest unread line, favourites) moved here verbatim out of `HomeScreen.kt`. A host calls
 * [rememberPartnerOverviewState] and then only decides *where* to draw the modules.
 */
@Stable
class PartnerOverviewState(
    val readiness: HomeReadiness,
    val activeMon: CharacterDtos.CharacterWithSprites?,
    val cardIcon: BitmapData?,
    val vbData: VBCharacterData?,
    val beData: BECharacterData?,
    val appData: AppHomeStats?,
    val nickname: String?,
    val speciesName: String?,
    val transformationHistory: List<CharacterDtos.TransformationHistory>,
    val specialMissions: List<SpecialMissions>,
    val vitalsHistory: List<VitalsHistory>,
    val speechBubbleText: String?,
    val favoriteCharacters: List<CharacterDtos.CharacterWithSprites>,
    val favoriteIndex: Int,
    val favoriteTransitionDirection: Int,
    val profileLoading: Boolean,
    val collectionFailed: Boolean,
    val cardsFailed: Boolean,
    val cardsReady: Boolean,
    val connectionReady: Boolean,
    val adventureMissionsFinished: Boolean,
    val reload: () -> Unit,
    val acknowledgeAdventureFinished: () -> Unit,
    val cycleFavorite: (Int) -> Unit,
)

/**
 * The state of the overview when there is a partner and a card icon: READY, or the explicit
 * PROFILE_UNAVAILABLE panel when the device payload is missing.
 */
val PartnerOverviewState.hasRenderableProfile: Boolean
    get() = activeMon != null && cardIcon != null &&
        (beData != null || vbData != null || (activeMon.characterType == DeviceType.APP && appData != null))

@Composable
fun rememberPartnerOverviewState(
    homeScreenController: HomeScreenController,
    storageScreenController: StorageScreenController,
): PartnerOverviewState {
    val context = LocalContext.current
    val application = context.applicationContext as TamersLabApplication

    // Cached values live on the implementation; the deck only needs them as a warm start.
    val impl = homeScreenController as? HomeScreenControllerImpl
    val storageRepository = remember { application.container.storageRepository }
    val cardRepository = remember { CardRepository(application.container.db) }
    val dexRepository = remember { DexRepository(application.container.db) }

    var reload by rememberSaveable { mutableIntStateOf(0) }
    var collectionFailed by remember(reload) { mutableStateOf(false) }
    var cardsFailed by remember(reload) { mutableStateOf(false) }

    val collection by produceState(impl?.cachedHomeCollection, storageRepository, reload) {
        try {
            storageRepository.getAllCharacters().collect {
                impl?.cachedHomeCollection = it
                value = it
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            collectionFailed = true
        }
    }
    val allCharacters = collection.orEmpty()
    val participation = rememberPartnerParticipation()
    val activeMon = allCharacters.firstOrNull {
        it.active && participation?.owned(it.id)?.usable != false
    }

    val cards by produceState(impl?.cachedHomeCards, dexRepository, reload) {
        try {
            dexRepository.getAllDims().collect {
                impl?.cachedHomeCards = it
                value = it
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            cardsFailed = true
        }
    }

    val secrets by application.container.dataStoreSecretsRepository.secretsFlow
        .collectAsStateWithLifecycle(initialValue = null)
    val connectionReady = secrets?.let { !it.isMissingSecrets() }
    val cardsReady = !cards.isNullOrEmpty()

    val favoriteCharacters = remember(allCharacters, participation) {
        allCharacters
            .filter {
                it.isFavorite && !it.isInAdventure && participation?.owned(it.id)?.usable != false
            }
            .sortedBy { it.id }
    }
    val favoriteIndex = favoriteCharacters.indexOfFirst { it.id == activeMon?.id }
    var favoriteTransitionDirection by rememberSaveable { mutableStateOf(1) }
    var favoriteSwitchInFlight by remember { mutableStateOf(false) }

    val cycleFavorite: (Int) -> Unit = { direction ->
        if (!favoriteSwitchInFlight && favoriteIndex >= 0 && favoriteCharacters.size > 1) {
            val nextIndex = (favoriteIndex + direction + favoriteCharacters.size) % favoriteCharacters.size
            val nextCharacter = favoriteCharacters[nextIndex]
            favoriteTransitionDirection = direction
            favoriteSwitchInFlight = true
            storageScreenController.setActive(
                characterId = nextCharacter.id,
                announce = false,
                onCompletion = { favoriteSwitchInFlight = false },
                onRejected = { favoriteSwitchInFlight = false },
            )
        }
    }

    val latestReaction by (
        activeMon?.let { character ->
            application.container.db.userCharacterDao().getIndividualId(character.id)
                .flatMapLatest { individualId ->
                    application.container.chatRepository.getLatestUnreadAssistantMessage(individualId)
                }
        } ?: flowOf(null)
    ).collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(activeMon?.id) {
        activeMon?.let { homeScreenController.checkDailyDiary(it.id) }
    }

    val cachedDetails = impl?.cachedPartnerDetails?.takeIf {
        it.characterId == activeMon?.id && it.cardCharacterId == activeMon?.charId
    }
    var profileLoading by remember(activeMon?.id, activeMon?.charId, reload) {
        mutableStateOf(activeMon != null && cachedDetails == null)
    }
    val partnerDetails by produceState(cachedDetails, activeMon?.id, activeMon?.charId, reload) {
        value = cachedDetails
        val partner = activeMon ?: return@produceState
        profileLoading = value == null
        try {
            val vb: Flow<VBCharacterData?> = if (partner.characterType == DeviceType.VBDevice) {
                storageRepository.getCharacterVbData(partner.id)
            } else {
                flowOf(null)
            }
            val be: Flow<BECharacterData?> = if (partner.characterType == DeviceType.BEDevice) {
                storageRepository.getCharacterBeData(partner.id)
            } else {
                flowOf(null)
            }
            val app: Flow<AppHomeStats?> = if (partner.characterType == DeviceType.APP) {
                application.container.db.appPartnerStateDao().home(partner.id)
            } else {
                flowOf(null)
            }
            val icon: Flow<CardDtos.CardIcon?> =
                if (partner.characterType == DeviceType.APP) {
                    app.flatMapLatest { data ->
                        data?.assignedDefinitionId?.let(cardRepository::getCardIconByCharaId)
                            ?: cardRepository.getDefinitionIconByCharaId(partner.charId)
                    }
                } else {
                    cardRepository.getCardIconByCharaId(partner.charId)
                }
            combine(vb, be, icon, app) { vbData, beData, iconData, appData ->
                HomePartnerDetails(partner.id, partner.charId, vbData, beData, iconData, appData)
            }.collect {
                impl?.cachedPartnerDetails = it
                value = it
                profileLoading = false
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            value = null
        } finally {
            profileLoading = false
        }
    }
    val currentDetails = partnerDetails?.takeIf {
        it.characterId == activeMon?.id && it.cardCharacterId == activeMon?.charId
    }
    val cardIconData = currentDetails?.icon
    val vbData = currentDetails?.vbData
    val beData = currentDetails?.beData
    val appData = currentDetails?.appData
    val cardIcon = cardIconData?.let {
        BitmapData(bitmap = it.cardIcon, width = it.cardIconWidth, height = it.cardIconHeight)
    }

    val speciesProfile by (
        activeMon?.let { character ->
            application.container.db.speciesProfileDao().getByCardCharacterIdFlow(character.charId)
        } ?: flowOf<SpeciesProfile?>(null)
    ).collectAsStateWithLifecycle(initialValue = null)

    val historyFlow = remember(storageRepository, activeMon?.id) {
        activeMon?.id?.let(storageRepository::getTransformationHistory) ?: flowOf(emptyList())
    }
    val transformationHistory by historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val vbSpecialMissions by (
        activeMon
            ?.takeIf { it.characterType == DeviceType.VBDevice || it.characterType == DeviceType.APP }
            ?.let { chara -> storageRepository.getSpecialMissions(chara.id) }
            ?: flowOf(emptyList())
    ).collectAsStateWithLifecycle(initialValue = emptyList())

    var vitalsHistory by remember { mutableStateOf<List<VitalsHistory>>(emptyList()) }
    LaunchedEffect(activeMon?.id) {
        val character = activeMon
        vitalsHistory = if (character != null) {
            application.container.db.userCharacterDao().getVitalsHistory(character.id)
        } else {
            emptyList()
        }
    }

    var adventureMissionsFinished by remember { mutableStateOf(false) }
    LaunchedEffect(true) {
        homeScreenController.didAdventureMissionsFinish {
            adventureMissionsFinished = it
        }
    }

    val readiness = homeReadiness(
        collection?.size,
        cards?.size,
        connectionReady,
        activeMon != null,
        cardIcon != null && (beData != null || vbData != null ||
            (activeMon?.characterType == DeviceType.APP && appData != null)),
    )

    val speciesName = speciesProfile?.speciesName?.takeIf { it.isNotBlank() }
        ?: speciesProfile?.matchedName?.takeIf { it.isNotBlank() }

    return PartnerOverviewState(
        readiness = readiness,
        activeMon = activeMon,
        cardIcon = cardIcon,
        vbData = vbData,
        beData = beData,
        appData = appData,
        nickname = activeMon?.nickname,
        speciesName = speciesName,
        transformationHistory = transformationHistory,
        specialMissions = vbSpecialMissions,
        vitalsHistory = vitalsHistory,
        speechBubbleText = latestReaction?.content,
        favoriteCharacters = favoriteCharacters,
        favoriteIndex = favoriteIndex,
        favoriteTransitionDirection = favoriteTransitionDirection,
        profileLoading = profileLoading,
        collectionFailed = collectionFailed,
        cardsFailed = cardsFailed,
        cardsReady = cardsReady,
        connectionReady = connectionReady == true,
        adventureMissionsFinished = adventureMissionsFinished,
        reload = { reload++ },
        acknowledgeAdventureFinished = { adventureMissionsFinished = false },
        cycleFavorite = cycleFavorite,
    )
}

