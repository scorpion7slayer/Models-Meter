# Tag releases and signing outside app stores

Models Meter publishes installable Android and Wear OS APKs to GitHub Releases.
No Google Play account is required. An optional iOS Ad Hoc IPA uses an Apple
Developer Program distribution certificate and registered devices; it does not
upload anything to App Store Connect or TestFlight.

## Android: already configured

These repository Actions secrets are in place:

| Secret | Value |
| --- | --- |
| `MODELS_METER_KEYSTORE_BASE64` | Base64 PKCS#12 containing the persistent `modelsmeter` private key |
| `MODELS_METER_SIGNING_PASSWORD` | Password for that key and keystore |

Keep the existing key. Both APKs must use the certificate documented in
[SIGNING.md](SIGNING.md), or installed copies cannot accept the update. CI checks
that fingerprint before distributing builds. Back up the ignored local keystore
and password securely. PR APKs use a disposable key and are only for testing.

## iOS: one-time Apple Developer setup

**Ad Hoc is limited to the devices registered in your Apple Developer account.**
A paid membership does not create a universally installable GitHub IPA. Other
users need their own signing/sideloading setup. The exported IPA contains Apple's
embedded provisioning profiles, including the registered device identifiers;
enabling the public GitHub release upload makes those profiles public too.

In [Certificates, Identifiers & Profiles](https://developer.apple.com/account/resources):

1. Create these two explicit App IDs, with their exact capitalization:
   - App: `dev.scorpion7slayer.modelsmeter`
   - Widget: `dev.scorpion7slayer.modelsmeter.Widgets`
2. Register `group.dev.scorpion7slayer.modelsmeter` as an App Group. Enable App
   Groups on **both** App IDs and associate that same group with each.
3. Register the UDIDs of the iPhone/iPad devices that will install the IPA.
4. Create an **Apple Distribution** certificate. Keep its private key in your
   macOS Keychain. Export the certificate **with the private key** as a
   password-protected `.p12`. A downloaded `.cer` alone is insufficient.
5. Create and download **two Ad Hoc distribution provisioning profiles**, one for
   each App ID. Select the same distribution certificate and the same devices in
   both profiles. Regenerate them after changing the App Group or device list.

Do not use Apple Development, Developer ID Application (macOS), App Store or
Enterprise profiles for this workflow. The project no longer hardcodes the
upstream author's Apple team. Local Xcode device builds need your own team under
Signing & Capabilities; CI supplies it explicitly.

## GitHub secrets and variables to add

Open [repository Actions settings](https://github.com/scorpion7slayer/Models-Meter/settings/secrets/actions).
Keep these values out of commits, issues and chat messages.

| Kind | Name | Value |
| --- | --- | --- |
| Secret | `IOS_CERTIFICATE_P12_BASE64` | Base64 of the exported Apple Distribution `.p12` including its private key |
| Secret | `IOS_CERTIFICATE_PASSWORD` | The `.p12` export password |
| Secret | `IOS_APP_PROFILE_BASE64` | Base64 of the app's Ad Hoc `.mobileprovision` |
| Secret | `IOS_WIDGET_PROFILE_BASE64` | Base64 of the widget's Ad Hoc `.mobileprovision` |
| Variable | `IOS_TEAM_ID` | Your 10-character Team ID from Apple Developer membership details |
| Variable | `IOS_RELEASE_ENABLED` | Set to `true` **after** testing signing successfully |

For the base64 secrets, use a single line. From a trusted local terminal, these
commands send files directly to GitHub without printing their contents (replace
the example filenames with yours):

```sh
base64 -i Distribution.p12 | tr -d '\n' | gh secret set IOS_CERTIFICATE_P12_BASE64 --repo scorpion7slayer/Models-Meter
base64 -i ModelsMeter-AdHoc.mobileprovision | tr -d '\n' | gh secret set IOS_APP_PROFILE_BASE64 --repo scorpion7slayer/Models-Meter
base64 -i ModelsMeter-Widget-AdHoc.mobileprovision | tr -d '\n' | gh secret set IOS_WIDGET_PROFILE_BASE64 --repo scorpion7slayer/Models-Meter
gh secret set IOS_CERTIFICATE_PASSWORD --repo scorpion7slayer/Models-Meter
gh variable set IOS_TEAM_ID --repo scorpion7slayer/Models-Meter --body YOURTEAMID
```

No App Store Connect API key, Apple account password, or Google Play service
account is required. Renew expired certificates/profiles and update their
secrets. The workflow validates expiration, bundle IDs, team, App Group, Ad Hoc
distribution mode, shared certificate and matching device lists, then verifies
the signatures of the exported app and widget. It cleans up the runner's
temporary keychain and provisioning profiles even on failure.

## Test before tagging

After the PR is merged, run the standalone **Build signed iOS Ad Hoc IPA**
workflow on `main` (or use the feature branch to validate it before merging):

```sh
gh workflow run ios-release.yml --repo scorpion7slayer/Models-Meter --ref main
```

This builds an IPA artifact without creating a release. Download
`models-meter-ios-signed`, install the IPA on a registered device using Xcode or
Apple Configurator, and check sign-in, Keychain persistence and widgets. Then
enable iOS tag releases:

```sh
gh variable set IOS_RELEASE_ENABLED --repo scorpion7slayer/Models-Meter --body true
```

While that variable is absent or `false`, tags publish only Android/Wear OS.
When it is `true`, a missing or invalid Apple secret fails the iOS job and blocks
publication of the combined release. A standalone iOS signing run always
requires the signing secrets, regardless of this flag.

## Publish a version

1. Merge the version's PR into `main` for a stable release, or use `alpha` for
   an alpha release. Follow the synchronized version rules in `AGENTS.md`.
2. Confirm the matching `CHANGELOG.md` section, run checks and inspect CI.
3. For the prepared stable version, run the following **only when ready to publish**:

   ```sh
   git switch main
   git pull --ff-only origin main
   git tag -a v1.0.1 -m 'Models Meter 1.0.1'
   git push origin v1.0.1
   ```

The tag triggers tests, lint, signed Android/Wear builds and, when enabled, the
signed iOS archive. The release stays a draft until all enabled artifacts are
verified and uploaded. Stable tags become latest; prerelease tags never do.
The workflow rejects a tag that disagrees with the app version and serializes
runs on the same ref. It does not publish a tag itself.

Assets: `ModelsMeter-<version>.apk`, `ModelsMeter-Wear-<version>.apk`,
`SHA256SUMS.txt`, and optionally `ModelsMeter-iOS-<version>.ipa` plus
`SHA256SUMS-iOS.txt`. The iOS simulator ZIP from iOS CI cannot be installed on a
physical iPhone and is not a replacement for the signed IPA.

References: [Apple Ad Hoc profiles](https://developer.apple.com/help/account/provisioning-profiles/create-an-ad-hoc-provisioning-profile),
[registered-device distribution](https://developer.apple.com/documentation/xcode/distributing-your-app-to-registered-devices),
[Apple device quotas](https://developer.apple.com/help/account/devices/devices-overview),
[GitHub's signing guide](https://docs.github.com/en/actions/how-tos/deploy/deploy-to-third-party-platforms/sign-xcode-applications).
