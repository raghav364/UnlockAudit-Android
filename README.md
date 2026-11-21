# Unlock Audit v2.0 — Android Studio source prototype

**Status: revised implementation candidate, NOT a compiled, device-tested or production-approved APK.**

This is a reconstructed, maintainable Kotlin Android application based on static inspection of the owner's `UnlockAudit-Android16-v1.1.apk`, not the original v1.1 source. It preserves package ID `com.raghava.unlockaudit`, targets Android 16 (API 36), and has versionCode **3** / versionName **2.0.0**. Android 10+ is supported by the declared minSdk 29 but **still requires device validation**.

## Features implemented in source

- Material 3 Compose UI with dynamic light/dark color schemes, plus manual System/Light/Dark preference.
- Home, History, Insights, and Settings tabs; virtualized `LazyColumn` day scrolling and incremental session expansion (30 at a time).
- Expandable date folders, detailed unlock-to-screen-off sessions, foreground app transitions and timestamps.
- Best-effort local app labels and icons (Android package visibility can restrict metadata; package name fallback).
- Date picker and literal, case-insensitive app/package search; daily **session counts** (not guaranteed physical unlock counts) and recorded session-time summaries.
- Room database with session/app-event foreign keys and indexed dates.
- One-time v1.1 plaintext history migration. If any blocks fail, the original text stays in SharedPreferences for recovery and can be exported from Settings.
- New sessions use `importKey = "live:<start>"` to avoid duplicates after a crash before clearing pending state.
- Explicit Usage Access checking, visible user-started foreground-service notification, notification permission request.
- Mark recovered/incomplete sessions as **interrupted**; exclude their durations from total time and don't attribute app events to them.
- CSV export through Android's document picker (SAF) with escaping and spreadsheet formula-injection defense.
- 30/90/365 inclusive calendar-day or unlimited history retention; all-data delete confirmation that stops tracking and clears the pending session.
- Offline app: no internet permission; device/cloud automatic backup explicitly disabled.

## Limitations and decisions

1. **App-use duration is NOT measured.** The recorded time is unlock-to-screen-off session duration. App events indicate app foreground transitions, not actual per-app usage duration. The UI labels these accordingly.
2. v1.1 formatted timestamps only to the minute, so precise seconds cannot be reconstructed from old text.
3. Dynamic Android broadcasts and background-start/battery restrictions may impact tracking on specific Android versions and phone manufacturers. The app does not silently restart at boot. If interrupted, the user must reopen and restart tracking as needed.
4. `specialUse` foreground-service eligibility / Google Play policy approval remains outstanding. The declaration is not proof of eligibility.
5. The original v1.1 APK signing **private key** is not embedded in an APK. In-place installation over v1.1 requires the same certificate/signing lineage, or the relevant Play App Signing workflow. **Never uninstall v1.1 before backing up history**.
6. Android platform package visibility limits some icons/labels; the app does not request restricted QUERY_ALL_PACKAGES.
7. Original history text with unknown locale formats can fail to parse. This is surfaced to the user and the original is preserved for export instead of dropping it.
8. Some more complete behaviors (foreground service edge cases, OS policy, accessibility, battery/OEM testing) are **not validated**.

## Environment / build

This workspace did not have Android SDK, Android Gradle Plugin caches, Gradle itself, emulator, or network access, so **the full Android app was not compiled, installed, executed, or signed**. The pure Kotlin domain logic and SQLite queries *were* verified offline (see `docs/TEST_REPORT.md`).

Use Android Studio with Android SDK Platform **36** and a JDK **17+** toolchain. Gradle **8.13** and Android Gradle Plugin **8.13.2** are pinned. The project also uses:

- Kotlin 2.2.21 / Compose compiler plugin
- Compose BOM `2026.04.01` (Compose 1.11 line, compatible with compileSdk 36); **do not upgrade to the Aug/Nov 2026 Compose BOM without moving to API 37/37.1 and AGP 9+**
- AndroidX Activity Compose 1.12.3, Lifecycle 2.10.0
- Room 2.8.5 with kapt

The `gradle/wrapper/gradle-wrapper.properties` URL is provided, but **`gradle-wrapper.jar` was unavailable** here and is intentionally not fabricated. In Android Studio, point the project to a local Gradle 8.13 installation. Alternatively, with Gradle already installed, run:

```bash
gradle wrapper --gradle-version 8.13
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest  # requires emulator/device
./gradlew :app:lintDebug
```

Do **not** distribute the debug build as a production update. Use the original release signing credentials, run all listed checks, then create a signed release AAB/APK. See `docs/QA_MATRIX.md`.

## Project structure

```
app/src/main/java/com/raghava/unlockaudit/
  MainActivity.kt
  ui/AuditApp.kt                 # Compose screens, date folders, search and export UI
  ui/AuditViewModel.kt
  data/{Entities,AuditDao,AuditDatabase,AuditRepository}.kt
  domain/{LegacyHistoryParser,EventReducer,CsvWriter}.kt
  service/TrackerService.kt
app/src/test/                    # JVM unit tests (not run by Gradle here)
app/src/androidTest/             # Room & Compose device tests (not run)
tools/DomainSmokeTest.kt         # Offline executable domain tests
tools/verify_offline.py          # Offline manifests and SQLite verification
```

## Original source and signing

- Original APK SHA-256: `d79c61e264e3a220bb8aa6ad72bfb0d25e3106b21475d07295623192d9ed0a78`
- Original application certificate SHA-256: `ef4b2ce0d625f3717b613570511cbef6bfcb04b817e96e11daa8191b9ba3a4a2`
- No private signing key was provided.

Reference docs:
- https://developer.android.com/blog/posts/whats-new-in-the-jetpack-compose-april-26-release
- https://developer.android.com/build/releases/agp-8-13-0-release-notes
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/reference/android/app/usage/UsageEvents.Event

## Review improvements, 2026-11-21

The extra static-review pass corrected literal `%`/`_` search, precise calendar-date retention, empty-day loading behavior, progressive 30-session detail rendering, and deletion while tracking is active. Offline tests pass; *none of these changes has been Android compiled or device tested*. A GitHub Actions validation workflow template is included but has not been run. See `docs/BUILD_AND_RELEASE.md` and `docs/VALIDATION_REPORT_2026-11-21.md` for the required release gate.

## Samsung Galaxy S26 Ultra safe QA preview (2026-11-21)

The `debug` build has a separate application ID, `com.raghava.unlockaudit.preview`, so it can install alongside v1.1 without destroying old private data. The release ID remains `com.raghava.unlockaudit`, and an in-place upgrade still requires the original signing key. The CI workflow includes Android 15/16 emulator tests and publishes a **QA preview APK artifact only after both pass**. This workflow has **not been run**; it does not make the project production-ready. See `docs/S26_ULTRA_BUILD_AND_QA.md`.
