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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.github.nacabaro.vbhelper.di.VBHelper
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch

@Composable
fun WorldScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as VBHelper
    var location by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var status by remember { mutableStateOf("Allow location access to discover nearby Digimon.") }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) status = "Radar active" else status = "Location permission is required."
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener {
                if (it != null) {
                    location = it.latitude to it.longitude
                    scope.launch { app.container.worldRepository.ensureSpawns(it.latitude, it.longitude) }
                    status = "Radar active"
                }
            }
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
            drawCircle(Color(0xFF4FC3F7), radius, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            drawCircle(Color.White, 8f, center)
            drawCircle(Color.White.copy(alpha = .35f), radius * .5f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }
        if (location == null) {
            Button(onClick = {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) { Text("Enable location") }
        }
    }
}
