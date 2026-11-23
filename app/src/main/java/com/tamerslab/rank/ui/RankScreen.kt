package com.tamerslab.rank.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.tamerslab.R
import com.tamerslab.components.InstrumentSpacing
import com.tamerslab.components.TopBanner
import com.tamerslab.components.VitalButton
import com.tamerslab.components.VitalButtonStyle
import com.tamerslab.components.VitalSectionBar
import com.tamerslab.components.cyberFrame
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.navigation.NavigationItems
import com.tamerslab.navigation.navigatePrimary
import com.tamerslab.rank.domain.RankActivityEntry
import com.tamerslab.rank.domain.RankCalculator
import com.tamerslab.rank.domain.RankConfig
import com.tamerslab.rank.domain.RankNotification
import com.tamerslab.rank.domain.TamerProfileSnapshot
import com.tamerslab.rank.domain.TamerRanks
import com.tamerslab.rank.domain.TxpSource
import com.tamerslab.ui.theme.SurfaceElevatedPurple
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextMutedOnDark
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Offline Tamer tab, rebuilt to read as one of the app's instrument pages (Storage/Items/Settings
 * vocabulary) while following the shipped VB Lab Tamer anatomy: large rank emblem with Tamer Level
 * and remaining EXP, record rows (bits/partners/Digidex/Win rate/trophies), recent
 * activity and the emblem gallery. Rank is a level milestone, so no invented tier names are shown.
 */
@Composable
fun RankScreen(navController: NavController) {
    val context = LocalContext.current
    val application = context.applicationContext as TamersLabApplication
    val container = application.container
    val repository = container.rankRepository
    val config = remember { RankConfig() }

    val profile by repository.profile.collectAsStateWithLifecycle(initialValue = emptyProfile())
    val activity by repository.activity.collectAsStateWithLifecycle(initialValue = emptyList())
    val today = remember { LocalDate.now().toString() }
    val todayCaps by remember(repository, today) { repository.dailyCaps(today) }
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    val notificationsEnabled by container.rankSettings.notificationsEnabled
        .collectAsStateWithLifecycle(initialValue = true)

    val bits by container.currencyRepository.currencyValue.collectAsStateWithLifecycle(initialValue = 0)
    val partners by remember { container.db.userCharacterDao().observeCharacterCount() }
        .collectAsStateWithLifecycle(initialValue = 0)
    val wins by remember { container.db.userCharacterDao().observeTotalWins() }
        .collectAsStateWithLifecycle(initialValue = 0)
    val losses by remember { container.db.userCharacterDao().observeTotalLosses() }
        .collectAsStateWithLifecycle(initialValue = 0)
    val trophies by remember { container.db.userCharacterDao().observeTotalTrophies() }
        .collectAsStateWithLifecycle(initialValue = 0)
    val dexDiscovered by remember { container.db.dexDao().observeDiscoveredCount() }
        .collectAsStateWithLifecycle(initialValue = 0)
    val dexTotal by remember { container.db.dexDao().observeCatalogCount() }
        .collectAsStateWithLifecycle(initialValue = 0)

    var notification by remember { mutableStateOf<RankNotification?>(null) }
    LaunchedEffect(repository, notificationsEnabled) {
        repository.notifications.collect { event ->
            if (notificationsEnabled) notification = event
        }
    }

    Scaffold(
        topBar = {
            TopBanner(
                text = stringResource(R.string.rank_tab_title),
                subtitle = stringResource(R.string.rank_subtitle),
                onBackClick = { navController.popBackStack() },
                onGearClick = { navController.navigatePrimary(NavigationItems.Settings) },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = InstrumentSpacing.Screen),
        ) {
            Spacer(Modifier.height(InstrumentSpacing.ControlGap))
            RankHero(profile = profile, config = config)
            Spacer(Modifier.height(InstrumentSpacing.SectionGap))

            VitalSectionBar(stringResource(R.string.rank_record_title))
            Spacer(Modifier.height(InstrumentSpacing.ControlGap))
            RecordRows(
                profile = profile,
                bits = bits,
                partners = partners,
                dexDiscovered = dexDiscovered,
                dexTotal = dexTotal,
                wins = wins,
                losses = losses,
                trophies = trophies,
            )
            Spacer(Modifier.height(InstrumentSpacing.SectionGap))

            VitalSectionBar(stringResource(R.string.rank_earn_title))
            Spacer(Modifier.height(InstrumentSpacing.ControlGap))
            EarnSection(config = config, usedToday = todayCaps)
            Spacer(Modifier.height(InstrumentSpacing.SectionGap))

            VitalSectionBar(stringResource(R.string.rank_activity_title))
            Spacer(Modifier.height(InstrumentSpacing.ControlGap))
            ActivityList(activity = activity)
            Spacer(Modifier.height(InstrumentSpacing.SectionGap))

            VitalSectionBar(stringResource(R.string.rank_gallery_title))
            Spacer(Modifier.height(InstrumentSpacing.ControlGap))
            GalleryGrid(currentRank = profile.rank, config = config)
            Spacer(Modifier.height(32.dp))
        }
    }

    notification?.let { event ->
        RankUpDialog(event = event, onDismiss = { notification = null })
    }
}

