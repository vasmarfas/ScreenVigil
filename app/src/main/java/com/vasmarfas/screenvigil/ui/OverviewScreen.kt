package com.vasmarfas.screenvigil.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vasmarfas.screenvigil.Config
import com.vasmarfas.screenvigil.R
import com.vasmarfas.screenvigil.ScreenVigilState
import com.vasmarfas.screenvigil.camera.CameraController
import com.vasmarfas.screenvigil.motion.MotionDetector
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PermissionState(
    val camera: Boolean,
    val writeSettings: Boolean,
    val notifications: Boolean,
    val rootAvailable: Boolean,
) {
    val allGranted: Boolean get() = camera && writeSettings && notifications
}

private val clock = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
fun OverviewScreen(
    live: ScreenVigilState.Live,
    config: Config,
    permissions: PermissionState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onWake: () -> Unit,
    onRequestCamera: () -> Unit,
    onRequestWriteSettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onGrantWithRoot: () -> Unit,
) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = SystemClock.elapsedRealtime()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        SectionCard(
            title = if (live.serviceRunning) {
                stringResource(R.string.service_running)
            } else {
                stringResource(R.string.service_stopped)
            },
        ) {
            Hint(
                if (live.serviceRunning) {
                    stringResource(R.string.service_state_hint_running)
                } else {
                    stringResource(R.string.service_state_hint_stopped)
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (live.serviceRunning) {
                    Button(onClick = onStop) { Text(stringResource(R.string.service_stop)) }
                } else {
                    Button(onClick = onStart) { Text(stringResource(R.string.service_start)) }
                }
                OutlinedButton(onClick = onWake, enabled = live.serviceRunning) {
                    Text(stringResource(R.string.ov_wake_now))
                }
            }
        }

        if (!permissions.allGranted) {
            SectionCard(title = stringResource(R.string.perm_title)) {
                PermissionRow(
                    title = stringResource(R.string.perm_camera),
                    why = stringResource(R.string.perm_camera_why),
                    granted = permissions.camera,
                    onGrant = onRequestCamera,
                )
                PermissionRow(
                    title = stringResource(R.string.perm_write_settings),
                    why = stringResource(R.string.perm_write_settings_why),
                    granted = permissions.writeSettings,
                    onGrant = onRequestWriteSettings,
                )
                PermissionRow(
                    title = stringResource(R.string.perm_notifications),
                    why = stringResource(R.string.perm_notifications_why),
                    granted = permissions.notifications,
                    onGrant = onRequestNotifications,
                )
                if (permissions.rootAvailable) {
                    TextButton(onClick = onGrantWithRoot) {
                        Text(stringResource(R.string.perm_grant_root))
                    }
                }
            }
        }

        SectionCard(title = stringResource(R.string.ov_section_live)) {
            MeterRow(
                label = stringResource(R.string.ov_room_light),
                valueText = stringResource(R.string.unit_of_255, live.avgLuma),
                fraction = live.avgLuma / 255f,
                hint = stringResource(R.string.ov_room_light_hint),
                highlight = true,
            )
            MeterRow(
                label = stringResource(R.string.ov_brightness),
                valueText = stringResource(R.string.unit_of_255, live.appliedBrightness),
                fraction = live.appliedBrightness / 255f,
                hint = stringResource(R.string.ov_brightness_target, live.targetBrightness),
                highlight = true,
            )
            val shownChange = if (config.wakeZoneEnabled) live.zoneDiffPercent else live.diffPercent
            MeterRow(
                label = stringResource(R.string.ov_change),
                valueText = "%.2f %%".format(shownChange),
                fraction = shownChange / 100f,
                hint = stringResource(
                    R.string.ov_change_hint,
                    "%.1f".format(config.motionRatio * 100f),
                    "%.1f".format(config.wakeMotionRatio * 100f),
                ),
            )
            StatRow(
                stringResource(R.string.ov_movement),
                movementText(live),
                highlight = live.motionNearby || live.motion,
            )
            StatRow(
                stringResource(R.string.ov_screen),
                if (live.screenOn) {
                    stringResource(R.string.ov_screen_on)
                } else {
                    stringResource(R.string.ov_screen_off)
                },
            )
            StatRow(stringResource(R.string.ov_proximity), proximityText(live, config))
            if (live.brightnessWriteFailed) {
                Hint(stringResource(R.string.ov_brightness_failed))
            }
        }

        SectionCard(title = stringResource(R.string.ov_section_counters)) {
            StatRow(stringResource(R.string.ov_camera), cameraStateText(live))
            StatRow(stringResource(R.string.ov_frames), live.frameCount.toString())
            StatRow(stringResource(R.string.ov_restarts), live.cameraRestarts.toString())
            StatRow(stringResource(R.string.ov_wakes), live.wakeCount.toString())
            StatRow(
                stringResource(R.string.ov_last_wake),
                if (live.lastWakeAt > 0) {
                    clock.format(Date(live.lastWakeAt))
                } else {
                    stringResource(R.string.ov_never)
                },
            )
            StatRow(stringResource(R.string.ov_light_change), live.lightChanges.toString())
            StatRow(stringResource(R.string.ov_uptime), uptimeText(live.startedElapsed, now))
            if (live.quietActive) {
                StatRow(stringResource(R.string.ov_quiet_active), "✓", highlight = true)
            }
            if (config.haEnabled && live.haStatus.isNotBlank()) {
                StatRow(stringResource(R.string.ov_ha_status), live.haStatus)
            }
        }

        FramePreviewCard()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun movementText(live: ScreenVigilState.Live): String = when {
    live.motionNearby -> stringResource(R.string.ov_movement_near)
    live.motionVerdict == MotionDetector.Verdict.LIGHT_CHANGE ->
        stringResource(R.string.ov_movement_light)

    live.motionVerdict == MotionDetector.Verdict.TOO_DARK ->
        stringResource(R.string.ov_movement_dark)

    live.motionVerdict == MotionDetector.Verdict.NO_REFERENCE ->
        stringResource(R.string.ov_movement_waiting)

    live.motion -> stringResource(R.string.ov_movement_far)
    else -> stringResource(R.string.ov_movement_none)
}

@Composable
private fun proximityText(live: ScreenVigilState.Live, config: Config): String = when {
    !live.proximityAvailable -> stringResource(R.string.ov_proximity_absent)
    !config.proximityWake -> stringResource(R.string.ov_proximity_off)
    live.proximityNear -> stringResource(R.string.ov_proximity_near)
    else -> stringResource(R.string.ov_proximity_far)
}

@Composable
private fun PermissionRow(title: String, why: String, granted: Boolean, onGrant: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (granted) {
                    stringResource(R.string.perm_granted)
                } else {
                    stringResource(R.string.perm_missing)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (granted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        if (!granted) {
            Hint(why)
            TextButton(onClick = onGrant) { Text(stringResource(R.string.perm_grant)) }
        }
    }
}

@Composable
private fun FramePreviewCard() {
    val frame by ScreenVigilState.preview.collectAsState()
    val current = frame ?: return
    if (current.width <= 0 || current.height <= 0) return
    SectionCard(title = stringResource(R.string.ov_preview)) {
        val bitmap = Bitmap.createBitmap(
            current.pixels,
            current.width,
            current.height,
            Bitmap.Config.ARGB_8888,
        )
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.None,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(current.width.toFloat() / current.height.toFloat()),
        )
        Hint(stringResource(R.string.ov_preview_hint))
    }
}

@Composable
private fun cameraStateText(live: ScreenVigilState.Live): String {
    val name = when (live.cameraState) {
        CameraController.State.IDLE -> stringResource(R.string.cam_idle)
        CameraController.State.OPENING -> stringResource(R.string.cam_opening)
        CameraController.State.RUNNING -> stringResource(R.string.cam_running)
        CameraController.State.ERROR -> stringResource(R.string.cam_error)
        CameraController.State.RESTARTING -> stringResource(R.string.cam_restarting)
    }
    val extra = live.cameraMessage?.takeIf { it.isNotBlank() }
    return if (extra != null) "$name · $extra" else name
}

private fun uptimeText(startedAt: Long, now: Long): String {
    if (startedAt <= 0) return "—"
    val seconds = (now - startedAt) / 1000
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%d:%02d:%02d".format(h, m, s)
}
