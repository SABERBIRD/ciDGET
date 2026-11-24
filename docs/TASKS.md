# Implementation tasks

Each task is meant for one agent session and one pull request. Read [AGENTS.md](../AGENTS.md) first. A task is done when **all** its acceptance criteria hold and `./gradlew check` passes in CI.

Order: tasks within a stage can run in parallel where *Depends on* allows.

---

## Stage 2: MVP

### T1. Project skeleton and CI
- **Depends on:** –
- **Do:** Gradle setup as in BUILD_AND_RELEASE.md §1 with modules `:core`, `:github`, `:app`. `build.yml` and `release.yml` as in §2. An empty Glance widget that renders `~/ $ ci`. `en` + `uk` string resources. `locales_config.xml`. `allowBackup=false`. Launcher activity = settings screen placeholder. Generate and commit the public signing key per BUILD_AND_RELEASE.md §1 and sign debug and release builds with it.
- **Done when:** `./gradlew check assembleDebug` passes locally and in CI; the debug APK artifact is uploaded; a `v0.0.1-test` tag on a fork builds a signed release (`apksigner verify` passes); two consecutive debug APKs from CI install over each other without uninstalling; the widget can be placed on an API 31 emulator.

### T2. Core models and aggregation
- **Depends on:** T1
- **Do:** `:core` models (`Run`, `BranchHead`, `BranchStatus`, `Status`, `GreyReason`, `RunState`), `aggregate()`, sorting and filtering, worst-of-repo — exactly per AGGREGATION.md.
- **Done when:** tests A1–A28 exist and pass; `:core` has no Android or OkHttp dependency; public functions have KDoc.

### T3. GitHub client
- **Depends on:** T2
- **Do:** `:github` per GITHUB_API.md §1–§5: OkHttp client factory (cache dir passed in), interceptors (auth, headers, `no-cache`), DTOs, mappers to `:core`, `listUserRepos`, `getRepo`, `listBranches`, `listRuns(page)`, `listRunsForSha`, the refresh algorithm §3 as `fetchRepoState(...)`, error mapping, header parsing (rate limit, retry-after, token expiry).
- **Done when:** the tests in GITHUB_API.md §6 pass; nothing logs the `Authorization` header (test); no exception escapes the public API (test with a throwing interceptor).

