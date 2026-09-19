package com.github.nacabaro.vbhelper.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.nacabaro.vbhelper.battle.AssetAudioPlayer
import com.github.nacabaro.vbhelper.battle.BattleAssetPaths
import com.github.nacabaro.vbhelper.battle.BattleCharacterImage
import com.github.nacabaro.vbhelper.battle.DigimonAnimationType
import com.github.nacabaro.vbhelper.battle.HitEffectOverlay
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

data class OfflineBattleParticipant(
    val character: CharacterDtos.CharacterWithSprites?,
    val assetCharacterId: String?,
    val displayName: String,
    val maxHp: Int,
    val attackPower: Int
) {
    val stableId: String
        get() = character?.id?.toString() ?: assetCharacterId ?: displayName
}

fun offlineBattleParticipant(
    character: CharacterDtos.CharacterWithSprites,
    maxHp: Int,
    attackPower: Int
): OfflineBattleParticipant = OfflineBattleParticipant(
    character = character,
    assetCharacterId = null,
    displayName = character.nickname?.takeIf { it.isNotBlank() }
        ?: character.speciesName?.takeIf { it.isNotBlank() }
        ?: "Stored Digimon #${character.id}",
    maxHp = maxHp.coerceAtLeast(1),
    attackPower = attackPower.coerceAtLeast(1)
)

fun assetOfflineBattleParticipant(
    characterId: String,
    displayName: String,
    maxHp: Int,
    attackPower: Int
): OfflineBattleParticipant = OfflineBattleParticipant(
    character = null,
    assetCharacterId = characterId,
    displayName = displayName,
    maxHp = maxHp.coerceAtLeast(1),
    attackPower = attackPower.coerceAtLeast(1)
)

