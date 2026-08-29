package com.remotwrt.bot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.remotwrt.bot.data.CommandInfo
import com.remotwrt.bot.data.DeviceInfo
import com.remotwrt.bot.data.LuciActionException
import com.remotwrt.bot.data.LuciAuthException
import com.remotwrt.bot.data.LuciClient
import com.remotwrt.bot.data.Prefs
import com.remotwrt.bot.data.RemotbotStatus
import com.remotwrt.bot.ui.theme.RemotWRTBotTheme
import com.remotwrt.bot.work.MonitorScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val prefs = Prefs(this)

        setContent {
            RemotWRTBotTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var screen by remember {
                        mutableStateOf(if (prefs.isConfigured) Screen.MAIN else Screen.SETUP)
                    }
                    when (screen) {
                        Screen.SETUP -> SetupScreen(
                            prefs = prefs,
                            onLoggedIn = {
                                MonitorScheduler.start(this@MainActivity)
                                screen = Screen.MAIN
                            }
                        )
                        Screen.MAIN -> MainScaffold(
                            prefs = prefs,
                            onLogout = {
                                MonitorScheduler.stop(this@MainActivity)
                                prefs.clear()
                                screen = Screen.SETUP
                            }
                        )
                    }
                }
            }
        }
    }
}

private enum class Screen { SETUP, MAIN }

private enum class BottomTab { HOME, DEVICES, MEMORY, ADGUARD, LOGS, SETTINGS }

@Composable
private fun MainScaffold(prefs: Prefs, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(BottomTab.HOME) }

    Scaffold(
        topBar = { AppHeader(tab, prefs) },
        bottomBar = { AppBottomNav(current = tab, onSelect = { tab = it }) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                BottomTab.HOME -> HomeTabContent(prefs)
                BottomTab.DEVICES -> DevicesTabContent(prefs)
                BottomTab.MEMORY -> MemoryTabContent(prefs)
                BottomTab.ADGUARD -> AdguardTabContent(prefs)
                BottomTab.LOGS -> LogsTabContent(prefs)
                BottomTab.SETTINGS -> SettingsTabContent(prefs, onLogout = onLogout)
            }
        }
    }
}

