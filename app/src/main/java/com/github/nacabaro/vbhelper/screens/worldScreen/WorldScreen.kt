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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.github.nacabaro.vbhelper.di.VBHelper
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun WorldScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as VBHelper
    var location by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var status by remember { mutableStateOf("Allow location access to discover nearby Digimon.") }
    val spawns by app.container.worldRepository.observeSpawns().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    fun loadLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            status = "Location permission is required."
            return
        }
        LocationServices.getFusedLocationProviderClient(context).lastLocation
            .addOnSuccessListener { currentLocation ->
                if (currentLocation == null) {
                    status = "Waiting for a location fix."
                    return@addOnSuccessListener
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
            .addOnFailureListener {
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
            drawCircle(Color(0xFF4FC3F7), radius, style = Stroke(3f))
            drawCircle(Color.White, 8f, center)
            drawCircle(Color.White.copy(alpha = .35f), radius * .5f, center, style = Stroke(2f))
            drawCircle(Color.White.copy(alpha = .18f), radius * .75f, center, style = Stroke(1f))
            location?.let { (playerLat, playerLon) ->
                spawns.forEach { spawn ->
                    val northMeters = (spawn.latitude - playerLat) * 111_320.0
                    val eastMeters = (spawn.longitude - playerLon) *
                        111_320.0 * cos(Math.toRadians(playerLat))
                    val scale = radius / 500.0f
                    val distance = sqrt(northMeters * northMeters + eastMeters * eastMeters)
                    val displayedDistance = distance.coerceAtMost(500.0)
                    val factor = if (distance == 0.0) 0.0 else displayedDistance / distance
                    val point = Offset(
                        center.x + (eastMeters * factor * scale).toFloat(),
                        center.y - (northMeters * factor * scale).toFloat()
                    )
                    drawCircle(
                        if (distance <= 40.0) Color(0xFF69F0AE) else Color(0xFFFFC107),
                        12f,
                        point
                    )
                    drawCircle(Color.White, 12f, point, style = Stroke(2f))
                }
            }
        }
        Text("${spawns.size} nearby Digimon", style = MaterialTheme.typography.titleMedium)
        Text("Green: interaction range (40 m) • Yellow: detected")
        if (location == null) {
            Button(onClick = {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) { Text("Enable location") }
        }
    }
}
