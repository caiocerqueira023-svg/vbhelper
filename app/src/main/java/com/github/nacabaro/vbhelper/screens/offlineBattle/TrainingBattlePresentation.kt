package com.github.nacabaro.vbhelper.screens.offlineBattle

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.battle.AttackSpriteManager
import com.github.nacabaro.vbhelper.battle.HitEffectSpriteManager
import com.github.nacabaro.vbhelper.battle.IndividualSpriteManager
import com.github.nacabaro.vbhelper.battle.offline.core.BattleConfiguration
import com.github.nacabaro.vbhelper.battle.offline.core.BattleAttribute
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSide
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSimulator
import com.github.nacabaro.vbhelper.battle.offline.core.BattleStrategy
import com.github.nacabaro.vbhelper.battle.offline.core.BattleTeam
import com.github.nacabaro.vbhelper.battle.offline.core.CombatantDefinition
import com.github.nacabaro.vbhelper.battle.offline.data.GenericTechniqueCatalog
import com.github.nacabaro.vbhelper.battle.offline.data.TrainingBattleFactory
import com.github.nacabaro.vbhelper.battle.offline.data.TrainingParticipantInput
import com.github.nacabaro.vbhelper.battle.offline.data.BlastEvolutionRepository
import com.github.nacabaro.vbhelper.battle.offline.data.BlastEvolutionData
import com.github.nacabaro.vbhelper.battle.offline.data.DexJogressResolver
import com.github.nacabaro.vbhelper.domain.device_data.BlastEvolutionSlot
import com.github.nacabaro.vbhelper.domain.card.CardAttackArt
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.screens.OfflineBattleParticipant
import com.github.nacabaro.vbhelper.screens.OfflineBattleSpriteSet
import com.github.nacabaro.vbhelper.rendering.sprite3d.ResidentFrameImage
import com.github.nacabaro.vbhelper.rendering.sprite3d.SpriteExtrusionGlb
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.createARGBIntArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BattleFighterPresentation(
    val combatantId: String,
    val externalCharacterId: String,
    val displayName: String,
    val modelGlb: ByteArray,
    val poseNames: Set<String>,
    val setKey: String,
    val attackVisuals: Map<String, Bitmap>,
    val impactModels: Map<String, ByteArray>,
    /** Stage-aware size applied after the sprite geometry is normalized to unit height. */
    val visualScaleMultiplier: Float = 1f,
    /** Species special-move name for the innate special; null keeps the catalog name. */
    val specialDisplayName: String? = null,
    /** Raw species name for universal-table matching. */
    val speciesName: String? = null,
    val attackProfile: FinisherAttackProfile = resolveFinisherAttackProfile(null),
    /** Prebuilt alpha-derived white poses, retained by the renderer for form transitions. */
    val transitionGlb: ByteArray? = null,
)

/**
 * Sprite frames are normalized to the same model height, so the two newborn
 * stages need an explicit physical scale to remain proportional in the arena.
 */
internal fun battleFighterScaleMultiplier(stage: Int): Float = when (stage) {
    0 -> 0.58f // Baby I
    1 -> 0.72f // Baby II
    else -> 1f
}

/** FORM and Jogress are independent equipped slots, even when both resolve to the same species. */
internal fun equippedFinisherResultSpecies(blastMode: String, formSpecies: String?, jogressSpecies: String?): List<String> =
    listOfNotNull(
        formSpecies?.takeIf { blastMode == BlastEvolutionSlot.FORM && it.isNotBlank() },
        jogressSpecies?.takeIf { it.isNotBlank() },
    )

data class TrainingBattlePresentation(
    val simulator: BattleSimulator,
    val alliedDefinitions: List<CombatantDefinition>,
    val opposingDefinitions: List<CombatantDefinition>,
    val fighters: Map<String, BattleFighterPresentation>,
    val arenaManifest: OfflineArenaManifest,
    /** Legacy single-result lookup keyed by combatantId. */
    val altForms: Map<String, BattleFighterPresentation> = emptyMap(),
    /** Every equipped FORM and Jogress result, keyed by the result presentation's setKey. */
    val preparedForms: Map<String, BattleFighterPresentation> = emptyMap(),
)

