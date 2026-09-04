# Codex Meter

Codex Meter is an unofficial open-source Android phone app for viewing the Codex
allowance attached to a signed-in ChatGPT account.

The application lives in [`android/`](android/): `app/` contains the native UI,
widgets, notifications, and networking; `shared/` contains the pure-Java usage
logic. Documentation, release notes, and CI entrypoints live at the repository root.
The app talks directly to ChatGPT/Codex endpoints and stores credentials only
on-device in Android Keystore. There is no backend.

## Android — Version 2.8.0

Version 2.8.0 adapts to Free-tier monthly Codex limits when a paid plan expires, adds opt-in diagnostic log tracing/export, and declutters usage-history analytics with customizable highlights. This stable release consolidates the 2.8.0-alpha.1 channel build; alpha remains opt-in under Settings → Updates → Update channel.

### Live countdowns

Samsung lock-screen widgets can display the remaining time until each usage window resets. The countdown is driven locally by Android `Chronometer` views using the reset timestamp already cached from the usage response; it does not repeatedly contact the server merely to update seconds or minutes.

### Live usage monitor

Settings includes an optional, user-started live usage monitor that runs only until the next available usage reset. It shows the real five-hour and weekly allowance values, marks a missing window as unavailable, and refreshes whenever the app receives new usage data. Android 16 can promote the notification as a Live Update, while compatible Samsung firmware can also surface it in the Now Bar. The monitor can be stopped at any time and is cleared when the user signs out.

### Reset alerts

Users can choose silent, notification-sound, or alarm-sound alerts for the five-hour limit, weekly limit, or both. Alerts can be conditional on the most recently observed allowance being below a selected threshold. Android schedules the notification for the cached reset time and performs a normal background refresh after the alert fires.

### New model alerts

Settings → Notifications → **New Codex model alerts** announces newly available
models for the connected account, with the same alert style as resets. The first
successful catalog check is silent; later checks deduplicate by model ID.

### Widget surfaces

The app includes:

- Responsive home-screen widgets with ring, four-dial, and battery-list layouts, plus Adaptive / Dials / Progress bars layout preference and drag-reorderable meter slots (Codex 5-hour/weekly, next reset, reset credits).
- Both-window, five-hour-only, and weekly-only configurations (legacy metric mode; meters checklist supersedes this when customized).
- Optional reset-credit inventory, expiration, and redemption controls.
- Transparent through opaque backgrounds, including a Background off toggle and three One UI-style opacity steps.
- Samsung One UI presentation throughout the dashboard, settings, and widget configuration surfaces.
- Samsung lock/AOD providers for both usage windows together or dedicated five-hour and weekly views.
- High-resolution supersampled lock-screen geometry with native Android text overlays.
- Optional live time-to-reset labels on supported lock-screen hosts.

## Authentication and data handling

- Browser-based ChatGPT sign-in using OAuth authorization code + PKCE and a localhost loopback callback.
- Access-token refresh with refresh-token rotation preservation.
- Android Keystore AES-GCM encryption for locally stored tokens.
- Usage and reset-credit retrieval from the ChatGPT backend routes used by Codex.
- No analytics, advertisements, WebView, or application-level relay server.

## Compatibility

- Minimum Android 8.0 (API 26)
- Compile SDK Android 16 (API 36)
- Target Android 16 (API 36)
- Universal DEX APK with no native ABI libraries
- Standard Android home-screen widgets
- Private Samsung One UI lock/AOD integration on compatible Galaxy firmware

## Build from source

### Android

See [`android/README.md`](android/README.md). The Android project uses Gradle with the OneUI-Design and oneui-icons libraries so its dashboards use Samsung-style SESL components, typography, and iconography.

Requirements:

- JDK 17 or newer
- Android SDK Platform 36
- Android Build Tools 36.x
- `ANDROID_SDK_ROOT` or `ANDROID_HOME` configured
- A GitHub Packages token in `GH_ACCESS_TOKEN` (with `read:packages`) and your username in `GH_USERNAME` when the OneUI-Design dependencies are not already cached

From the repository root:

```bash
./run-tests.sh
./build.sh
```

Or from `android/` directly. `build.sh` assembles the release APK with Gradle and signs it with a local development key under `android/.local-signing/`. This locally signed APK will not install over the distributed release build. Artifacts land in `android/dist/`.

## Releases

Creating a `v*` tag that matches the Gradle `versionName` in `android/app/build.gradle.kts` (for example `v2.6.5`) runs the full CI pipeline and publishes the signed phone APK and its SHA-256 checksum to GitHub Releases. CI authenticates and decrypts the persistent PKCS#12 release keystore `android/ci/release-keystore.p12.enc` (alias `codexmeter`) using the `ANDROID_SIGNING_PASSWORD` repository Actions secret, so every release is signed with the same certificate and installs in place over previous releases. Release notes are taken from the root `CHANGELOG.md`.

## Platform stability

The ChatGPT usage and reset-credit routes and Samsung's lock-screen metadata are implementation details rather than stable third-party Android SDK contracts. OpenAI or Samsung may change eligibility, routing, response fields, host behavior, or private metadata.

OpenAI, ChatGPT, Codex, Samsung, Galaxy, One UI, and related marks belong to their respective owners. This project is not affiliated with or endorsed by OpenAI or Samsung.
