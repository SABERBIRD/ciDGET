# Architecture

## 1. Modules

```
ciDGET/
├── core/     pure Kotlin (JVM), no Android, no OkHttp
│             model, aggregation, sorting/filtering, column fitting, text formatting
├── github/   pure Kotlin (JVM): OkHttp + kotlinx.serialization
│             API client, DTOs → core models, error mapping, header parsing
└── app/      Android application
              Glance widget, WorkManager, DataStore, Keystore crypto,
              settings UI (Compose M3), activities, manual DI
```

Dependency direction: `app → github → core`, `app → core`. `core` and `github` are `kotlin("jvm")` modules, so their tests run on a plain JVM (fast, no Robolectric).

Three modules is deliberate: enough to keep the pure logic testable and free of Android, few enough to stay simple (D5).

### Packages in `:app`

| Package | Contents |
|---|---|
| `widget` | `CidgetWidget` (GlanceAppWidget), `CidgetWidgetReceiver`, `WidgetContent` (size classes: 1×1 tile, narrow column, terminal), `Palette`, `Glyphs`, meta line |
| `widget.ui` | The terminal screens as a model (`UiScreen`/`UiLine`/`UiSpan`, built in `Screens.kt` from a `UiEnv`), drawn by two renderers: `GlanceUi` (in the widget) and `ComposeUi` (`WideTerminal`, full screen). `UiController` runs the taps (`Cmd`) as settings writes; `UiCallback` is the Glance entry point. |
| `widget.config` | `WidgetConfigActivity`: switches the widget to its in-widget settings, or shows the wide terminal for small widgets (D28) |
| `action` | `ActionActivity` (invisible trampoline: copy, share, paste token) |
| `settings` | `TokenController` (validate, save, delete tokens), clipboard paste, error texts |
| `data` | `TokenStore`, `SettingsRepository`, `SnapshotStore`, `RefreshRepository`, `RepoListStore`/`RepoListRepository` (cached pickable repos, D24) |
| `work` | `RefreshWorker`, `RepoListWorker`, `RefreshAllWorker` (periodic), `RefreshScheduler` |
| `di` | `AppGraph`: a single object created in `Application.onCreate` that builds the OkHttp client, stores and repositories, and merges redraw requests |

## 2. Data flow

```
          ┌──────────── triggers ─────────────┐
          │ [⟳] tap · widget added · repo     │
          │ changed · periodic WorkManager    │
          └──────────────┬────────────────────┘
                         ▼
                   RefreshWorker (per repo, unique work)
                         │ token from TokenStore (decrypted in memory)
                         ▼
                 :github GitHubClient ── OkHttp (+ETag cache) ──► api.github.com
                         │ Run / BranchHead models + meta (rate limit, token expiry)
                         ▼
                 :core aggregate() ──► List<BranchStatus>
                         ▼
                   SnapshotStore.save(repo, RepoSnapshot)   (keeps old data on error)
                         ▼
             CidgetWidget.updateAll / update(ids showing this repo)
                         ▼
     provideGlance: read WidgetConfig + RepoSnapshot → :core sort/filter/fit → UI
```

- The widget **never** does network work in `provideGlance`. It only reads local state, so it renders instantly from cache.
- Aggregation runs in the worker and the result is stored. Sorting, filtering and column fitting run in `provideGlance`, because they depend on per-widget settings and size.
- Relative times (`5m ago`, `AGE`) are computed at render time from `now`. Widgets are re-rendered after every refresh, so `AGE` can lag by at most one refresh interval. That is accepted; no minute ticker.

## 3. Refresh scheduling

| Trigger | Work |
|---|---|
| `[⟳]` tap | `OneTimeWorkRequest`, expedited (`OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST`), unique name `refresh:{repo}`, `KEEP` |
| Widget added / repo changed / token changed | same as above, for the affected repos |
| Periodic | one `PeriodicWorkRequest` `refresh-all` (`RefreshAllWorker`, `ExistingPeriodicWorkPolicy.UPDATE`), interval from the app settings (15, 30, 60, 120, 180 or 360 min, default 30, or off). It exists only while some widget shows a repo; it is re-synced at app start, after a repo switch, a widget removal and a settings change. Constraints: `NetworkType.CONNECTED` (setting: `UNMETERED`), `requiresBatteryNotLow` (setting, default on). It refreshes each **distinct repo used by at least one widget**, sequentially. |
| Boot / app update | WorkManager persists periodic work itself; nothing to do. |

