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
                        mutableStateOf(if (prefs.isConfigured) Screen.DASHBOARD else Screen.SETUP)
                    }
                    when (screen) {
                        Screen.SETUP -> SetupScreen(
                            prefs = prefs,
                            onLoggedIn = {
                                MonitorScheduler.start(this@MainActivity)
                                screen = Screen.DASHBOARD
                            }
                        )
                        Screen.DASHBOARD -> DashboardScreen(
                            prefs = prefs,
                            onLogout = {
                                MonitorScheduler.stop(this@MainActivity)
                                prefs.clear()
                                screen = Screen.SETUP
                            },
                            onManageDevices = { screen = Screen.DEVICES }
                        )
                        Screen.DEVICES -> DevicesScreen(
                            prefs = prefs,
                            onBack = { screen = Screen.DASHBOARD }
                        )
                    }
                }
            }
        }
    }
}

private enum class Screen { SETUP, DASHBOARD, DEVICES }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(prefs: Prefs, onLogout: () -> Unit, onManageDevices: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var status by remember { mutableStateOf<RemotbotStatus?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableStateOf(0) }
    var lastUpdated by remember { mutableStateOf<Long?>(null) }

    // Auto-refresh every 10s while the dashboard is on screen.
    LaunchedEffect(refreshTick) {
        while (true) {
            withContext(Dispatchers.IO) {
                try {
                    val result = LuciClient(prefs).fetchStatus()
                    withContext(Dispatchers.Main) {
                        status = result
                        error = null
                        lastUpdated = System.currentTimeMillis()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { error = "Gagal memuat status: ${e.message}" }
                }
            }
            delay(10_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("RemotWRT Bot")
                        lastUpdated?.let {
                            val fmt = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }
                            Text(
                                "Update terakhir: ${fmt.format(java.util.Date(it))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { refreshTick++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = {
                        context.startActivity(Intent(context, WebViewActivity::class.java))
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Pengaturan")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Filled.Logout, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
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
                StaggeredEntry(0) { StatusCard(s) }
                Spacer(Modifier.height(12.dp))
                StaggeredEntry(1) { ResourceCard(s) }
                Spacer(Modifier.height(12.dp))
                StaggeredEntry(2) { NetworkCard(s) }
                Spacer(Modifier.height(12.dp))
                StaggeredEntry(3) { DevicesCard(s, onManageDevices) }
                Spacer(Modifier.height(12.dp))
                StaggeredEntry(4) { MyIpCard(s) }
                Spacer(Modifier.height(12.dp))
                StaggeredEntry(5) { ServicesCard(s) }
                Spacer(Modifier.height(12.dp))
                StaggeredEntry(6) { CommandsCard(prefs) }
                Spacer(Modifier.height(12.dp))
                StaggeredEntry(7) { AppVersionCard() }
            }
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
private fun ResourceCard(s: RemotbotStatus) {
    InfoCard("Sumber Daya", Icons.Filled.Memory) {
        val cpuOver = s.cpuTemp != null && s.cpuTemp > s.cpuTempLimit
        val cpuColor = if (cpuOver) com.remotwrt.bot.ui.theme.RemotRed else MaterialTheme.colorScheme.primary
        val cpuFraction = if (s.cpuTemp != null && s.cpuTempLimit > 0)
            (s.cpuTemp / s.cpuTempLimit).toFloat().coerceIn(0f, 1f) else 0f
        val animatedCpuFraction by animateFloatAsState(
            targetValue = cpuFraction, animationSpec = tween(600), label = "cpu_temp_bar"
        )
        StatRow(
            "Suhu CPU",
            if (s.cpuTemp != null) "${s.cpuTemp}°C (batas ${s.cpuTempLimit}°C)" else "N/A",
            if (cpuOver) com.remotwrt.bot.ui.theme.RemotRed else Color.Unspecified
        )
        LinearProgressIndicator(
            progress = animatedCpuFraction,
            color = cpuColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.height(10.dp))

        val ramOver = s.memPct > s.ramLimit
        val ramColor = if (ramOver) com.remotwrt.bot.ui.theme.RemotRed else MaterialTheme.colorScheme.primary
        val animatedRamFraction by animateFloatAsState(
            targetValue = (s.memPct / 100f).coerceIn(0f, 1f), animationSpec = tween(600), label = "ram_bar"
        )
        StatRow("RAM", "${s.memPct}% (${s.memUsedMb}/${s.memTotalMb} MB)", if (ramOver) com.remotwrt.bot.ui.theme.RemotRed else Color.Unspecified)
        LinearProgressIndicator(
            progress = animatedRamFraction,
            color = ramColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.height(10.dp))

        s.diskPct?.let { diskPct ->
            val animatedDiskFraction by animateFloatAsState(
                targetValue = (diskPct / 100f).coerceIn(0f, 1f), animationSpec = tween(600), label = "disk_bar"
            )
            StatRow("Disk (overlay)", "$diskPct%")
            LinearProgressIndicator(
                progress = animatedDiskFraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.height(10.dp))
        }

        StatRow("Load Average", s.load1)
        StatRow("Uptime", formatUptime(s.uptimeSec))
    }
}

@Composable
private fun NetworkCard(s: RemotbotStatus) {
    InfoCard("Jaringan", Icons.Filled.Wifi) {
        StatRowPill(
            "WAN",
            if (s.wanUp) "Terhubung" else "Terputus",
            if (s.wanUp) com.remotwrt.bot.ui.theme.RemotGreen else com.remotwrt.bot.ui.theme.RemotRed,
            pulsing = s.wanUp
        )
    }
}

@Composable
private fun DevicesCard(s: RemotbotStatus, onManageDevices: () -> Unit) {
    InfoCard("Device", Icons.Filled.Devices) {
        StatRow("Online", "${s.devicesOnline}")
        StatRow("Whitelist", "${s.whitelistCount}")
        StatRow(
            "Pending Approval",
            "${s.pendingCount}",
            if (s.pendingCount > 0) com.remotwrt.bot.ui.theme.RemotAmber else Color.Unspecified
        )
        StatRow("Blocked", "${s.blockedCount}")
        Spacer(Modifier.height(8.dp))
        Button(onClick = onManageDevices, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Kelola Device")
        }
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
                Text(
                    "Update tersedia: ${info.versionTag}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = com.remotwrt.bot.ui.theme.RemotAmber
                )
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
                        Text(if (needsPermission) "Coba Lagi Setelah Izin Diberikan" else "Update Sekarang")
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
private fun MyIpCard(s: RemotbotStatus) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    InfoCard("Informasi IP Publik", Icons.Filled.Public) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                s.myIp,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            IconButton(onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(s.myIp))
                android.widget.Toast.makeText(context, "IP disalin", android.widget.Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Salin IP")
            }
        }
        Spacer(Modifier.height(4.dp))
        StatRow("ISP", s.myIpIsp)
        StatRow("Lokasi", listOf(s.myIpCity, s.myIpRegion).filter { it.isNotBlank() && it != "-" }.joinToString(", ").ifBlank { "-" })
        StatRow("Negara", s.myIpCountry)
    }
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
fun DevicesScreen(prefs: Prefs, onBack: () -> Unit) {
    var devices by remember { mutableStateOf<List<DeviceInfo>?>(null) }
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
                withContext(Dispatchers.Main) {
                    devices = result
                    error = null
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { error = "Gagal memuat device: ${e.message}" }
            }
        }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kelola Device") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshTick++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
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
                                actions = actionsFor(tab, device) { action -> performAction(device.mac, action) }
                            )
                        }
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
    actions: List<DeviceAction>
) {
    val (categoryText, categoryColor) = categoryLabel(device.category)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    device.name.ifBlank { "-" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
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
