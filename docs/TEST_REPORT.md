# Offline test report — 2026-11-21

Input APK: `UnlockAudit-Android16-v1.1.apk`, SHA-256 `d79c61e264e3a220bb8aa6ad72bfb0d25e3106b21475d07295623192d9ed0a78`.

- **Kotlin domain smoke: 5/5 groups passed** using local Kotlin 1.9.0 JDK compiler and a runnable JVM jar. Tests: v1.1 timestamp/event parsing; rejected-record and deterministic hash behavior; ordered/deduplicated app events; CSV quoted/formula-safe writer; US locale legacy parsing.
- **Structural and SQLite offline checks: 19/19 passed**. These include Android XML parse, package ID, API target, backup policy, absence of INTERNET and QUERY_ALL_PACKAGES, Room and service source assertions, existence of all four screens and scrolling, retention, exported document picker, daily SQL sum correctness and filter behavior.
- **Full Android build, Gradle test tasks, runtime/UI tests, signed release, emulator/device tests: NOT RUN.** Android SDK, Gradle and emulator weren't installed in the working environment; outbound Gradle dependency downloads were unavailable.

**Interpretation:** Passing offline checks proves selected pure logic and SQL behavior, not Android lifecycle, UI compilation, app operation or production readiness. In particular, the Kotlin 1.9.0 smoke compiler was used only on pure JVM files; the full project pins Kotlin 2.2.21 and requires Android Gradle.
