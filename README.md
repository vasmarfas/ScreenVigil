# ScreenVigil

Camera-driven auto-brightness and wake-on-approach for Android tablets that have no ambient light sensor.

[Русская версия](README.ru.md)

**❤️ Support the project:** if you'd like to support further development, see [`SUPPORT.md`](SUPPORT.md).

## The problem

Cheap tablets are the obvious choice for a wall-mounted Home Assistant dashboard, and a lot of them ship without an ambient light sensor. Android hides the auto-brightness toggle entirely when `android.sensor.light` is missing, so the screen stays at whatever level you left it: too dim during the day, blinding at night. The second annoyance is the opposite of a screensaver — you want the panel dark most of the time and lit when you walk up to it.

ScreenVigil solves both from the front camera. Once a second it grabs one small preview frame, averages the luma plane to estimate room brightness, and diffs it against the previous frame to detect movement. Two numbers per second are enough to drive `Settings.System.SCREEN_BRIGHTNESS` and a short wake lock.

The camera stream never leaves `onPreviewFrame`. Frames aren't stored, encoded, or uploaded — the buffer is reused and overwritten every second. With the optional Home Assistant integration the app sends numbers over HTTP, nothing else.

## Requirements

- Android 6.0 (API 23) or newer
- A usable front or back camera
- `CAMERA` and `WRITE_SETTINGS`
- Root is optional and only unlocks extras listed below

Developed against a Lenovo TB-X103F (MT8163, 1 GB RAM, Android 6.0.1) running 24/7 as a Home Assistant panel.

## Install

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`WRITE_SETTINGS` is an appop, not a runtime permission, so `pm grant` won't help. Either use the button in the app, or grant both over adb — handy for a tablet that is already screwed to a wall:

```bash
adb shell pm grant com.vasmarfas.screenvigil android.permission.CAMERA
adb shell appops set com.vasmarfas.screenvigil WRITE_SETTINGS allow
```

## Setup

Start the service from the first tab. A foreground notification appears with a live readout (`room 125 · screen 176 · change 0.0% · still`) — Android requires the notification, so it may as well be useful.

Then calibrate for your room, which takes a minute:

1. Cover the camera with your hand and read *Room light* on the **Now** tab. That's your dark end.
2. Uncover it and read the value under normal lighting. That's your bright end.
3. Put those two numbers into *Dark room level* and *Bright room level* on the **Settings** tab.

The defaults (10 and 180) are deliberately wide. On the test tablet the real range turned out to be 3–130, meaning the screen would never have reached full brightness with the stock upper bound. Auto-exposure compresses the range — that's expected, and the reason calibration is per-room rather than a fixed lux table.

Brightness follows a straight line between the two points. If you want a different response — flat during the day, aggressive at dusk — the **Curve** tab lets you drag arbitrary points.

## Wake-up: presence vs. proximity

A single motion threshold is useless in a living room. Anything sensitive enough to catch you walking past also fires when someone shifts on the sofa, and the panel lights up all evening. ScreenVigil keeps two thresholds over the same frame diff:

| Setting | Effect | Default |
|---|---|---|
| Movement counts as presence at | Keeps an already-lit screen awake, drives the Home Assistant motion sensor | 2 % of the frame |
| Wake the screen only above | The only thing that turns the screen on | 12 % of the frame |

The separation works because frame coverage is a decent proxy for distance: someone half a metre from the camera occupies a large share of a 160×120 frame, someone across the room occupies a few percent. Measured on the test setup, sofa-scale movement sat under 1 % while an approach cleared the 12 % threshold comfortably.

Tuning is empirical and the **Now** tab makes it easy — *Change in the picture* shows the live percentage. Move the way you normally do while seated, note the number, walk up to the tablet, note it again, and put the wake threshold between them.

If percentages alone can't separate the two (a corridor in view, a TV in frame), enable *React only in the middle of the picture*. Wake decisions then use only a central rectangle of the frame; the debug preview draws its border in green so you can see what counts.

Devices with a proximity sensor can use it as an additional trigger. Most tablets don't have one — the **Now** tab states whether yours does.

