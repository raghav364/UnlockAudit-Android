# Android validation and release gate

**Status: NOT BUILT and NOT PRODUCTION-READY.** A source project is not a signed APK.

## Prerequisites

1. Android Studio with Android SDK API 36 + Build Tools, JDK 17+, Gradle 8.13 and internet to resolve the project's dependencies.
2. An Android emulator (API 35 or 36) and at least one actual phone for unlock, Usage Access, service, notification and battery behavior.
3. Original v1.1 release signing key OR authorized Play App Signing route for an in-place update. Do **not** upload private signing keys to public repos or share them in chat.
4. Independent backup of the original history, as feasible, before running any update tests. Never uninstall v1.1 if preserving its data matters.

## Commands

The ZIP doesn't contain the Gradle wrapper JAR. Use a local Gradle 8.13 installation or create a wrapper on a trusted build machine.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
gradle --no-daemon :app:connectedDebugAndroidTest  # emulator/device connected
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk` after a successful debug build. **It is not a release APK, and it cannot update v1.1 signed with another key.**

## Release gate

- All Gradle unit tests and Android lint must pass.
- Room and Compose instrumentation tests must pass on emulator.
- Manually test Android 10, 13, 14, 15 and 16, especially screen-off/unlock, notification permission denial, Usage Access revocation, force-stop/OS process termination and background battery policies.
- Test daily history, search, calendar, dark/light, settings, migration with *actual v1.1 data*, retention, deletion during active recording, and long CSV exports.
- Verify Play Store foreground-service `specialUse` declaration / eligibility and user disclosure if distributing on Play.
- Build a minified release, sign with the original authorized signing identity, install-update over a v1.1 test device *without uninstall*, compare all old sessions, and check generated certificate SHA-256. Original APK certificate SHA-256: `ef4b2ce0d625f3717b613570511cbef6bfcb04b817e96e11daa8191b9ba3a4a2`.
- Run Android's `apksigner verify --verbose --print-certs app-release.apk`, and install/smoke test the exact signed release artifact.

The included `.github/workflows/android-ci.yml` is a suggested CI setup for JVM tests, lint, debug build and API 35 emulator tests; **it has not been executed here.** It does not create a production-signed release.
