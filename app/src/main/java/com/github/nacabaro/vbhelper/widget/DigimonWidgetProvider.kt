package com.github.nacabaro.vbhelper.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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

    override fun onDisabled(context: Context) {
        stopAnimation()
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
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
        private const val SPRITE_REFRESH_EVERY = 120 // frames (~26s)

        private val handler = Handler(Looper.getMainLooper())
        private var running = false
        private var frameCounter = 0

        // Animation state (shared across all instances of this widget)
        private var posX = 0f
        private var direction = 1 // +1 right, -1 left
        private var frameIndex = 0 // 0 or 1
        private var isWalking = true
        private var ticksLeftInMode = Random.nextInt(MIN_WALK_TICKS, MAX_WALK_TICKS + 1)

        private var cachedSprites: CharacterDtos.WidgetSprites? = null
        private var idleBmps: Array<Bitmap?> = arrayOf(null, null)
        private var walkBmps: Array<Bitmap?> = arrayOf(null, null)
        private var spriteW = 0
        private var spriteH = 0

        private val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            isAntiAlias = false
            isFilterBitmap = false // keep pixel art crisp
        }

        private val tickRunnable = object : Runnable {
            override fun run() {
                if (!running) return
                val ctx = appContext ?: return
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
                handler.postDelayed(this, FRAME_MS)
            }
        }

        @Volatile
        private var appContext: Context? = null

        fun ensureAnimationRunning(context: Context) {
            appContext = context.applicationContext
            if (running) return
            running = true
            handler.removeCallbacks(tickRunnable)
            handler.post(tickRunnable)
        }

        fun stopAnimation() {
            running = false
            handler.removeCallbacks(tickRunnable)
        }

        /** Call from the app when the active Digimon changes so the widget reloads sprites. */
        fun notifyActiveCharacterChanged(context: Context) {
            cachedSprites = null
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
            frameCounter++
            if (frameCounter % SPRITE_REFRESH_EVERY == 0) {
                // Periodically re-query DB in case active Digimon changed
                cachedSprites = null
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
            if (cachedSprites != null && idleBmps[0] != null) return

            val sprites = runBlocking(Dispatchers.IO) {
                try {
                    val app = context.applicationContext as? VBHelper
                    app?.container?.db?.userCharacterDao()?.getActiveCharacterWidgetSprites()
                } catch (_: Exception) {
                    null
                }
            } ?: return

            cachedSprites = sprites
            spriteW = sprites.width
            spriteH = sprites.height

            fun toBmp(bytes: ByteArray): Bitmap? {
                return try {
                    BitmapData(bytes, sprites.width, sprites.height).getBitmap()
                        .copy(Bitmap.Config.ARGB_8888, false)
                } catch (_: Exception) {
                    null
                }
            }

            idleBmps[0] = toBmp(sprites.spriteIdle1)
            idleBmps[1] = toBmp(sprites.spriteIdle2)
            walkBmps[0] = toBmp(sprites.spriteWalk1)
            walkBmps[1] = toBmp(sprites.spriteWalk2)
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
            val canvasW = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, minW.toFloat(), context.resources.displayMetrics
            ).roundToInt().coerceAtLeast(1)
            val canvasH = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, minH.toFloat(), context.resources.displayMetrics
            ).roundToInt().coerceAtLeast(1)

            val views = RemoteViews(context.packageName, R.layout.digimon_widget)

            val bitmap = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.TRANSPARENT)

            val src = if (isWalking) {
                walkBmps[frameIndex]
            } else {
                idleBmps[frameIndex]
            }

            if (src != null && spriteW > 0 && spriteH > 0) {
                val drawW = spriteW * SCALE
                val drawH = spriteH * SCALE

                // Clamp position so Digimon stays fully visible
                val maxX = (canvasW - drawW).toFloat().coerceAtLeast(0f)
                if (posX < 0f) {
                    posX = 0f
                    direction = 1
                } else if (posX > maxX) {
                    posX = maxX
                    direction = -1
                }

                val y = ((canvasH - drawH) / 2f).coerceAtLeast(0f)

                val matrix = Matrix()
                // Scale up for pixel art
                matrix.postScale(SCALE.toFloat(), SCALE.toFloat())
                if (direction < 0) {
                    // Flip horizontally around the sprite center
                    matrix.postScale(-1f, 1f, drawW / 2f, drawH / 2f)
                }
                matrix.postTranslate(posX, y)

                canvas.drawBitmap(src, matrix, paint)
            } else {
                // No active Digimon – draw a small placeholder dot so the widget is visible
                val p = Paint().apply {
                    color = Color.GRAY
                    isAntiAlias = true
                }
                val r = 8f * density
                canvas.drawCircle(canvasW / 2f, canvasH / 2f, r, p)
            }

            views.setImageViewBitmap(R.id.widget_canvas, bitmap)

            // Click opens MainActivity (app home)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)

            try {
                manager.updateAppWidget(appWidgetId, views)
            } catch (_: Exception) {
                // Widget may have been removed mid-update
            }
        }
    }
}
