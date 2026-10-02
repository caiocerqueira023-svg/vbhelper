package com.github.nacabaro.vbhelper.screens.offlineBattle

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.battle.AttackSpriteManager
import com.github.nacabaro.vbhelper.battle.HitEffectSpriteManager
import com.github.nacabaro.vbhelper.battle.IndividualSpriteManager
import com.github.nacabaro.vbhelper.battle.offline.core.BattleConfiguration
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSide
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSimulator
import com.github.nacabaro.vbhelper.battle.offline.core.BattleStrategy
import com.github.nacabaro.vbhelper.battle.offline.core.CombatantDefinition
import com.github.nacabaro.vbhelper.battle.offline.data.TrainingBattleFactory
import com.github.nacabaro.vbhelper.battle.offline.data.TrainingParticipantInput
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

data class TrainingBattlePresentation(
    val simulator: BattleSimulator,
    val alliedDefinitions: List<CombatantDefinition>,
    val opposingDefinitions: List<CombatantDefinition>,
    val fighters: Map<String, BattleFighterPresentation>,
    val arenaManifest: OfflineArenaManifest
)

/** Reads and extrudes user-owned sprite art before the 30 Hz simulation begins. */
object TrainingBattlePresentationFactory {
    suspend fun create(
        context: Context,
        allies: List<OfflineBattleParticipant>,
        opponents: List<OfflineBattleParticipant>,
        randomSeed: Long,
        arenaManifestPath: String = OfflineArenaManifest.DEFAULT_MANIFEST_PATH
    ): TrainingBattlePresentation = withContext(Dispatchers.IO) {
        val radar = arenaManifestPath == OfflineArenaManifest.RADAR_MANIFEST_PATH
        require(allies.size in 1..2 && opponents.size in 1..2 && (radar || allies.size <= opponents.size))
        val appContext = context.applicationContext
        val arenaManifest = OfflineArenaManifest.read(appContext, arenaManifestPath)
        val inputsBySide = mapOf(
            BattleSide.ALLIED to allies.map { it.toInput() },
            BattleSide.OPPOSING to opponents.map { it.toInput() }
        )
        val simulator = TrainingBattleFactory.create(
            allies = inputsBySide.getValue(BattleSide.ALLIED),
            opponents = inputsBySide.getValue(BattleSide.OPPOSING),
            configuration = BattleConfiguration(
                randomSeed = randomSeed,
                defaultPaused = true,
                arenaRadius = arenaManifest.playableRadius,
                lineupRowSpacing = arenaManifest.lineupRowSpacing,
                lineupDepthRatio = arenaManifest.lineupDepthRatio,
                minimumLineupDepth = arenaManifest.minimumLineupDepth
            ),
            allowAlliedAdvantage = radar
        )
        val spriteManager = IndividualSpriteManager(appContext)
        val attackSpriteManager = AttackSpriteManager(appContext)
        val hitEffectSpriteManager = HitEffectSpriteManager(appContext)
        val impactModels = buildMap {
            hitEffectSpriteManager.loadHitSprite("hit_01")?.toResidentFrame()?.let { put("normal", SpriteExtrusionGlb.buildBillboard(it)) }
            hitEffectSpriteManager.loadHitSprite("hit_02")?.toResidentFrame()?.let { put("special", SpriteExtrusionGlb.buildBillboard(it)) }
        }
        try {
            val fighters = LinkedHashMap<String, BattleFighterPresentation>()
            for (side in BattleSide.entries) {
                val team = if (side == BattleSide.ALLIED) allies else opponents
                for (participant in team) {
                    val profile = participant.toInput()
                    val combatantId = "${if (side == BattleSide.ALLIED) "ally" else "opponent"}:${profile.instanceId}"
                    val artId = profile.externalCharacterId ?: "storage:${profile.instanceId}"
                    val cardCharacterId = participant.character?.charId ?: participant.cardCharacterId
                    val importedAttackArt = cardCharacterId?.let {
                        (appContext as VBHelper).container.db.cardAttackArtDao().getForCharacter(it)
                    }
                    val poses = participant.spriteSet?.let(::spriteSetPoses)
                        ?: participant.character?.let { character ->
                        val source = (appContext as VBHelper).container.db.spriteDao().getForCharacter(character.charId)
                        if (source == null) databasePoses(character) else {
                            mapOf("idle" to source.spriteIdle1, "idle2" to source.spriteIdle2,
                                "walk" to source.spriteWalk1, "walk2" to source.spriteWalk2,
                                "attack" to source.spriteAttack, "guard" to source.spriteDodge,
                                "defeated" to source.spriteSleep).mapNotNull { (pose, bytes) ->
                                if (bytes.isEmpty()) null else pose to ResidentFrameImage(
                                    BitmapData(bytes, source.width, source.height).createARGBIntArray(), source.width, source.height)
                            }.toMap()
                        }
                    }
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
                        attackVisuals = buildMap {
                            attackSpriteManager.getAttackSprite(artId, isLarge = false, importedArt = importedAttackArt)?.let { put("small", it) }
                            attackSpriteManager.getAttackSprite(artId, isLarge = true, importedArt = importedAttackArt)?.let { put("large", it) }
                        },
                        impactModels = impactModels,
                        visualScaleMultiplier = battleFighterScaleMultiplier(profile.stage),
                    )
                }
            }
            val alliedDefinitions = inputsBySide.getValue(BattleSide.ALLIED)
                .map { TrainingBattleFactory.definition(it, BattleSide.ALLIED) }
            val opposingDefinitions = inputsBySide.getValue(BattleSide.OPPOSING)
                .map { TrainingBattleFactory.definition(it, BattleSide.OPPOSING) }
            TrainingBattlePresentation(simulator, alliedDefinitions, opposingDefinitions, fighters, arenaManifest)
        } catch (failure: Throwable) {
            Log.e("OfflineBattle", "Could not create training battle art", failure)
            throw failure
        } finally {
            spriteManager.clearCache()
            hitEffectSpriteManager.clearCache()
        }
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
            initialEnergy = initialEnergy
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
