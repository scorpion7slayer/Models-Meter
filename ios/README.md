# Codex Meter for iPhone and iPad

Native SwiftUI client for viewing the Codex allowance attached to a signed-in
ChatGPT account. It shows adaptive standard and model-specific usage windows,
Free-tier monthly limits, purchased usage credits, reset times, earned reset
credits, local burn history, notifications, and WidgetKit widgets.

This directory is the **iOS** package of the Codex Meter monorepo. The Android
application lives under [`../android/`](../android/). Behavior is aligned with
the Android app where platform APIs allow; it does not include Samsung One UI,
Wear OS tiles/complications, Now Bar / Live Update monitors, or Android in-app
APK updates.

Portable behavior through Android 2.8.0 is included: adaptive refresh, additional
limit parsing, Free-tier monthly windows, reorderable dashboard sections,
scrubbable on-device burn charts with insights and plan-value estimates,
usage-history customize, usage-credit and reset-credit auto-hiding, diagnostic
log export, and widgets that follow the weekly or monthly long window.

## Requirements

- Xcode 26 or newer
- iOS or iPadOS 26 or newer
- An Apple development team for device builds, App Groups, and the widget
  extension

## Build and test

From this `ios/` directory:

```sh
swift test --package-path CodexMeterCore
xcodebuild -project CodexMeter.xcodeproj -scheme CodexMeter \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
xcodebuild -project CodexMeter.xcodeproj -scheme CodexMeter \
  -destination 'platform=iOS Simulator,name=iPhone 17e' \
  -parallel-testing-enabled NO test
```

The signed-out screen includes an offline demo mode. Automated tests never
contact OpenAI.

## Layout

| Path | Role |
|------|------|
| `CodexMeter/` | Main app target |
| `CodexMeterWidgets/` | WidgetKit extension |
| `CodexMeterCore/` | Shared models/parsers (local Swift package) |
| `CodexMeterTests/` | Unit tests |
| `CodexMeterUITests/` | UI tests |

## Data and stability

OAuth credentials are stored only in the device Keychain. Widgets receive a
sanitized usage snapshot through an App Group and never receive credentials.
The app has no analytics, advertisements, or application relay server.

The ChatGPT usage and reset-credit routes are implementation details and may
change without notice. This app is not affiliated with or endorsed by OpenAI.
Production distribution is gated on confirming acceptable OpenAI OAuth, API,
trademark, and branding use; see `RELEASE_CHECKLIST.md`.

## License

MIT. See the repository root `LICENSE` and the in-app acknowledgements.

## New Codex model alerts

Settings → Notifications → **New Codex model alerts** announces models newly
visible in the connected account's Codex picker, using the same local notification
style as reset alerts. Master notifications and system permission must be enabled.
The first nonempty catalog establishes a silent baseline. IDs are retained across
refreshes and app restarts, separately per account, so renames, removals, and
reappearances do not trigger duplicates. Turning alerts off continues to update
the baseline without announcements; signing out clears discovery history.

Checks run with successful foreground/background usage refreshes, at most once
per 15 minutes while the process runs. Background delivery depends on iOS allowing
a refresh; this is not a server push or a guarantee of immediate release detection.
Empty/invalid catalogs and request failures leave the baseline intact and do not
fail usage refreshes. Demo mode does not check the live catalog.

Both mobile clients use the authenticated `backend-api/codex/models` route and
`visibility: list`, following the [Codex catalog protocol](https://github.com/openai/codex/blob/rust-v0.153.3/codex-rs/codex-api/src/endpoint/models.rs).
The compatibility query version is `0.153.3`, independent of Meter's release
version. This private endpoint and its client-version filtering may change; keep
both clients' compatibility versions aligned when updating the integration.
