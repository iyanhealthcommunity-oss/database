package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.PackageTier
import com.example.data.model.RequestStatus
import com.example.data.model.SessionStatus
import com.example.ui.theme.IhcAmber
import com.example.ui.theme.IhcAmberContainer
import com.example.ui.theme.IhcBasicBg
import com.example.ui.theme.IhcBasicSlate
import com.example.ui.theme.IhcDangerBg
import com.example.ui.theme.IhcDangerRed
import com.example.ui.theme.IhcEmerald
import com.example.ui.theme.IhcEmeraldContainer
import com.example.ui.theme.IhcOnEmeraldContainer
import com.example.ui.theme.IhcPremiumBg
import com.example.ui.theme.IhcPremiumGold
import com.example.ui.theme.IhcPureWhite
import com.example.ui.theme.IhcStandardBg
import com.example.ui.theme.IhcStandardBlue
import kotlinx.coroutines.delay

@Composable
fun IhcLogoImage(
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp
) {
    Image(
        painter = painterResource(id = R.drawable.ihc_logo),
        contentDescription = "Logo IHC Iyan Health Community",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(IhcPureWhite)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(cornerRadius))
    )
}

/**
 * Mandatory IHC pulsing logo loading indicator used across all loading, upload, sync, and backup states.
 */
@Composable
fun IhcPulsingLogoLoading(
    message: String = "Memuat data IHC…",
    modifier: Modifier = Modifier,
    logoSize: Dp = 84.dp
) {
    val transition = rememberInfiniteTransition(label = "ihc_logo_pulse")
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = modifier
            .padding(24.dp)
            .testTag("ihc_loading_indicator"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IhcLogoImage(
            size = logoSize,
            modifier = Modifier
                .scale(scale)
                .alpha(alpha),
            cornerRadius = 20.dp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun IhcSplashScreen(message: String = "Menyiapkan ruang kerja Coach Iyan…") {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IhcPureWhite),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IhcPulsingLogoLoading(message = message, logoSize = 112.dp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "IHC MANAGEMENT SYSTEM",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF111315)
            )
        }
    }
}

@Composable
fun IhcExitScreen(message: String = "Menutup sesi Coach Iyan…") {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IhcPureWhite)
            .testTag("exit_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IhcPulsingLogoLoading(message = message, logoSize = 104.dp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sampai jumpa lagi, Coach",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF111315)
            )
        }
    }
}

@Composable
fun IhcHeaderBar(
    title: String,
    subtitle: String? = null,
    trailingContent: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            IhcLogoImage(size = 42.dp, cornerRadius = 11.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        trailingContent()
    }
}

@Composable
fun IhcPackageBadge(tier: PackageTier, modifier: Modifier = Modifier) {
    val (bg, fg) = when (tier) {
        PackageTier.PREMIUM -> IhcPremiumBg to IhcPremiumGold
        PackageTier.STANDARD -> IhcStandardBg to IhcStandardBlue
        PackageTier.BASIC -> IhcBasicBg to IhcBasicSlate
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = tier.label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun IhcSessionStatusBadge(status: SessionStatus, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status) {
        SessionStatus.TERJADWAL -> IhcStandardBg to IhcStandardBlue
        SessionStatus.HADIR -> IhcEmeraldContainer to IhcOnEmeraldContainer
        SessionStatus.TIDAK_HADIR -> IhcDangerBg to IhcDangerRed
        SessionStatus.DIBATALKAN -> IhcBasicBg to IhcBasicSlate
    }
    Surface(modifier = modifier, color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun IhcRequestStatusBadge(status: RequestStatus, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status) {
        RequestStatus.MENUNGGU -> IhcAmberContainer to IhcAmber
        RequestStatus.DISETUJUI -> IhcEmeraldContainer to IhcOnEmeraldContainer
        RequestStatus.DIBALAS -> IhcStandardBg to IhcStandardBlue
        RequestStatus.DITOLAK -> IhcDangerBg to IhcDangerRed
    }
    Surface(modifier = modifier, color = bg, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun IhcEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun IhcMetricChart(
    title: String,
    unit: String,
    values: List<Double>,
    lineColor: Color = IhcEmerald,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (values.isNotEmpty()) {
                    val latest = values.last()
                    val delta = if (values.size >= 2) latest - values.first() else 0.0
                    val deltaText = if (values.size >= 2) {
                        val sign = if (delta > 0) "+" else ""
                        " ($sign${String.format("%.1f", delta)} $unit)"
                    } else ""
                    Text(
                        text = "${String.format("%.1f", latest)} $unit$deltaText",
                        style = MaterialTheme.typography.labelMedium,
                        color = lineColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (values.size < 2) {
                Text(
                    text = if (values.isEmpty()) "Belum ada data." else "Tambah minimal 2 data untuk melihat grafik tren.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                ) {
                    val minVal = values.minOrNull() ?: 0.0
                    val maxVal = values.maxOrNull() ?: 1.0
                    val range = (maxVal - minVal).takeIf { it > 0.01 } ?: 1.0
                    val padX = 14f
                    val padY = 14f
                    val usableW = (size.width - padX * 2).coerceAtLeast(1f)
                    val usableH = (size.height - padY * 2).coerceAtLeast(1f)

                    val points = values.mapIndexed { idx, v ->
                        val x = padX + (idx.toFloat() / (values.size - 1).coerceAtLeast(1)) * usableW
                        val normY = ((v - minVal) / range).toFloat()
                        val y = padY + (1f - normY) * usableH
                        Offset(x, y)
                    }

                    val path = Path().apply {
                        points.forEachIndexed { idx, p ->
                            if (idx == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 5f, cap = StrokeCap.Round)
                    )
                    points.forEach { pt ->
                        drawCircle(color = IhcPureWhite, radius = 7f, center = pt)
                        drawCircle(color = lineColor, radius = 5f, center = pt)
                    }
                }
            }
        }
    }
}

@Composable
fun IhcRestTimerCard(
    defaultSeconds: Int = 60,
    modifier: Modifier = Modifier
) {
    var targetSeconds by remember(defaultSeconds) { mutableIntStateOf(defaultSeconds.coerceIn(10, 600)) }
    var remainingSeconds by remember(defaultSeconds) { mutableIntStateOf(defaultSeconds.coerceIn(10, 600)) }
    var isRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning, remainingSeconds) {
        if (isRunning && remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds -= 1
            if (remainingSeconds == 0) {
                isRunning = false
            }
        }
    }

    val mins = remainingSeconds / 60
    val secs = remainingSeconds % 60
    val formatted = String.format("%02d:%02d", mins, secs)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Timer,
                    contentDescription = "Timer Istirahat",
                    tint = if (remainingSeconds == 0) IhcEmerald else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (remainingSeconds == 0) "Waktu Istirahat Selesai!" else "Timer Istirahat",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (remainingSeconds == 0) IhcEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = {
                        targetSeconds = (targetSeconds + 15).coerceAtMost(300)
                        remainingSeconds = (remainingSeconds + 15).coerceAtMost(300)
                    },
                    modifier = Modifier.testTag("timer_plus_15")
                ) {
                    Text("+15d")
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        if (remainingSeconds == 0) remainingSeconds = targetSeconds
                        isRunning = !isRunning
                    },
                    modifier = Modifier.testTag("timer_play_pause")
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isRunning) "Jeda Timer" else "Mulai Timer"
                    )
                }
                IconButton(
                    onClick = {
                        isRunning = false
                        remainingSeconds = targetSeconds
                    },
                    modifier = Modifier.testTag("timer_reset")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Reset Timer"
                    )
                }
            }
        }
    }
}
