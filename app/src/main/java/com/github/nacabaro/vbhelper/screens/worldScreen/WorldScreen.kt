package com.github.nacabaro.vbhelper.screens.worldScreen

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.getBitmap
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sqrt

/** Radius, in meters, within which the player can tap a Digimon to chat. */
private const val INTERACTION_RANGE_METERS = 40.0

@Composable
fun WorldScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as VBHelper
    var location by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var status by remember {
        mutableStateOf("Permita o acesso à localização para descobrir Digimon próximos.")
    }
    val spawns by app.container.worldRepository.observeSpawns().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(1f) }
    var idleFrame by remember { mutableIntStateOf(0) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(700L)
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

    fun loadLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            status = "É necessária permissão de localização."
            return
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        fun applyLocation(currentLocation: android.location.Location?) {
            if (currentLocation == null) {
                status = "Aguardando sinal de localização. Saia para uma área aberta."
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
                    status = "Radar ativo"
                }.onFailure {
                    status = "Não foi possível carregar Digimon próximos."
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
                    .addOnFailureListener { status = "Não foi possível ler a localização." }
            }
        }.addOnFailureListener {
            status = "Não foi possível ler a localização."
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) loadLocation()
        else status = "É necessária permissão de localização."
    }

    LaunchedEffect(Unit) {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        } else {
            loadLocation()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            loadLocation()
        }
    }

    Scaffold(
        topBar = { TopBanner(text = stringResource(R.string.nav_world)) }
    ) { contentPadding ->
        Column(
            Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color(0xFFDCE8D5))
            ) {
                val density = LocalDensity.current
                val boxWidthPx = with(density) { maxWidth.toPx() }
                val center = Offset(boxWidthPx / 2f, boxWidthPx / 2f)
                val visibleRadiusMeters = 500.0 / zoom
                val scale = (boxWidthPx / 2f) / visibleRadiusMeters.toFloat()
                val interactionRadiusPx = (INTERACTION_RANGE_METERS * scale).toFloat()

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val gridSpacingPx = (60f * zoom).coerceAtLeast(20f)
                    var x = center.x % gridSpacingPx
                    while (x < size.width) {
                        drawLine(Color(0xFFB9CDAF), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
                        x += gridSpacingPx
                    }
                    var y = center.y % gridSpacingPx
                    while (y < size.height) {
                        drawLine(Color(0xFFB9CDAF), Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
                        y += gridSpacingPx
                    }
                    drawCircle(
                        color = Color(0x552196F3),
                        radius = interactionRadiusPx,
                        center = center,
                        style = Stroke(width = 3f)
                    )
                }

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (center.x - 14.dp.toPx()).toInt(),
                                (center.y - 14.dp.toPx()).toInt()
                            )
                        }
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2196F3))
                        .border(2.dp, Color.White, CircleShape)
                )

                location?.let { (playerLat, playerLon) ->
                    spawnBitmaps.forEach { (spawn, image) ->
                        val northMeters = (spawn.latitude - playerLat) * 111_320.0
                        val eastMeters = (spawn.longitude - playerLon) *
                            111_320.0 * cos(Math.toRadians(playerLat))
                        val distance = sqrt(northMeters * northMeters + eastMeters * eastMeters)

                        if (distance <= visibleRadiusMeters) {
                            val px = center.x + (eastMeters * scale).toFloat()
                            val py = center.y - (northMeters * scale).toFloat()
                            val withinRange = distance <= INTERACTION_RANGE_METERS
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
                                                "Chegue mais perto para interagir com este Digimon."
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("${spawns.size} Digimon próximos", style = MaterialTheme.typography.titleMedium)
            Text("Toque em um Digimon dentro do círculo azul para conversar.")

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Button(onClick = { zoom = (zoom / 1.5f).coerceAtLeast(.5f) }) { Text("-") }
                Text("Zoom ${(zoom * 100).toInt()}%", modifier = Modifier.padding(top = 12.dp))
                Button(onClick = { zoom = (zoom * 1.5f).coerceAtMost(4f) }) { Text("+") }
            }

            if (location == null) {
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
                ) { Text("Ativar localização") }
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
