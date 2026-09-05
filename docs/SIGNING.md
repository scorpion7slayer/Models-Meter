# Models Meter Android signing

Distributed Models Meter builds use application ID `dev.scorpion7slayer.modelsmeter`
and the fork's RSA 3072-bit signing key. This is a separate app from Codex Meter;
the first install needs a new sign-in and can coexist with the original app.

Signing certificate SHA-256:

```text
2561ff561e466bf4c64fd93057eb2d0d94e36ee0b512827c11ab917c3106d0cb
```

Local release builds keep the key in ignored files:

- `android/.local-signing/models-meter-release.p12`
- `android/.local-signing/models-meter-password`

Keep a secure backup of both files. They must never be committed or attached to
an issue. The corresponding private GitHub Actions repository secrets are
`MODELS_METER_KEYSTORE_BASE64` and `MODELS_METER_SIGNING_PASSWORD` (no trailing newline).
Only manually dispatched and tag builds use them. Pull-request builds use a
disposable test key and cannot update a distributed installation.

`./build.sh` runs `apksigner verify --verbose --print-certs` and produces
`SHA256SUMS.txt` beside the APK. The in-app updater also checks the APK checksum,
application ID, version, and signing certificate before asking Android to install.
It reads releases only from `scorpion7slayer/Models-Meter`.

A valid signature proves APK integrity and signing identity, not Google approval.
[Google Play Protect](https://developers.google.com/android/play-protect/warning-dev-guidance)
can request a scan or warn about an app installed outside Google Play. Keep Play
Protect enabled; an emulator installation does not predict its verdict on every
phone. There is no Play Store certification claimed for this fork.
