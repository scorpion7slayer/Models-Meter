# Models Meter

Models Meter is an independent Android fork of [Codex Meter](https://github.com/BenItBuhner/Codex-Meter), maintained by [Theo (scorpion7slayer)](https://github.com/scorpion7slayer). BenIt Buhner and That Josh Guy are the developers of the original project.

[Models Meter source](https://github.com/scorpion7slayer/Models-Meter) · [Build downloads](https://github.com/scorpion7slayer/Models-Meter/actions/workflows/build-apk.yml)


Models Meter is an unofficial open-source Android phone app for viewing the Codex
allowance attached to a signed-in ChatGPT account.

The application lives in [`android/`](android/): `app/` contains the native UI,
widgets, notifications, and networking; `shared/` contains the pure-Java usage
logic. Documentation, release notes, and CI entrypoints live at the repository root.
The app talks directly to ChatGPT/Codex endpoints and stores credentials only
on-device in Android Keystore. There is no backend.

## Android — Version 1.0.0

Version 1.0.0 introduces the Models Meter identity, account-specific model discovery alerts, a Latest models dashboard card and widget, and updates from this fork. It retains the original project’s quota, reset-credit, and usage-history features.

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

Manually dispatched builds use the fork's persistent signing key from the private Actions secrets `MODELS_METER_KEYSTORE_BASE64` and `MODELS_METER_SIGNING_PASSWORD`. The `ModelsMeter-1.0.0.apk` artifact and `SHA256SUMS.txt` are retained for 30 days. Pull-request builds use a disposable test key and are not an update channel. Explicitly requested `v*` tags matching the Gradle version also publish a release, using notes from `CHANGELOG.md`. The updater reads only [this fork's releases](https://github.com/scorpion7slayer/Models-Meter/releases).

The application ID is `dev.scorpion7slayer.modelsmeter`. Models Meter installs alongside Codex Meter; sign in again on the first install. Future Models Meter APKs must use the same signing key. Back up `android/.local-signing/models-meter-release.p12` and `models-meter-password` securely; never commit them. Google Play Protect can request a scan of an APK distributed outside Google Play; a valid signature does not guarantee a warning-free install.

The **Latest models** dashboard card and home-screen widget use the account's Codex catalog. Newly discovered model IDs appear first. Discovery dates are local first-seen dates, not official release dates. An initial catalog establishes a silent notification baseline. Cached names and the last successful check remain visible offline, and sign-out clears them. Long-press the home screen → Widgets → Models Meter → Latest models, or use **Add models widget** on the dashboard. Both the icon and widget have light/dark variants following the system theme; the app icon inside Models Meter follows its selected appearance.

## Platform stability

The ChatGPT usage and reset-credit routes and Samsung's lock-screen metadata are implementation details rather than stable third-party Android SDK contracts. OpenAI or Samsung may change eligibility, routing, response fields, host behavior, or private metadata.

OpenAI, ChatGPT, Codex, Samsung, Galaxy, One UI, and related marks belong to their respective owners. This project is not affiliated with or endorsed by OpenAI or Samsung.
