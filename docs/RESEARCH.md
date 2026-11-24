# Research: platform constraints and verifications

Stage 1 of the brief. **R** items are findings from the platform documentation and known behaviour; they set the design. **V** items are open checks that must pass before or during implementation. The task that owns each check is in [TASKS.md](TASKS.md).

## R. Findings

| # | Topic | Finding | Design consequence |
|---|---|---|---|
| R1 | Monospace font | Glance `TextStyle.fontFamily` accepts the generic families (`Monospace`, `SansSerif`, `Serif`, `Cursive`) and system family names. It cannot load font files from `res/font` (RemoteViews limitation). | Use `FontFamily.Monospace`. Its look depends on the device (usually Droid Sans Mono). |
| R2 | Widget width | `SizeMode.Exact` + `LocalSize.current` give the size in dp for each size the launcher reports (portrait/landscape) on API 31+. Glyph advance is measured with `Paint(Typeface.MONOSPACE).measureText`. Some launchers report sizes a few dp off. | Character budget formula in WIDGET.md §3.1, with a 1-char safety margin. |
| R3 | Scrolling | Glance `LazyColumn` maps to a `ListView` fed by `RemoteCollectionItems` (API 31+, no `RemoteViewsService`). It scrolls and items are clickable. All items are sent in one binder transaction (~1 MB limit). | Max rows capped at 200 (default 50); rows are light (≤ 6 views). |
| R4 | View count | Glance `Row`/`Column`/`Box` render at most **10 children**; extra children are dropped with a log warning. RemoteViews also has a total view budget. | Columns are concatenated into a few `Text` segments per row (WIDGET.md §3.2). |
| R5 | Responsive vs Exact | `Responsive` precomputes layouts for a fixed set of sizes and the launcher picks one without calling the app (fast), but the size is only a bucket. `Exact` recomposes for each reported size (usually 2). | `Exact`: column fitting needs the real width; the cost is 2 compositions per update. |
| R6 | Clipboard | Android 10+ restricts **reading** the clipboard in the background; **writing** is allowed. Android 13+ shows its own "copied" confirmation, so apps should not show a Toast as well. | Copy through a translucent `ActionActivity`; Toast only on API ≤ 32. |
| R7 | Trampolines | The Android 12 restriction on activity trampolines applies to notifications, not to widget `PendingIntent`s. | `ActionActivity` → chooser is allowed. |
| R8 | Opening runs | `ACTION_VIEW` on `https://github.com/...` resolves to the GitHub app through its verified App Link if installed, else the default browser. | No package checks. |
| R9 | Background work | `PeriodicWorkRequest` minimum is 15 min. Doze and App Standby defer work. Expedited work has a quota and falls back to normal work when it runs out. | Defaults in ARCHITECTURE.md §3. |
| R10 | Widget update | `updatePeriodMillis` below 30 min is ignored and wakes the device, so it is unsuitable. | `updatePeriodMillis="0"`; WorkManager only. |
| R11 | Configuration | `widgetFeatures="reconfigurable|configuration_optional"` (API 31+) lets the widget be placed without the config screen and reconfigured by long-press. | New widgets start with global defaults; repo picked from the header. |
| R12 | Glyph support | `Paint.hasGlyph(String)` (API 23+) tells whether the font (with fallbacks) can draw a string. | Automatic ASCII fallback (WIDGET.md §1). |
| R13 | Languages | On API 33+ the system offers a per-app language setting when `locales_config.xml` is declared. On 31–32 the app follows the system language. | `values/` (en) + `values-uk/`, `locales_config.xml`. No AppCompat dependency for in-app switching. |
| R14 | Token encryption | `EncryptedSharedPreferences` (security-crypto) is deprecated. Keystore AES-GCM with an app-managed file is the supported path. | SECURITY.md. |
| R15 | GitHub run list | The list endpoint returns the latest attempt of each run (`run_attempt`). Filtered queries (`head_sha`, `branch`, …) return at most 1,000 results. A conditional request answered with `304` does not count against the rate limit. | AGGREGATION.md §4, GITHUB_API.md §3. |

Confidence: R1–R14 come from the official Android/Glance documentation and are standard; R4's exact limit and R3's transaction limit should still be observed on a device (V8).

## V. Verifications

### V1–V7: token and API (run `tools/verify-token.sh`)

The project owner runs the script with a fine-grained PAT limited to `SABERBIRD/testING` with Actions: read (Metadata: read is added automatically). The output contains only status codes and headers.

| # | Question | Expected | Result |
|---|---|---|---|
| V1 | Can the PAT read runs of a private repo? | `200`, runs listed | **Pass** (2026-11-24): `200`, `x-accepted-github-permissions: actions=read` |
| V2 | Does `/branches` work with Actions + Metadata only? | `200`, or `403` with `x-accepted-github-permissions: contents=read` → then D7 adds Contents: read | **No**: `403`, `x-accepted-github-permissions: contents=read`. Contents: read added to D7. |
| V3 | Does `/user/repos` list only the granted repos? | only `SABERBIRD/testING` | **Partly**: the granted private repo plus **all public repos of the owner**; non-granted private repos are absent. The picker shows both, with a lock on private ones. |
| V4 | Format of `github-authentication-token-expiration` | e.g. `2026-12-24 00:00:00 UTC` | **Pass**: `2026-12-24 15:32:55 UTC`, present on every response including errors (absent on `304`) |
| V5 | Does a `304` leave `x-ratelimit-remaining` unchanged? | 2nd and 3rd calls `304`, same `remaining` | **Pass**: `304`, `304`, remaining stayed 4988 |
| V6 | No-access repo / invalid token | `404` / `401` | **Pass**: `404` / `401` |
| V7 | Big public repo with many branches and runs | `200`, `link` header with `rel="next"` | **Pass**, `microsoft/vscode`: runs 400 pages × 100, branches 62 pages × 100. The branch-page cap is too small for such repos, which led to D20 (single-branch lookups for displayed branches). Public repos' branch lists work without Contents: read. |

### V8–V10: device checks (done by the implementing agent on an emulator, API 31 and 36)

| # | Check |
|---|---|
| V8 | A row with 12 `Text` children loses the 11th and 12th (confirms R4); a 200-row `LazyColumn` renders and scrolls. |
| V9 | Character budget: at font 13 sp, scale 1.0 and 1.3, the computed `charsPerLine` fills the row without wrapping on a Pixel launcher at 2×1, 4×2 and 5×5. |
| V10 | Copy from the widget works on API 31 (Toast shown) and API 33+ (system confirmation, no Toast); share opens the chooser; a row tap opens the run in the GitHub app when installed. |

### V11: fixture — resolved

GitHub Actions jobs in the private `SABERBIRD/testING` repository currently fail within 2 seconds, without logs or steps. This is the usual symptom of Actions minutes or billing being unavailable for private repositories on the account. Resolved on 2026-11-24 by making `testING` public. See TESTING.md §3.
