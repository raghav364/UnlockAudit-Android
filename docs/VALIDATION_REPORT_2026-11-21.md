# Unlock Audit v2.0 — verified development status

**Date:** 2026-11-21  
**Source:** User-supplied `UnlockAudit-Android16-v1.1.apk` and rebuilt v2.0 Android Studio project.  
**Overall:** SOURCE REVIEW AND OFFLINE CHECKS PASSED; NOT COMPILED, NOT DEVICE TESTED, NOT A PRODUCTION-READY APK.

## Findings and corrections implemented

| Check / issue | Outcome |
|---|---|
| Date-based history grouping | Source implementation present; SQL sample grouping verified using SQLite |
| Search for `%`, `_`, mixed-case names | Replaced wildcard LIKE matching with literal `instr(lower(...))`; regressions verified using SQLite |
| Retention 30/90/365/forever | Fixed inclusive local-day boundary using `RetentionPolicy`; tested with Halifax timezone and invalid values |
| Empty expanded folder spinner | Fixed so an empty result shows a message after loading, rather than spinning indefinitely; source-reviewed, device test outstanding |
| Many sessions in one day | Progressive display of 30 sessions per batch; Android UI performance not yet tested |
| Deleting history while tracking | Stops service, clears pending session, uses mutex and database transaction to prevent late writes; source-reviewed, race/device stress tests outstanding |
| Sensitive CSV app labels | Escapes CSV and shields formula prefixes including leading whitespace; tested with JVM Kotlin |
| Exact usage meaning | UI calls counts “Sessions” rather than guaranteed physical unlocks; app-duration-per-app is **not** measured |
| Privacy declarations | No INTERNET or QUERY_ALL_PACKAGES permission; Android backup disabled in manifest/XML; no dynamic privacy assessment executed |

## Tests executed in this environment

- **24/24** structural, XML and actual SQLite query checks passed (`tools/verify_offline.py`).
- **6/6** Kotlin/JVM domain smoke groups passed (`tools/DomainSmokeTest.kt`, using Kotlin 1.9.0): legacy parsing, bad-record recovery, usage-event normalization, CSV safety, US format, retention boundaries.
- Existing JUnit unit tests and Android instrumented tests are included but **not executed with Gradle**.
- `tools/verify.sh` is the repeatable offline check entry point.

### Tests NOT executed

- Gradle dependency resolution / full Android Kotlin+Compose compilation.
- APK packaging, runtime installation, lint, runtime test suites, OS security behavior, battery/performance monitoring.
- Room and Compose on-device instrumentation.
- In-place v1.1 to v2.0 history migration on an actual handset.
- OS 10/13/14/15/16 and OEM regression (Samsung/Pixel).
- Release APK signing and signing-certificate verification.
- Google Play foreground service `specialUse` eligibility review / Play approval.

## Why a production-ready APK is unavailable here

This environment lacks the Android SDK, Gradle, emulator and downloadable Android dependencies. Its network attempts to services.gradle.org / dl.google.com / Maven failed. No original release signing private key was provided. The APK certificate hash is visible in the original app, but the original **private key cannot be recovered from the APK**, so building with another key could not update v1.1 in place and migrate its app-private history.

**Do not uninstall v1.1 or clear its app data if keeping history is important.**

## Next actions in an Android build environment

1. Use Android Studio / Gradle 8.13 with Android SDK 36 to build and run JUnit, lint, Compose, Room tests.
2. Test the tracking service with real lock/unlock, usage permission revocation and process death on Android 16 and older supported versions.
3. Test migration by installing an authorized same-key update over populated v1.1 data.
4. Review foreground-service policy and verify behavior on supported phone manufacturers.
5. Sign release with the existing authorized key and run `apksigner verify`, install/update and smoke testing of the **exact release artifact**.

See `docs/BUILD_AND_RELEASE.md` and `.github/workflows/android-ci.yml` inside the project. The CI configuration is provided but has not been executed.