/** Reads and extrudes user-owned sprite art before the 30 Hz simulation begins. */
object TrainingBattlePresentationFactory {
    suspend fun create(
        context: Context,
        allies: List<OfflineBattleParticipant>,
        opponents: List<OfflineBattleParticipant>,
        randomSeed: Long,
        arenaManifestPath: String = OfflineArenaManifest.DEFAULT_MANIFEST_PATH,
        extraItems: List<com.github.nacabaro.vbhelper.battle.offline.core.BattleItemDefinition> = emptyList()
    ): TrainingBattlePresentation = withContext(Dispatchers.IO) {
        val radar = arenaManifestPath == OfflineArenaManifest.RADAR_MANIFEST_PATH
        require(allies.size in 1..2 && opponents.size in 1..2 && (radar || allies.size <= opponents.size))
        require((allies + opponents).all { it.stableId.isNotBlank() }) { "Instância sem identidade." }
        val alliedSources = allies.mapNotNull { it.character?.id }
        require(alliedSources.distinct().size == alliedSources.size) { "Um parceiro não pode ocupar dois slots aliados." }
        val combatantIds = allies.map { "ally:${it.stableId}" } + opponents.map { "opponent:${it.stableId}" }
        require(combatantIds.distinct().size == combatantIds.size) { "Cada combatente precisa ter uma instância própria." }
        val appContext = context.applicationContext
        val arenaManifest = OfflineArenaManifest.read(appContext, arenaManifestPath)
        val blastData = BlastEvolutionRepository.load(appContext)
        val db = (appContext as VBHelper).container.db
        val participantsBySide = mapOf(BattleSide.ALLIED to allies, BattleSide.OPPOSING to opponents)
        val sourceCardIds = (allies + opponents).mapNotNull { it.character?.charId ?: it.cardCharacterId }.toSet()
        val equippedTargets = (allies + opponents).flatMap {
            equippedFinisherResultSpecies(it.blastMode, it.blastTargetSpecies, it.jogressResultSpecies)
        }.map { BlastEvolutionRepository.normalize(blastData, it) }.toSet()
        // One setup-time scan only when a result is equipped; retain only source/result profiles.
        val loadedProfiles = if (equippedTargets.isEmpty()) {
            sourceCardIds.mapNotNull { db.speciesProfileDao().getByCardCharacterId(it) }
        } else {
            db.speciesProfileDao().getAll().filter { profile ->
                profile.cardCharacterId in sourceCardIds || profile.normalizedNames(blastData).any { it in equippedTargets }
            }
        }
        val profilesByCardId = loadedProfiles.associateBy { it.cardCharacterId }
        val resultProfiles = buildMap<String, MutableList<SpeciesProfile>> {
            loadedProfiles.forEach { profile ->
                profile.normalizedNames(blastData).filter { it in equippedTargets }.forEach { name ->
                    getOrPut(name) { mutableListOf() }.add(profile)
                }
            }
        }
        val inputsBySide = mapOf(
            BattleSide.ALLIED to allies.map { it.toInput() },
            BattleSide.OPPOSING to opponents.map { it.toInput() }
        ).mapValues { (side, inputs) ->
            inputs.mapIndexed { index, input ->
                val participant = participantsBySide.getValue(side)[index]
                val sourceProfile = (participant.character?.charId ?: participant.cardCharacterId)?.let(profilesByCardId::get)
                input.copy(
                    specialDisplayNameOverride = input.specialDisplayNameOverride ?: sourceProfile?.firstSpecialMove(),
                    speciesName = input.speciesName?.takeIf { it.isNotBlank() }
                        ?: sourceProfile?.speciesName?.takeIf { it.isNotBlank() }
                        ?: sourceProfile?.matchedName?.takeIf { it.isNotBlank() },
                )
            }
        }
        val spriteManager = IndividualSpriteManager(appContext)
        val attackSpriteManager = AttackSpriteManager(appContext)
        val hitEffectSpriteManager = HitEffectSpriteManager(appContext)
        val impactModels = buildMap {
            hitEffectSpriteManager.loadHitSprite("hit_01")?.toResidentFrame()?.let { put("normal", SpriteExtrusionGlb.buildBillboard(it)) }
            hitEffectSpriteManager.loadHitSprite("hit_02")?.toResidentFrame()?.let { put("special", SpriteExtrusionGlb.buildBillboard(it)) }
        }
        try {
            val fighters = LinkedHashMap<String, BattleFighterPresentation>()
            val jogressConstraints = LinkedHashMap<String, Pair<List<String>, BattleAttribute?>>()
            val constraintCache = mutableMapOf<Triple<Long?, String?, String>, Pair<List<String>, BattleAttribute?>>()
            val importedPoses = mutableMapOf<Long, Map<String, ResidentFrameImage>?>()
            val importedAttackArt = mutableMapOf<Long, CardAttackArt?>()
            val attacks = mutableMapOf<String, Map<String, Bitmap>>()
            val transitions = mutableMapOf<ByteArray, ByteArray>()
            suspend fun posesForCharacter(cardCharacterId: Long): Map<String, ResidentFrameImage>? {
                if (!importedPoses.containsKey(cardCharacterId)) {
                    importedPoses[cardCharacterId] = spritePosesForCharacter(db, cardCharacterId)
                }
                return importedPoses[cardCharacterId]
            }
            suspend fun attackVisuals(cardCharacterId: Long?, artId: String): Map<String, Bitmap> {
                if (cardCharacterId != null && !importedAttackArt.containsKey(cardCharacterId)) {
                    importedAttackArt[cardCharacterId] = db.cardAttackArtDao().getForCharacter(cardCharacterId)
                }
                val imported = cardCharacterId?.let(importedAttackArt::get)
                val key = if (imported != null) "card:$cardCharacterId" else "asset:$artId"
                return attacks.getOrPut(key) {
                    buildMap {
                        attackSpriteManager.getAttackSprite(artId, isLarge = false, importedArt = imported)?.let { put("small", it) }
                        attackSpriteManager.getAttackSprite(artId, isLarge = true, importedArt = imported)?.let { put("large", it) }
                    }
                }
            }
            fun transition(model: ByteArray, poses: Map<String, ResidentFrameImage>): ByteArray =
                transitions.getOrPut(model) { buildFinisherTransitionGlb(poses) }
            for (side in BattleSide.entries) {
                val team = participantsBySide.getValue(side)
                for ((index, participant) in team.withIndex()) {
                    val profile = inputsBySide.getValue(side)[index]
                    val combatantId = "${if (side == BattleSide.ALLIED) "ally" else "opponent"}:${profile.instanceId}"
                    val cardCharacterId = participant.character?.charId ?: participant.cardCharacterId
                    profile.jogressResultSpecies?.takeIf { it.isNotBlank() }?.let { choice ->
                        val key = Triple(cardCharacterId, profile.speciesName, BlastEvolutionRepository.normalize(blastData, choice))
                        jogressConstraints[combatantId] = constraintCache.getOrPut(key) {
                            resolveJogressConstraints(db, blastData, profile, participant, choice)
                        }
                    }
                    val artId = profile.externalCharacterId ?: "storage:${profile.instanceId}"
                    val poses = participant.spriteSet?.let(::spriteSetPoses)
                        ?: cardCharacterId?.let { posesForCharacter(it) }
                        ?: participant.character?.let(::databasePoses)
                        ?: assetPoses(spriteManager, artId)
                    if (poses.isEmpty()) throw IllegalStateException("Os sprites de ${profile.displayName} não estão disponíveis no app.")
                    val model = SpriteExtrusionGlb.build(poses)
                    fighters[combatantId] = BattleFighterPresentation(
                        combatantId = combatantId,
                        externalCharacterId = artId,
                        displayName = profile.displayName,
                        modelGlb = model,
                        poseNames = poses.keys,
                        setKey = "${combatantId}:${model.contentHashCode()}:${profile.stage}",
                        attackVisuals = attackVisuals(cardCharacterId, artId),
                        impactModels = impactModels,
                        visualScaleMultiplier = battleFighterScaleMultiplier(profile.stage),
                        specialDisplayName = profile.specialDisplayNameOverride,
                        speciesName = profile.speciesName,
                        attackProfile = resolveFinisherAttackProfile(profile.specialDisplayNameOverride),
                        // Unequipped partners may also take part in a Jogress transition.
                        transitionGlb = if (equippedTargets.isNotEmpty()) transition(model, poses) else null,
                    )
                }
            }
            val initialAlliedDefinitions = inputsBySide.getValue(BattleSide.ALLIED)
                .map { TrainingBattleFactory.definition(it, BattleSide.ALLIED) }
                .map { withJogressConstraints(it, jogressConstraints) }
            val initialOpposingDefinitions = inputsBySide.getValue(BattleSide.OPPOSING)
                .map { TrainingBattleFactory.definition(it, BattleSide.OPPOSING) }
                .map { withJogressConstraints(it, jogressConstraints) }
            val preparedForms = buildPreparedForms(db, blastData, resultProfiles, fighters,
                initialAlliedDefinitions + initialOpposingDefinitions, ::posesForCharacter, ::attackVisuals, ::transition)
            fun enrichedDefinition(definition: CombatantDefinition): CombatantDefinition {
                val form = preparedForms.values.firstOrNull {
                    it.combatantId == definition.combatantId && definition.blastMode == BlastEvolutionSlot.FORM &&
                        BlastEvolutionRepository.normalize(blastData, it.displayName) ==
                        BlastEvolutionRepository.normalize(blastData, definition.blastTargetSpecies.orEmpty())
                }
                return definition.copy(blastFormSpecial = form?.specialDisplayName)
            }
            val alliedDefinitions = initialAlliedDefinitions.map(::enrichedDefinition)
            val opposingDefinitions = initialOpposingDefinitions.map(::enrichedDefinition)
            val altForms = (alliedDefinitions + opposingDefinitions).mapNotNull { definition ->
                val results = preparedForms.values.filter { it.combatantId == definition.combatantId }
                // Preserve the legacy helper's single-result preference; preparedForms contains both slots.
                val alt = listOfNotNull(definition.jogressResultSpecies, definition.blastTargetSpecies)
                    .firstNotNullOfOrNull { target -> results.firstOrNull {
                        BlastEvolutionRepository.normalize(blastData, it.displayName) == BlastEvolutionRepository.normalize(blastData, target)
                    } }
                alt?.let { definition.combatantId to it }
            }.toMap()
            // Use these exact enriched definitions in both the simulator and the presentation.
            val configuration = BattleConfiguration(
                randomSeed = randomSeed,
                defaultPaused = true,
                arenaRadius = arenaManifest.playableRadius,
                lineupRowSpacing = arenaManifest.lineupRowSpacing,
                lineupDepthRatio = arenaManifest.lineupDepthRatio,
                minimumLineupDepth = arenaManifest.minimumLineupDepth,
            )
            val simulator = BattleSimulator(
                configuration = configuration,
                alliedTeam = BattleTeam(configuration.alliedTeamId, BattleSide.ALLIED, alliedDefinitions),
                opposingTeam = BattleTeam(configuration.opposingTeamId, BattleSide.OPPOSING, opposingDefinitions),
                techniqueCatalog = GenericTechniqueCatalog.definitionsForVersion(configuration.rulesetVersion),
                trainingItems = TrainingBattleFactory.trainingItems + extraItems,
            )
            TrainingBattlePresentation(simulator, alliedDefinitions, opposingDefinitions, fighters, arenaManifest, altForms, preparedForms)
        } catch (failure: Throwable) {
            Log.e("OfflineBattle", "Could not create training battle art", failure)
            throw failure
        } finally {
            spriteManager.clearCache()
            hitEffectSpriteManager.clearCache()
        }
    }

