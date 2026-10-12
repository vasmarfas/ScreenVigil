package com.vasmarfas.screenvigil

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import com.vasmarfas.screenvigil.diag.Diag
import com.vasmarfas.screenvigil.kiosk.ForegroundWatcher
import com.vasmarfas.screenvigil.root.RootShell
import com.vasmarfas.screenvigil.ui.CurveScreen
import com.vasmarfas.screenvigil.ui.DiagScreen
import com.vasmarfas.screenvigil.ui.OverviewScreen
import com.vasmarfas.screenvigil.ui.PermissionState
import com.vasmarfas.screenvigil.ui.SettingsScreen
import com.vasmarfas.screenvigil.ui.theme.ScreenVigilTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    private val resumeTicks = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Diag.attach(this)
        setContent {
            ScreenVigilTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        ScreenVigilApp(resumeTicks.intValue)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ScreenVigilState.uiVisible = true
        resumeTicks.intValue++
    }

    override fun onPause() {
        super.onPause()
        ScreenVigilState.uiVisible = false
        ScreenVigilState.publishPreview(null)
    }
}

@Composable
private fun ScreenVigilApp(resumeTick: Int) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val config by prefs.config.collectAsState()
    val live by ScreenVigilState.live.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var permissionRefresh by remember { mutableIntStateOf(0) }
    var rootAvailable by remember { mutableIntStateOf(0) }
    var haTestAwaitedSince by remember { mutableLongStateOf(0L) }
    val haResultOk = stringResource(R.string.ha_result_ok)
    val haResultFailFormat = stringResource(R.string.ha_result_fail)

    LaunchedEffect(live.haStatusAt) {
        if (haTestAwaitedSince > 0L && live.haStatusAt >= haTestAwaitedSince) {
            haTestAwaitedSince = 0L
            val message = if (live.haStatus == "ok") {
                haResultOk
            } else {
                String.format(haResultFailFormat, live.haStatus)
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val available = withContext(Dispatchers.IO) { RootShell.isAvailable() }
        rootAvailable = if (available) 1 else 0
        ScreenVigilState.update { it.copy(rootAvailable = available) }
    }

    val importResultFormat = stringResource(R.string.set_import_result)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissionRefresh++ }

    val permissions = remember(permissionRefresh, rootAvailable, resumeTick) {
        PermissionState(
            camera = context.checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
            writeSettings = SettingsWriter.canWriteSystem(context),
            notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            batteryUnrestricted = context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName),
            rootAvailable = rootAvailable == 1,
            usageAccess = ForegroundWatcher(context).hasAccess(),
            overlayAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                Settings.canDrawOverlays(context),
        )
    }

    Column {
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf(
                R.string.tab_overview,
                R.string.tab_settings,
                R.string.tab_curve,
                R.string.tab_diag,
            ).forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(stringResource(label)) },
                )
            }
        }
        when (tab) {
            0 -> OverviewScreen(
                live = live,
                config = config,
                permissions = permissions,
                onStart = {
                    KioskSensorService.start(context)
                    Toast.makeText(context, R.string.service_running, Toast.LENGTH_SHORT).show()
                },
                onStop = { KioskSensorService.stop(context) },
                onWake = { KioskSensorService.command(context, KioskSensorService.ACTION_WAKE) },
                onRequestCamera = {
                    val list = mutableListOf(Manifest.permission.CAMERA)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        list += Manifest.permission.POST_NOTIFICATIONS
                    }
                    permissionLauncher.launch(list.toTypedArray())
                },
                onRequestWriteSettings = { openWriteSettings(context) },
                onRequestNotifications = { openNotificationSettings(context) },
                onRequestBattery = { openBatteryOptimizationSettings(context) },
                onGrantWithRoot = { grantWithRoot(context) },
            )

            1 -> SettingsScreen(
                config = config,
                prefs = prefs,
                rootAvailable = permissions.rootAvailable,
                proximityAvailable = live.proximityAvailable,
                usageAccess = permissions.usageAccess,
                overlayAllowed = permissions.overlayAllowed,
                haStatus = live.haStatus,
                onTestHa = {
                    haTestAwaitedSince = System.currentTimeMillis()
                    KioskSensorService.command(context, KioskSensorService.ACTION_HA_TEST)
                },
                onGrantUsageAccess = { openUsageAccessSettings(context) },
                onGrantOverlay = { openOverlaySettings(context) },
                onExport = {
                    copyToClipboard(context, prefs.exportJson())
                    Toast.makeText(context, R.string.diag_copied, Toast.LENGTH_SHORT).show()
                },
                onImport = {
                    val text = readClipboard(context)
                    val applied = if (text.isNullOrBlank()) 0 else prefs.importJson(text)
                    Toast.makeText(
                        context,
                        String.format(importResultFormat, applied),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )

            2 -> CurveScreen(config = config, prefs = prefs, live = live)

            else -> DiagScreen(
                onCopyLog = { text ->
                    copyToClipboard(context, text)
                    Toast.makeText(context, R.string.diag_copied, Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

private fun openWriteSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                "package:${context.packageName}".toUri(),
            ),
        )
    }
}

private fun openNotificationSettings(context: Context) {
    runCatching {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData("package:${context.packageName}".toUri())
        }
        context.startActivity(intent)
    }
}

private fun openBatteryOptimizationSettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
}

private fun openUsageAccessSettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
}

private fun openOverlaySettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri(),
            ),
        )
    }
}

private fun grantWithRoot(context: Context) {
    Thread {
        val pkg = context.packageName
        RootShell.grantWriteSettings(pkg)
        RootShell.grantUsageStats(pkg)
        RootShell.exemptFromDoze(pkg)
        RootShell.grantPermission(pkg, Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RootShell.grantPermission(pkg, Manifest.permission.POST_NOTIFICATIONS)
        }
        Diag.event("разрешения выданы через root")
    }.start()
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    manager?.setPrimaryClip(ClipData.newPlainText("screenvigil", text))
}

private fun readClipboard(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    return manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
}
