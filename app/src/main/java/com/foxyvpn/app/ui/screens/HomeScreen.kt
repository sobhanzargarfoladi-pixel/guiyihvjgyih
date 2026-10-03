package com.foxyvpn.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foxyvpn.app.FoxyVpnApp
import com.foxyvpn.app.data.GUARDIAN_ENDPOINT_DEFAULT
import com.foxyvpn.app.data.GuardianClient
import com.foxyvpn.app.data.formatBytes
import com.foxyvpn.app.data.model.ConnectionState
import com.foxyvpn.app.ui.components.FoxyGradient
import com.foxyvpn.app.ui.components.FoxyWordmark
import com.foxyvpn.app.ui.components.foxyBackground
import com.foxyvpn.app.ui.theme.FoxSeed
import com.foxyvpn.app.ui.theme.LocalFoxyStatusColors
import com.foxyvpn.app.ui.theme.ThemeController
import com.foxyvpn.app.ui.theme.ThemeMode
import com.foxyvpn.app.vpn.FoxyVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private sealed interface QuotaUi {
    data object Loading : QuotaUi
    data object Error : QuotaUi
    data object Unlimited : QuotaUi
    data class Data(val remaining: Long?, val max: Long?) : QuotaUi
}

private suspend fun loadQuota(app: FoxyVpnApp): QuotaUi = withContext(Dispatchers.IO) {
    runCatching<QuotaUi> {
        val token = app.authRepository.currentAccessToken() ?: return@runCatching QuotaUi.Error
        val e = GuardianClient().fetchUserInfo(GUARDIAN_ENDPOINT_DEFAULT, token)
        if (!e.limitedBandwidth) QuotaUi.Unlimited else QuotaUi.Data(e.quotaRemaining, e.maxBytes)
    }.getOrElse { QuotaUi.Error }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    app: FoxyVpnApp,
    themeController: ThemeController,
    onRequestConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by FoxyVpnService.state.collectAsState()
    val lastError by FoxyVpnService.lastError.collectAsState()
    val selectedProxy by app.proxyStateStore.selectedProxyFlow.collectAsState()

    var quota by remember { mutableStateOf<QuotaUi>(QuotaUi.Loading) }
    LaunchedEffect(state) {
        quota = loadQuota(app)
        while (state == ConnectionState.CONNECTED) {
            delay(60_000)
            quota = loadQuota(app)
        }
    }

    val systemInDarkTheme = isSystemInDarkTheme()
    val haptics = LocalHapticFeedback.current

    Box(Modifier.fillMaxSize().foxyBackground()) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { FoxyWordmark() },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    actions = {
                        val mode = themeController.mode
                        val showingDark = themeController.resolveDark(systemInDarkTheme)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .combinedClickable(
                                    role = Role.Button,
                                    onClick = { themeController.toggle(systemInDarkTheme) },
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        themeController.set(ThemeMode.SYSTEM)
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = when (mode) {
                                    ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                                    ThemeMode.LIGHT -> Icons.Filled.LightMode
                                    ThemeMode.DARK -> Icons.Filled.DarkMode
                                },
                                contentDescription = when (mode) {
                                    ThemeMode.SYSTEM ->
                                        "Theme: follow system. Tap to switch to ${if (showingDark) "light" else "dark"} mode"
                                    ThemeMode.LIGHT -> "Theme: light. Tap for dark mode, long press to follow the system"
                                    ThemeMode.DARK -> "Theme: dark. Tap for light mode, long press to follow the system"
                                },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                QuotaCard(quota)
                Spacer(Modifier.height(12.dp))
                PowerButton(state) {
                    if (state == ConnectionState.DISCONNECTED) onRequestConnect() else onDisconnect()
                }

                Text(
                    text = when (state) {
                        ConnectionState.CONNECTED -> "Connected"
                        ConnectionState.CONNECTING -> "Connecting\u2026"
                        ConnectionState.DISCONNECTED -> "Not connected"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (state) {
                        ConnectionState.CONNECTED -> "Your traffic is going through Foxy Pro"
                        ConnectionState.CONNECTING -> "Tap to cancel"
                        ConnectionState.DISCONNECTED -> "Tap the button to connect"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val error = lastError
                if (error != null && state != ConnectionState.CONNECTING) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.weight(1f))
                LocationCard(
                    name = selectedProxy?.let { it.countryName.ifBlank { it.countryCode } } ?: "Recommended",
                    onClick = onOpenServers,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun QuotaCard(quota: QuotaUi) {
    val cs = MaterialTheme.colorScheme
    val fraction = (quota as? QuotaUi.Data)?.let { d ->
        if (d.remaining != null && d.max != null && d.max > 0) (d.remaining.toFloat() / d.max).coerceIn(0f, 1f) else null
    }
    val animated by animateFloatAsState(fraction ?: 0f, label = "quota-fraction")
    val low = fraction != null && fraction < 0.1f

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "DATA REMAINING",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = when (quota) {
                        QuotaUi.Loading -> "\u2026"
                        QuotaUi.Error -> "\u2014"
                        QuotaUi.Unlimited -> "Unlimited"
                        is QuotaUi.Data -> when {
                            quota.remaining != null -> formatBytes(quota.remaining)
                            quota.max != null -> "Up to " + formatBytes(quota.max)
                            else -> "Limited"
                        }
                    },
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = if (low) cs.error else cs.onSurface,
                )
                if (quota is QuotaUi.Data && quota.remaining != null && quota.max != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "of " + formatBytes(quota.max),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            if (fraction != null) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(cs.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animated)
                            .clip(CircleShape)
                            .background(if (low) Brush.linearGradient(listOf(cs.error, cs.error)) else FoxyGradient),
                    )
                }
            }
        }
    }
}

@Composable
private fun PowerButton(state: ConnectionState, onClick: () -> Unit) {
    val sc = LocalFoxyStatusColors.current
    val accent by animateColorAsState(
        targetValue = when (state) {
            ConnectionState.CONNECTED -> sc.connected
            ConnectionState.CONNECTING -> sc.connecting
            ConnectionState.DISCONNECTED -> FoxSeed
        },
        label = "power-accent",
    )
    val active = state != ConnectionState.DISCONNECTED
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (state == ConnectionState.CONNECTING) 1100 else 2600, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "pulse-ring",
    )

    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = 80.dp.toPx()
            val outer = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(accent.copy(alpha = 0.12f), radius = base * 1.22f, center = c)
            if (active) {
                for (i in 0..1) {
                    val t = (ring + i * 0.5f) % 1f
                    drawCircle(
                        accent.copy(alpha = (1f - t) * 0.4f),
                        radius = base + (outer - base) * t,
                        center = c,
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(160.dp)
                .shadow(
                    elevation = if (active) 24.dp else 10.dp,
                    shape = CircleShape,
                    ambientColor = accent,
                    spotColor = accent,
                )
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(lerp(accent, Color.White, 0.25f), accent, lerp(accent, Color.Black, 0.25f)),
                    ),
                )
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = if (state == ConnectionState.DISCONNECTED) "Connect" else "Disconnect",
                tint = Color.White,
                modifier = Modifier.size(72.dp),
            )
        }
    }
}

@Composable
private fun LocationCard(name: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(cs.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Public, contentDescription = null, tint = cs.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Location", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = cs.onSurfaceVariant)
        }
    }
}
