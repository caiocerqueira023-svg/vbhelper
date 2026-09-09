package com.github.nacabaro.vbhelper.screens.worldScreen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.github.nacabaro.vbhelper.di.VBHelper
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.github.nacabaro.vbhelper.utils.BitmapData
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sqrt

@Composable
fun WorldScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as VBHelper
    var location by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var status by remember { mutableStateOf("Allow location access to discover nearby Digimon.") }
    val spawns by app.container.worldRepository.observeSpawns().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(1f) }
    var idleFrame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(700L)
            idleFrame = 1 - idleFrame
        }
    }
    val animatedSpawnImages = remember(spawns, idleFrame) {
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
    val animatedSpawnIds = remember(animatedSpawnImages) {
        animatedSpawnImages.map { it.first.id }.toSet()
    }
    fun loadLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            status = "Location permission is required."
            return
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        fun applyLocation(currentLocation: android.location.Location?) {
                if (currentLocation == null) {
                    status = "Waiting for a location fix. Move outdoors or enable device location."
                    return
                }
                location = currentLocation.latitude to currentLocation.longitude
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            app.container.worldRepository.ensureSpawns(
                                currentLocation.latitude,
                                currentLocation.longitude
                            )
                        }
                    }.onSuccess {
                        status = "Radar active"
                    }.onFailure {
                        status = "Could not load nearby Digimon."
                    }
                }
        }
        client.lastLocation.addOnSuccessListener { cachedLocation ->
            if (cachedLocation != null) {
                applyLocation(cachedLocation)
            } else {
                client.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    CancellationTokenSource().token
                ).addOnSuccessListener(::applyLocation)
                    .addOnFailureListener { status = "Could not read device location." }
            }
        }.addOnFailureListener {
            status = "Could not read device location."
        }
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) loadLocation()
        else status = "Location permission is required."
    }
    LaunchedEffect(Unit) {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            loadLocation()
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("World", style = MaterialTheme.typography.headlineMedium)
        Text(status)
        Spacer(Modifier.height(16.dp))
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2.2f
            drawCircle(Color(0xFF102A43), radius)
            drawCircle(Color(0xFF173B5E), radius * .75f)
            drawCircle(Color(0xFF1D4D73), radius * .5f)
            drawCircle(Color(0xFF4FC3F7), radius, style = Stroke(3f))
            drawCircle(Color.White, 8f, center)
            drawCircle(Color.White.copy(alpha = .35f), radius * .5f, center, style = Stroke(2f))
            drawCircle(Color.White.copy(alpha = .18f), radius * .75f, center, style = Stroke(1f))
            for (i in -2..2) {
                val offset = i * radius * .25f
                drawLine(Color.White.copy(alpha = .12f), Offset(center.x + offset, center.y - radius),
                    Offset(center.x + offset, center.y + radius), 1f)
                drawLine(Color.White.copy(alpha = .12f), Offset(center.x - radius, center.y + offset),
                    Offset(center.x + radius, center.y + offset), 1f)
            }
            location?.let { (playerLat, playerLon) ->
                spawns.forEach { spawn ->
                    val northMeters = (spawn.latitude - playerLat) * 111_320.0
                    val eastMeters = (spawn.longitude - playerLon) *
                        111_320.0 * cos(Math.toRadians(playerLat))
                    val scale = radius / (500.0f / zoom)
                    val distance = sqrt(northMeters * northMeters + eastMeters * eastMeters)
                    val displayedDistance = distance.coerceAtMost(500.0)
                    val factor = if (distance == 0.0) 0.0 else displayedDistance / distance
                    val point = Offset(
                        center.x + (eastMeters * factor * scale).toFloat(),
                        center.y - (northMeters * factor * scale).toFloat()
                    )
                    if (spawn.id !in animatedSpawnIds) {
                        drawCircle(
                            if (distance <= 40.0) Color(0xFF69F0AE) else Color(0xFFFFC107),
                            12f,
                            point
                        )
                        drawCircle(Color.White, 12f, point, style = Stroke(2f))
                    }
                }
            }
            animatedSpawnImages.forEach { (spawn, image) ->
                location?.let { (playerLat, playerLon) ->
                    val northMeters = (spawn.latitude - playerLat) * 111_320.0
                    val eastMeters = (spawn.longitude - playerLon) *
                        111_320.0 * cos(Math.toRadians(playerLat))
                    val distance = sqrt(northMeters * northMeters + eastMeters * eastMeters)
                    val visibleRadius = 500.0 / zoom
                    if (distance <= visibleRadius) {
                        val point = Offset(
                            center.x + (eastMeters * radius / visibleRadius).toFloat(),
                            center.y - (northMeters * radius / visibleRadius).toFloat()
                        )
                        drawImage(
                            image,
                            dstOffset = IntOffset((point.x - 24f).toInt(), (point.y - 24f).toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(48, 48)
                        )
                    }
                }
            }
        }
        Text("${spawns.size} nearby Digimon", style = MaterialTheme.typography.titleMedium)
        Text("Green: interaction range (40 m) • Radar range: ${(500 / zoom).toInt()} m")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { zoom = (zoom / 1.5f).coerceAtLeast(.5f) }) { Text("-") }
            Text("Zoom ${(zoom * 100).toInt()}%", modifier = Modifier.padding(top = 12.dp))
            Button(onClick = { zoom = (zoom * 1.5f).coerceAtMost(4f) }) { Text("+") }
        }
        if (location == null) {
            Button(onClick = {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) { Text("Enable location") }
        }
    }
}
