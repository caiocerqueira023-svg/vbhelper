package com.github.nacabaro.vbhelper.screens.worldScreen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.getBitmap
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Radius, in meters, within which the player can tap a Digimon to chat. */
private const val INTERACTION_RANGE_METERS = 40.0
private const val GRID_SIZE_METERS = 20.0
/** Low-pass factor for heading (0..1). Higher = snappier, lower = smoother. */
private const val COMPASS_SMOOTHING = 0.28f

@Composable
fun WorldScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as VBHelper
    var location by remember { mutableStateOf<Location?>(null) }
    var origin by remember { mutableStateOf<Location?>(null) }
    var heading by remember { mutableFloatStateOf(0f) }
    var status by remember {
        mutableStateOf(context.getString(R.string.ui_world_grant_location))
    }
    val spawns by app.container.worldRepository.observeSpawns().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(2.4f) }
    var idleFrame by remember { mutableIntStateOf(0) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(700L)
            idleFrame = 1 - idleFrame
        }
    }

    val spawnBitmaps = remember(spawns, idleFrame) {
        spawns.mapNotNull { spawn ->
            runCatching {
                val frame = if (idleFrame == 0) spawn.spriteIdle else spawn.spriteIdle2
                spawn to BitmapData(
                    bitmap = frame,
                    width = spawn.spriteWidth.coerceAtLeast(1),
                    height = spawn.spriteHeight.coerceAtLeast(1)
                ).getBitmap().asImageBitmap()
            }.getOrNull()
        }
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasLocationPermission = granted.values.any { it } ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (hasLocationPermission) {
            status = context.getString(R.string.ui_world_location_active)
        } else {
            status = context.getString(R.string.ui_world_location_required)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Mantém uma assinatura contínua do GPS em vez de consultar apenas a localização em cache.
    DisposableEffect(hasLocationPermission) {
        if (!hasLocationPermission) {
            onDispose { }
        } else {
            val client = LocationServices.getFusedLocationProviderClient(context)
            var lastSpawnRefresh: Location? = null

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { newLocation ->
                        if (origin == null) {
                            origin = Location(newLocation).apply {
                                latitude = newLocation.latitude
                                longitude = newLocation.longitude
                            }
                        }

                        location = newLocation
                        status = context.getString(R.string.ui_world_radar_active)

                        // Keep follow system and chat start-following in sync with GPS.
                        app.container.worldRepository.updateLastKnownLocation(
                            newLocation.latitude,
                            newLocation.longitude
                        )
                        scope.launch {
                            val stopped = withContext(Dispatchers.IO) {
                                app.container.worldRepository.processFollowMovement(
                                    newLocation.latitude,
                                    newLocation.longitude
                                )
                            }
                            if (stopped.isNotEmpty()) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.ui_world_stopped_following_toast),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        val shouldRefreshSpawns = lastSpawnRefresh == null ||
                            lastSpawnRefresh!!.distanceTo(newLocation) >= 60f

                        if (shouldRefreshSpawns) {
                            lastSpawnRefresh = Location(newLocation)
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        app.container.worldRepository.ensureSpawns(
                                            newLocation.latitude,
                                            newLocation.longitude
                                        )
                                    }
                                }.onFailure {
                                    status = context.getString(R.string.ui_world_load_nearby_failed)
                                }
                            }
                        }
                    }
                }
            }

            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                2_000L
            )
                .setMinUpdateIntervalMillis(1_000L)
                .setMinUpdateDistanceMeters(2f)
                .setWaitForAccurateLocation(false)
                .build()

            client.lastLocation.addOnSuccessListener { cached ->
                cached?.let { cachedLocation ->
                    if (origin == null) {
                        origin = Location(cachedLocation).apply {
                            latitude = cachedLocation.latitude
                            longitude = cachedLocation.longitude
                        }
                    }
                    location = cachedLocation
                    app.container.worldRepository.updateLastKnownLocation(
                        cachedLocation.latitude,
                        cachedLocation.longitude
                    )
                }
            }

            client.requestLocationUpdates(request, callback, context.mainLooper)
                .addOnFailureListener {
                    status = context.getString(R.string.ui_world_tracking_failed)
                }

            onDispose {
                client.removeLocationUpdates(callback)
            }
        }
    }

    // TYPE_ROTATION_VECTOR combines the available orientation sensors. The map uses
    // Android's azimuth for the top of the current display, which is the only stable
    // reference for a full 360° turn. Mixing the top and screen-normal axes made the
    // selected direction ambiguous after half a turn on some devices.
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rotationMatrix = FloatArray(9)
        val displayRotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        // Local filter state — do not read Compose state from the sensor thread.
        var smoothedHeading = 0f
        var hasSample = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // event.values can be 3–5 floats; getRotationMatrixFromVector accepts both.
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                // The rotation-vector axes are tied to the device's natural
                // orientation, while the UI follows the current display rotation.
                // Without this remap, turning the phone through 180° makes the map
                // appear to undo its rotation as the screen coordinate system flips.
                val displayRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.display.rotation
                } else {
                    @Suppress("DEPRECATION")
                    (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                        .defaultDisplay.rotation
                }
                val (axisX, axisY) = when (displayRotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(
                    rotationMatrix,
                    axisX,
                    axisY,
                    displayRotationMatrix
                )

                SensorManager.getOrientation(displayRotationMatrix, orientationAngles)
                // orientationAngles[0] is azimuth in radians (-π..π).
                val degrees = (Math.toDegrees(orientationAngles[0].toDouble()).toFloat() + 360f) % 360f

                if (!hasSample) {
                    smoothedHeading = degrees
                    hasSample = true
                } else {
                    // Shortest-path low-pass so 359° → 1° does not spin the long way.
                    val delta = ((degrees - smoothedHeading + 540f) % 360f) - 180f
                    smoothedHeading = (smoothedHeading + delta * COMPASS_SMOOTHING + 360f) % 360f
                }
                heading = smoothedHeading
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (rotationSensor != null) {
            sensorManager.registerListener(
                listener,
                rotationSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    Scaffold(
        topBar = {
            TopBanner(text = "${stringResource(R.string.nav_world)} • ${cardinalDirection(heading)}")
        }
    ) { contentPadding ->
        Column(
            Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.ui_world_direction, cardinalDirection(heading), heading.roundToInt()),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))

            Button(onClick = { navController.navigate(NavigationItems.WorldRecruits.route) }) {
                Text(stringResource(R.string.ui_world_recruits_button))
            }
            Spacer(Modifier.height(8.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(0.5f, 4f)
                        }
                    }
            ) {
                val density = LocalDensity.current
                val boxWidthPx = with(density) { maxWidth.toPx() }
                val center = Offset(boxWidthPx / 2f, boxWidthPx / 2f)
                val visibleRadiusMeters = 500.0 / zoom
                val scale = (boxWidthPx / 2f) / visibleRadiusMeters.toFloat()
                val interactionRadiusPx = (INTERACTION_RANGE_METERS * scale).toFloat()

                // O jogador permanece sempre no centro da tela. O mundo é que se desloca
                // em sentido contrário ao movimento do jogador.
                val playerOffset = center
                val playerDisplacement = origin?.let { originLocation ->
                    location?.let { currentLocation ->
                        val northMeters = (currentLocation.latitude - originLocation.latitude) * 111_320.0
                        val eastMeters = (currentLocation.longitude - originLocation.longitude) *
                            111_320.0 * cos(Math.toRadians(originLocation.latitude))
                        Offset(
                            (eastMeters * scale).toFloat(),
                            (-northMeters * scale).toFloat()
                        )
                    }
                } ?: Offset.Zero

                val primaryColor = MaterialTheme.colorScheme.primary

                Canvas(modifier = Modifier.fillMaxSize()) {
                    // A grade representa o mundo, se desloca com o jogador e gira com a bússola.
                    withTransform({
                        rotate(degrees = -heading, pivot = center)
                    }) {
                        val gridSpacingPx = (GRID_SIZE_METERS * scale).toFloat().coerceAtLeast(20f)
                        val originX = center.x - playerDisplacement.x
                        val originY = center.y - playerDisplacement.y

                        // Desenha a grade em uma área maior que a tela para que a rotação
                        // nunca revele as bordas vazias do Canvas.
                        val diagonal = sqrt(
                            size.width * size.width +
                                size.height * size.height
                        )

                        var x = originX % gridSpacingPx
                        while (x > -diagonal) x -= gridSpacingPx
                        while (x < size.width + diagonal) {
                            drawLine(
                                Color(0xFF555555),
                                Offset(x, -diagonal),
                                Offset(x, size.height + diagonal),
                                strokeWidth = 2f
                            )
                            x += gridSpacingPx
                        }

                        var y = originY % gridSpacingPx
                        while (y > -diagonal) y -= gridSpacingPx
                        while (y < size.height + diagonal) {
                            drawLine(
                                Color(0xFF555555),
                                Offset(-diagonal, y),
                                Offset(size.width + diagonal, y),
                                strokeWidth = 2f
                            )
                            y += gridSpacingPx
                        }
                    }

                    drawCircle(
                        color = primaryColor.copy(alpha = 0.35f),
                        radius = interactionRadiusPx,
                        center = playerOffset,
                        style = Stroke(width = 3f)
                    )

                    // Rosa dos ventos: os quatro pontos cardeais giram ao redor do jogador.
                    // Quando o aparelho aponta para uma direção, o marcador correspondente
                    // fica no topo do mapa, como em uma bússola.
                    val compassRadius = size.minDimension / 2f - 28.dp.toPx()
                    val arrowLength = 18.dp.toPx()
                    val arrowHalfWidth = 8.dp.toPx()
                    val cardinals = listOf(
                        0f to primaryColor, // Norte
                        90f to Color(0xFFB0B0B0),                // Leste
                        180f to Color(0xFFB0B0B0),               // Sul
                        270f to Color(0xFFB0B0B0)                // Oeste
                    )

                    cardinals.forEach { (cardinalAzimuth, arrowColor) ->
                        val screenAngleDeg = cardinalAzimuth - heading - 90f
                        val screenAngle = screenAngleDeg * (PI / 180.0)
                        val direction = Offset(
                            cos(screenAngle).toFloat(),
                            sin(screenAngle).toFloat()
                        )
                        val tangent = Offset(-direction.y, direction.x)
                        val tip = center + direction * compassRadius
                        val baseCenter = tip - direction * arrowLength
                        val arrowPath = Path().apply {
                            moveTo(tip.x, tip.y)
                            lineTo(
                                baseCenter.x + tangent.x * arrowHalfWidth,
                                baseCenter.y + tangent.y * arrowHalfWidth
                            )
                            lineTo(
                                baseCenter.x - tangent.x * arrowHalfWidth,
                                baseCenter.y - tangent.y * arrowHalfWidth
                            )
                            close()
                        }
                        drawPath(arrowPath, arrowColor)
                    }
                }

                // Letras cardeais acompanham as setas e giram pela borda do mapa.
                val cardinalNames = stringArrayResource(R.array.world_cardinal_directions_8)
                listOf(
                    0f to cardinalNames[0],
                    90f to cardinalNames[2],
                    180f to cardinalNames[4],
                    270f to cardinalNames[6]
                ).forEach { (cardinalAzimuth, label) ->
                    val compassRadiusPx = boxWidthPx / 2f - with(density) { 48.dp.toPx() }
                    val angleRadians = (cardinalAzimuth - heading - 90f) * (PI / 180.0)
                    val labelX = center.x + cos(angleRadians).toFloat() * compassRadiusPx
                    val labelY = center.y + sin(angleRadians).toFloat() * compassRadiusPx
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.offset {
                            IntOffset(
                                (labelX - 8.dp.toPx()).toInt(),
                                (labelY - 10.dp.toPx()).toInt()
                            )
                        }
                    )
                }

                // O marcador azul permanece centralizado; a grade e os objetos do mundo se movem ao redor dele.
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (playerOffset.x - 14.dp.toPx()).toInt(),
                                (playerOffset.y - 14.dp.toPx()).toInt()
                            )
                        }
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(2.dp, Color.White, CircleShape)
                )

                // A posição dos Digimon usa a mesma origem fixa do jogador.
                origin?.let { originLocation ->
                    spawnBitmaps.forEach { (spawn, image) ->
                        val northMeters = (spawn.latitude - originLocation.latitude) * 111_320.0
                        val eastMeters = (spawn.longitude - originLocation.longitude) *
                            111_320.0 * cos(Math.toRadians(originLocation.latitude))
                        val distanceFromCenter = sqrt(northMeters * northMeters + eastMeters * eastMeters)

                        if (distanceFromCenter <= visibleRadiusMeters * 1.25) {
                            // Posição geográfica do Digimon em relação ao jogador.
                            // O mesmo vetor é rotacionado junto com a grade.
                            val worldX = (eastMeters * scale).toFloat() - playerDisplacement.x
                            val worldY = (-northMeters * scale).toFloat() - playerDisplacement.y
                            val angle = Math.toRadians((-heading).toDouble())
                            val cosAngle = cos(angle).toFloat()
                            val sinAngle = sin(angle).toFloat()

                            val rotatedX = worldX * cosAngle - worldY * sinAngle
                            val rotatedY = worldX * sinAngle + worldY * cosAngle

                            val px = center.x + rotatedX
                            val py = center.y + rotatedY
                            val playerLocation = location
                            val distanceToPlayer = if (playerLocation != null) {
                                val northToPlayer = (spawn.latitude - playerLocation.latitude) * 111_320.0
                                val eastToPlayer = (spawn.longitude - playerLocation.longitude) *
                                    111_320.0 * cos(Math.toRadians(playerLocation.latitude))
                                sqrt(northToPlayer * northToPlayer + eastToPlayer * eastToPlayer)
                            } else Double.MAX_VALUE
                            val withinRange = distanceToPlayer <= INTERACTION_RANGE_METERS
                            val markerSizeDp = 40.dp

                            Image(
                                bitmap = image,
                                contentDescription = spawn.speciesName ?: "Digimon",
                                filterQuality = FilterQuality.None,
                                modifier = Modifier
                                    .offset {
                                        val markerSizePx = markerSizeDp.toPx()
                                        IntOffset(
                                            (px - markerSizePx / 2f).toInt(),
                                            (py - markerSizePx / 2f).toInt()
                                        )
                                    }
                                    .size(markerSizeDp)
                                    .then(
                                        if (spawn.isFollowing) {
                                            Modifier.border(2.dp, Color(0xFF4FC3F7), CircleShape)
                                        } else Modifier
                                    )
                                    .clickable {
                                        if (withinRange) {
                                            navController.navigate(
                                                NavigationItems.WorldChat.route.replace(
                                                    "{spawnId}",
                                                    spawn.id.toString()
                                                )
                                            )
                                        } else {
                                            toastMessage =
                                                context.getString(R.string.ui_world_too_far)
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.ui_world_nearby_count, spawns.size),
                style = MaterialTheme.typography.titleMedium
            )
            Text(stringResource(R.string.ui_world_tap_instruction))
            Text(stringResource(R.string.ui_world_grid_instruction))
            Text(stringResource(R.string.ui_world_zoom_instruction))
            Text(stringResource(R.string.ui_world_compass_instruction))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Button(onClick = { zoom = (zoom / 1.5f).coerceAtLeast(.5f) }) { Text("-") }
                Text(
                    stringResource(R.string.ui_world_zoom_label, (zoom * 100).toInt()),
                    modifier = Modifier.padding(top = 12.dp)
                )
                Button(onClick = { zoom = (zoom * 1.5f).coerceAtMost(4f) }) { Text("+") }
            }

            if (!hasLocationPermission || location == null) {
                Button(
                    onClick = {
                        launcher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text(stringResource(R.string.ui_world_enable_location)) }
            }
        }
    }

    toastMessage?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            toastMessage = null
        }
    }
}

@Composable
private fun cardinalDirection(degrees: Float): String {
    val names = stringArrayResource(R.array.world_cardinal_directions_8)
    val normalized = (degrees + 360f) % 360f
    val index = ((normalized + 22.5f) / 45f).toInt() % 8
    return names[index]
}
