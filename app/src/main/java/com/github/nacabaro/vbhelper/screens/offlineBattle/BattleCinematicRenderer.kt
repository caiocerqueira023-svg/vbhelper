package com.github.nacabaro.vbhelper.screens.offlineBattle

import android.opengl.Matrix
import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.rendering.sprite3d.ResidentFrameImage
import com.github.nacabaro.vbhelper.rendering.sprite3d.SpriteExtrusionGlb
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Scene
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Shared, prewarmed depth-tested effects for ordinary wind-ups and finisher cinematics. */
internal class BattleCinematicRenderer(
    private val engine: Engine,
    private val scene: Scene,
    private val loader: AssetLoader,
    private val resources: ResourceLoader,
) {
    private data class Effect(val asset: FilamentAsset, val instances: List<Int>, val materials: List<MaterialInstance>)
    private val effects = linkedMapOf<String, Effect>()
    private val transform = FloatArray(16)
    private val colors = mutableMapOf<Int, FloatArray>()
    private val attackSpriteColors = mutableMapOf<String, Map<String, Int>>()
    var assetLoadCount = 0
        private set

    fun prepare(presentations: Collection<BattleFighterPresentation>) {
        val generic = cinematicEffectModels
        listOf("charge", "impact", "after", "ring-a", "ring-b", "beam", "trail-a", "trail-b", "slash").forEach { key ->
            load(key, generic.getValue(when {
                key.startsWith("ring") -> "ring"
                key == "beam" || key.startsWith("trail") -> "beam"
                key == "slash" -> "slash"
                else -> "orb"
            }))
        }
        repeat(20) { load("data:$it", generic.getValue("data")) }
        for (presentation in presentations) {
            attackSpriteColors.getOrPut(presentation.setKey) {
                presentation.attackVisuals.mapValues { (variant, bitmap) ->
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    dominantAttackSpriteColor(pixels, if (variant == "large") presentation.attackProfile.colorArgb
                        else 0xFF2DE1FC.toInt())
                }
            }
            val startup = "startup:${presentation.combatantId}"
            load("$startup:orb", generic.getValue("orb"))
            load("$startup:ring", generic.getValue("ring"))
            repeat(8) { load("$startup:data:$it", generic.getValue("data")) }
            val key = "shot:${presentation.setKey}"
            if (key in effects) continue
            val image = presentation.attackVisuals["large"] ?: presentation.attackVisuals["small"]
            val bytes = image?.let { bitmap ->
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                SpriteExtrusionGlb.buildBillboard(ResidentFrameImage(pixels, bitmap.width, bitmap.height))
            } ?: generic.getValue("orb")
            load(key, bytes)
            presentation.impactModels["special"]?.let { load("hit:${presentation.setKey}", it) }
            if (presentation.attackProfile.style == FinisherAttackStyle.BARRAGE) {
                load("$key:1", bytes)
                load("$key:2", bytes)
            }
        }
    }

    private fun load(key: String, bytes: ByteArray) {
        if (key in effects) return
        var pending: FilamentAsset? = null
        try {
            val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { flip() }
            val asset = checkNotNull(loader.createAsset(buffer))
            pending = asset
            resources.loadResources(asset)
            asset.releaseSourceData()
            val renderables = engine.renderableManager
            val instances = asset.entities.filter(renderables::hasComponent).map(renderables::getInstance)
            val materials = instances.flatMap { instance ->
                renderables.setLayerMask(instance, 0xFF, 0)
                renderables.setCastShadows(instance, false)
                renderables.setReceiveShadows(instance, false)
                (0 until renderables.getPrimitiveCount(instance)).map { renderables.getMaterialInstanceAt(instance, it) }
            }
            materials.forEach { it.setDepthCulling(true); it.setDepthWrite(false) }
            scene.addEntities(asset.entities)
            effects[key] = Effect(asset, instances, materials)
            assetLoadCount++
            pending = null
        } finally {
            pending?.let { scene.removeEntities(it.entities); loader.destroyAsset(it) }
        }
    }

    fun hide() {
        effects.values.forEach { effect -> effect.instances.forEach { engine.renderableManager.setLayerMask(it, 0xFF, 0) } }
    }

    fun updateStartup(
        snapshot: BattleSnapshot,
        fighters: Map<String, BattleFighterPresentation>,
        manifest: OfflineArenaManifest,
        yaw: Double,
        pitch: Double,
        anchors: Map<String, BattleEffectAnchor>,
        allowMotion: Boolean,
    ) {
        hide()
        val members = (snapshot.alliedMembers + snapshot.opposingMembers).associateBy { it.combatantId }
        for (member in members.values) {
            val progress = member.activeTechniqueStartupProgress ?: continue
            if (member.health <= 0 || member.state !in listOf(CombatantState.ATTACK_STARTUP, CombatantState.USING_SPECIAL)) continue
            val presentation = fighters[member.combatantId] ?: continue
            val scale = manifest.fighterScale * presentation.visualScaleMultiplier
            val special = member.activeTechniqueKind == TechniqueKind.SPECIAL
            val cue = battleStartupVisual(progress, special, allowMotion)
            val point = world(member.position, manifest.positionScale)
            val anchor = anchors[member.combatantId] ?: BattleEffectAnchor(point.x, scale * 0.55f, point.z)
            val target = members[member.targetId]
            val towardRight = target?.let {
                (it.position.x-member.position.x)*cos(yaw) - (it.position.z-member.position.z)*sin(yaw)
            } ?: 1.0
            val leading = if (towardRight >= 0.0) 1f else -1f
            val support = member.activeTechniqueKind == TechniqueKind.SUPPORT || member.activeTechniqueKind == TechniqueKind.HEAL
            val lateral = if (support) 0f else leading * scale * 0.28f
            val up = scale * 0.10f
            val x = anchor.x + cos(yaw).toFloat()*lateral - (sin(yaw)*sin(pitch)).toFloat()*up
            val y = anchor.y + cos(pitch).toFloat()*up
            val z = anchor.z - sin(yaw).toFloat()*lateral - (cos(yaw)*sin(pitch)).toFloat()*up
            val fallbackColor = when {
                special -> presentation.attackProfile.colorArgb
                support -> 0xFF35D48B.toInt()
                else -> 0xFF2DE1FC.toInt()
            }
            val color = startupAttackSpriteColor(member.activeTechniqueKind,
                attackSpriteColors[presentation.setKey].orEmpty(), fallbackColor)
            val key = "startup:${member.combatantId}"
            show("$key:orb", x, y, z, scale*cue.orbSize, scale*cue.orbSize,
                cue.orbOpacity, color, yaw, pitch)
            ring("$key:ring", point, scale*cue.floorSize, cue.floorOpacity, color)
            if (allowMotion) repeat(cue.particleCount) { index ->
                val phase = (progress + index.toFloat()/cue.particleCount) % 1f
                val radius = scale * (0.36f*(1f-phase) + 0.05f)
                val angle = index*2.39996f + progress*0.9f
                val horizontal = cos(angle)*radius
                val vertical = sin(angle)*radius
                show("$key:data:$index", x + cos(yaw).toFloat()*horizontal - (sin(yaw)*sin(pitch)).toFloat()*vertical,
                    y + cos(pitch).toFloat()*vertical,
                    z - sin(yaw).toFloat()*horizontal - (cos(yaw)*sin(pitch)).toFloat()*vertical,
                    scale*0.045f, scale*0.045f, (0.20f + phase*0.35f), color, yaw, pitch, index*30f)
            }
        }
    }

    fun update(
        snapshot: BattleSnapshot,
        fighters: Map<String, BattleFighterPresentation>,
        forms: Map<String, BattleFighterPresentation>,
        manifest: OfflineArenaManifest,
        yaw: Double,
        pitch: Double,
        allowMotion: Boolean,
        anchors: Map<String, BattleEffectAnchor> = emptyMap(),
    ) {
        hide()
        val sequence = snapshot.finisher?.let { cinematicVisualSnapshot(it, allowMotion) } ?: return
        val members = (snapshot.alliedMembers + snapshot.opposingMembers).associateBy { it.combatantId }
        val lead = members[sequence.leadId] ?: return
        val partner = members[sequence.partnerId]
        val target = members[sequence.targetId]
        val result = preparedFinisherPresentation(sequence, forms) ?: fighters[sequence.leadId] ?: return
        val scale = manifest.fighterScale * result.visualScaleMultiplier
        val anchor = cinematicAnchor(lead.position, partner?.position, sequence.kind)
        val from = world(anchor, manifest.positionScale)
        val to = world(target?.position ?: BattlePosition(anchor.x + 2f, anchor.z), manifest.positionScale)
        val color = result.attackProfile.colorArgb
        val p = sequence.phaseProgress
        val height = scale * result.attackProfile.emitterHeight
        val seconds = sequence.elapsedMillis / 1000f
        val dx = to.x - from.x
        val dz = to.z - from.z
        val distance = hypot(dx, dz).coerceAtLeast(0.01f)
        val muzzle = BattlePosition(from.x + dx / distance * scale * 0.30f, from.z + dz / distance * scale * 0.30f)
        when (sequence.phase) {
            BattleFinisherPhase.FOCUS -> ring("ring-a", from, scale * (0.7f + p * 0.35f), sin(p * PI).toFloat() * 0.5f, color)
            BattleFinisherPhase.TRANSFORM -> {
                val amount = sin(p * PI).toFloat().coerceAtLeast(0f)
                ring("ring-a", from, scale * (0.7f + p * 1.15f), amount * 0.65f, color)
                val sources = if (sequence.kind == BattleFinisherKind.JOGRESS && partner != null) {
                    cinematicFusionSources(lead.position, partner.position, target?.position,
                        scale / manifest.positionScale).let { listOf(it.first, it.second) }
                } else sequence.participantIds.mapNotNull { members[it]?.position }
                if (allowMotion) repeat(20) { index ->
                    val source = world(sources.getOrElse(index % sources.size.coerceAtLeast(1)) { anchor }, manifest.positionScale)
                    val merge = if (sequence.kind == BattleFinisherKind.JOGRESS) cinematicEase(p) else 0f
                    val angle = index * 2.39996f + seconds * 1.9f
                    val radius = scale * (0.3f + (index % 4) * 0.09f) * (1f - merge * 0.6f)
                    val y = ((index * 0.137f + p * 1.8f) % 1f) * scale * 1.45f
                    show("data:$index", source.x + (from.x - source.x) * merge + cos(angle) * radius,
                        y + 0.06f, source.z + (from.z - source.z) * merge + sin(angle) * radius,
                        scale * 0.075f, scale * 0.075f, amount * 0.8f, color, yaw, pitch, index * 17f + p * 90f)
                }
            }
            BattleFinisherPhase.REVEAL -> ring("ring-a", from, scale * (1.1f + p * 0.8f), (1f - p) * 0.7f, color)
            BattleFinisherPhase.CHARGE -> {
                val radius = scale * (0.12f + 0.33f * cinematicEase(p))
                show("charge", muzzle.x, height, muzzle.z, radius, radius, 0.45f + p * 0.5f, color, yaw, pitch)
                ring("ring-a", from, scale * (1.2f - p * 0.35f), 0.22f + p * 0.2f, color)
                if (allowMotion) repeat(10) { index ->
                    val angle = index * 2.39996f + seconds
                    val radiusIn = scale * (1f - ((p + index * 0.1f) % 1f))
                    show("data:$index", muzzle.x + cos(angle) * radiusIn, height + sin(angle) * radiusIn * 0.5f,
                        muzzle.z + sin(angle) * radiusIn, scale * 0.045f, scale * 0.045f,
                        0.45f, color, yaw, pitch)
                }
            }
            BattleFinisherPhase.RELEASE -> {
                val travel = cinematicLaunchProgress(p)
                if (p < 0.28f) show("charge", muzzle.x, height, muzzle.z,
                    scale * 0.55f, scale * 0.55f, 1f - p / 0.28f, color, yaw, pitch)
                ring("ring-a", from, scale * (0.8f + p * 1.2f), (1f - p) * 0.4f, color)
                when (result.attackProfile.style) {
                    FinisherAttackStyle.BEAM -> beam(muzzle, to, height, scale * 0.64f, travel, 0.85f, color, yaw, pitch)
                    FinisherAttackStyle.MELEE -> if (p > 0.65f) show("slash", to.x, height, to.z,
                        scale * 1.25f, scale * 1.25f, cinematicEase((p - 0.65f) / 0.35f), color, yaw, pitch, -25f)
                    FinisherAttackStyle.PROJECTILE -> {
                        shot(result, muzzle, to, height, travel, scale * 0.82f, yaw, pitch)
                        if (allowMotion && travel > 0f) {
                            val tail = cinematicAttackPoint(muzzle, to, (travel - 0.18f).coerceAtLeast(0f))
                            val head = cinematicAttackPoint(muzzle, to, travel)
                            beam(tail, head, height, scale * 0.20f, 1f, 0.55f, color, yaw, pitch, "trail-a")
                        }
                    }
                    FinisherAttackStyle.BARRAGE -> repeat(3) { index ->
                        val t = cinematicLaunchProgress(p, index)
                        if (t > 0f) shot(result, muzzle, to, height + (index - 1) * scale * 0.12f,
                            t, scale * 0.58f, yaw, pitch, index)
                    }
                }
                if (sequence.kind == BattleFinisherKind.DUO && partner != null) fighters[partner.combatantId]?.let { other ->
                    shot(other, world(partner.position, manifest.positionScale), to, height,
                        cinematicLaunchProgress(p, 1), scale * 0.70f, yaw, pitch)
                }
            }
            BattleFinisherPhase.IMPACT -> {
                val success = sequence.damage > 0
                val burst = if (success) 1f else 0.32f
                val victim = fighters[sequence.targetId]
                val victimScale = manifest.fighterScale * (victim?.visualScaleMultiplier ?: 1f)
                if (success && victim != null && p < 0.42f) {
                    val size = 1.65f + cinematicEase(p / 0.42f) * 0.45f
                    val placement = anchors[sequence.targetId]?.let {
                        battleImpactPlacementFromAnchor(it, victimScale, yaw, pitch, size)
                    } ?: battleImpactPlacement(to.x, to.z, victimScale, yaw, pitch, size)
                    show("hit:${victim.setKey}", placement.x, placement.y, placement.z, placement.height, placement.height,
                        (1f - p / 0.42f).coerceIn(0f, 1f), 0xFFFFFFFF.toInt(), yaw, pitch)
                }
                val contact = anchors[sequence.targetId]?.let {
                    battleImpactPlacementFromAnchor(it, victimScale, yaw, pitch, 1.35f + p*1.6f)
                } ?: battleImpactPlacement(to.x, to.z, victimScale, yaw, pitch)
                show("impact", contact.x, contact.y, contact.z, victimScale * (1.35f + p * 1.6f), victimScale * (1.35f + p * 1.6f),
                    burst * (1f - p).pow(1.5f), color, yaw, pitch)
                ring("ring-a", to, victimScale * (1.2f + p * 2.3f), burst * (1f - p) * 0.70f, color)
                if (success && allowMotion) repeat(12) { index ->
                    val angle = index * 2.39996f
                    val radius = victimScale * (0.25f + cinematicEase(p) * 1.3f)
                    show("data:$index", contact.x + cos(angle) * radius, contact.y + sin(angle) * radius * 0.65f,
                        contact.z + sin(angle) * radius * 0.35f, victimScale * 0.065f, victimScale * 0.27f,
                        (1f - p).pow(2f) * 0.85f, color, yaw, pitch, Math.toDegrees(angle.toDouble()).toFloat() - 90f)
                }
                if (result.attackProfile.style == FinisherAttackStyle.BEAM && p < 0.65f) {
                    beam(muzzle, to, height, scale * 0.64f * (1f - p), 1f, 1f - p, color, yaw, pitch)
                }
            }
            BattleFinisherPhase.AFTERMATH -> {
                show("after", to.x, height * 0.6f, to.z, scale * (0.9f + p * 0.7f), scale * (0.9f + p * 0.7f),
                    (1f - p) * 0.25f, color, yaw, pitch)
                ring("ring-a", to, scale * (1.3f + p), (1f - p) * 0.18f, color)
            }
            BattleFinisherPhase.RESTORE -> ring("ring-a", from, scale * (1.1f - p * 0.4f), sin(p * PI).toFloat() * 0.5f, color)
        }
    }

    private fun world(point: BattlePosition, scale: Float) = BattlePosition(point.x * scale, point.z * scale)

    private fun shot(presentation: BattleFighterPresentation, from: BattlePosition, to: BattlePosition,
        height: Float, progress: Float, size: Float, yaw: Double, pitch: Double, index: Int = 0) {
        val key = "shot:${presentation.setKey}" + if (index == 0) "" else ":$index"
        val facing = worldAttackFacing(to.x - from.x, to.z - from.z, yaw, pitch,
            authoredFacingLeft = BATTLE_ATTACK_ART_FACES_LEFT)
        show(key, from.x + (to.x - from.x) * progress, height, from.z + (to.z - from.z) * progress,
            size, size, if (progress > 0f) 1f else 0f, 0xFFFFFFFF.toInt(), yaw, pitch,
            facing.rotationDegrees, facing.scaleX)
    }

    private fun ring(key: String, point: BattlePosition, size: Float, opacity: Float, color: Int) =
        show(key, point.x, 0.045f, point.z, size, size, opacity, color, 0.0, PI / 2)

    private fun show(key: String, x: Float, y: Float, z: Float, width: Float, height: Float,
        opacity: Float, color: Int, yaw: Double, pitch: Double, roll: Float = 0f, horizontalScale: Float = 1f) {
        val effect = effects[key] ?: return
        if (opacity <= 0.01f) return
        Matrix.setIdentityM(transform, 0)
        Matrix.translateM(transform, 0, x, y, z)
        Matrix.rotateM(transform, 0, Math.toDegrees(yaw).toFloat(), 0f, 1f, 0f)
        Matrix.rotateM(transform, 0, -Math.toDegrees(pitch).toFloat(), 1f, 0f, 0f)
        Matrix.rotateM(transform, 0, roll, 0f, 0f, 1f)
        Matrix.scaleM(transform, 0, width.coerceAtLeast(0.001f) * horizontalScale, height.coerceAtLeast(0.001f), 1f)
        display(effect, opacity, color)
    }

    private fun beam(from: BattlePosition, to: BattlePosition, height: Float, thickness: Float,
        progress: Float, opacity: Float, color: Int, yaw: Double, pitch: Double, key: String = "beam") {
        val effect = effects[key] ?: return
        if (progress <= 0.01f) return
        val dx = (to.x - from.x) * progress
        val dz = (to.z - from.z) * progress
        val length = hypot(dx, dz).coerceAtLeast(0.001f)
        val ux = dx / length
        val uz = dz / length
        // A world-space strip along the attack axis; choose its perpendicular to face the camera.
        var vx = uz * cos(pitch).toFloat()
        var vy = (uz * sin(yaw) - ux * cos(yaw)).toFloat() * sin(pitch).toFloat()
        var vz = -ux * cos(pitch).toFloat()
        // For nearly horizontal views a vertical beam plane gives the most readable cross-section.
        if (abs(vy) < 0.4f) { vx = 0f; vy = 1f; vz = 0f }
        val vl = sqrt(vx * vx + vy * vy + vz * vz).coerceAtLeast(0.001f)
        vx /= vl; vy /= vl; vz /= vl
        Matrix.setIdentityM(transform, 0)
        transform[0] = ux * length; transform[1] = 0f; transform[2] = uz * length
        transform[4] = vx * thickness; transform[5] = vy * thickness; transform[6] = vz * thickness
        transform[8] = -uz; transform[9] = 0f; transform[10] = ux
        transform[12] = from.x + dx / 2; transform[13] = height; transform[14] = from.z + dz / 2
        display(effect, opacity, color)
    }

    private fun display(effect: Effect, opacity: Float, color: Int) {
        val root = engine.transformManager.getInstance(effect.asset.root)
        if (root != 0) engine.transformManager.setTransform(root, transform)
        val tint = colors.getOrPut(color) { Colors.toLinear(Colors.RgbType.SRGB,
            ((color ushr 16) and 255) / 255f, ((color ushr 8) and 255) / 255f, (color and 255) / 255f) }
        effect.materials.forEach { it.setParameter("baseColorFactor", tint[0], tint[1], tint[2], opacity.coerceIn(0f, 1f)) }
        effect.instances.forEach { engine.renderableManager.setLayerMask(it, 0xFF, 0x01) }
    }

    fun release() {
        effects.values.forEach { scene.removeEntities(it.asset.entities); loader.destroyAsset(it.asset) }
        effects.clear()
        colors.clear()
        attackSpriteColors.clear()
    }
}