One hard limit: in a fully dark room the camera has nothing to work with, so an approach won't be detected. Dim rooms are fine. Sensor noise measured 0.00 % of the frame at the default pixel threshold, which is why the dark cutoff can sit as low as 2 without causing false wakes.

## Home Assistant

The app can POST its numbers to `/api/states`, turning the tablet into a light and presence sensor for the room it hangs in.

1. In Home Assistant open your profile → **Security** → **Long-lived access tokens** → create one. **It must belong to an administrator account** — writing states over the REST API requires admin rights, and a non-admin token fails with `401 Unauthorized` even though it can read fine.
2. In ScreenVigil, fill in the base URL (`http://homeassistant.local:8123`), paste the token, enable the integration and press *Send a test update now*. The result is shown right under the button.

Created entities, with `screenvigil` as the configurable prefix:

| Entity | Meaning |
|---|---|
| `sensor.screenvigil_luma` | room brightness, 0–255 |
| `sensor.screenvigil_brightness` | current screen brightness, 0–255 |
| `binary_sensor.screenvigil_motion` | movement in front of the tablet, `device_class: motion` |
| `binary_sensor.screenvigil_screen` | screen on/off |
| `sensor.screenvigil_status` | camera state; frames, restarts, wake-ups and uptime as attributes |

Updates are sent on an interval and immediately when motion or screen state flips.

Entities created through the REST API are not registered in the entity registry and disappear when Home Assistant restarts, reappearing on the next update. That's a property of `/api/states`, not of the app. If you need them to survive restarts, bridge through MQTT.

## Running on a kiosk

- If your kiosk app manages brightness itself (Fully Kiosk does, under Device Management), turn that off. A window-level `screenBrightness` overrides the system value ScreenVigil writes. Check with `adb shell dumpsys power | grep mScreenBrightnessOverrideFromWindowManager` — it should print `-1`. The Home Assistant companion app doesn't set it.
- The service starts on `BOOT_COMPLETED`. Some Chinese and Lenovo firmwares need the app added to an autostart whitelist; reboot and check whether the notification comes back.
- Screens can't be turned off through a public API, so ScreenVigil writes `SCREEN_OFF_TIMEOUT` (30 s by default) and lets the system do it. The original value is restored when you stop the service.

## Remote control

Every action and setting is reachable over broadcast, which is convenient for scripting or Home Assistant shell commands:

```bash
adb shell am broadcast -a com.vasmarfas.screenvigil.COMMAND --es cmd status
adb shell am broadcast -a com.vasmarfas.screenvigil.COMMAND --es cmd set --es key wake_motion_ratio --es value 0.25
```

`start`, `stop`, `status`, `wake`, `screen_off`, `restart_camera`, `set`, `export`, `import`, `reset`, `csv`. `status` returns JSON with everything the app currently sees. Set a password in the settings to require `--es token <value>` on every command.

Android won't deliver broadcasts to an app that has never been launched or was force-stopped, so open it once after installing.

## Root extras

All optional, all off by default:

- write system settings through `su` when `WRITE_SETTINGS` was never granted
- grant `CAMERA` and `WRITE_SETTINGS` to itself on start
- turn the screen off after N seconds of inactivity instead of waiting for the system timeout, useful when another app holds the screen awake

## Settings

Keys below are what `cmd set` and the exported JSON use.