    /** Builds only equipped result species; repeated leads share decoded art and model bytes. */
    private suspend fun buildPreparedForms(
        db: AppDatabase,
        blastData: BlastEvolutionData,
        profiles: Map<String, List<SpeciesProfile>>,
        fighters: Map<String, BattleFighterPresentation>,
        definitions: List<CombatantDefinition>,
        posesForCharacter: suspend (Long) -> Map<String, ResidentFrameImage>?,
        attackVisuals: suspend (Long?, String) -> Map<String, Bitmap>,
        transition: (ByteArray, Map<String, ResidentFrameImage>) -> ByteArray,
    ): Map<String, BattleFighterPresentation> {
        val results = LinkedHashMap<String, BattleFighterPresentation>()
        val resultArt = mutableMapOf<String, BattleFighterPresentation?>()
        for (definition in definitions) {
            val base = fighters[definition.combatantId] ?: continue
            val targets = equippedFinisherResultSpecies(definition.blastMode, definition.blastTargetSpecies,
                definition.jogressResultSpecies).distinctBy { BlastEvolutionRepository.normalize(blastData, it) }
            for (target in targets) {
                val normalizedTarget = BlastEvolutionRepository.normalize(blastData, target)
                if (!resultArt.containsKey(normalizedTarget)) {
                    var importedResult: BattleFighterPresentation? = null
                    for (profile in profiles[normalizedTarget].orEmpty()) {
                        val poses = posesForCharacter(profile.cardCharacterId) ?: continue
                        val model = SpriteExtrusionGlb.build(poses)
                        val artId = "card:${profile.cardCharacterId}"
                        val scale = db.characterDao().getById(profile.cardCharacterId)?.stage
                            ?.let(::battleFighterScaleMultiplier) ?: 1f
                        val special = profile.firstSpecialMove()
                        importedResult = BattleFighterPresentation(
                            combatantId = definition.combatantId,
                            externalCharacterId = artId,
                            displayName = target,
                            modelGlb = model,
                            poseNames = poses.keys,
                            setKey = "$normalizedTarget:$artId:${model.contentHashCode()}",
                            attackVisuals = attackVisuals(profile.cardCharacterId, artId),
                            impactModels = base.impactModels,
                            visualScaleMultiplier = scale,
                            specialDisplayName = special,
                            speciesName = profile.speciesName?.takeIf { it.isNotBlank() }
                                ?: profile.matchedName?.takeIf { it.isNotBlank() } ?: target,
                            attackProfile = resolveFinisherAttackProfile(special),
                            transitionGlb = transition(model, poses),
                        )
                        break
                    }
                    resultArt[normalizedTarget] = importedResult
                }
                val imported = resultArt[normalizedTarget] ?: continue
                val presentation = imported.copy(
                    combatantId = definition.combatantId,
                    displayName = target,
                    setKey = "${definition.combatantId}:blast:${imported.setKey}",
                )
                results[presentation.setKey] = presentation
            }
        }
        return results
    }

