# Terms of Use

**Effective Date:** 2026-08-11

These Terms of Use ("Terms") govern your use of **WakeLux** (the "App"), developed by vasmarfas ("we," "our," or "us"). By downloading, installing, or using the App, you agree to these Terms. If you don't agree, don't use the App.

## 1. What the App Does

WakeLux is an Android app that uses the device camera to estimate room brightness and detect movement, and uses that to adjust screen brightness and wake the screen. It runs entirely on your device. It has no account system, no backend service, and no cloud component of ours. An optional feature lets you connect the App to a Home Assistant server that you configure and control yourself — see the [Privacy Policy](privacy-policy.md) for what that feature transmits and to whom.

## 2. License to Use the App

The App's source code is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE), with additional terms in [NOTICE.md](NOTICE.md). In short: you may install and use the App for free for any noncommercial purpose, including running it on your own devices. Commercial use — selling the App, bundling it into a paid product or service, or using it as part of a business's operations — is not licensed and requires a separate written agreement with us.

If you obtained the App from an app store (such as Google Play), your use is also subject to that store's own terms of service, which apply alongside these Terms.

## 3. Acceptable Use

Forking the project's repository to prepare and submit a pull request is the normal way to contribute, and these Terms don't restrict it. Beyond that, you agree not to:

- rebrand a copy or a modified version of the App — give it a different name, icon, or application ID meant to present it as an independent app, or keep the WakeLux name on one — and distribute either to other users;
- publish or ship a modified version of the App to other users as a standalone alternative (an app store listing, a separate release, a built APK handed out to people who aren't contributing to it) rather than as a contribution in progress;
- represent a modified or unofficial version of the App as being the WakeLux project, or as being endorsed by, affiliated with, or supported by us;
- use the App for any commercial purpose without our prior written permission;
- attempt to circumvent, disable, or interfere with any part of the App's operation in a way intended to harm other users or third-party systems (for example, systems you connect the App to).

Improving WakeLux is welcome and encouraged — through pull requests, patches, and issues submitted to the official repository. See [NOTICE.md](NOTICE.md) for exactly what that means for your contribution.

## 4. Third-Party Integrations

If you enable the optional Home Assistant integration, you are solely responsible for the server you connect the App to, the token you use, and anything that server or its automations do with the values the App sends it. We do not operate, control, or have access to your Home Assistant instance, and we are not responsible for its behavior, availability, or security.

## 5. Camera and System Permissions

The App requires camera access and permission to modify system display settings to function as intended. By granting these permissions, you acknowledge that the App will read camera frames continuously while its service is running (processed only in memory, never stored — see the [Privacy Policy](privacy-policy.md)) and will change your device's screen brightness and screen-off timeout automatically.

## 6. No Warranty

The App is provided **"as is"** and **"as available,"** without warranty of any kind, express or implied, including but not limited to warranties of merchantability, fitness for a particular purpose, and noninfringement. We do not warrant that the App will be uninterrupted, error-free, or that it will correctly detect brightness or movement in every environment or on every device. You are responsible for verifying that the App's behavior — including automatic screen brightness and wake-up — is suitable for your specific setup before relying on it.

## 7. Limitation of Liability

To the fullest extent permitted by applicable law, we will not be liable for any indirect, incidental, special, consequential, or punitive damages, or any loss of data, arising out of or related to your use of, or inability to use, the App — including damages resulting from changes the App makes to your device's system settings, or from any device or automation connected through the optional Home Assistant integration.

## 8. Changes to These Terms

We may update these Terms from time to time. Material changes will be reflected by a new Effective Date at the top of this document and published together with the App release that introduces them. Continuing to use the App after an update means you accept the revised Terms.

## 9. Termination

Your rights under these Terms and under the [License](LICENSE) end automatically if you violate them, subject to the cure period described in the License. You may stop using the App and uninstall it at any time.

## 10. Contact

Questions about these Terms can be sent through the project's issue tracker.

**Developer:** vasmarfas
**Contact:** https://github.com/vasmarfas/WakeLux/issues