Worker rules:
- Skip a repo while `rateLimitedUntil > now`. Skip everything while the token is `INVALID` (401) until it is replaced.
- Return `Result.retry()` (exponential backoff, initial 1 min) for `Offline`, `Timeout` and `Server`; `Result.success()` for everything else, including handled errors.
- Set `refreshing = true` in the repo state at start and `false` in a `finally`, then update widgets.
- Battery saver: WorkManager already defers work in Doze and App Standby; no extra code.

## 4. Storage

| Store | Tech | Contents |
|---|---|---|
| `TokenStore` | file `files/token.bin` (IV + AES-GCM ciphertext of a small JSON payload), key in Android Keystore | the PATs (D23: id, label, token, state `OK`/`INVALID`, expiry) |
| `SettingsRepository` | Preferences DataStore `settings` (one instance) | per-widget `WidgetConfig` JSON under `widget_config_{appWidgetId}` (no global defaults, D28); app-wide `AppSettings` JSON under `app_settings` (refresh interval, Wi-Fi only, battery, expiry warning days); `schema` (keys of older versions are removed once) |
| `SnapshotStore` | one JSON file per repo in `files/snapshots/{owner}__{repo}.json` (kotlinx.serialization, atomic write via temp + rename) | `RepoSnapshot` |
| `RepoListStore` | one JSON file `files/repos.json` (atomic write) | pickable repos of all tokens with the id of the token that sees each; read by the widget, refreshed by `RepoListWorker` (D24) |
| HTTP cache | OkHttp `Cache` in `cacheDir/http2` | ETag'd responses. `Vary: Authorization` is stripped before caching (SECURITY.md rule 1); the pre-fix `http` directory is deleted at start. |

```kotlin
@Serializable data class RepoSnapshot(
    val repo: String,                     // "owner/repo"
    val fetchedAt: Instant?,              // last successful refresh
    val branches: List<BranchStatus>,     // aggregated, unsorted
    val headsApproximate: Boolean,
    val error: ApiError?,                 // last refresh error (:github sealed type, serializable), null if the last refresh succeeded
    val errorAt: Instant?,
    val rateLimitedUntil: Instant?,
    val refreshing: Boolean,
    val branchesForbidden: Boolean,
    val branchesForbiddenCheckedAt: Instant?,
)
```

Backups: `android:allowBackup="false"`. The Keystore key does not survive a restore, so a backed-up ciphertext is useless and the snapshots may contain private data.

The widget reads state through Glance's `provideContent { }` by collecting `SettingsRepository`, `SnapshotStore`, `RepoListStore` and `TokenStore` flows (each flow created once with `remember`), so a store update re-renders a running session. A session that has gone idle needs an explicit `update()`: every change asks `AppGraph.redraw(id)` / `redrawRepo(repo)` / `redrawAll()`, and these requests are merged for 30 ms so a burst becomes one update per widget.

A refresh that was announced with `[...]` (`prepare`) but cannot run (token became invalid, rate limit) clears the flag itself; a stored `refreshing` flag is dropped when snapshots are loaded in a new process.

## 5. Dependencies (all Apache-2.0, GPL-3.0-compatible)

| Library | Module |
|---|---|
| kotlinx-coroutines, kotlinx-serialization-json, kotlinx-datetime | core, github, app |
| okhttp (+ mockwebserver for tests) | github |
| androidx.glance:glance-appwidget, glance-material3 | app |
| androidx.work:work-runtime-ktx | app |
| androidx.datastore | app |
| androidx.activity-compose, compose-bom, material3 | app |
| junit / kotlin-test | tests |

Adding any other dependency requires a note in this table and in DECISIONS.md. Versions are pinned in `gradle/libs.versions.toml`, using the latest stable versions at the time of implementation.

## 6. Threading

- `:core` is synchronous and pure.
- `:github` exposes `suspend` functions. It uses `Call.await` via `suspendCancellableCoroutine`, or `withContext(Dispatchers.IO) { call.execute() }`.
- Workers are `CoroutineWorker`s.
- Glance `provideGlance` does only local reads.

## 7. Future modules (not in this version)

The token is read-only in this version. Planned later and deliberately not prepared in code beyond clean module boundaries:
- re-running a workflow and deleting merged branches need **Actions: write** / **Contents: write** and would be a separate feature module with its own token-scope check;
- notifications when a pinned branch turns red;
- faster polling while runs are yellow.