internal fun preparedFinisherPresentation(sequence: BattleFinisherSnapshot, forms: Map<String, BattleFighterPresentation>): BattleFighterPresentation? {
    val species = sequence.resultSpecies ?: return null
    fun key(value: String?) = value.orEmpty().lowercase(java.util.Locale.ROOT)
        .replace("(x-antibody)", "x").filter(Char::isLetterOrDigit)
    return forms.values.firstOrNull { it.combatantId == sequence.leadId &&
        (key(it.speciesName) == key(species) || key(it.displayName) == key(species)) }
}

/** Original procedural artwork, generated once before scene readiness; no character/media assets. */
private val cinematicEffectModels: Map<String, ByteArray> by lazy {
    listOf("orb", "ring", "beam", "slash", "data").associateWith { kind ->
        val size = if (kind == "data") 8 else 64
        val pixels = IntArray(size * size) { index ->
            val x = (index % size + 0.5f) / size * 2f - 1f
            val y = (index / size + 0.5f) / size * 2f - 1f
            val radius = hypot(x, y)
            val alpha = when (kind) {
                "ring" -> exp(-abs(radius - 0.78f) * 42f) * (1f - radius).coerceAtLeast(0f) * 4f
                "beam" -> (1f - abs(y)).coerceAtLeast(0f).pow(3f) * (1f - abs(x).pow(12f))
                "slash" -> exp(-abs(radius - 0.72f) * 28f) * ((atan2(y, x) + 1.6f) / 2.9f).coerceIn(0f, 1f) *
                    if (atan2(y, x) in -1.6f..1.3f) 1f else 0f
                "data" -> if (abs(x) > 0.7f || abs(y) > 0.7f || abs(x) < 0.2f) 0.8f else 0.25f
                else -> (1f - radius).coerceAtLeast(0f).pow(1.6f)
            }
            ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24) or 0x00FFFFFF
        }
        SpriteExtrusionGlb.buildBillboard(ResidentFrameImage(pixels, size, size))
    }
}