private fun emptyProfile(): TamerProfileSnapshot = TamerProfileSnapshot(
    totalTxp = 0L,
    level = 1,
    rank = 0,
    lastLevelUpAt = null,
    lastRankUpAt = null,
)

@Composable
private fun RankHero(profile: TamerProfileSnapshot, config: RankConfig) {
    val level = profile.level.coerceAtMost(config.levelCap)
    val remaining = RankCalculator.levelRemaining(profile.totalTxp, config)
    val levelProgress = RankCalculator.levelProgress(profile.totalTxp, config)
    val nextRankLevel = config.nextRankLevel(profile.rank)
    val stacked = LocalDensity.current.fontScale > 1.6f

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stack = stacked || maxWidth < 320.dp
        if (stack) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmblemBlock(rank = profile.rank, size = 112)
                Spacer(Modifier.height(InstrumentSpacing.Panel))
                LevelBlock(
                    level = level,
                    remaining = remaining,
                    levelProgress = levelProgress,
                    nextRankLevel = nextRankLevel,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EmblemBlock(rank = profile.rank, size = 104)
                Spacer(Modifier.width(InstrumentSpacing.Detail))
                LevelBlock(
                    level = level,
                    remaining = remaining,
                    levelProgress = levelProgress,
                    nextRankLevel = nextRankLevel,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun emblemInk(): Color =
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
        MaterialTheme.colorScheme.onBackground
    } else {
        Color.White
    }

@Composable
private fun EmblemBlock(rank: Int, size: Int = 104) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .background(SurfaceElevatedPurple)
                .cyberFrame(active = true),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(TamerRanks.emblem(rank)),
                contentDescription = stringResource(R.string.rank_emblem_desc),
                colorFilter = ColorFilter.tint(emblemInk()),
                modifier = Modifier.size((size * 0.68f).dp),
            )
        }
        Text(
            text = stringResource(R.string.rank_tamer_rank, rank),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryOnDark,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun LevelBlock(
    level: Int,
    remaining: Long,
    levelProgress: Float,
    nextRankLevel: Int?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.rank_level_label).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondaryOnDark,
            letterSpacing = 1.sp,
        )
        Text(
            text = level.toString(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = TextPrimaryOnDark,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.rank_until_next_level),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondaryOnDark,
            )
            Spacer(Modifier.width(InstrumentSpacing.ControlGap))
            Text(
                text = stringResource(R.string.rank_progress_remaining, remaining),
                style = MaterialTheme.typography.titleMedium,
                color = VitalCyan,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(6.dp))
        RankProgressBar(
            progress = levelProgress,
            contentDescription = stringResource(R.string.rank_progress_level_a11y),
        )
        Spacer(Modifier.height(InstrumentSpacing.ControlGap))
        Text(
            text = if (nextRankLevel != null) {
                stringResource(R.string.rank_next_rank, nextRankLevel)
            } else {
                stringResource(R.string.rank_max)
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryOnDark,
        )
    }
}

@Composable
private fun RecordRows(
    profile: TamerProfileSnapshot,
    bits: Int,
    partners: Int,
    dexDiscovered: Int,
    dexTotal: Int,
    wins: Int,
    losses: Int,
    trophies: Int,
) {
    Column(Modifier.fillMaxWidth()) {
        StatRow(Icons.Outlined.Paid, stringResource(R.string.rank_stat_bits), bits.toString())
        Hairline()
        StatRow(Icons.Outlined.Pets, stringResource(R.string.rank_stat_partners), partners.toString())
        Hairline()
        StatRow(Icons.Outlined.MenuBook, stringResource(R.string.rank_stat_digidex), "$dexDiscovered / $dexTotal")
        Hairline()
        WinRateRow(wins = wins, losses = losses)
        Hairline()
        StatRow(Icons.Outlined.EmojiEvents, stringResource(R.string.rank_stat_trophies), trophies.toString())
        Hairline()
        StatRow(Icons.Outlined.MilitaryTech, stringResource(R.string.rank_stat_txp), profile.totalTxp.toString())
    }
}

