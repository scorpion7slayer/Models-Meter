# Models Meter

For signed APK/IPA distribution and releases triggered by version tags, see
[the release setup guide](docs/RELEASING.md).

Models Meter is an independent fork of [Codex Meter](https://github.com/BenItBuhner/Codex-Meter), maintained by [Theo (scorpion7slayer)](https://github.com/scorpion7slayer). BenIt Buhner and That Josh Guy are the developers of the original project; Filip Bukovina contributed the original iOS app.

[Source](https://github.com/scorpion7slayer/Models-Meter) · [Android and Wear OS downloads](https://github.com/scorpion7slayer/Models-Meter/actions/workflows/build-apk.yml) · [iOS builds](https://github.com/scorpion7slayer/Models-Meter/actions/workflows/ios-ci.yml)

## Version 1.0.2

This release completes the internal Models Meter rename and improves quota cards and widgets. After upgrading, reconnect accounts, reconfigure settings and recreate widgets; previous internal storage and settings exports are not imported. Update both phone and watch together.

Track subscription usage, quota reset times and model names for **ChatGPT, Anthropic / Claude, Cursor and OpenCode Go**. Android, Wear OS and iOS are included. Each provider keeps its own connection and cache on the device; there is no backend, advertising or analytics.

- **Latest models** on the dashboard, with a full model list and new-model notifications. The first successful catalog establishes a silent baseline; later discoveries are deduplicated by model ID. Discovery dates mean first seen by the app, not official release dates.
- **System / Français / English** in settings. System uses French when the first system language is French; other unsupported languages fall back to English.
- **Provider choice for each Android and iOS widget**, independent of the dashboard. Add widgets using the normal home-screen picker. Android's models widget resizes horizontally and vertically from **2 × 1 cells** and shows more names as it grows. iOS uses the widget sizes offered by WidgetKit.
- **Wear OS companion** with provider selection, latest model names, usage tiles, complications and the live monitor. Sanitized snapshots sync from the Android phone; credentials stay on the phone. Phone alerts can mirror through normal Wear OS notification settings.
- Light/dark Models Meter icons, fork attribution and GitHub links. Android updates continue to use this fork and its persistent signing certificate.

The provider APIs have different capabilities. Claude's catalog is public provider data; Cursor's catalog is the Cloud Agents API catalog and needs an optional Cursor user API key. Neither implies that every listed model is available on every subscription. See [provider setup and limitations](docs/PROVIDERS.md).

## Connect an account

Open **Settings → Providers** and select a provider. Claude and Cursor offer an integrated provider login page plus manual session-token entry when embedded login is unavailable. OpenCode Go uses a workspace API key. ChatGPT retains its existing OAuth flow. Never share credentials in issues, screenshots or chat.

Credentials are encrypted with Android Keystore or Apple Keychain. Widgets and the Wear companion receive display data only. Usage failures preserve the last successful response and show an error; a missing quota is displayed as unavailable, never as a full allowance.

ChatGPT-specific features remain available: usage history and pace estimates, reset credits, credit expiration reminders and confirmed credit redemption. They are not represented as supported actions for other providers.

## Platforms and builds

| Platform | Source | Requirements | Artifact |
| --- | --- | --- | --- |
| Android phone | `android/app`, `android/shared` | JDK 17+, SDK 36, Build Tools 36 | `android/dist/ModelsMeter-1.0.2.apk` |
| Wear OS | `android/wear`, `android/shared` | SDK 37.0, paired Android phone, Wear OS API 30+ | `android/dist/ModelsMeter-Wear-1.0.2.apk` |
| iPhone / iPad | `ios/` | Xcode 26+, iOS 26+ | Xcode app / simulator build |

Phone minimum: Android 8.0 (API 26). Samsung lock-screen and Now Bar features depend on compatible Galaxy firmware. Wear OS and phone builds share the same application ID and signing certificate for Data Layer communication.

```sh
./run-tests.sh
./lint.sh
./build.sh
```

The root wrappers run the Android project and build both APKs. Set `JAVA_HOME` and `ANDROID_SDK_ROOT` as needed. Vendored One UI dependencies allow builds without GitHub Packages credentials; see [Android development](android/README.md).

On macOS:

```sh
swift test --package-path ios/ModelsMeterCore
xcodebuild -project ios/ModelsMeter.xcodeproj -scheme ModelsMeter \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

Open `ios/ModelsMeter.xcodeproj` in Xcode, choose an iPhone simulator, then Run. A physical iPhone requires your Apple development team and provisioning; an unsigned simulator artifact cannot install on an iPhone. See [iOS development](ios/README.md).

To test Android, start an AVD in Android Studio's Device Manager, build, then install with `adb install -r android/dist/ModelsMeter-1.0.2.apk`. On a phone, download and extract the Actions artifact and open the **phone** APK. Install the separate Wear APK on the watch, not the phone.

## Distribution and signing

Manually dispatched Android builds use the fork's persistent signing key from Actions secrets. APKs and `SHA256SUMS.txt` are retained for 30 days. Pull-request builds use a disposable test key and cannot update a distributed build. Version 1.0.2 uses Android code **3** and retains the 1.0.0 application ID and certificate for in-place upgrades.

`dev.scorpion7slayer.modelsmeter` installs alongside the original Codex Meter app. The updater reads [this fork's releases](https://github.com/scorpion7slayer/Models-Meter/releases). Tags matching the Gradle version publish signed releases only when explicitly requested; a workflow artifact alone is not an updater release.

Keep `android/.local-signing/` private and backed up. [Signing details](docs/SIGNING.md) explain certificate verification. A valid signature does not guarantee that Google Play Protect will skip its scan or warning for an APK installed outside Google Play.

## Development and localization

See [CONTRIBUTING.md](CONTRIBUTING.md). English source text and `localization/fr.json` generate native French resources with `python3 scripts/generate-localizations.py`. Export the opaque iOS icon variants with `swift scripts/export-ios-icons.swift` on macOS.

Provider account endpoints and Samsung lock-screen metadata can change without notice. Models Meter is not affiliated with OpenAI, Anthropic, Cursor, OpenCode or Samsung. Names and marks belong to their respective owners.
