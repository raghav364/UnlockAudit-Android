# Implementation plan and traceability — Unlock Audit v2.0

## Product scope

User-requested twelve features; implementation in source files below.

| Feature | Source implementation | Acceptance criteria (not yet device-verified) |
|---|---|---|
| Material 3 UI | `ui/AuditApp.kt` | Navigation and cards accessible; modern typography and responsive sizing |
| Scrollable home/history | Compose LazyColumn | Scroll 100+ days fluidly; no truncation |
| Expandable date folders | `DayCard` | One group per local date, reverse chronological order; expand/collapse |
| Detailed sessions | `SessionCard` | View start/end, duration, app transitions and interruption marker |
| App names/icons | `AppIcon`, `AuditRepository.appLabel` | Display local name/icon when visible; gracefully fallback to package |
| Calendar/date search | `HistoryScreen` + Room queries | Filter by date; app/package query returns relevant sessions |
| Daily summaries | Room `days` query + `SummaryRow` | Count unlocks, total session duration, distinct apps without join inflation |
| Light/dark mode | MaterialTheme and theme preference | System and manual themes persist |
| Room + migration | database and parser | Convert v1.1 text; idempotent; never discard rejected text |
| Reliable controls | TrackerService + AppOps check | User-granted permission, notification, explicit start/stop and recovery |
| Privacy/retention | repo/settings/manifest | No INTERNET, backup excluded; 30/90/365/all; confirm clearing |
| CSV export | domain/CsvWriter + SAF | Safe CSV quoting, formula prefix protection, user-chosen destination |

## Data architecture

`Room audit_v2.db` has `sessions(id, startedAt, endedAt, localDate, interrupted, source, importKey)` and `app_events(id, sessionId, occurredAt, packageName, appLabel)`. `app_events.sessionId` is a cascading foreign key. Date is ISO local date at session start. The app's legacy `audit` preferences are read for migration; new settings are `settings_v2`.

`MainActivity` uses a Compose `Scaffold`. `AuditViewModel` drives DB `Flow`s through Room and retains filters. `TrackerService` receives dynamic unlock/screen-off broadcasts, serializes them through a coroutine channel, queries usage events and commits sessions. User starts the foreground service while app is visible. It does not do covert background startup or auto-run at boot.

## Data migration

1. Before first migration, do not delete/reinstall the v1.1 application.
2. On v2.0 launch, read `SharedPreferences("audit")["history"]`.
3. Parse headers exactly matching `Session: <formatted start> to <formatted end>` and app transitions `<formatted time><two spaces><package name>`; DateFormat MEDIUM/SHORT, common English/French Canadian locale variants.
4. Import each parseable session and events inside a Room transaction. Unique hash-based import key prevents retries from duplicating records.
5. Record unparseable block count. If nonzero, keep entire raw text and expose a Settings export. Never claim full migration when the warning appears.
6. Clear the original plaintext only if all sessions parse successfully, *after* the DB transaction succeeds.
7. The legacy `start` key is separate from migration and is reconciled by the tracking service if an incomplete session is discovered. Recovery marks it interrupted and excludes time from duration summaries.

## Runtime state / design constraints

- `Tracking requested` is durable user preference, distinct from `TrackerService.isRunning` in-process actual active state.
- Start is gated on Usage Access AppOps `MODE_ALLOWED`; Android notification permission requested if needed.
- Recovered interrupted intervals are counted as an unlock record but **not** included in exact session-duration metrics; app events are not ascribed to them.
- On a service stop, the pending session is written before marking tracking stopped.
- Auto-backup and device-transfer exclusions are explicit. CSV shares only when user chooses a document destination.
- No INTERNET permission, no QUERY_ALL_PACKAGES, no boot-start receiver.

## Release gate

Detailed QA matrix in `QA_MATRIX.md`. This source is **not production-approved** until Android build, lint, unit, instrumentation, upgrade migration, OEM, battery, accessibility, privacy, signing, and policy checks have passed.