    private fun SpeciesProfile.normalizedNames(blastData: BlastEvolutionData): Set<String> =
        listOfNotNull(speciesName, matchedName).filter { it.isNotBlank() }
            .map { BlastEvolutionRepository.normalize(blastData, it) }.toSet()

    // Room's SpeciesProfileConverters has already decoded the persisted specialMoves JSON.
    private fun SpeciesProfile.firstSpecialMove(): String? = specialMoves.firstOrNull()?.takeIf { it.isNotBlank() }

    private suspend fun spritePosesForCharacter(
        db: AppDatabase,
        cardCharacterId: Long,
    ): Map<String, ResidentFrameImage>? {
        val source = db.spriteDao().getForCharacter(cardCharacterId) ?: return null
        if (source.width <= 0 || source.height <= 0) return null
        return mapOf(
            "idle" to source.spriteIdle1, "idle2" to source.spriteIdle2,
            "walk" to source.spriteWalk1, "walk2" to source.spriteWalk2,
            "attack" to source.spriteAttack, "guard" to source.spriteDodge,
            "defeated" to source.spriteSleep
        ).mapNotNull { (pose, bytes) ->
            if (bytes.isEmpty()) null else pose to ResidentFrameImage(
                BitmapData(bytes, source.width, source.height).createARGBIntArray(), source.width, source.height)
        }.toMap().takeIf { it.isNotEmpty() }
    }