### T4. Token storage and token screen
- **Depends on:** T1
- **Do:** `TokenStore` per SECURITY.md. Settings → Token screen (Compose M3): paste, show/hide, validate (`/user/repos?per_page=1`), save, replace, delete (evicts HTTP cache and snapshots), shows last 4 chars, expiry date, `INVALID` state with explanation. Link to user/TOKEN.md (the GitHub page). If the branch list for any repo was forbidden, show a hint: "Add Contents: Read-only to the token for exact branch heads". The repo picker marks private repos with a lock and lists public repos too (V3: `/user/repos` returns the granted private repos plus all of the owner's public repos).
- **Done when:** instrumented test of encrypt/decrypt round trip and of corrupt-file recovery; manual check on emulator; the token string appears in no log (`adb logcat` grep during save and refresh).

### T5. Snapshot store, refresh worker, manual refresh
- **Depends on:** T3, T4
- **Do:** `SnapshotStore`, `SettingsRepository` (only fields needed so far: per-widget repo), `RefreshWorker`, `RefreshScheduler` one-time path, `RefreshCallback` for `[⟳]`, `refreshing` flag and `[...]` indicator. Behaviour per ARCHITECTURE.md §2–§4.
- **Done when:** worker tests from TESTING.md §1 pass; with no network the old snapshot is kept and the error is recorded; the second refresh with no changes gets `304`s (log `networkResponse.code` in debug).

### T6. Widget MVP list
- **Depends on:** T5
- **Do:** `LIST` size class with fixed columns `STATUS BRANCH RUN_NUMBER AGE COPY` (no fitting yet: just truncate the branch), header with repo name and `[⟳]`, meta line with age/errors/`no token`, empty states, dark palette, Unicode glyphs with `hasGlyph` fallback. Row tap opens the run. `RepoPickerActivity` from the header (repos from `/user/repos` + manual `owner/repo` with validation via `getRepo`). Refresh on widget add and repo change.
- **Done when:** on an emulator with the `testING` fixture, branches and colours match TESTING.md §3, given the fixture works (V11); a row tap opens the run URL; cached data shows immediately after a device reboot, before any network.

### T7. Copy and share
- **Depends on:** T6
- **Do:** `ActionActivity` and the `COPY`/`SHARE` cells per WIDGET.md §4.
- **Done when:** V10 on API 31 and API 36 emulators; the share text format has a unit test in `:core`.

**→ MVP review with the owner (install the debug APK).** At this point, ask the owner to run `tools/verify-token.sh` if V1–V7 are still pending.

---

## Stage 3: Columns and sizes

### T8. Column fitting engine
- **Depends on:** T2
- **Do:** `:core` column model (table in WIDGET.md §3), formatters, fitting algorithm §3.1, segment merging §3.2 (as pure data: list of segments with colour role and text).
- **Done when:** tests L1–L10 plus formatter tests pass.

### T9. Widget uses fitting, TINY mode
- **Depends on:** T6, T8
- **Do:** `SizeMode.Exact`, `charsPerLine` from size + font metrics, render segments, all 12 columns, `TINY` class, per-widget font size.
- **Done when:** V8 and V9 checked and their results written into RESEARCH.md; resizing from 1×1 to 5×5 never wraps a row; TINY tap opens the right run.

### T10. Per-widget settings, global defaults, configuration activity
- **Depends on:** T9
- **Do:** full `WidgetConfig` + global defaults in DataStore; the configuration activity (`reconfigurable|configuration_optional`) with repo, "copy settings from widget…", "use global defaults"; settings screens for columns (enable, drag order via `sh.calvin.reorderable`, priority high/medium/low) and display (glyph style, font size, TINY source). Enforce "STATUS or BRANCH must stay enabled". Delete config in `onDeleted`.
- **Done when:** two widgets with different repos and columns work independently; copying settings works; the defaults change all widgets that inherit them and none that don't; settings survive an app restart.

---

## Stage 4: Finish

### T11. Background refresh
- **Depends on:** T5
- **Do:** periodic work per ARCHITECTURE.md §3, settings for interval/off/unmetered/battery-not-low, refresh only repos in use, rate-limit and 401 skips.
- **Done when:** with WorkManager test helpers, the periodic work is enqueued, re-enqueued with a new interval, and cancelled when off; a 401 stops further API calls (fake client counts calls).

### T12. Error, stale and token-expiry display
- **Depends on:** T6, T11
- **Do:** all meta-line states from WIDGET.md §2, stale marker, token expiry warning (D13 days, configurable), errors in the settings screen too, TINY `!` marker.
- **Done when:** each row of the GITHUB_API.md §5 table produces the documented text (unit test on the formatter + manual check of offline/401/404).

### T13. Filters and sorting settings
- **Depends on:** T10
- **Do:** UI for sort key, pinned branches (choose from known branches or type), hide idle after N days, max rows, show all branches. Wire to the `:core` functions from T2.
- **Done when:** manual check with `testING` for each option; pinned branches stay first under every sort.

### T14. Themes
- **Depends on:** T9
- **Do:** light palette, DARK/LIGHT/SYSTEM setting (global and per widget), bracket glyph style.
- **Done when:** screenshots of the three themes and both glyph styles in the PR; contrast ≥ 4.5:1 for all text roles (state the measured ratios in the PR).

### T15. Localization pass
- **Depends on:** all UI tasks
- **Do:** every user-visible string in `values/strings.xml` and `values-uk/strings.xml`; plurals where needed (`5 хв тому` etc.). Widget column texts (`5m`, `#412`) stay in their compact terminal form in both languages.
- **Done when:** lint `MissingTranslation` is an error and passes; a Ukrainian screenshot of settings and widget is in the PR.

### T16. Documentation pass
- **Depends on:** everything
- **Do:** update README (screenshots), user/INSTALL.md, user/TOKEN.md, ARCHITECTURE.md to match the code; fill in RESEARCH.md results.
- **Done when:** the owner can install and set up the app from the docs alone.

---

## Owner actions (not agent tasks)

| When | Action |
|---|---|
| Now | Add **Contents: Read-only** to the `ciDGET` token (docs/user/TOKEN.md). |
| After T7 | Install the debug APK and review the MVP. |