@Composable
private fun StatRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = InstrumentSpacing.ControlGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(SurfaceElevatedPurple)
                .cyberFrame(active = false),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = VitalCyan, modifier = Modifier.size(20.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimaryOnDark,
            modifier = Modifier
                .weight(1f)
                .padding(start = InstrumentSpacing.Detail),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = VitalCyan,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun WinRateRow(wins: Int, losses: Int) {
    val total = wins + losses
    val rate = if (total == 0) 0 else (wins * 100) / total
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = InstrumentSpacing.ControlGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(SurfaceElevatedPurple)
                .cyberFrame(active = false),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.MilitaryTech, contentDescription = null, tint = VitalCyan, modifier = Modifier.size(20.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = InstrumentSpacing.Detail),
        ) {
            Text(
                text = stringResource(R.string.rank_win_rate_label),
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimaryOnDark,
            )
            Text(
                text = stringResource(
                    R.string.rank_win_loss_line,
                    stringResource(R.string.rank_wins_label), wins,
                    stringResource(R.string.rank_losses_label), losses,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryOnDark,
            )
        }
        Text(
            text = "$rate%",
            style = MaterialTheme.typography.titleMedium,
            color = VitalCyan,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private data class EarnSourceUi(
    val source: TxpSource,
    val icon: ImageVector,
    val labelRes: Int,
)

private val earnSources = listOf(
    EarnSourceUi(TxpSource.NFC_READ, Icons.Outlined.Nfc, R.string.rank_earn_watch_read),
    EarnSourceUi(TxpSource.NFC_WRITE, Icons.Outlined.Upload, R.string.rank_earn_watch_write),
    EarnSourceUi(TxpSource.BATTLE_WIN, Icons.Outlined.Bolt, R.string.rank_earn_battle_win),
    EarnSourceUi(TxpSource.BATTLE_LOSS, Icons.Outlined.Shield, R.string.rank_earn_battle_loss),
    EarnSourceUi(TxpSource.QUEST, Icons.Outlined.TaskAlt, R.string.rank_earn_quest),
    EarnSourceUi(TxpSource.DEX_NEW, Icons.Outlined.MenuBook, R.string.rank_earn_dex),
    EarnSourceUi(TxpSource.ITEM_USE, Icons.Outlined.Inventory2, R.string.rank_earn_item),
)

@Composable
private fun EarnSection(config: RankConfig, usedToday: Map<TxpSource, Int>) {
    Column(Modifier.fillMaxWidth()) {
        earnSources.forEachIndexed { index, entry ->
            val cap = config.dailyCaps[entry.source] ?: entry.source.dailyCap
            val used = usedToday[entry.source] ?: 0
            val remaining = (cap - used).coerceAtLeast(0)
            EarnRow(
                icon = entry.icon,
                label = stringResource(entry.labelRes),
                amount = entry.source.defaultAmount,
                remaining = remaining,
                cap = cap,
            )
            if (index != earnSources.lastIndex) Hairline()
        }
    }
}

@Composable
private fun EarnRow(icon: ImageVector, label: String, amount: Int, remaining: Int, cap: Int) {
    val actions = if (amount > 0) remaining / amount else 0
    val used = (cap - remaining).coerceAtLeast(0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = VitalCyan, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(InstrumentSpacing.ControlGap))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimaryOnDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (remaining > 0) {
                stringResource(R.string.rank_earn_compact, amount, used, cap, actions)
            } else {
                stringResource(R.string.rank_earn_compact_capped, amount, used, cap)
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (remaining > 0) TextSecondaryOnDark else TextMutedOnDark,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ActivityList(activity: List<RankActivityEntry>) {
    Column(Modifier.fillMaxWidth()) {
        if (activity.isEmpty()) {
            Text(
                text = stringResource(R.string.rank_activity_empty),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryOnDark,
                modifier = Modifier.padding(vertical = InstrumentSpacing.ControlGap),
            )
            return@Column
        }
        val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM HH:mm") }
        activity.take(8).forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = sourceLabel(entry.source),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimaryOnDark,
                    )
                    Text(
                        text = dateFormatter.format(
                            Instant.ofEpochMilli(entry.occurredAt).atZone(ZoneId.systemDefault()),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryOnDark,
                    )
                }
                Text(
                    text = "+${entry.delta}",
                    style = MaterialTheme.typography.titleMedium,
                    color = VitalCyan,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Hairline()
        }
    }
}

@Composable
private fun GalleryGrid(currentRank: Int, config: RankConfig) {
    Column(Modifier.fillMaxWidth()) {
        for (row in 0 until TamerRanks.COUNT / 4) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(InstrumentSpacing.ControlGap),
            ) {
                for (column in 0 until 4) {
                    val rank = row * 4 + column
                    GalleryTile(
                        rank = rank,
                        currentRank = currentRank,
                        entryLevel = config.rankLevelThresholds[rank],
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(InstrumentSpacing.ControlGap))
        }
    }
}

@Composable
private fun GalleryTile(rank: Int, currentRank: Int, entryLevel: Int, modifier: Modifier = Modifier) {
    val unlocked = rank <= currentRank
    val label = if (unlocked) {
        stringResource(R.string.rank_tamer_rank, rank)
    } else {
        stringResource(R.string.rank_gallery_locked, entryLevel)
    }
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .background(SurfaceElevatedPurple)
            .cyberFrame(active = rank == currentRank)
            .semantics { contentDescription = label }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(TamerRanks.emblem(rank)),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (unlocked) emblemInk() else TextMutedOnDark),
            modifier = Modifier
                .size(38.dp)
                .alpha(if (unlocked) 1f else 0.35f),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (unlocked) TextPrimaryOnDark else TextMutedOnDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun sourceLabel(source: TxpSource): String = when (source) {
    TxpSource.NFC_READ, TxpSource.NFC_WRITE, TxpSource.NFC_TRANSFER, TxpSource.NFC_MISSION ->
        stringResource(R.string.rank_source_watch)
    TxpSource.BATTLE_WIN, TxpSource.BATTLE_LOSS -> stringResource(R.string.rank_source_battle)
    TxpSource.QUEST -> stringResource(R.string.rank_source_quest)
    TxpSource.DEX_NEW, TxpSource.DEX_EVOLUTION -> stringResource(R.string.rank_source_dex)
    TxpSource.ITEM_USE -> stringResource(R.string.rank_source_item)
    TxpSource.DAILY -> stringResource(R.string.rank_source_daily)
    TxpSource.WORLD, TxpSource.ADVENTURE -> stringResource(R.string.rank_source_world)
    TxpSource.STORAGE -> stringResource(R.string.rank_source_activity)
}

@Composable
private fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SurfaceStroke.copy(alpha = 0.6f)),
    )
}

@Composable
fun RankProgressBar(progress: Float, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val description = contentDescription
    val track = SurfaceStroke
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(track, ProgressBarShape)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(safeProgress, 0f..1f)
                if (description != null) this.contentDescription = description
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth(safeProgress)
                .height(8.dp)
                .background(VitalCyan, ProgressBarShape),
        )
    }
}

private val ProgressBarShape = CutCornerShape(2.dp)

@Composable
private fun RankUpDialog(event: RankNotification, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Image(
                painter = painterResource(TamerRanks.emblem(event.newRank)),
                contentDescription = stringResource(R.string.rank_emblem_desc),
                colorFilter = ColorFilter.tint(emblemInk()),
                modifier = Modifier.size(56.dp),
            )
        },
        title = {
            Text(
                text = stringResource(
                    if (event.rankUp) R.string.rank_up_title else R.string.rank_level_up_title,
                ),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.rank_up_body, event.newRank, event.newLevel),
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            VitalButton(onClick = onDismiss, style = VitalButtonStyle.PRIMARY) {
                Text(stringResource(R.string.rank_continue))
            }
        },
    )
}

/**
 * Minimal Home rank readout: a small emblem plus the Tamer level inside the existing HUD key
 * rhythm. It is not a card and carries no progress chrome.
 */
@Composable
fun RankHudReadout(
    level: Int,
    emblemRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.rank_hud_a11y, level)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(emblemRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(VitalCyan),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = level.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimaryOnDark,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
