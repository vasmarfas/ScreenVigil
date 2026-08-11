# Privacy Policy

**Effective Date:** 2026-08-11

**ScreenVigil** ("we," "our," or "us") is committed to protecting your privacy. This Privacy Policy explains how we collect, use, and safeguard your information when you use our Android application (the "App").

## 1. Information Collection and Use

### Camera (screen brightness and motion detection)

The App uses the device's front (or back) camera to estimate room brightness and detect movement, replacing the ambient light sensor most tablets don't have.

- **Purpose:** Once a second the App reads one preview frame and computes two numbers from it — the average brightness of the image and the share of pixels that changed since the previous frame. These numbers drive the screen brightness and the screen wake-up logic.
- **Privacy:** Frames are processed entirely in volatile memory (RAM) on your device. They are **never** saved to storage, encoded into an image or video file, displayed outside the App, or transmitted anywhere. Each frame is overwritten by the next one within about a second. The App's debug preview screen, if you open it, renders the current frame in memory for as long as that screen is visible and discards it when you leave.

### App settings

Your configuration — brightness thresholds, motion thresholds, schedule, camera selection and, if you choose to fill them in, a Home Assistant server address and access token — is stored in the App's private, on-device preferences. It is not accessible to other apps and is not synced anywhere unless you enable the optional Home Assistant integration described below.

### Diagnostics log (optional, off by default)

If you turn on the CSV diagnostics log in Settings, the App writes numeric measurements (timestamp, room brightness, screen brightness, percentage of change, movement flag) to a folder inside the App's own storage. This file never leaves the device on its own and is deleted automatically when the App is uninstalled.

## 2. Third-Party Services

The App does not use Firebase, Google Analytics, Crashlytics, advertising SDKs, or any other third-party analytics or tracking service. Nothing about your usage of the App is reported to us or to anyone else.

### Optional Home Assistant integration

If you switch this on and enter your own Home Assistant server address and a long-lived access token, the App sends plain numeric values to that address over HTTP: room brightness, screen brightness, motion state, screen on/off state, and basic service statistics (frame count, camera restarts, uptime). No images or camera data are ever included. The server you send this to is one you configure yourself — typically a device on your own local network — and its operator (usually you) is the only party that receives this data; we do not.

The source code of the App is available for review on GitHub.

## 3. Permissions

The App requests the following permissions and uses them only for the stated purpose:

- **Camera** — to read preview frames as described in Section 1. No photos or video are captured or stored.
- **Modify system settings** (`WRITE_SETTINGS`) — to set the screen brightness and the system screen-off timeout.
- **Wake lock** — to briefly turn the screen on when movement is detected.
- **Post notifications** — Android requires a persistent notification while the background service runs; the App uses it to show live status.
- **Receive boot completed** — to restart the service automatically after the device reboots, if you enabled that option.
- **Internet** and **access network state** — used only when you enable the optional Home Assistant integration; otherwise unused.
- **Foreground service (camera type)** — required by Android to keep the camera open while the screen is off.

## 4. Children's Privacy

The App is not directed at children and does not knowingly collect personal information from anyone, including children under 13.

## 5. Data Retention and Security

We do not operate any server that stores your data, and the App does not transmit personal data to us. Everything the App keeps — settings and, optionally, the diagnostics log — stays on your device and is removed when you uninstall the App. If you enable the Home Assistant integration, retention of the values sent is governed by your own Home Assistant instance, not by us.

## 6. Your Rights

Because we do not collect or hold any personal data about you, there is nothing for us to access, export, or delete on your behalf. Uninstalling the App removes all data it stored locally. If you enabled the Home Assistant integration, you can delete the created entities directly in your own Home Assistant instance.

## 7. Changes to This Privacy Policy

We may update this Privacy Policy from time to time. Material changes will be reflected by a new Effective Date at the top of this document and published together with the App release that introduces them.

## 8. Contact Us

If you have any questions or concerns about this Privacy Policy, please contact us.

**Developer:** vasmarfas
**Contact:** https://github.com/vasmarfas/ScreenVigil/issues