    private fun withJogressConstraints(
        definition: CombatantDefinition,
        constraints: Map<String, Pair<List<String>, BattleAttribute?>>
    ): CombatantDefinition {
        val (species, attribute) = constraints[definition.combatantId] ?: return definition
        return definition.copy(jogressPartnerSpecies = species, jogressPartnerAttribute = attribute)
    }

    /**
     * Partner constraints for an equipped Jogress result: universal-table
     * partner species, Dex specific-route partner species, and the Dex
     * attribute-route requirement (if any).
     */
    private suspend fun resolveJogressConstraints(
        db: AppDatabase,
        blastData: BlastEvolutionData,
        input: TrainingParticipantInput,
        participant: OfflineBattleParticipant,
        choice: String
    ): Pair<List<String>, BattleAttribute?> {
        fun norm(name: String) = BlastEvolutionRepository.normalize(blastData, name)
        val universal = blastData.jogress
            .filter { norm(it.result) == norm(choice) }
            .mapNotNull { it.partnerFor(input.speciesName.orEmpty()) { norm(it) } }
        val cardCharacterId = participant.character?.charId ?: participant.cardCharacterId
        val dexSpec = cardCharacterId?.let { DexJogressResolver.specificPartners(db, it, choice) } ?: emptyList()
        val dexAttr = cardCharacterId?.let { DexJogressResolver.attributeFor(db, it, choice) }
        return ((universal + dexSpec).distinct() to dexAttr)
    }

