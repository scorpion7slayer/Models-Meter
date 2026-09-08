# Contributing

This repository is a monorepo:

- Shared docs and release notes live at the repository root (`README.md`,
  `CHANGELOG.md`, `LICENSE`, `AGENTS.md`).
- **Android** lives under [`android/`](android/) (Gradle, `app/`, `shared/`,
  `tests/`).

Keep Android source changes under `android/`. Prefer focused commits and
update tests with behavior changes. Do not commit credentials, tokens, or
generated build artifacts.

## Android local setup

Install JDK 17 or newer and Android SDK Platform 36 with Build Tools 36.x. Set
`ANDROID_SDK_ROOT` or `ANDROID_HOME` to the SDK directory.

The OneUI-Design dependencies are hosted on GitHub Packages. Export `GH_USERNAME`
and a `GH_ACCESS_TOKEN` with `read:packages` access before running a full build or
Android lint when `android/vendor/m2` is incomplete.

From the repository root (wrappers) or from `android/`:

```bash
./run-tests.sh
./build.sh
./lint.sh
```

See [`android/README.md`](android/README.md) for module layout details.

## Release channels (Android)

Two long-lived branches feed two update channels in the app (Settings → Updates →
Update channel):

- `main` is the **stable** channel. Tags look like `v1.0.0`.
- `alpha` is the **rapid-iteration** channel. Tags look like `v1.1.0-alpha.1` and
  publish as GitHub prereleases. Create the channel branch only when explicitly requested.


Both channels are built by the same tag-triggered CI job and signed with the same
Models Meter keystore stored in the private Actions secrets
`MODELS_METER_KEYSTORE_BASE64` and `MODELS_METER_SIGNING_PASSWORD`, so the in-app updater's SHA-256 and signing-certificate checks
pass when switching channels in either direction — no uninstall/reinstall.

Versioning rules (enforced by CI on tags):

- **Alpha releases** bump only `versionName` and must keep `versionCode` **equal
  to** the newest stable release's `versionCode`. Android permits
  equal-`versionCode` installs, which is what makes the one-tap "Return to
  stable" flow an ordinary in-place install. The `versionName` must be the
  **next** stable version plus `-alpha.N` (after stable `1.0.0`, the first alpha
  is `1.1.0-alpha.1`, then `1.1.0-alpha.2`, ...). Never suffix the shipped
  stable itself (`1.0.0-alpha.1` after `1.0.0`): SemVer orders `X.Y.Z-alpha.N`
  *below* `X.Y.Z`, so the in-app updater would never offer it.
- **Stable releases** drop the suffix and bump `versionCode` by one, so a stable
  promotion is a normal upgrade for both channels.

Cutting an alpha: branch work off `alpha`, set `versionName` to the next stable
version plus the alpha suffix (for example, `1.1.0-alpha.1` while stable is
`1.0.0`) in `android/app/build.gradle.kts`,
`AppConstants.java`, `android/build.sh`, and the guards in `android/run-tests.sh`,
add a `## 1.1.0-alpha.1` section to `CHANGELOG.md`, then tag `v1.1.0-alpha.1`.

Promoting to stable: merge `alpha` into `main`, drop the suffix, bump
`versionCode`, consolidate the alpha changelog sections under the stable version,
then tag as usual.

Models Meter begins at version `1.0.0` / code `1` with its own application ID,
`dev.scorpion7slayer.modelsmeter`. CI ignores inherited tags belonging to the
original Codex Meter application when checking the version-code invariant.
Dispatch builds keep the same signing key as releases; pull-request builds use
a disposable local key. Never commit a keystore or its password.

## Restored targets in 1.0.1

`./build.sh` and `./lint.sh` include both phone and Wear OS modules. Install SDK platforms 36 and 37.0. Keep the phone and watch application IDs, version code/name and signing certificate aligned so Data Layer trust works. Version 1.0.1 uses code 2 on Android and build 2 on iOS. Include `android/wear/build.gradle.kts` and `ios/CodexMeter.xcodeproj/project.pbxproj` in explicitly requested version bumps.

Run `swift test --package-path ios/CodexMeterCore` and Xcode's app/unit/UI checks for iOS changes. Simulator builds do not require a distribution signing identity. Physical iOS distribution requires the user's Apple provisioning. Do not publish a release tag without an explicit request.

For translations, edit `localization/fr.json` and run `python3 scripts/generate-localizations.py`. Check French, English and the unsupported-system-language fallback. Internal IDs, stored enum values, API fields and provider/model names must remain untranslated.