@Composable
private fun AppHeader(tab: BottomTab, prefs: Prefs) {
    val (title, subtitleFallback) = when (tab) {
        BottomTab.HOME -> "RemotWRT Bot" to "Aman"
        BottomTab.DEVICES -> "Perangkat Jaringan" to "Kelola Device"
        BottomTab.MEMORY -> "Memori" to "Informasi Disk"
        BottomTab.ADGUARD -> "AdGuard Home" to "Filtering DNS"
        BottomTab.LOGS -> "Log Sistem" to "logread router"
        BottomTab.SETTINGS -> "Preferensi" to "Konfigurasi Aplikasi"
    }
    val domain = remember(prefs.baseUrl) {
        prefs.baseUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
    }
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Cloud,
                        contentDescription = null,
                        tint = com.remotwrt.bot.ui.theme.RemotIndigoLight,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    PulseDot(color = com.remotwrt.bot.ui.theme.RemotGreen, size = 6.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (tab == BottomTab.HOME && domain.isNotBlank()) "$domain • $subtitleFallback" else subtitleFallback,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun AppBottomNav(current: BottomTab, onSelect: (BottomTab) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = current == BottomTab.HOME,
            onClick = { onSelect(BottomTab.HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = current == BottomTab.DEVICES,
            onClick = { onSelect(BottomTab.DEVICES) },
            icon = { Icon(Icons.Filled.Devices, contentDescription = null) },
            label = { Text("Devices") }
        )
        NavigationBarItem(
            selected = current == BottomTab.MEMORY,
            onClick = { onSelect(BottomTab.MEMORY) },
            icon = { Icon(Icons.Filled.Storage, contentDescription = null) },
            label = { Text("Memori") }
        )
        NavigationBarItem(
            selected = current == BottomTab.ADGUARD,
            onClick = { onSelect(BottomTab.ADGUARD) },
            icon = { Icon(Icons.Filled.Shield, contentDescription = null) },
            label = { Text("AdGuard") }
        )
        NavigationBarItem(
            selected = current == BottomTab.LOGS,
            onClick = { onSelect(BottomTab.LOGS) },
            icon = { Icon(Icons.Filled.Description, contentDescription = null) },
            label = { Text("Logs") }
        )
        NavigationBarItem(
            selected = current == BottomTab.SETTINGS,
            onClick = { onSelect(BottomTab.SETTINGS) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text("Settings") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(prefs: Prefs, onLoggedIn: () -> Unit) {
    var baseUrl by remember { mutableStateOf(prefs.baseUrl.ifBlank { "https://" }) }
    var username by remember { mutableStateOf(prefs.username) }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text("RemotWRT Bot", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Hubungkan ke domain tunnel Cloudflare Anda (mis. https://router.my.id)",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("URL Server (domain .my.id)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username LuCI") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password LuCI") },
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                error = null
                loading = true
                prefs.baseUrl = baseUrl
                prefs.username = username
                prefs.password = password
            },
            enabled = !loading && baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Menghubungkan..." else "Simpan & Login")
        }

        if (loading) {
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    try {
                        LuciClient(prefs).login()
                        withContext(Dispatchers.Main) { onLoggedIn() }
                    } catch (e: LuciAuthException) {
                        withContext(Dispatchers.Main) {
                            error = e.message
                            loading = false
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            error = "Gagal terhubung: ${e.message}"
                            loading = false
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun HomeTabContent(prefs: Prefs) {
    var status by remember { mutableStateOf<RemotbotStatus?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }

    // Auto-refresh every 10s while Home is on screen.
    LaunchedEffect(refreshTick) {
        while (true) {
            withContext(Dispatchers.IO) {
                try {
                    val result = LuciClient(prefs).fetchStatus()
                    withContext(Dispatchers.Main) {
                        status = result
                        error = null
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { error = "Gagal memuat status: ${e.message}" }
                }
            }
            delay(10_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
        }

        val s = status
        if (s == null) {
            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            StaggeredEntry(0) { TrafficHeroCard(s) }
            Spacer(Modifier.height(12.dp))
            StaggeredEntry(1) { QuickStatsGrid(s) }
            Spacer(Modifier.height(16.dp))
            StaggeredEntry(2) { StatusCard(s) }
            Spacer(Modifier.height(12.dp))
            StaggeredEntry(3) { ServicesCard(s) }
            Spacer(Modifier.height(12.dp))
            StaggeredEntry(4) { CommandsCard(prefs) }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var idx = 0
    while (value >= 1024 && idx < units.size - 1) {
        value /= 1024
        idx++
    }
    return String.format(java.util.Locale.getDefault(), "%.1f %s", value, units[idx])
}

private fun formatRate(bytesPerSec: Double): String {
    val bitsPerSec = bytesPerSec * 8
    return when {
        bitsPerSec >= 1_000_000 -> String.format(java.util.Locale.getDefault(), "%.1f Mbps", bitsPerSec / 1_000_000)
        bitsPerSec >= 1_000 -> String.format(java.util.Locale.getDefault(), "%.1f Kbps", bitsPerSec / 1_000)
        else -> String.format(java.util.Locale.getDefault(), "%.0f bps", bitsPerSec)
    }
}

@Composable
private fun TrafficHeroCard(s: RemotbotStatus) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        com.remotwrt.bot.ui.theme.RemotIndigoDark,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .border(1.dp, com.remotwrt.bot.ui.theme.RemotIndigo.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column {
            Text(
                "Real-time Traffic",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.ArrowDownward, contentDescription = null,
                            tint = com.remotwrt.bot.ui.theme.RemotGreen, modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Download", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(formatRate(s.netRxRateBps), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${formatBytes(s.netRxBytes)} total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(48.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                )
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Upload", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.ArrowUpward, contentDescription = null,
                            tint = com.remotwrt.bot.ui.theme.RemotIndigoLight, modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(formatRate(s.netTxRateBps), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${formatBytes(s.netTxBytes)} total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- WAN status + public IP, merged in from the former Network/MyIp cards ----
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            )
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    if (s.wanUp) "WAN Terhubung" else "WAN Terputus",
                    if (s.wanUp) com.remotwrt.bot.ui.theme.RemotGreen else com.remotwrt.bot.ui.theme.RemotRed,
                    pulsing = s.wanUp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.myIp,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    IconButton(
                        onClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(s.myIp))
                            android.widget.Toast.makeText(context, "IP disalin", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Salin IP", modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            val location = listOf(s.myIpCity, s.myIpRegion).filter { it.isNotBlank() && it != "-" }.joinToString(", ")
            Text(
                listOf(s.myIpIsp, location).filter { it.isNotBlank() && it != "-" }.joinToString(" • "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickStatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    badge: String?,
    progress: Float?,
    progressColor: Color,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                badge?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            progress?.let {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = it.coerceIn(0f, 1f),
                    color = progressColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

@Composable
private fun QuickStatsGrid(s: RemotbotStatus) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickStatCard(
                icon = Icons.Filled.Memory,
                label = "CPU Load",
                value = s.load1,
                badge = null,
                progress = null,
                progressColor = Color.Unspecified,
                modifier = Modifier.weight(1f)
            )
            val ramOver = s.memPct > s.ramLimit
            QuickStatCard(
                icon = Icons.Filled.Storage,
                label = "RAM Usage",
                value = "${s.memPct}%",
                badge = "${(s.memTotalMb - s.memUsedMb).coerceAtLeast(0)}MB Free",
                progress = s.memPct / 100f,
                progressColor = if (ramOver) com.remotwrt.bot.ui.theme.RemotRed else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val cpuOver = s.cpuTemp != null && s.cpuTemp > s.cpuTempLimit
            QuickStatCard(
                icon = Icons.Filled.Thermostat,
                label = "Temperature",
                value = if (s.cpuTemp != null) "${s.cpuTemp}\u00B0C" else "N/A",
                badge = null,
                progress = if (s.cpuTemp != null && s.cpuTempLimit > 0) (s.cpuTemp / s.cpuTempLimit).toFloat() else null,
                progressColor = if (cpuOver) com.remotwrt.bot.ui.theme.RemotRed else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            QuickStatCard(
                icon = Icons.Filled.Schedule,
                label = "Uptime",
                value = formatUptime(s.uptimeSec),
                badge = null,
                progress = null,
                progressColor = Color.Unspecified,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StaggeredEntry(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 50L)
        visible = true
    }
    val animatedAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300),
        label = "entry_alpha"
    )
    val animatedOffset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (visible) 0.dp else 14.dp,
        animationSpec = tween(300),
        label = "entry_offset"
    )
    Box(
        modifier = Modifier
            .alpha(animatedAlpha)
            .offset(y = animatedOffset)
    ) {
        content()
    }
}

@Composable
private fun InfoCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(250),
        label = "chevron_rotation"
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(16.dp)
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Sembunyikan" else "Tampilkan",
                    modifier = Modifier.rotate(chevronRotation)
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
private fun PulseDot(color: Color, size: androidx.compose.ui.unit.Dp = 8.dp) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.5f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .alpha(alpha)
            .background(color, shape = androidx.compose.foundation.shape.CircleShape)
    )
}

@Composable
private fun StatusPill(text: String, color: Color, pulsing: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (pulsing) {
            PulseDot(color = color)
            Spacer(Modifier.width(6.dp))
        }
        Surface(
            color = color.copy(alpha = 0.15f),
            contentColor = color,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun StatRowPill(label: String, text: String, color: Color, pulsing: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        StatusPill(text, color, pulsing)
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

@Composable
private fun StatusCard(s: RemotbotStatus) {
    InfoCard("Status Bot", Icons.Filled.SmartToy) {
        StatRowPill(
            "Berjalan",
            if (s.running) "Berjalan" else "Berhenti",
            if (s.running) com.remotwrt.bot.ui.theme.RemotGreen else com.remotwrt.bot.ui.theme.RemotRed,
            pulsing = s.running
        )
        StatRow("Diaktifkan", if (s.enabled) "Ya" else "Tidak")
        StatRow("Token", if (s.tokenSet) s.tokenPreview else "Belum diatur")
        StatRow("User Diizinkan", "${s.allowedCount}")
        StatRow("Versi Paket", s.pkgVersion)
    }
}

@Composable
private fun AppVersionCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var checking by remember { mutableStateOf(true) }
    var updateInfo by remember { mutableStateOf<com.remotwrt.bot.update.UpdateInfo?>(null) }
    var checkError by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var needsPermission by remember { mutableStateOf(false) }
    var checkTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(checkTrigger) {
        checking = true
        checkError = null
        withContext(Dispatchers.IO) {
            try {
                val info = com.remotwrt.bot.update.GithubUpdater().fetchLatestRelease()
                withContext(Dispatchers.Main) {
                    updateInfo = if (info != null && info.versionCode > com.remotwrt.bot.BuildConfig.VERSION_CODE) info else null
                    checking = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    checkError = "Gagal cek update: ${e.message}"
                    checking = false
                }
            }
        }
    }

    InfoCard("Tentang Aplikasi", Icons.Filled.Info) {
        StatRow("Versi", "${com.remotwrt.bot.BuildConfig.VERSION_NAME} (build ${com.remotwrt.bot.BuildConfig.VERSION_CODE})")
        Spacer(Modifier.height(8.dp))

        when {
            checking -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Memeriksa update...", style = MaterialTheme.typography.bodySmall)
                }
            }
            checkError != null -> {
                Text(checkError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { checkTrigger++ }, modifier = Modifier.fillMaxWidth()) { Text("Coba Lagi") }
            }
            updateInfo == null -> {
                Text(
                    "Sudah versi terbaru.",
                    style = MaterialTheme.typography.bodySmall,
                    color = com.remotwrt.bot.ui.theme.RemotGreen
                )
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { checkTrigger++ }, modifier = Modifier.fillMaxWidth()) { Text("Cek Update") }
            }
            else -> {
                val info = updateInfo!!
                val prefs = remember { com.remotwrt.bot.data.Prefs(context) }
                val alreadyDownloadedFile = remember(info.versionCode) {
                    if (prefs.downloadedUpdateVersionCode == info.versionCode) {
                        val f = java.io.File(prefs.downloadedUpdateFilePath)
                        if (f.exists()) f else null
                    } else null
                }
                Text(
                    "Update tersedia: ${info.versionTag}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = com.remotwrt.bot.ui.theme.RemotAmber
                )
                if (alreadyDownloadedFile != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Sudah diunduh otomatis di background, tinggal dipasang.",
                        style = MaterialTheme.typography.bodySmall,
                        color = com.remotwrt.bot.ui.theme.RemotGreen
                    )
                }
                if (info.releaseNotes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        info.releaseNotes.take(300),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))

                downloadError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                }

                if (downloading) {
                    if (progress in 0f..1f) {
                        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    Button(
                        onClick = {
                            if (!com.remotwrt.bot.update.UpdateInstaller.canInstallPackages(context)) {
                                needsPermission = true
                                com.remotwrt.bot.update.UpdateInstaller.requestInstallPermission(context)
                                return@Button
                            }
                            if (alreadyDownloadedFile != null) {
                                com.remotwrt.bot.update.UpdateInstaller.installApk(context, alreadyDownloadedFile)
                                return@Button
                            }
                            downloadError = null
                            downloading = true
                            scope.launch {
                                try {
                                    val dir = java.io.File(context.cacheDir, "apk_updates").apply { mkdirs() }
                                    val dest = java.io.File(dir, "update-${info.versionTag}.apk")
                                    withContext(Dispatchers.IO) {
                                        com.remotwrt.bot.update.GithubUpdater().downloadApk(info.assetApiUrl, dest) { p ->
                                            progress = p
                                        }
                                    }
                                    com.remotwrt.bot.update.UpdateInstaller.installApk(context, dest)
                                } catch (e: Exception) {
                                    downloadError = "Gagal update: ${e.message}"
                                } finally {
                                    downloading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when {
                                needsPermission -> "Coba Lagi Setelah Izin Diberikan"
                                alreadyDownloadedFile != null -> "Install Sekarang"
                                else -> "Update Sekarang"
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatUptime(seconds: Long): String {
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    return "${days}h ${hours}j ${minutes}m"
}

@Composable
private fun ServiceRow(label: String, enabled: Boolean, running: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        val (text, color) = when {
            running -> "Berjalan" to com.remotwrt.bot.ui.theme.RemotGreen
            enabled -> "Aktif tapi berhenti" to com.remotwrt.bot.ui.theme.RemotAmber
            else -> "Nonaktif" to MaterialTheme.colorScheme.onSurfaceVariant
        }
        StatusPill(text, color, pulsing = running)
    }
}

@Composable
private fun ServicesCard(s: RemotbotStatus) {
    InfoCard("Layanan", Icons.Filled.Dns) {
        ServiceRow("OpenClash", s.openclashEnabled, s.openclashRunning)
        ServiceRow("Cloudflared", s.cloudflaredEnabled, s.cloudflaredRunning)
    }
}

private data class CategoryMeta(val key: String, val icon: String, val color: Color)

// Same colors as vpn.html's --social/--game/--shopping/--streaming CSS vars,
// so the badges look consistent with the web dashboard.
private val CATEGORY_META = listOf(
    CategoryMeta("social", "💬", Color(0xFF818CF8)),
    CategoryMeta("game", "🎮", Color(0xFF4ADE80)),
    CategoryMeta("shopping", "🛒", Color(0xFFC084FC)),
    CategoryMeta("streaming", "📺", Color(0xFFFB923C))
)

@Composable
private fun CommandsCard(prefs: Prefs) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var commands by remember { mutableStateOf<List<CommandInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var runningId by remember { mutableStateOf<String?>(null) }
    var confirmCommand by remember { mutableStateOf<CommandInfo?>(null) }
    var resultDialog by remember { mutableStateOf<Pair<String, String>?>(null) } // label to output/error

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val result = LuciClient(prefs).fetchCommands()
                withContext(Dispatchers.Main) { commands = result }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { error = "Gagal memuat command: ${e.message}" }
            }
        }
    }

    fun executeCommand(cmd: CommandInfo) {
        runningId = cmd.id
        scope.launch {
            val output = withContext(Dispatchers.IO) {
                try {
                    LuciClient(prefs).runCommand(cmd.id)
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
            }
            resultDialog = cmd.label to output
            runningId = null
        }
    }

    InfoCard("Perintah Cepat", Icons.Filled.Bolt) {
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        val list = commands
        if (list == null && error == null) {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        } else if (list != null && list.isEmpty()) {
            Text(
                "Belum ada command. Tambahkan lewat UCI di router:\nconfig command 'nama'\n  option label 'Label'\n  option cmd 'perintah shell'",
                style = MaterialTheme.typography.bodySmall
            )
        } else if (list != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { cmd ->
                    OutlinedButton(
                        onClick = { confirmCommand = cmd },
                        enabled = runningId == null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (runningId == cmd.id) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(cmd.label)
                    }
                }
            }
        }
    }

    confirmCommand?.let { cmd ->
        AlertDialog(
            onDismissRequest = { confirmCommand = null },
            title = { Text("Jalankan Command?") },
            text = { Text("Jalankan \"${cmd.label}\" di router sekarang?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCommand = null
                    executeCommand(cmd)
                }) { Text("Jalankan") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCommand = null }) { Text("Batal") }
            }
        )
    }

    resultDialog?.let { (label, output) ->
        AlertDialog(
            onDismissRequest = { resultDialog = null },
            title = { Text(label) },
            text = {
                Text(
                    output.ifBlank { "(tidak ada output)" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { resultDialog = null }) { Text("Tutup") }
            }
        )
    }
}

// ==================== Devices management screen ====================

private enum class DeviceTab(val label: String) {
    ONLINE("Online"),
    WHITELIST("Whitelist"),
    PENDING("Pending"),
    BLOCKED("Blocked")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesTabContent(prefs: Prefs) {
    var devices by remember { mutableStateOf<List<DeviceInfo>?>(null) }
    var namedDevices by remember { mutableStateOf<List<com.remotwrt.bot.data.NamedDeviceInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(DeviceTab.ONLINE) }
    // MAC currently mid-action, so we can show a small spinner on just that row.
    var busyMac by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    suspend fun reload() {
        withContext(Dispatchers.IO) {
            try {
                val result = LuciClient(prefs).fetchDevices()
                // Best-effort: the named-device registry (custom names/icons +
                // Clash connection categories) is only really useful for the
                // Online tab, but it's cheap enough to refresh every cycle here
                // too rather than adding a second polling loop.
                val namedResult = try {
                    LuciClient(prefs).fetchNamedDevices()
                } catch (_: Exception) {
                    null
                }
                withContext(Dispatchers.Main) {
                    devices = result
                    if (namedResult != null) namedDevices = namedResult
                    error = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { error = "Gagal memuat device: ${e.message}" }
            }
        }
    }

    val namedByIp = remember(namedDevices) {
        namedDevices?.associateBy { it.ip } ?: emptyMap()
    }

    // Auto-refresh every 10s while this screen is open, same cadence as the dashboard.
    LaunchedEffect(refreshTick) {
        while (true) {
            reload()
            delay(10_000)
        }
    }

    fun performAction(mac: String, action: String) {
        busyMac = mac
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    LuciClient(prefs).setDeviceCategory(mac, action)
                    withContext(Dispatchers.Main) { error = null }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { error = "Aksi gagal: ${e.message}" }
                }
            }
            reload()
            busyMac = null
        }
    }

    val filtered = remember(devices, tab) {
        val list = devices ?: return@remember null
        when (tab) {
            DeviceTab.ONLINE -> list.filter { it.online }
            DeviceTab.WHITELIST -> list.filter { it.category == "whitelist" }
            DeviceTab.PENDING -> list.filter { it.category == "pending" }
            DeviceTab.BLOCKED -> list.filter { it.category == "blocked" }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Kelola Device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { refreshTick++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }
            TabRow(selectedTabIndex = tab.ordinal) {
                DeviceTab.entries.forEach { t ->
                    Tab(
                        selected = tab == t,
                        onClick = { tab = t },
                        text = { Text(t.label) }
                    )
                }
            }

            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }

                if (devices == null) {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (filtered.isNullOrEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                        Text(emptyMessageFor(tab), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered, key = { it.mac }) { device ->
                            DeviceRow(
                                device = device,
                                busy = busyMac == device.mac,
                                actions = actionsFor(tab, device) { action -> performAction(device.mac, action) },
                                named = namedByIp[device.ip]
                            )
                        }
                    }
                }
            }
        }
    }

private fun emptyMessageFor(tab: DeviceTab): String = when (tab) {
    DeviceTab.ONLINE -> "Tidak ada device yang online saat ini."
    DeviceTab.WHITELIST -> "Belum ada device di whitelist."
    DeviceTab.PENDING -> "Tidak ada device yang menunggu approval."
    DeviceTab.BLOCKED -> "Tidak ada device yang diblokir."
}

/** Which action buttons to show, tailored per tab so each list only offers actions that make sense there. */
private fun actionsFor(
    tab: DeviceTab,
    device: DeviceInfo,
    perform: (String) -> Unit
): List<DeviceAction> = when (tab) {
    DeviceTab.ONLINE -> buildList {
        if (device.category != "whitelist") add(DeviceAction("Whitelist", false) { perform("whitelist") })
        if (device.category != "blocked") add(DeviceAction("Blokir", true) { perform("block") })
    }
    DeviceTab.WHITELIST -> listOf(
        DeviceAction("Blokir", true) { perform("block") },
        DeviceAction("Reset", false) { perform("unblock") }
    )
    DeviceTab.PENDING -> listOf(
        DeviceAction("Whitelist", false) { perform("whitelist") },
        DeviceAction("Blokir", true) { perform("block") }
    )
    DeviceTab.BLOCKED -> listOf(
        DeviceAction("Ke Pending", false) { perform("pending") },
        DeviceAction("Whitelist", false) { perform("whitelist") }
    )
}

private data class DeviceAction(val label: String, val destructive: Boolean, val onClick: () -> Unit)

@Composable
private fun categoryLabel(category: String): Pair<String, Color> = when (category) {
    "whitelist" -> "Whitelist" to com.remotwrt.bot.ui.theme.RemotGreen
    "blocked" -> "Diblokir" to com.remotwrt.bot.ui.theme.RemotRed
    "pending" -> "Pending" to com.remotwrt.bot.ui.theme.RemotAmber
    else -> "Belum Dikenal" to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun statusColor(status: String): Color = when (status) {
    "TERHUBUNG" -> com.remotwrt.bot.ui.theme.RemotGreen
    "TERHUBUNG TIDAK AKTIF" -> com.remotwrt.bot.ui.theme.RemotAmber
    "TIDAK DIKETAHUI" -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> com.remotwrt.bot.ui.theme.RemotRed
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun formatDuration(seconds: Long): String {
    if (seconds < 60) return "< 1 menit"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}j ${minutes}m" else "${minutes}m"
}

@Composable
private fun DeviceRow(
    device: DeviceInfo,
    busy: Boolean,
    actions: List<DeviceAction>,
    named: com.remotwrt.bot.data.NamedDeviceInfo? = null
) {
    val (categoryText, categoryColor) = categoryLabel(device.category)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (named != null) {
                        Text(named.icon, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        (named?.name?.takeIf { it.isNotBlank() } ?: device.name).ifBlank { "-" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Badge(categoryText, categoryColor)
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "IP: ${device.ip}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Text(
                "MAC: ${device.mac}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Status: ", style = MaterialTheme.typography.bodySmall)
                Text(
                    device.status,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = statusColor(device.status)
                )
            }
            if (device.online && device.onlineSinceSec != null) {
                Text(
                    "Online sejak: ${formatDuration(device.onlineSinceSec)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = com.remotwrt.bot.ui.theme.RemotGreen
                )
            }

            // Connection category badges (Sosmed/Game/Belanja/Streaming), merged in
            // from the named-device registry -- only present for devices with a
            // custom name AND active Clash connections right now.
            val activeCats = named?.let { n -> CATEGORY_META.filter { !n.categories[it.key].isNullOrEmpty() } }.orEmpty()
            if (activeCats.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    activeCats.forEach { meta ->
                        val apps = named!!.categories[meta.key].orEmpty()
                        val topApp = apps.firstOrNull()?.name ?: meta.key
                        val extraCount = apps.size - 1
                        val label = if (extraCount > 0) "${meta.icon} $topApp +$extraCount" else "${meta.icon} $topApp"
                        Surface(
                            color = meta.color.copy(alpha = 0.15f),
                            contentColor = meta.color,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            if (actions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                if (busy) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        actions.forEach { action ->
                            OutlinedButton(
                                onClick = action.onClick,
                                colors = if (action.destructive)
                                    ButtonDefaults.outlinedButtonColors(contentColor = com.remotwrt.bot.ui.theme.RemotRed)
                                else ButtonDefaults.outlinedButtonColors(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(action.label)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== AdGuard Home tab ====================

private enum class AdguardSubTab(val label: String) {
    OVERVIEW("Overview"), QUERYLOG("Query Log"), FILTERS("Filters"), SECURITY("Security"), RULES("Rules")
}

@Composable
fun AdguardTabContent(prefs: Prefs) {
    var subTab by remember { mutableStateOf(AdguardSubTab.OVERVIEW) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = subTab.ordinal, edgePadding = 12.dp) {
            AdguardSubTab.entries.forEach { t ->
                Tab(selected = subTab == t, onClick = { subTab = t }, text = { Text(t.label) })
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (subTab) {
                AdguardSubTab.OVERVIEW -> AdguardOverviewTab(prefs)
                AdguardSubTab.QUERYLOG -> AdguardQueryLogTab(prefs)
                AdguardSubTab.FILTERS -> AdguardFiltersTab(prefs)
                AdguardSubTab.SECURITY -> AdguardSecurityTab(prefs)
                AdguardSubTab.RULES -> AdguardRulesTab(prefs)
            }
        }
    }
}

// ---- Overview ----

@Composable
private fun AdguardOverviewTab(prefs: Prefs) {
    var overview by remember { mutableStateOf<com.remotwrt.bot.data.AdguardOverview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var showProtectionConfirm by remember { mutableStateOf<Boolean?>(null) } // target state to confirm
    var applying by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    suspend fun reload() {
        withContext(Dispatchers.IO) {
            try {
                val result = LuciClient(prefs).fetchAdguardOverview()
                withContext(Dispatchers.Main) { overview = result; error = null }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = "Tidak bisa terhubung ke AdGuard Home: ${e.message}"
                }
            }
        }
    }

    LaunchedEffect(refreshTick) { reload() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = { refreshTick++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
        }

        val o = overview
        if (o == null && error == null) {
            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (o != null) {
            InfoCard("Status", Icons.Filled.Shield) {
                StatRowPill(
                    "Service",
                    if (o.running) "Berjalan" else "Offline",
                    if (o.running) com.remotwrt.bot.ui.theme.RemotGreen else com.remotwrt.bot.ui.theme.RemotRed,
                    pulsing = o.running
                )
                StatRowPill(
                    "Protection",
                    if (o.protectionEnabled) "Aktif" else "Nonaktif",
                    if (o.protectionEnabled) com.remotwrt.bot.ui.theme.RemotGreen else com.remotwrt.bot.ui.theme.RemotRed
                )
                StatRow("Versi", o.version)
                StatRow("DNS Port", "${o.dnsPort}")
                Spacer(Modifier.height(10.dp))
                if (applying) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else {
                    Button(
                        onClick = { showProtectionConfirm = !o.protectionEnabled },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (o.protectionEnabled)
                            ButtonDefaults.buttonColors(containerColor = com.remotwrt.bot.ui.theme.RemotRed)
                        else ButtonDefaults.buttonColors()
                    ) {
                        Text(if (o.protectionEnabled) "Matikan Protection" else "Aktifkan Protection")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            InfoCard("Statistik (24 jam)", Icons.Filled.BarChart) {
                StatRow("Total Query", "${o.numDnsQueries}")
                StatRow("Query Diblokir", "${o.numBlockedFiltering}")
                StatRow("Persentase Blokir", "${"%.1f".format(o.blockedPct)}%")
                StatRow("Klien Aktif", "${o.numClients}")
                if (o.avgProcessingMs > 0) {
                    StatRow("Rata-rata Respons", "${"%.1f".format(o.avgProcessingMs)} ms")
                }
            }

            Spacer(Modifier.height(12.dp))
            InfoCard("DNS", Icons.Filled.Dns) {
                StatRowPill(
                    "DNSSEC",
                    if (o.dnssecEnabled) "Aktif" else "Nonaktif",
                    if (o.dnssecEnabled) com.remotwrt.bot.ui.theme.RemotGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (o.upstreamDns.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Upstream DNS:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    o.upstreamDns.forEach { u ->
                        Text(
                            u,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    showProtectionConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { showProtectionConfirm = null },
            title = { Text(if (target) "Aktifkan Protection?" else "Matikan Protection?") },
            text = {
                Text(
                    if (target) "Filtering DNS AdGuard Home akan diaktifkan kembali."
                    else "Semua perangkat di jaringan TIDAK akan terlindungi filtering DNS sampai diaktifkan lagi."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = target
                    showProtectionConfirm = null
                    applying = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { LuciClient(prefs).setAdguardProtection(t) }
                        } catch (_: Exception) { /* surfaced on next reload via error state */ }
                        applying = false
                        reload()
                    }
                }) { Text("Ya, Lanjutkan") }
            },
            dismissButton = {
                TextButton(onClick = { showProtectionConfirm = null }) { Text("Batal") }
            }
        )
    }
}

// ---- Query Log ----

@Composable
private fun AdguardQueryLogTab(prefs: Prefs) {
    var entries by remember { mutableStateOf<List<com.remotwrt.bot.data.AdguardQueryLogEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var refreshTick by remember { mutableStateOf(0) }
    var actionTarget by remember { mutableStateOf<com.remotwrt.bot.data.AdguardQueryLogEntry?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    suspend fun reload() {
        withContext(Dispatchers.IO) {
            try {
                val result = LuciClient(prefs).fetchAdguardQueryLog(search = search, limit = 60)
                withContext(Dispatchers.Main) { entries = result; error = null }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { error = "Gagal memuat query log: ${e.message}" }
            }
        }
    }

    LaunchedEffect(refreshTick) { reload() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Cari domain/klien") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { refreshTick++ }),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { refreshTick++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        val list = entries
        if (list == null && error == null) {
            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (list != null && list.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                Text("Tidak ada query.", style = MaterialTheme.typography.bodyMedium)
            }
        } else if (list != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list, key = { it.time + it.domain }) { entry ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().clickable { actionTarget = entry }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    entry.domain,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Badge(
                                    if (entry.blocked) "Diblokir" else "Diizinkan",
                                    if (entry.blocked) com.remotwrt.bot.ui.theme.RemotRed else com.remotwrt.bot.ui.theme.RemotGreen
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${entry.type} • ${entry.client} • ${entry.time}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (entry.reason.isNotBlank()) {
                                Text(
                                    entry.reason,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    actionTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { if (!busy) actionTarget = null },
            title = { Text(entry.domain) },
            text = { Text("Pilih aksi untuk domain ini.") },
            confirmButton = {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Row {
                        TextButton(onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        LuciClient(prefs).setAdguardQueryLogAction(entry.domain, "whitelist")
                                    }
                                } catch (_: Exception) { }
                                busy = false
                                actionTarget = null
                                reload()
                            }
                        }) { Text("Whitelist") }
                        TextButton(onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        LuciClient(prefs).setAdguardQueryLogAction(entry.domain, "block")
                                    }
                                } catch (_: Exception) { }
                                busy = false
                                actionTarget = null
                                reload()
                            }
                        }) { Text("Blokir", color = com.remotwrt.bot.ui.theme.RemotRed) }
                    }
                }
            },
            dismissButton = {
                if (!busy) TextButton(onClick = { actionTarget = null }) { Text("Tutup") }
            }
        )
    }
}

// ---- Filters ----

@Composable
private fun AdguardFiltersTab(prefs: Prefs) {
    var filters by remember { mutableStateOf<List<com.remotwrt.bot.data.AdguardFilter>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var busyUrl by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<com.remotwrt.bot.data.AdguardFilter?>(null) }
    var refreshingAll by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    suspend fun reload() {
        withContext(Dispatchers.IO) {
            try {
                val result = LuciClient(prefs).fetchAdguardFilters()
                withContext(Dispatchers.Main) { filters = result; error = null }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { error = "Gagal memuat filter: ${e.message}" }
            }
        }
    }

    LaunchedEffect(refreshTick) { reload() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val list = filters
            Text(
                if (list != null) "${list.count { it.enabled }} dari ${list.size} aktif" else "Memuat...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Tambah Filter")
                }
                IconButton(onClick = { refreshTick++ }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
        }

        Button(
            onClick = {
                refreshingAll = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            LuciClient(prefs).setAdguardFilterAction("refresh")
                        }
                    } catch (_: Exception) { }
                    refreshingAll = false
                    reload()
                }
            },
            enabled = !refreshingAll,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            if (refreshingAll) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("Update Semua Filter")
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        val list = filters
        if (list == null && error == null) {
            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (list != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list, key = { it.url }) { filter ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    filter.name.ifBlank { filter.url },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (busyUrl == filter.url) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Switch(
                                        checked = filter.enabled,
                                        onCheckedChange = { newVal ->
                                            busyUrl = filter.url
                                            scope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        LuciClient(prefs).setAdguardFilterAction(
                                                            "toggle", url = filter.url, name = filter.name, enabled = newVal
                                                        )
                                                    }
                                                } catch (_: Exception) { }
                                                busyUrl = null
                                                reload()
                                            }
                                        }
                                    )
                                }
                            }
                            Text(
                                filter.url,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${filter.rulesCount} rules • update terakhir: ${filter.lastUpdated}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { deleteTarget = filter }) {
                                Text("Hapus", color = com.remotwrt.bot.ui.theme.RemotRed)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        var newUrl by remember { mutableStateOf("") }
        var adding by remember { mutableStateOf(false) }
        var addError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!adding) showAddDialog = false },
            title = { Text("Tambah Filter") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nama") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        label = { Text("URL filter (https://...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    addError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !adding,
                    onClick = {
                        if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                            addError = "URL harus diawali http:// atau https://"
                            return@TextButton
                        }
                        adding = true
                        addError = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    LuciClient(prefs).setAdguardFilterAction("add", url = newUrl, name = newName)
                                }
                                showAddDialog = false
                                reload()
                            } catch (e: Exception) {
                                addError = e.message
                            }
                            adding = false
                        }
                    }
                ) { Text(if (adding) "Menambahkan..." else "Tambah") }
            },
            dismissButton = {
                TextButton(onClick = { if (!adding) showAddDialog = false }) { Text("Batal") }
            }
        )
    }

    deleteTarget?.let { filter ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Hapus Filter?") },
            text = { Text("Filter \"${filter.name.ifBlank { filter.url }}\" akan dihapus.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = filter
                    deleteTarget = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                LuciClient(prefs).setAdguardFilterAction("remove", url = target.url)
                            }
                        } catch (_: Exception) { }
                        reload()
                    }
                }) { Text("Hapus", color = com.remotwrt.bot.ui.theme.RemotRed) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Batal") }
            }
        )
    }
}

// ---- Security & DNS ----

@Composable
private fun AdguardSecurityTab(prefs: Prefs) {
    var security by remember { mutableStateOf<com.remotwrt.bot.data.AdguardSecurity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var pendingToggle by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // feature to enabled-target
    var applying by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    suspend fun reload() {
        withContext(Dispatchers.IO) {
            try {
                val result = LuciClient(prefs).fetchAdguardSecurity()
                withContext(Dispatchers.Main) { security = result; error = null }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { error = "Gagal memuat pengaturan: ${e.message}" }
            }
        }
    }

    LaunchedEffect(refreshTick) { reload() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { refreshTick++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
        }

        val s = security
        if (s == null && error == null) {
            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (s != null) {
            InfoCard("Keamanan", Icons.Filled.Security) {
                SecurityToggleRow("Safe Search", s.safeSearchEnabled, applying) { target ->
                    pendingToggle = "safesearch" to target
                }
                SecurityToggleRow("Parental Control", s.parentalEnabled, applying) { target ->
                    pendingToggle = "parental" to target
                }
                SecurityToggleRow("Safe Browsing", s.safeBrowsingEnabled, applying) { target ->
                    pendingToggle = "safebrowsing" to target
                }
                SecurityToggleRow("DNSSEC", s.dnssecEnabled, applying) { target ->
                    pendingToggle = "dnssec" to target
                }
            }

            Spacer(Modifier.height(12.dp))
            InfoCard("DNS", Icons.Filled.Dns) {
                StatRow("Ukuran Cache", "${s.cacheSize} B")
                if (s.upstreamDns.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Upstream DNS:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    s.upstreamDns.forEach { u ->
                        Text(
                            u,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    pendingToggle?.let { (feature, target) ->
        val label = when (feature) {
            "safesearch" -> "Safe Search"
            "parental" -> "Parental Control"
            "safebrowsing" -> "Safe Browsing"
            "dnssec" -> "DNSSEC"
            else -> feature
        }
        AlertDialog(
            onDismissRequest = { pendingToggle = null },
            title = { Text(if (target) "Aktifkan $label?" else "Matikan $label?") },
            text = { Text("Perubahan ini langsung diterapkan ke AdGuard Home.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingToggle = null
                    applying = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                LuciClient(prefs).setAdguardSecurityToggle(feature, target)
                            }
                        } catch (_: Exception) { }
                        applying = false
                        reload()
                    }
                }) { Text("Ya, Lanjutkan") }
            },
            dismissButton = {
                TextButton(onClick = { pendingToggle = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun SecurityToggleRow(label: String, checked: Boolean, disabled: Boolean, onRequestChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, enabled = !disabled, onCheckedChange = { onRequestChange(it) })
    }
}

// ---- Custom Rules ----

@Composable
private fun AdguardRulesTab(prefs: Prefs) {
    var originalRules by remember { mutableStateOf<String?>(null) }
    var rulesText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showSaveConfirm by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(refreshTick) {
        withContext(Dispatchers.IO) {
            try {
                val result = LuciClient(prefs).fetchAdguardRules()
                withContext(Dispatchers.Main) {
                    originalRules = result
                    rulesText = result
                    error = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { error = "Gagal memuat custom rules: ${e.message}" }
            }
        }
    }

    val dirty = originalRules != null && rulesText != originalRules

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Satu aturan per baris. Contoh: ||contoh-iklan.com^ untuk blokir, " +
                "@@||situs-aman.com^ untuk izinkan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
        }

        if (originalRules == null && error == null) {
            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            OutlinedTextField(
                value = rulesText,
                onValueChange = { rulesText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp)
                ),
                placeholder = { Text("||contoh-domain.com^") }
            )

            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { rulesText = originalRules ?: "" },
                    enabled = dirty && !saving,
                    modifier = Modifier.weight(1f)
                ) { Text("Reset") }
                Button(
                    onClick = { showSaveConfirm = true },
                    enabled = dirty && !saving,
                    modifier = Modifier.weight(1f)
                ) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Simpan")
                }
            }
        }
    }

    if (showSaveConfirm) {
        AlertDialog(
            onDismissRequest = { showSaveConfirm = false },
            title = { Text("Simpan Custom Rules?") },
            text = { Text("Aturan ini akan langsung diterapkan ke AdGuard Home.") },
            confirmButton = {
                TextButton(onClick = {
                    showSaveConfirm = false
                    saving = true
                    val toSave = rulesText
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { LuciClient(prefs).saveAdguardRules(toSave) }
                            originalRules = toSave
                            error = null
                        } catch (e: Exception) {
                            error = "Gagal menyimpan: ${e.message}"
                        }
                        saving = false
                    }
                }) { Text("Ya, Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirm = false }) { Text("Batal") }
            }
        )
    }
}


@Composable
fun MemoryTabContent(prefs: Prefs) {
    var disks by remember { mutableStateOf<List<com.remotwrt.bot.data.DiskInfo>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            error = null
            try {
                disks = withContext(Dispatchers.IO) { LuciClient(prefs).fetchDisks() }
            } catch (e: Exception) {
                error = e.message ?: "Gagal memuat informasi memori"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { reload() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Disk & Storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Data read-only dari DiskMan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = ::reload, enabled = !loading) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Refresh, contentDescription = "Muat ulang")
                }
            }
        }

        error?.let { message ->
            item {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = com.remotwrt.bot.ui.theme.RemotRed.copy(alpha = 0.12f))) {
                    Text(message, modifier = Modifier.padding(14.dp), color = com.remotwrt.bot.ui.theme.RemotRed)
                }
            }
        }

        if (!loading && error == null && disks?.isEmpty() == true) {
            item {
                Text("Tidak ada disk fisik yang terdeteksi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        disks?.let { list ->
            items(list, key = { it.path }) { disk -> DiskInfoCard(disk) }
        }
    }
}

@Composable
private fun DiskInfoCard(disk: com.remotwrt.bot.data.DiskInfo) {
    val healthy = disk.health.equals("PASSED", ignoreCase = true) ||
        disk.health.equals("OK", ignoreCase = true)
    val healthColor = when {
        healthy -> com.remotwrt.bot.ui.theme.RemotGreen
        disk.health == "-" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> com.remotwrt.bot.ui.theme.RemotRed
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(disk.model, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(disk.path, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
                Text(disk.size, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider()
            DiskDetailRow("Serial Number", disk.serialNumber)
            DiskDetailRow("Temp", disk.temperature)
            DiskDetailRow("Partition Table", disk.partitionTable)
            DiskDetailRow("SATA Version", disk.sataVersion)
            DiskDetailRow("Health Status", "${disk.health} · ${disk.status}", healthColor)

            if (disk.partitions.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text("Partitions", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                disk.partitions.filter { it.name != "-" }.forEach { partition ->
                    Text(
                        "${partition.name} · ${partition.size} · ${partition.fileSystem} · ${partition.mountPoint} · ${partition.usage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DiskDetailRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

@Composable
fun LogsTabContent(prefs: Prefs) {
    var logText by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableStateOf(0) }
    var showRebootConfirm by remember { mutableStateOf(false) }
    var rebooting by remember { mutableStateOf(false) }
    var rebootResult by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    suspend fun reload() {
        loading = true
        withContext(Dispatchers.IO) {
            try {
                val result = LuciClient(prefs).fetchSystemLog()
                withContext(Dispatchers.Main) {
                    logText = result
                    error = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { error = "Gagal memuat log: ${e.message}" }
            }
        }
        loading = false
    }

    LaunchedEffect(refreshTick) { reload() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Log Sistem (logread)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { refreshTick++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            when {
                loading && logText == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                logText != null -> {
                    val scrollState = rememberScrollState()
                    // Auto-scroll to the bottom (newest lines) whenever the log refreshes.
                    LaunchedEffect(logText) { scrollState.animateScrollTo(scrollState.maxValue) }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            logText!!.ifBlank { "(log kosong)" },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Reboot button, carried over from the old Terminal tab -- this uses the
        // dedicated /reboot endpoint directly, not the generic named-command
        // system, so it works out of the box with no extra UCI setup.
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            rebootResult?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            OutlinedButton(
                onClick = { showRebootConfirm = true },
                enabled = !rebooting,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = com.remotwrt.bot.ui.theme.RemotRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (rebooting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text("Reboot Router")
            }
        }
    }

    if (showRebootConfirm) {
        AlertDialog(
            onDismissRequest = { showRebootConfirm = false },
            title = { Text("Reboot Router?") },
            text = { Text("Router akan restart. Koneksi semua device akan terputus sebentar.") },
            confirmButton = {
                TextButton(onClick = {
                    showRebootConfirm = false
                    rebooting = true
                    scope.launch {
                        val output = withContext(Dispatchers.IO) {
                            try {
                                LuciClient(prefs).rebootRouter()
                                "Perintah reboot terkirim. Router akan restart sebentar lagi."
                            } catch (e: Exception) {
                                "Gagal: ${e.message}"
                            }
                        }
                        rebootResult = output
                        rebooting = false
                    }
                }) { Text("Reboot", color = com.remotwrt.bot.ui.theme.RemotRed) }
            },
            dismissButton = {
                TextButton(onClick = { showRebootConfirm = false }) { Text("Batal") }
            }
        )
    }
}

// ==================== Settings tab ====================

@Composable
fun SettingsTabContent(prefs: Prefs, onLogout: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var baseUrl by remember { mutableStateOf(prefs.baseUrl) }
    var username by remember { mutableStateOf(prefs.username) }
    var password by remember { mutableStateOf(prefs.password) }
    var saving by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Cloudflare Connection Setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Router API URL (LuCI)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.weight(1f)
            )
        }

        saveError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        saveMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = com.remotwrt.bot.ui.theme.RemotGreen, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                saveError = null
                saveMessage = null
                saving = true
                prefs.baseUrl = baseUrl
                prefs.username = username
                prefs.password = password
                scope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            LuciClient(prefs).login()
                            withContext(Dispatchers.Main) { saveMessage = "Berhasil terhubung." }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { saveError = "Gagal terhubung: ${e.message}" }
                        }
                    }
                    saving = false
                }
            },
            enabled = !saving && baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
            } else {
                Icon(Icons.Filled.CloudDone, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(if (saving) "Menghubungkan..." else "Save & Connect")
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { context.startActivity(Intent(context, WebViewActivity::class.java)) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Buka Panel LuCI Lengkap")
        }

        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        )
        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { confirmLogout = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = com.remotwrt.bot.ui.theme.RemotRed),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Logout")
        }

        Spacer(Modifier.height(20.dp))
        AppVersionCard()
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Logout?") },
            text = { Text("Anda perlu login ulang untuk memakai app ini lagi.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onLogout()
                }) { Text("Logout", color = com.remotwrt.bot.ui.theme.RemotRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Batal") }
            }
        )
    }
}
