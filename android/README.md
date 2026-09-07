# Models Meter for Android

Native phone app and Wear OS companion for ChatGPT, Anthropic, Cursor and OpenCode Go. See [provider setup](../docs/PROVIDERS.md). This directory contains the Android Gradle project.

## Layout

| Path | Role |
|------|------|
| `app/` | Phone app (`dev.scorpion7slayer.modelsmeter`) |
| `wear/` | Companion app with sanitized phone synchronization |
| `shared/` | Pure-Java usage models and core logic |
| `tests/` | Pure-Java self-tests used by `./run-tests.sh` |
| `vendor/m2/` | Cached One UI / SESL Maven artifacts |
| `ci/` | Encrypted release keystore material for GitHub Actions |

## Build and test

From this `android/` directory (or via the repo-root wrappers):

```bash
./run-tests.sh
./lint.sh
./build.sh
```

Requirements: JDK 17+, Android SDK Platforms 36 and 37.0, Build Tools 36.x, and
`ANDROID_SDK_ROOT` / `ANDROID_HOME`. `vendor/m2` covers SESL deps offline;
optional `GH_USERNAME` / `GH_ACCESS_TOKEN` refresh GitHub Packages.

The signed phone and Wear APKs land in `android/dist/`. See the repository root
[`README.md`](../README.md) for product notes and release tagging.

## New Codex model alerts

Settings → Notifications → **New Codex model alerts** announces newly visible
models in the connected account's Codex picker using the selected alert style.
Master alerts and Android notification permission must be enabled. The first
nonempty catalog is a silent baseline; later additions are announced once per ID.
History persists separately per account until sign-out. Renames and model
reappearances do not alert again. While alerts are off, successful checks still
advance the baseline without notifications. The toggle is included in settings
export/import.

The catalog is checked alongside successful usage refreshes (including background
workers), no more than once per 15 minutes while the process runs. Delivery depends
on the existing refresh schedule and Android background restrictions. Empty or
invalid responses and network failures preserve the baseline and never fail the
usage refresh. This detects availability for the account, not global release dates.

The authenticated `backend-api/codex/models` route, `visibility: list` filter, and
`client_version=0.153.3` follow the [Codex catalog protocol](https://github.com/openai/codex/blob/rust-v0.153.3/codex-rs/codex-api/src/endpoint/models.rs).
The private endpoint/client compatibility version may need updating when Codex
changes.