    private fun OfflineBattleParticipant.toInput(): TrainingParticipantInput {
        val identity = stableId
        return TrainingParticipantInput(
            instanceId = identity,
            sourceCharacterId = character?.id,
            externalCharacterId = externalCharacterId ?: assetCharacterId,
            displayName = displayName,
            stage = stage,
            maxHealth = maxHp,
            attack = attackPower,
            strategy = BattleStrategy.BALANCED,
            vitalStats = vitalStats,
            attribute = attribute,
            stableRngKey = stableRngKey,
            personalityType = personalityType,
            techniqueIds = techniqueIds,
            initialHealth = initialHealth,
            initialEnergy = initialEnergy,
            aiProfile = aiProfile,
            specialDisplayNameOverride = specialDisplayNameOverride,
            blastMode = blastMode,
            blastTargetSpecies = blastTargetSpecies,
            jogressResultSpecies = jogressResultSpecies,
            speciesName = speciesName
        )
    }

    private fun databasePoses(character: CharacterDtos.CharacterWithSprites): Map<String, ResidentFrameImage> {
        val sprites = listOf(
            "idle" to character.spriteIdle,
            "idle2" to character.spriteIdle2,
            "walk" to character.spriteRun1,
            "walk2" to character.spriteRun2,
            "attack" to character.spriteIdle
        )
        return sprites.mapNotNull { (pose, bytes) ->
            if (bytes.isEmpty() || character.spriteWidth <= 0 || character.spriteHeight <= 0) return@mapNotNull null
            runCatching {
                val pixels = BitmapData(bytes, character.spriteWidth, character.spriteHeight).createARGBIntArray()
                ResidentFrameImage(pixels, character.spriteWidth, character.spriteHeight)
            }.getOrNull()?.let { pose to it }
        }.toMap()
    }

    private fun spriteSetPoses(spriteSet: OfflineBattleSpriteSet): Map<String, ResidentFrameImage> {
        val sprites = mapOf(
            "idle" to spriteSet.idle,
            "idle2" to spriteSet.idle2,
            "walk" to spriteSet.walk,
            "walk2" to spriteSet.walk2,
            "attack" to spriteSet.attack,
            "defeated" to spriteSet.defeated
        )
        return sprites.mapNotNull { (pose, bytes) ->
            if (bytes.isEmpty() || spriteSet.width <= 0 || spriteSet.height <= 0) return@mapNotNull null
            runCatching {
                val pixels = BitmapData(bytes, spriteSet.width, spriteSet.height).createARGBIntArray()
                ResidentFrameImage(pixels, spriteSet.width, spriteSet.height)
            }.getOrNull()?.let { pose to it }
        }.toMap()
    }

    private fun assetPoses(manager: IndividualSpriteManager, characterId: String): Map<String, ResidentFrameImage> {
        val frames = manager.getAvailableFrames(characterId)
        if (frames.isEmpty()) return emptyMap()
        val selected = mapOf("idle" to 1, "idle2" to 2, "walk" to 3, "walk2" to 4,
            "attack" to 11, "guard" to 12, "defeated" to 10)
            .filterValues { it in frames }
        return selected.mapNotNull { (pose, frameNumber) ->
            val bitmap = manager.loadSpriteFrame(characterId, frameNumber) ?: return@mapNotNull null
            bitmap.toResidentFrame()?.let { pose to it }
        }.toMap()
    }

    private fun Bitmap.toResidentFrame(): ResidentFrameImage? = runCatching {
        val argb = IntArray(width * height)
        getPixels(argb, 0, width, 0, 0, width, height)
        ResidentFrameImage(argb, width, height)
    }.getOrNull()
}
