package com.github.nacabaro.vbhelper.widget

import android.app.PendingIntent
import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.View
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Bundle
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.github.nacabaro.vbhelper.ui.PublicationGeneration
import com.github.nacabaro.vbhelper.ui.SpritePreviewPolicy
import kotlinx.coroutines.CancellationException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.widget.RemoteViews
import com.github.nacabaro.vbhelper.MainActivity
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.getBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.room.withTransaction
import androidx.compose.ui.graphics.toArgb
import com.github.nacabaro.vbhelper.source.AppThemeSettings
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Home-screen widget that shows the active Digimon walking side-to-side
 * (walk animation) and occasionally pausing (idle animation).
 * Tapping the widget opens the app home (MainActivity).
 */
class DigimonWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        ensureAnimationRunning(context)
        for (id in appWidgetIds) {
            renderFrame(context, appWidgetManager, id)
        }
    }

    override fun onEnabled(context: Context) {
        ensureAnimationRunning(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        renderStates.remove(appWidgetId)
        ensureAnimationRunning(context)
        renderFrame(context, appWidgetManager, appWidgetId)
    }

    override fun onDisabled(context: Context) {
        stopAnimation()
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { renderStates.remove(it) }
        // If no widgets left, animation will be stopped on next tick check.
        val remaining = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, DigimonWidgetProvider::class.java))
        if (remaining.isEmpty()) {
            stopAnimation()
        }
    }

    companion object {
        private const val FRAME_MS = 220L
        private const val WALK_SPEED_DP = 2.2f
        private const val SCALE = 3 // pixel-art scale factor
        private const val MIN_WALK_TICKS = 18
        private const val MAX_WALK_TICKS = 40
        private const val MIN_IDLE_TICKS = 8
        private const val MAX_IDLE_TICKS = 20
        private const val REFRESH_MS = 26_000L

        private val handler = Handler(Looper.getMainLooper())
        private var running = false
        private val generation = PublicationGeneration()
        private var registeredReceiver = false
        private val renderStates = mutableMapOf<Int, RenderState>()
        private class RenderState(val width: Int, val height: Int) {
            val buffers = arrayOfNulls<Bitmap>(2)
            var nextBuffer = 0
            var busy = false
            var pending = false
            var lastKey: String? = null
            var initializedGeneration = -1L
            var empty = true
        }

        // Animation state (shared across all instances of this widget)
        private var posX = 0f
        private var direction = 1 // +1 right, -1 left
        private var frameIndex = 0 // 0 or 1
        private var isWalking = true
        private var ticksLeftInMode = Random.nextInt(MIN_WALK_TICKS, MAX_WALK_TICKS + 1)

        private var cachedSprites: CharacterDtos.WidgetSprites? = null
        private var partnerName: String? = null
        private var lastSpriteRefreshAt = 0L
        private var spritesResolved = false
        @Volatile private var loadInFlight = false
        private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var idleBmps: Array<Bitmap?> = arrayOf(null, null)
        private var walkBmps: Array<Bitmap?> = arrayOf(null, null)
        private var spriteW = 0
        private var spriteH = 0

        private val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            isAntiAlias = false
            isFilterBitmap = false // keep pixel art crisp
        }

        private val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handler.removeCallbacks(tickRunnable)
                if (intent.action == Intent.ACTION_SCREEN_ON && running) handler.post(tickRunnable)
            }
        }

        private val tickRunnable = object : Runnable {
            override fun run() {
                if (!running) return
                val ctx = appContext ?: return
                if (!(ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive) return
                val manager = AppWidgetManager.getInstance(ctx)
                val ids = manager.getAppWidgetIds(
                    ComponentName(ctx, DigimonWidgetProvider::class.java)
                )
                if (ids.isEmpty()) {
                    stopAnimation()
                    return
                }
                advanceState(ctx)
                for (id in ids) {
                    renderFrame(ctx, manager, id)
                }
                handler.postDelayed(this, if (spritesResolved && idleBmps[0] == null && walkBmps[0] == null || !ValueAnimator.areAnimatorsEnabled())
                    REFRESH_MS else FRAME_MS)
            }
        }

        @Volatile
        private var appContext: Context? = null

        fun ensureAnimationRunning(context: Context) {
            appContext = context.applicationContext
            if (!registeredReceiver) {
                ContextCompat.registerReceiver(context.applicationContext, screenReceiver,
                    IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) },
                    ContextCompat.RECEIVER_NOT_EXPORTED)
                registeredReceiver = true
            }
            if (running) return
            running = true
            handler.removeCallbacks(tickRunnable)
            handler.post(tickRunnable)
        }

        fun stopAnimation() {
            running = false
            generation.invalidate()
            loadInFlight = false
            handler.removeCallbacks(tickRunnable)
            renderStates.clear()
            if (registeredReceiver) {
                runCatching { appContext?.unregisterReceiver(screenReceiver) }
                registeredReceiver = false
            }
        }

        /** Call from the app when the active Digimon changes so the widget reloads sprites. */
        fun notifyActiveCharacterChanged(context: Context) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                handler.post { notifyActiveCharacterChanged(context.applicationContext) }
                return
            }
            generation.invalidate()
            loadInFlight = false
            renderStates.clear()
            cachedSprites = null
            partnerName = null
            spritesResolved = false
            idleBmps = arrayOf(null, null)
            walkBmps = arrayOf(null, null)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, DigimonWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                ensureAnimationRunning(context)
                for (id in ids) {
                    renderFrame(context, manager, id)
                }
            }
        }

        private fun advanceState(context: Context) {
            if (!loadInFlight && SystemClock.elapsedRealtime() - lastSpriteRefreshAt >= REFRESH_MS) {
                // Periodically re-query DB in case active Digimon changed
                generation.invalidate()
                spritesResolved = false
                lastSpriteRefreshAt = SystemClock.elapsedRealtime()
            }

            if (!ValueAnimator.areAnimatorsEnabled()) {
                frameIndex = 0
                isWalking = false
                posX = 0f
                return
            }

            ticksLeftInMode--
            if (ticksLeftInMode <= 0) {
                isWalking = !isWalking
                ticksLeftInMode = if (isWalking) {
                    Random.nextInt(MIN_WALK_TICKS, MAX_WALK_TICKS + 1)
                } else {
                    Random.nextInt(MIN_IDLE_TICKS, MAX_IDLE_TICKS + 1)
                }
            }

            frameIndex = (frameIndex + 1) % 2

            if (isWalking) {
                val density = context.resources.displayMetrics.density
                val step = WALK_SPEED_DP * density * direction
                posX += step
            }
        }

        private fun loadSpritesIfNeeded(context: Context) {
            // A resolved-empty result is cached until the next invalidation or
            // refresh deadline; the animation tick must not re-query every frame.
            if (spritesResolved || loadInFlight) return
            loadInFlight = true
            val loadGeneration = generation.current()
            val appContext = context.applicationContext
            ioScope.launch {
                var sprites: CharacterDtos.WidgetSprites? = null
                var name: String? = null
                try {
                    val app = appContext as? VBHelper
                    val db = app?.container?.db
                    if (db != null) {
                        db.withTransaction {
                            val active = db.userCharacterDao().getActiveCharacter().first()
                            name = active?.let {
                                val stored = db.userCharacterDao().getCharacter(it.id)
                                db.digimonIndividualDao().getIndividual(stored.individualId)?.nickname
                            }
                                ?.takeIf(String::isNotBlank)
                                ?: active?.let { db.speciesProfileDao().getByCardCharacterId(it.charId)?.speciesName }
                            sprites = db.userCharacterDao().getActiveCharacterWidgetSprites()
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    sprites = null
                }
                val loaded = sprites
                fun toBmp(bytes: ByteArray): Bitmap? {
                    return try {
                        if (loaded == null || !SpritePreviewPolicy.validFrame(loaded.width, loaded.height, bytes.size)) return null
                        BitmapData(bytes, loaded!!.width, loaded.height).getBitmap()
                            .copy(Bitmap.Config.ARGB_8888, false)
                    } catch (_: Exception) {
                        null
                    }
                }
                val idle = arrayOf<Bitmap?>(null, null)
                val walk = arrayOf<Bitmap?>(null, null)
                var width = 0
                var height = 0
                if (loaded != null) {
                    width = loaded.width
                    height = loaded.height
                    idle[0] = toBmp(loaded.spriteIdle1)
                    idle[1] = toBmp(loaded.spriteIdle2)
                    walk[0] = toBmp(loaded.spriteWalk1)
                    walk[1] = toBmp(loaded.spriteWalk2)
                }
                handler.post {
                    if (!generation.accepts(loadGeneration) || !running) return@post
                    cachedSprites = loaded
                    partnerName = name
                    spritesResolved = true
                    loadInFlight = false
                    lastSpriteRefreshAt = SystemClock.elapsedRealtime()
                    if (loaded == null) {
                        idleBmps = arrayOf(null, null)
                        walkBmps = arrayOf(null, null)
                        spriteW = 0
                        spriteH = 0
                    } else {
                        spriteW = width
                        spriteH = height
                        idleBmps = idle
                        walkBmps = walk
                    }
                    val ctx = appContext
                    if (ctx != null) {
                        val manager = AppWidgetManager.getInstance(ctx)
                        val ids = manager.getAppWidgetIds(ComponentName(ctx, DigimonWidgetProvider::class.java))
                        for (id in ids) {
                            renderFrame(ctx, manager, id)
                        }
                        handler.removeCallbacks(tickRunnable)
                        handler.postDelayed(tickRunnable, if (loaded == null) REFRESH_MS else FRAME_MS)
                    }
                }
            }
        }

        private fun renderFrame(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int
        ) {
            loadSpritesIfNeeded(context)

            val options = manager.getAppWidgetOptions(appWidgetId)
            val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
            val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40)
            val density = context.resources.displayMetrics.density

            // Convert dp to px for the canvas
            val requestedW = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, minW.toFloat(), context.resources.displayMetrics
            ).roundToInt().coerceAtLeast(1)
            val requestedH = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, minH.toFloat(), context.resources.displayMetrics
            ).roundToInt().coerceAtLeast(1)

            val src = if (isWalking) {
                walkBmps[frameIndex] ?: idleBmps[0]
            } else {
                idleBmps[frameIndex]
            }

            val ratio = minOf(1f, kotlin.math.sqrt(256_000f / (requestedW.toLong() * requestedH).coerceAtLeast(1)))
            val canvasW = (requestedW * ratio).roundToInt().coerceAtLeast(1)
            val canvasH = (requestedH * ratio).roundToInt().coerceAtLeast(1)
            val record = renderStates[appWidgetId]?.takeIf { it.width == canvasW && it.height == canvasH }
                ?: RenderState(canvasW, canvasH).also { renderStates[appWidgetId] = it }
            val palette = AppThemeSettings.readTheme(context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)).palette
            val currentGeneration = generation.current()
            val empty = src == null
            val key = if (empty) "$currentGeneration:empty:${palette.surfaceElevated}" else
                "$currentGeneration:$frameIndex:$isWalking:$posX:$direction:${palette.surfaceElevated}"
            if (record.lastKey == key) return
            if (record.busy) { record.pending = true; return }

            if (src != null && spriteW > 0 && spriteH > 0) {
                val drawW = spriteW * SCALE

                // Clamp position so Digimon stays fully visible
                val maxX = (requestedW - drawW).toFloat().coerceAtLeast(0f)
                if (posX < 0f) {
                    posX = 0f
                    direction = 1
                } else if (posX > maxX) {
                    posX = maxX
                    direction = -1
                }

            }
            val x = posX * ratio
            val facing = direction
            val width = spriteW
            val height = spriteH
            val name = partnerName
            record.busy = true
            record.pending = false
            ioScope.launch(Dispatchers.Default) {
                val bitmap = if (src == null) null else {
                    val bufferIndex = record.nextBuffer
                    record.nextBuffer = 1 - bufferIndex
                    val buffer = record.buffers[bufferIndex] ?: Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
                        .also { record.buffers[bufferIndex] = it }
                    buffer.eraseColor(Color.TRANSPARENT)
                    val drawW = width * SCALE * ratio
                    val drawH = height * SCALE * ratio
                    val matrix = Matrix().apply {
                        postScale(SCALE * ratio, SCALE * ratio)
                        if (facing > 0) postScale(-1f, 1f, drawW / 2f, drawH / 2f)
                        postTranslate(x, ((canvasH - drawH) / 2f).coerceAtLeast(0f))
                    }
                    Canvas(buffer).drawBitmap(src, matrix, paint)
                    buffer
                }
                handler.post {
                    record.busy = false
                    if (!running || !generation.accepts(currentGeneration) || renderStates[appWidgetId] !== record) return@post
                    val views = RemoteViews(context.packageName, R.layout.digimon_widget)
                    bitmap?.let { views.setImageViewBitmap(R.id.widget_canvas, it) }
                    if (record.initializedGeneration != currentGeneration || record.empty != empty) {
                        views.setViewVisibility(R.id.widget_canvas, if (empty) View.GONE else View.VISIBLE)
                        views.setViewVisibility(R.id.widget_empty, if (empty) View.VISIBLE else View.GONE)
                        views.setInt(R.id.widget_empty, "setBackgroundColor", palette.surfaceElevated.toArgb())
                        views.setTextColor(R.id.widget_empty, palette.textPrimary.toArgb())
                        views.setContentDescription(R.id.widget_canvas, context.getString(R.string.immersion_widget_partner,
                            name ?: context.getString(R.string.widget_digimon_label)))
                        views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, appWidgetId,
                            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                        runCatching { manager.updateAppWidget(appWidgetId, views) }
                        record.initializedGeneration = currentGeneration
                        record.empty = empty
                    } else runCatching { manager.partiallyUpdateAppWidget(appWidgetId, views) }
                    record.lastKey = key
                    if (record.pending) renderFrame(context, manager, appWidgetId)
                }
            }
        }
    }
}