| Key | Default | Meaning |
|---|---|---|
| `auto_brightness` | true | adjust brightness from the camera |
| `motion_enabled` | true | wake the screen on movement |
| `luma_min` / `luma_max` | 10 / 180 | dark and bright room levels |
| `brightness_min` / `brightness_max` | 10 / 255 | screen brightness bounds |
| `alpha` | 0.15 | exponential smoothing factor |
| `hysteresis` | 5 | minimum change worth writing |
| `curve_enabled` / `curve_points` | false / — | custom brightness curve |
| `pixel_threshold` | 20 | per-pixel luma delta that counts as changed |
| `motion_ratio` | 0.02 | frame share counted as presence |
| `confirm_frames` | 2 | consecutive frames confirming presence |
| `wake_motion_ratio` | 0.12 | frame share required to wake the screen |
| `wake_confirm_frames` | 2 | consecutive frames confirming a wake |
| `wake_zone_enabled` / `wake_zone_size` | false / 0.5 | restrict wake decisions to the centre of the frame |
| `proximity_wake` | false | use the proximity sensor as an extra trigger |
| `light_change_ratio` | 0.5 | above this share it's a light switch, not movement |
| `dark_cutoff_luma` | 2 | ignore movement below this room brightness |
| `screen_off_timeout_ms` | 30000 | system screen-off timeout |
| `manage_screen_off_timeout` | true | whether to touch that timeout at all |
| `keep_awake_on_motion` | true | keep a lit screen awake while presence continues |
| `wakelock_ms` | 1000 | wake lock duration |
| `frame_interval_ms` | 1000 | frame processing interval |
| `watchdog_timeout_ms` | 15000 | camera silence before a forced restart |
| `camera_facing` | 1 | 1 front, 0 back |
| `ae_lock` / `exposure_compensation` | false / 0 | camera exposure control |
| `quiet_enabled`, `quiet_start_min`, `quiet_end_min`, `quiet_brightness_max`, `quiet_no_wake` | false, 1380, 420, 40, false | quiet hours, minutes from midnight |
| `root_enabled`, `root_settings_fallback`, `root_force_screen_off`, `root_force_screen_off_delay_ms`, `root_auto_grant` | false, true, false, 30000, false | root extras |
| `ha_enabled`, `ha_url`, `ha_token`, `ha_prefix`, `ha_interval_sec` | false, "", "", screenvigil, 30 | Home Assistant |
| `start_on_boot` | true | autostart after reboot |
| `command_token` | "" | password for broadcast commands |
| `diag_file_log`, `diag_history_min` | false, 60 | CSV logging and chart history depth |

Changes apply immediately. Switching camera, exposure lock or exposure compensation restarts the camera; everything else doesn't.

## Implementation notes

Camera1 (`android.hardware.Camera`), not CameraX or Camera2. On the target hardware the Camera2 HAL reports `LEGACY`, so Camera2 would be a wrapper around the same code path with more moving parts. The camera opens once, binds preview to an off-screen `SurfaceTexture`, and delivers frames through `setPreviewCallbackWithBuffer` with three reused buffers — no per-frame allocation. Preview size is the smallest the device offers (160×120 here).

One pass over the luma plane produces the average, the changed-pixel count and, when the wake zone is on, the same count restricted to a central rectangle. Frames above 65 536 pixels are subsampled by stride so switching to the back camera can't blow up CPU usage. Measured cost on MT8163: 1–2 % for the app process, plus 8–10 % for `mm-qcamera-daemon`, which is the price of holding a preview open and can't be reduced from the app — the HAL offers no fixed low-fps range.

Waking uses `SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP | ON_AFTER_RELEASE` with a timeout. Deprecated since API 17, and still the only way to wake a screen without root or device-owner privileges.

For 24/7 operation there are three layers of recovery: a watchdog inside the camera thread restarts the pipeline when frames stop, an error callback handles HAL death, and the service watches the controller's state timestamp and rebuilds the whole controller (thread included) if it stops changing for 90 seconds — that last one covers a native call blocking forever, where the in-thread watchdog can't run either. All intervals use `SystemClock.elapsedRealtime()`; wall-clock time jumps by decades when a batteryless device boots before NTP sync, which quietly breaks anything measured with `currentTimeMillis()`.

## Building

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Kotlin, Compose, `minSdk 23`. No image processing library — the whole pipeline is one loop over a `ByteArray`. 53 unit tests cover the frame maths, both motion thresholds, the light-change filter, the brightness curve and quiet hours across midnight.

## Licence

[PolyForm Noncommercial License 1.0.0](LICENSE). Free for personal use, study, and modification; forking to submit a pull request is the normal, welcome way to contribute; no commercial use without a separate agreement. Rebranding a fork or shipping it to other users as an independent app isn't allowed. See [NOTICE.md](NOTICE.md) for the exact terms, [privacy-policy.md](privacy-policy.md) for what the App does with data, and [terms.md](terms.md) for the terms of use.
