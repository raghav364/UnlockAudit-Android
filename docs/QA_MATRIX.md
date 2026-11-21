# QA acceptance matrix — v2.0

**Status: pending real Android execution.** Pure offline tests have passed but do not substitute for installing and running the app.

| Category | Test | Expected result | Current status |
|---|---|---|---|
| Offline | Legacy text parser for Canadian/US time formats | Parse sessions & app rows | PASS (smoke) |
| Offline | Corrupted history | No silent loss, warning and preserved raw history | PASS (parser + source verification) |
| Offline | Duplicate foreground events | Chronological filter + dedup | PASS (smoke) |
| Offline | CSV writer sanitizes formula-like labels | Safe quoted output | PASS (smoke) |
| Offline | Date grouping SQL | Distinct session count, no inflated durations | PASS (SQLite) |
| Offline | Date/app search SQL | Exact subset of matching sessions | PASS (SQLite) |
| Offline | XML and permission declarations | Valid XML and no INTERNET permission | PASS (structural) |
| Build | Android Studio Gradle sync | Dependencies resolve | NOT RUN |
| Build | `:app:assembleDebug` | Compiles, packages with no errors | NOT RUN |
| Build | `:app:lintDebug` | No serious app lint warnings | NOT RUN |
| Unit tests | `:app:testDebugUnitTest` | All JUnit tests pass | NOT RUN |
| Instrumentation | Room in-memory DAO tests | CRUD, cascades, uniqueness | NOT RUN |
| Instrumentation | Compose navigation test | All screens reachable | NOT RUN |
| Permissions | Usage Access initially denied | Request and informative UI; service not started | NOT RUN |
| Permissions | Usage Access revoked during tracking | No crash; user sees/recoverable gap | NOT RUN |
| Permissions | Notification permission denied | Accurate state, Android task-manager disclosure | NOT RUN |
| Tracking | Start while already unlocked | Partial current session tracked as newly started | NOT RUN |
| Tracking | Lock/unlock 20 times | 20 correctly ordered sessions; no duplicates | NOT RUN |
| Tracking | Rapid screen toggles | No negative time, no race/duplicate records | NOT RUN |
| Tracking | Stop while unlocked | Session persisted, notification removed | NOT RUN |
| Tracking | Force-stop/restart/OS process death | Honest interrupted-state representation, no inaccurate duration | NOT RUN |
| Tracking | Reboot and re-open | Does not start without allowed user interaction | NOT RUN |
| Storage | In-place upgrade over v1.1 | Preserves prior history; no data loss | NOT RUN — signing key needed |
| Storage | Mixed locale and malformed v1.1 data | Import correct records, export original text of rejected entries | NOT RUN |
| Storage | Retention 30/90/365/forever | Exactly correct rows retained, cascading event deletion | NOT RUN |
| Storage | Delete all with cancel/confirm | Cancel no deletion; confirm deletes completed history | NOT RUN |
| Export | CSV after 1000 sessions | Valid UTF-8, quoting, complete columns, no crashes | NOT RUN |
| UI | Long list (1000+ days) | Responsive scroll and stable expand/collapse | NOT RUN |
| UI | Dark/light and rotation | No UI clipping or lost preferences | NOT RUN |
| UI | Screen reader and font scaling | Accessible labels/targets/readability | NOT RUN |
| Security | Cloud backup and device transfer checks | Local history not automatically backed up | NOT RUN |
| Security | Dependency and dynamic security assessment | No unresolved significant issues | NOT RUN |
| OEM devices | Android 10, 13, 14, 15, 16 | Correct service/permission handling | NOT RUN |
| OEM devices | Samsung and Pixel devices | Session tracking across background/battery conditions | NOT RUN |
| Performance | 24h battery, memory and crash monitoring | Acceptable thresholds defined by product | NOT RUN |
| Release | Signed release bundle, install/update | Same signing identity for upgrade; Play policy reviewed | NOT RUN |

## Runtime manual test sequence

1. Install v1.1 signed with the original release key, grant Usage Access, create several sessions, including app changes. Keep an independent backup of `history` before updating.
2. Create v2.0 signed with same key and update *without uninstalling*. Confirm historic days and details.
3. Deny Usage Access and confirm tracking cannot start. Grant it, start tracking, unlock, open two apps, screen off, verify new day folder.
4. Expand a day, then session; verify app labels, icons and ordered timestamps. Search and calendar-filter; clear filters.
5. Start tracking, swipe app away, induce simulated process death, reopen, check interruption indicator and non-inflated duration.
6. Change theme and retention, export CSV to Documents, check header/escaping using a reader. Confirm delete prompts and results.
7. Repeat with Android notification permission denied and with the phone locked/unlocked quickly.
8. Run instrumented tests on actual devices and emulators, inspect Logcat without private data leakage, test 24h battery impact.
