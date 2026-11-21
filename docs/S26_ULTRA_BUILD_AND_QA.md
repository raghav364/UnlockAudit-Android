# Galaxy S26 Ultra: secure v2 preview, tests, and production-signing gate

**Status 2026-11-21:** v2 app source exists; local offline tests pass. No full Android compilation, connected-device run, Samsung test, or production signing is completed. The cloud workflow has been authored but not executed.

## APK variants

- **Debug QA preview**: `com.raghava.unlockaudit.preview`, version `2.0.0-preview`. Installs alongside v1.1 and does not alter its app-private history. It starts with an empty v2 database because Android applications are isolated by package ID. Do not present this as a production update.
- **Release update**: `com.raghava.unlockaudit`, version `2.0.0`, versionCode `3`. To update v1.1 in place, it must be signed by the same original private signing key or compatible authorized signing lineage. No key has been provided and it cannot be extracted from an APK. Do not distribute a new-key release with this ID as if it could update v1.1.

## Cloud build (GitHub Actions)

1. Upload the complete project root (contents of ZIP) to the root of a private GitHub repository. `.github/workflows/android-ci.yml` must reside in `.github/workflows` in the repository root.
2. Open **Actions** and run **Unlock Audit v2 - build and test** using `workflow_dispatch` (or push to main).
3. CI runs Gradle unit tests, Android lint, debug and test compilation, validates the APK signature/package, and runs Room/Compose instrumentation tests on Android 15/API35 and Android 16/API36 emulators.
4. Only if all jobs pass does a final downloadable artifact named `UnlockAudit-v2.0-Android16-QA-preview` appear. Its filename inside the artifact is `UnlockAudit-v2.0-S26-Ultra-QA-preview.apk`.
5. **These jobs have not been run or independently verified yet.** If they fail, review the build logs, fix and rerun; no production release should be declared from passing checks alone.

## Manual Samsung Galaxy S26 Ultra test matrix (still mandatory)

1. Confirm One UI/Android release in **Settings > About phone > Software information**; Samsung S26 Ultra originally shipped with Android 16/One UI 8.5, but devices may have since upgraded. Confirm on the actual device instead of assuming.
2. Install QA preview and launch it; test screen sizing, system fonts, dark mode, rotated display and all four tabs.
3. Grant **Usage Access** explicitly to the preview app; verify rejected access prevents start and explains next action.
4. Grant notifications when prompted. Start tracking, lock, unlock, open at least three apps, lock again. Verify one recorded session, start/end, real app labels (or safe package fallback), correct day.
5. Repeat 30 unlock cycles and verify stable counting/no duplicates; test midnight boundary, timezone changes, date search, literal `%`/`_`, and many-day scroll.
6. Force stop and restart; verify visible tracking state is truthful and interrupted history is labeled rather than reported as full accurate duration. Test permission revocation while running.
7. Samsung Device care can put apps to sleep. Review Settings > Device care > Battery > Background usage limits and allow appropriate app activity if needed. Do not hide ongoing tracking notification.
8. Test CSV export/import safety, 30/90/365-day retention, confirmation/clear-all, restart after clear, and no history recreation.
9. Verify battery drain (multi-hour), memory, no ANRs/crashes, and no unexpected network requests. Validate notification stop action and user consent.
10. Test signing certificate match and in-place migration against a *backup-controlled* v1.1 install before replacing the user's existing app. Preview install cannot test this migration because it has a different package ID.
11. Release only after fixes, repeat tests on the **exact final signed release**, and verify platform policy requirements if distributing via Google Play.

## Safeguards

- Do not uninstall v1.1 or clear data until a verified history preservation plan is available.
- A GitHub CI **debug preview** is automatically debug-signed by the runner. It is not production-signed and its signing key can change on subsequent runs.
- Do not send signing keystores or passwords in chat. Use secure CI secret storage after confirming key provenance.
- Google Play may require justification or deny the `specialUse` foreground service classification. Check Play policy separately; passing lint is not Play approval.
