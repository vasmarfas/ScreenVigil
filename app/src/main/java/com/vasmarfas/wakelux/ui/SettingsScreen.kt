package com.vasmarfas.wakelux.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vasmarfas.wakelux.Config
import com.vasmarfas.wakelux.Prefs
import com.vasmarfas.wakelux.R

@Composable
fun SettingsScreen(
    config: Config,
    prefs: Prefs,
    rootAvailable: Boolean,
    proximityAvailable: Boolean,
    haStatus: String,
    onTestHa: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val seconds = stringResource(R.string.unit_seconds)
    val minutes = stringResource(R.string.unit_minutes)
    val ms = stringResource(R.string.unit_ms)
    val percent = stringResource(R.string.unit_percent)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
    ) {
        SectionCard(stringResource(R.string.sec_features)) {
            SwitchRow(
                label = stringResource(R.string.set_autobrightness),
                subtitle = stringResource(R.string.set_autobrightness_hint),
                checked = config.autoBrightness,
            ) { prefs.putBoolean(Prefs.KEY_AUTO_BRIGHTNESS, it) }
            SwitchRow(
                label = stringResource(R.string.set_motion),
                subtitle = stringResource(R.string.set_motion_hint),
                checked = config.motionEnabled,
            ) { prefs.putBoolean(Prefs.KEY_MOTION, it) }
        }

        SectionCard(stringResource(R.string.sec_brightness)) {
            IntSliderRow(
                label = stringResource(R.string.set_luma_min),
                value = config.lumaMin,
                range = 0..254,
                hint = stringResource(R.string.set_luma_min_hint),
            ) { prefs.putInt(Prefs.KEY_LUMA_MIN, it.coerceAtMost(config.lumaMax - 1)) }
            IntSliderRow(
                label = stringResource(R.string.set_luma_max),
                value = config.lumaMax,
                range = 1..255,
                hint = stringResource(R.string.set_luma_max_hint),
            ) { prefs.putInt(Prefs.KEY_LUMA_MAX, it.coerceAtLeast(config.lumaMin + 1)) }
            IntSliderRow(
                label = stringResource(R.string.set_brightness_min),
                value = config.brightnessMin,
                range = 1..255,
                hint = stringResource(R.string.set_brightness_min_hint),
            ) { prefs.putInt(Prefs.KEY_BRIGHTNESS_MIN, it) }
            IntSliderRow(
                label = stringResource(R.string.set_brightness_max),
                value = config.brightnessMax,
                range = 1..255,
            ) { prefs.putInt(Prefs.KEY_BRIGHTNESS_MAX, it) }
            FloatSliderRow(
                label = stringResource(R.string.set_alpha),
                value = config.alpha,
                range = 0.01f..1f,
                hint = stringResource(R.string.set_alpha_hint),
            ) { prefs.putFloat(Prefs.KEY_ALPHA, it) }
            IntSliderRow(
                label = stringResource(R.string.set_hysteresis),
                value = config.hysteresis,
                range = 1..40,
                hint = stringResource(R.string.set_hysteresis_hint),
            ) { prefs.putInt(Prefs.KEY_HYSTERESIS, it) }
        }

        SectionCard(stringResource(R.string.sec_motion)) {
            IntSliderRow(
                label = stringResource(R.string.set_pixel_threshold),
                value = config.pixelThreshold,
                range = 1..100,
                hint = stringResource(R.string.set_pixel_threshold_hint),
            ) { prefs.putInt(Prefs.KEY_PIXEL_THRESHOLD, it) }
            FloatSliderRow(
                label = stringResource(R.string.set_motion_ratio),
                value = config.motionRatio * 100f,
                range = 0.1f..30f,
                format = { "%.1f %%".format(it) },
                hint = stringResource(R.string.set_motion_ratio_hint),
            ) { prefs.putFloat(Prefs.KEY_MOTION_RATIO, it / 100f) }
            IntSliderRow(
                label = stringResource(R.string.set_confirm_frames),
                value = config.confirmFrames,
                range = 1..10,
                hint = stringResource(R.string.set_confirm_frames_hint),
            ) { prefs.putInt(Prefs.KEY_CONFIRM_FRAMES, it) }
            FloatSliderRow(
                label = stringResource(R.string.set_light_change_ratio),
                value = config.lightChangeRatio * 100f,
                range = 10f..100f,
                format = { "%.0f %%".format(it) },
                hint = stringResource(R.string.set_light_change_ratio_hint),
            ) { prefs.putFloat(Prefs.KEY_LIGHT_CHANGE_RATIO, it / 100f) }
            IntSliderRow(
                label = stringResource(R.string.set_dark_cutoff),
                value = config.darkCutoffLuma,
                range = 0..60,
                hint = stringResource(R.string.set_dark_cutoff_hint),
            ) { prefs.putInt(Prefs.KEY_DARK_CUTOFF, it) }
        }

        SectionCard(stringResource(R.string.sec_wake)) {
            FloatSliderRow(
                label = stringResource(R.string.set_wake_ratio),
                value = config.wakeMotionRatio * 100f,
                range = 0.5f..60f,
                format = { "%.1f %%".format(it) },
                hint = stringResource(R.string.set_wake_ratio_hint),
            ) { prefs.putFloat(Prefs.KEY_WAKE_RATIO, it / 100f) }
            IntSliderRow(
                label = stringResource(R.string.set_wake_confirm),
                value = config.wakeConfirmFrames,
                range = 1..10,
            ) { prefs.putInt(Prefs.KEY_WAKE_CONFIRM, it) }
            SwitchRow(
                label = stringResource(R.string.set_wake_zone),
                subtitle = stringResource(R.string.set_wake_zone_hint),
                checked = config.wakeZoneEnabled,
            ) { prefs.putBoolean(Prefs.KEY_WAKE_ZONE, it) }
            if (config.wakeZoneEnabled) {
                IntSliderRow(
                    label = stringResource(R.string.set_wake_zone_size),
                    value = (config.wakeZoneFraction * 100f).toInt(),
                    range = 10..90,
                    suffix = percent,
                    hint = stringResource(R.string.set_wake_zone_size_hint),
                ) { prefs.putFloat(Prefs.KEY_WAKE_ZONE_SIZE, it / 100f) }
            }
            SwitchRow(
                label = stringResource(R.string.set_proximity),
                subtitle = if (proximityAvailable) {
                    stringResource(R.string.set_proximity_hint)
                } else {
                    stringResource(R.string.ov_proximity_absent)
                },
                checked = config.proximityWake,
                enabled = proximityAvailable,
            ) { prefs.putBoolean(Prefs.KEY_PROXIMITY, it) }
            SwitchRow(
                label = stringResource(R.string.set_keep_awake),
                subtitle = stringResource(R.string.set_keep_awake_hint),
                checked = config.keepAwakeOnMotion,
            ) { prefs.putBoolean(Prefs.KEY_KEEP_AWAKE, it) }
            IntSliderRow(
                label = stringResource(R.string.set_wakelock_ms),
                value = config.wakeLockMs,
                range = 200..10_000,
                suffix = ms,
                hint = stringResource(R.string.set_wakelock_ms_hint),
            ) { prefs.putInt(Prefs.KEY_WAKELOCK_MS, it) }
        }

        SectionCard(stringResource(R.string.sec_screen)) {
            SwitchRow(
                label = stringResource(R.string.set_manage_timeout),
                checked = config.manageScreenOffTimeout,
            ) { prefs.putBoolean(Prefs.KEY_MANAGE_TIMEOUT, it) }
            IntSliderRow(
                label = stringResource(R.string.set_screen_timeout),
                value = config.screenOffTimeoutMs / 1000,
                range = 10..600,
                suffix = seconds,
                hint = stringResource(R.string.set_screen_timeout_hint),
            ) { prefs.putInt(Prefs.KEY_SCREEN_TIMEOUT, it * 1000) }
        }

        SectionCard(stringResource(R.string.sec_quiet)) {
            SwitchRow(
                label = stringResource(R.string.set_quiet_enabled),
                checked = config.quietEnabled,
            ) { prefs.putBoolean(Prefs.KEY_QUIET_ENABLED, it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    TextFieldRow(
                        label = stringResource(R.string.set_quiet_start),
                        value = formatMinutes(config.quietStartMin),
                    ) { text -> parseMinutes(text)?.let { prefs.putInt(Prefs.KEY_QUIET_START, it) } }
                }
                Column(modifier = Modifier.weight(1f)) {
                    TextFieldRow(
                        label = stringResource(R.string.set_quiet_end),
                        value = formatMinutes(config.quietEndMin),
                    ) { text -> parseMinutes(text)?.let { prefs.putInt(Prefs.KEY_QUIET_END, it) } }
                }
            }
            IntSliderRow(
                label = stringResource(R.string.set_quiet_max),
                value = config.quietBrightnessMax,
                range = 1..255,
            ) { prefs.putInt(Prefs.KEY_QUIET_MAX, it) }
            SwitchRow(
                label = stringResource(R.string.set_quiet_no_wake),
                checked = config.quietNoWake,
            ) { prefs.putBoolean(Prefs.KEY_QUIET_NO_WAKE, it) }
        }

        SectionCard(stringResource(R.string.sec_camera)) {
            Hint(stringResource(R.string.set_facing))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = config.cameraFacing == Prefs.FACING_FRONT,
                    onClick = { prefs.putInt(Prefs.KEY_FACING, Prefs.FACING_FRONT) },
                    label = { Text(stringResource(R.string.set_facing_front)) },
                )
                FilterChip(
                    selected = config.cameraFacing == Prefs.FACING_BACK,
                    onClick = { prefs.putInt(Prefs.KEY_FACING, Prefs.FACING_BACK) },
                    label = { Text(stringResource(R.string.set_facing_back)) },
                )
            }
            Spacer(Modifier.height(8.dp))
            IntSliderRow(
                label = stringResource(R.string.set_frame_interval),
                value = config.frameIntervalMs,
                range = 200..10_000,
                suffix = ms,
                hint = stringResource(R.string.set_frame_interval_hint),
            ) { prefs.putInt(Prefs.KEY_FRAME_INTERVAL, it) }
            IntSliderRow(
                label = stringResource(R.string.set_watchdog),
                value = config.watchdogTimeoutMs / 1000,
                range = 5..300,
                suffix = seconds,
            ) { prefs.putInt(Prefs.KEY_WATCHDOG, it * 1000) }
            SwitchRow(
                label = stringResource(R.string.set_ae_lock),
                subtitle = stringResource(R.string.set_ae_lock_hint),
                checked = config.aeLock,
            ) { prefs.putBoolean(Prefs.KEY_AE_LOCK, it) }
            IntSliderRow(
                label = stringResource(R.string.set_exposure_comp),
                value = config.exposureCompensation,
                range = -6..6,
            ) { prefs.putInt(Prefs.KEY_EXPOSURE_COMP, it) }
        }

        SectionCard(stringResource(R.string.sec_root)) {
            if (!rootAvailable) Hint(stringResource(R.string.set_root_unavailable))
            SwitchRow(
                label = stringResource(R.string.set_root_enabled),
                checked = config.rootEnabled,
                enabled = rootAvailable,
            ) { prefs.putBoolean(Prefs.KEY_ROOT_ENABLED, it) }
            SwitchRow(
                label = stringResource(R.string.set_root_fallback),
                checked = config.rootSettingsFallback,
                enabled = rootAvailable && config.rootEnabled,
            ) { prefs.putBoolean(Prefs.KEY_ROOT_FALLBACK, it) }
            SwitchRow(
                label = stringResource(R.string.set_root_force_off),
                subtitle = stringResource(R.string.set_root_force_off_hint),
                checked = config.rootForceScreenOff,
                enabled = rootAvailable && config.rootEnabled,
            ) { prefs.putBoolean(Prefs.KEY_ROOT_FORCE_OFF, it) }
            if (config.rootForceScreenOff) {
                IntSliderRow(
                    label = stringResource(R.string.set_root_force_off_delay),
                    value = config.rootForceScreenOffDelayMs / 1000,
                    range = 5..600,
                    suffix = seconds,
                ) { prefs.putInt(Prefs.KEY_ROOT_FORCE_OFF_DELAY, it * 1000) }
            }
            SwitchRow(
                label = stringResource(R.string.set_root_auto_grant),
                checked = config.rootAutoGrant,
                enabled = rootAvailable && config.rootEnabled,
            ) { prefs.putBoolean(Prefs.KEY_ROOT_AUTO_GRANT, it) }
        }

        SectionCard(stringResource(R.string.sec_ha)) {
            Hint(stringResource(R.string.set_ha_privacy))
            SwitchRow(
                label = stringResource(R.string.set_ha_enabled),
                checked = config.haEnabled,
            ) { prefs.putBoolean(Prefs.KEY_HA_ENABLED, it) }
            TextFieldRow(
                label = stringResource(R.string.set_ha_url),
                value = config.haUrl,
            ) { prefs.putString(Prefs.KEY_HA_URL, it) }
            TextFieldRow(
                label = stringResource(R.string.set_ha_token),
                value = config.haToken,
            ) { prefs.putString(Prefs.KEY_HA_TOKEN, it) }
            TextFieldRow(
                label = stringResource(R.string.set_ha_prefix),
                value = config.haPrefix,
            ) { prefs.putString(Prefs.KEY_HA_PREFIX, it) }
            IntSliderRow(
                label = stringResource(R.string.set_ha_interval),
                value = config.haIntervalSec,
                range = 5..600,
                suffix = seconds,
            ) { prefs.putInt(Prefs.KEY_HA_INTERVAL, it) }
            Hint(stringResource(R.string.set_ha_entities, config.haPrefix))
            OutlinedButton(onClick = onTestHa) { Text(stringResource(R.string.set_ha_test)) }
            StatRow(
                stringResource(R.string.ha_status_label),
                haStatus.ifBlank { stringResource(R.string.ha_status_none) },
                highlight = haStatus.isNotBlank() && haStatus != "ok",
            )
            haErrorHint(haStatus)?.let { Hint(it) }
        }

        SectionCard(stringResource(R.string.sec_service)) {
            SwitchRow(
                label = stringResource(R.string.set_boot),
                checked = config.startOnBoot,
            ) { prefs.putBoolean(Prefs.KEY_BOOT, it) }
            TextFieldRow(
                label = stringResource(R.string.set_cmd_token),
                value = config.commandToken,
            ) { prefs.putString(Prefs.KEY_CMD_TOKEN, it) }
            Hint(stringResource(R.string.set_cmd_token_hint))
            SwitchRow(
                label = stringResource(R.string.set_diag_file),
                checked = config.diagFileLog,
            ) { prefs.putBoolean(Prefs.KEY_DIAG_FILE, it) }
            IntSliderRow(
                label = stringResource(R.string.set_diag_history),
                value = config.diagHistoryMin,
                range = 5..360,
                suffix = minutes,
            ) { prefs.putInt(Prefs.KEY_DIAG_HISTORY, it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onExport) { Text(stringResource(R.string.set_export)) }
                TextButton(onClick = onImport) { Text(stringResource(R.string.set_import)) }
                TextButton(onClick = { prefs.resetAll() }) {
                    Text(stringResource(R.string.set_reset))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun haErrorHint(status: String): String? = when {
    status.isBlank() || status == "ok" -> null
    status.contains("401") || status.contains("403") -> stringResource(R.string.ha_hint_401)
    status.contains("404") -> stringResource(R.string.ha_hint_404)
    status.startsWith("HTTP") -> null
    else -> stringResource(R.string.ha_hint_network)
}
