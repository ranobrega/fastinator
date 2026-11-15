# Fastinator

A personal intermittent fasting tracker for Android. Start and stop a fast
manually, watch live progress against a fixed 13-hour target, keep a daily
session log, and review weekly and monthly reports. While a fast is active, a
persistent silent notification shows the live elapsed time. Fully offline: no
account, no analytics, no network permissions. Spec: v1.11.

## Features

- **Today** — circular progress ring with a live `HH:MM:SS` elapsed timer and
  achievement percentage (`0.00%`, can exceed 100%), a single Start/Stop button
  (light green when idle, light red while fasting), and a summary of the last
  completed session.
- **History** — day-grouped session log, newest first, with skipped days marked.
  Add/edit/delete sessions (undo snackbar, confirmation for today's most recent
  session). Overnight fasts belong to the day they started. Inline validation:
  end after start, no future times, no overlapping sessions.
- **Reports** — weekly (Sunday start) and monthly aggregations: total fasting
  time, daily averages, fasting vs skipped days, best day, and a daily
  achievement bar chart with a 100% reference line. Arrow navigation between
  periods. Reports are computed live from the session log (Room `Flow`), never
  cached.
- **Data export** — `fastinator/v1` JSON is written automatically to the app's
  export folder on every data change; a share action sends the latest file via
  the Android share sheet.
- **Backup** — Android OS auto-backup (v1 has no custom cloud backup).
- **Fasting notification** (spec §8.1) — while a fast is active, one ongoing,
  silent (LOW-importance) notification shows the live elapsed time (`HH:MM`,
  never seconds) and achievement (`0.00%`), refreshed once a minute; visible on
  the lock screen and shade, not dismissible while fasting, tap opens the app,
  and it is removed automatically when the fast ends. Backed by a `specialUse`
  foreground service; uses the runtime `POST_NOTIFICATIONS` permission
  (Android 13+, requested when a fast starts while the toggle is on). Can be
  turned off in Settings (own section right after Appearance, default on,
  persisted in DataStore) — toggling off mid-fast removes the notification
  without ending the fast. After a reboot mid-fast, the notification returns on
  the next app open (accepted gap).
- **Themes** — system default (follows the OS live), light, dark; the explicit
  choice persists.
- **Storage** — Room for sessions (start/end instants only; everything else is
  computed), Preferences DataStore for settings. First launch is clean: no
  sample data is ever seeded.

## Tech stack

Kotlin 2.x, Jetpack Compose, Material 3 (dynamic color on Android 12+), Room,
Preferences DataStore, kotlinx.coroutines. `minSdk 26`, `targetSdk 35`.

## Build and run

Prerequisites: JDK 17+ (Android Studio's bundled JBR works) and the Android SDK
(platform 36.1+ and build-tools). The Makefile wraps Gradle and adb; override
`JAVA_HOME` / `ADB` as needed.

```sh
make compile    # compile debug sources
make assemble   # build app/build/outputs/apk/debug/app-debug.apk
make test       # run the unit tests (Robolectric, incl. Compose UI tests)
make verify     # tests + build
make devices    # list adb-attached devices
make install    # test + build + install the APK on the connected phone
make run        # install + launch the app
make clean
```

To install on a physical phone (e.g. a Pixel with USB debugging enabled):
connect it, accept the RSA dialog on the phone, then `make devices` to confirm
it is listed and `make run`.

**Signing:** debug builds use the standard Android debug keystore. Release
builds are signed through environment variables (`KEYSTORE_PATH`,
`STORE_PASSWORD`, `KEY_PASSWORD`) — keystores and passwords are never committed.

## Testing

`make test` runs the Robolectric unit suite (session math and formatting,
validation rules, report aggregation, repository CRUD/persistence/reactivity,
JSON export schema, settings persistence (theme + notification toggle),
notification text format and service/manifest checks, manifest checks, and
Compose UI tests for all four screens). `make install` / `make run` run the
tests first, so a broken build never reaches the phone.

## Project layout

```
Fastinator/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/ruinobrega/fastinator/
│       │   ├── MainActivity.kt          # single activity, edge-to-edge
│       │   ├── DebugTag.kt              # test tags only in debug builds
│       │   ├── data/                    # Room (AppDatabase, SessionDao,
│       │   │                            #   SessionEntity, SessionRepository),
│       │   │                            # ExportManager (auto JSON export),
│       │   │                            # SettingsPreferences (DataStore)
│       │   ├── model/                   # Session, SessionValidator, ReportAggregator
│       │   ├── notification/            # FastingService (spec §8.1 persistent
│       │   │                            #   fasting notification)
│       │   └── ui/                      # FastinatorApp shell, screens
│       │                                # (Today/History/Reports/Settings),
│       │                                # components, theme
│       ├── main/res/
│       └── test/java/                   # unit + Compose UI tests
├── docs/                                # product spec (v1.11), code reviews,
│                                        # security review
├── gradle/
├── Makefile
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Documentation

- `docs/fastinator-specification.md` — product specification (v1.11, with changelog)
- `docs/code-review-v1.8-2026-11-14.md` — latest code review against the spec
- `docs/security-review-2026-11-14.md` — security review and open TODOs

## Privacy and security

- The app makes **no network calls**. It requests only `POST_NOTIFICATIONS`
  (Android 13+, runtime, for the fasting notification) and the install-time
  `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` pair. All data
  stays on the device.
- The session database is included in the Android OS auto-backup (silent
  restore on reinstall is accepted behavior). The JSON export is the manual,
  verifiable copy.
- **No API keys or secrets are stored in this repository.** Never commit
  `.env` files, keystores, or passwords. The debug keystore that used to sit in
  the repo was removed (debug builds now sign with the standard
  `~/.android/debug.keystore`), and `.gitignore` excludes `.env` and
  `*.keystore`/`*.jks` — see `docs/security-review-2026-11-14.md`.

Personal-use app, sideloaded directly — not published to any app store.