@Composable
fun OfflineBattleEntryPanel(
    player: OfflineBattleParticipant,
    opponents: List<OfflineBattleParticipant>,
    onStartBattle: (OfflineBattleParticipant) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
        ),
        border = BorderStroke(1.dp, SurfaceStroke)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = "Offline test battles",
                color = TextPrimaryOnDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Uses Digimon already stored in this app. This temporary list is only for battle testing.",
                color = TextSecondaryOnDark,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (opponents.isEmpty()) {
                Text(
                    text = "No offline opponents are available yet.",
                    color = TextSecondaryOnDark,
                    fontSize = 13.sp
                )
            } else {
                Text(
                    text = "${player.displayName} can challenge:",
                    color = VitalCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(opponents, key = { it.stableId }) { opponent ->
                        Button(
                            onClick = { onStartBattle(opponent) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text("Battle ${opponent.displayName}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocalBattleScreen(
    player: OfflineBattleParticipant,
    opponent: OfflineBattleParticipant,
    selectedBackgroundSet: Int,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val audioPlayer = remember { AssetAudioPlayer(context) }
    val coroutineScope = rememberCoroutineScope()

    var playerHp by remember(player.stableId, opponent.stableId) { mutableStateOf(player.maxHp) }
    var opponentHp by remember(player.stableId, opponent.stableId) { mutableStateOf(opponent.maxHp) }
    var playerAttacking by remember { mutableStateOf(false) }
    var opponentAttacking by remember { mutableStateOf(false) }
    var showPlayerHit by remember { mutableStateOf(false) }
    var showOpponentHit by remember { mutableStateOf(false) }
    var showPlayerDamage by remember { mutableStateOf(false) }
    var showOpponentDamage by remember { mutableStateOf(false) }
    var playerDamage by remember { mutableStateOf(0) }
    var opponentDamage by remember { mutableStateOf(0) }
    var turnInProgress by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Boolean?>(null) }

    BackHandler(enabled = true, onBack = onExit)

    fun rollDamage(power: Int): Int {
        val multiplier = Random.nextDouble(0.82, 1.18)
        return (power * multiplier).roundToInt().coerceAtLeast(1)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MultiLayerAnimatedBattleBackground(
            modifier = Modifier.fillMaxSize(),
            backgroundSetIndex = selectedBackgroundSet
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.26f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onExit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black.copy(alpha = 0.58f),
                        contentColor = TextPrimaryOnDark
                    )
                ) {
                    Text("Back")
                }
                Text(
                    text = "OFFLINE TEST BATTLE",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    color = VitalCyan,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BattleHealthBar(
                    name = player.displayName,
                    current = playerHp,
                    maximum = player.maxHp,
                    color = VitalCyan,
                    modifier = Modifier.weight(1f)
                )
                BattleHealthBar(
                    name = opponent.displayName,
                    current = opponentHp,
                    maximum = opponent.maxHp,
                    color = Color(0xFFFF6680),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BattleCharacterImage(
                    characterId = player.assetCharacterId.orEmpty(),
                    databaseCharacter = player.character,
                    animationType = if (playerAttacking) DigimonAnimationType.ATTACK else DigimonAnimationType.IDLE,
                    modifier = Modifier
                        .weight(1f)
                        .size(150.dp)
                        .scale(-1f, 1f),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = "VS",
                    color = TextPrimaryOnDark,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                BattleCharacterImage(
                    characterId = opponent.assetCharacterId.orEmpty(),
                    databaseCharacter = opponent.character,
                    animationType = if (opponentAttacking) DigimonAnimationType.ATTACK else DigimonAnimationType.IDLE,
                    modifier = Modifier
                        .weight(1f)
                        .size(150.dp),
                    contentScale = ContentScale.Fit
                )
            }

            if (result == null) {
                Text(
                    text = "Each attack includes an automatic counter-attack while both Digimon are standing.",
                    color = TextSecondaryOnDark,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (turnInProgress || result != null) return@Button
                        coroutineScope.launch {
                            turnInProgress = true
                            playerAttacking = true
                            audioPlayer.playOneShot(BattleAssetPaths.ATTACK_SOUND)
                            delay(420)

                            opponentDamage = rollDamage(player.attackPower)
                            showOpponentDamage = true
                            showOpponentHit = true
                            audioPlayer.playOneShot(BattleAssetPaths.DAMAGE_SOUND)
                            opponentHp = (opponentHp - opponentDamage).coerceAtLeast(0)
                            delay(620)
                            showOpponentHit = false
                            showOpponentDamage = false
                            playerAttacking = false

                            if (opponentHp == 0) {
                                result = true
                                turnInProgress = false
                                return@launch
                            }

                            opponentAttacking = true
                            audioPlayer.playOneShot(BattleAssetPaths.ATTACK_SOUND)
                            delay(420)
                            playerDamage = rollDamage(opponent.attackPower)
                            showPlayerDamage = true
                            showPlayerHit = true
                            audioPlayer.playOneShot(BattleAssetPaths.DAMAGE_SOUND)
                            playerHp = (playerHp - playerDamage).coerceAtLeast(0)
                            delay(620)
                            showPlayerHit = false
                            showPlayerDamage = false
                            opponentAttacking = false

                            if (playerHp == 0) result = false
                            turnInProgress = false
                        }
                    },
                    enabled = !turnInProgress,
                    modifier = Modifier
                        .fillMaxWidth(0.58f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VitalCyan,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.Gray
                    )
                ) {
                    Text(
                        text = if (turnInProgress) "Resolving..." else "Attack",
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
                    ),
                    border = BorderStroke(1.dp, if (result == true) VitalCyan else Color(0xFFFF6680)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (result == true) "VICTORY" else "DEFEAT",
                            color = if (result == true) VitalCyan else Color(0xFFFF6680),
                            fontWeight = FontWeight.Black,
                            fontSize = 26.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (result == true) {
                                "${player.displayName} defeated ${opponent.displayName}."
                            } else {
                                "${opponent.displayName} defeated ${player.displayName}."
                            },
                            color = TextPrimaryOnDark,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(onClick = onExit) { Text("Back to battles") }
                    }
                }
            }
        }

        AnimatedDamageNumber(
            damage = playerDamage,
            isVisible = showPlayerDamage,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        AnimatedDamageNumber(
            damage = opponentDamage,
            isVisible = showOpponentDamage,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        HitEffectOverlay(
            isVisible = showPlayerHit,
            modifier = Modifier.fillMaxSize(),
            isPlayerScreen = true,
            onAnimationComplete = { showPlayerHit = false }
        )
        HitEffectOverlay(
            isVisible = showOpponentHit,
            modifier = Modifier.fillMaxSize(),
            isPlayerScreen = false,
            onAnimationComplete = { showOpponentHit = false }
        )
    }
}

@Composable
private fun BattleHealthBar(
    name: String,
    current: Int,
    maximum: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = name,
            color = TextPrimaryOnDark,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1
        )
        LinearProgressIndicator(
            progress = { (current.toFloat() / maximum.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp),
            color = color,
            trackColor = Color.Black.copy(alpha = 0.55f)
        )
        Text(
            text = "$current / $maximum",
            color = TextSecondaryOnDark,
            fontSize = 11.sp
        )
    }
}
