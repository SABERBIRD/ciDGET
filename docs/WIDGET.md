# Widget specification

The widget is built with Jetpack Glance (`GlanceAppWidget`). Platform limits behind these rules are in [RESEARCH.md](RESEARCH.md).

## 1. Look: terminal style

- Font: `FontFamily.Monospace` everywhere. Glance cannot load bundled font files, so this is the device's system monospace font, usually Droid Sans Mono. Font size is a per-widget setting: 11–16 sp, default 13 sp.
- No icons or images except the status glyphs, which are text.
- Palette. The values are a starting point; each text colour must reach 4.5:1 contrast on its background.

| Token | Dark (default) | Light |
|---|---|---|
| `bg` | `#101214` | `#FAFAFA` |
| `fg` (branch, header) | `#D8DEE4` | `#1F2328` |
| `dim` (other columns, meta line) | `#8B949E` | `#59636E` |
| `prompt` (`~/repo $`) | `#58A6FF` | `#0969DA` |
| `red` | `#FF6B6B` | `#CF222E` |
| `yellow` | `#E3B341` | `#9A6700` |
| `green` | `#3FB950` | `#1A7F37` |
| `grey` | `#6E7681` | `#6E7781` |

- Theme setting (D12): `DARK` (default), `LIGHT`, `SYSTEM`. `SYSTEM` uses Glance `ColorProvider(day, night)`; the others pick one palette.

### Status glyphs (D10)

| Status | Unicode style (default) | Bracket style |
|---|---|---|
| RED | `✗` | `[FAIL]` |
| YELLOW | `●` | `[RUN]` |
| GREEN | `✓` | `[OK]` |
| GREY | `○` | `[--]` |

`○` (empty circle) is used for every grey case: no CI on the latest commit (`NO_RUNS`), cancelled, skipped only, not loaded. Branches without runs are shown by default (D21); their other columns are blank and a tap opens the branch page.

Glyph fallback: at startup, and whenever the font settings change, the app checks each glyph with `Paint(Typeface.MONOSPACE).hasGlyph(s)`. Missing glyphs fall back as `✗→x`, `●→*`, `✓→v`, `○→o`, `⧉→cp`, `↗→sh`, `⟳→r`, `…→~`. The result is cached in settings. The glyph is always paired with colour, so status is readable without colour vision.

In approximate-head mode (D8 fallback) the glyph gets a `~` suffix in the status column, for example `✓~`.

## 2. Layout

```
~/testING $ ci               [⟳] [⚙]    <- header: prompt (tap = repo list), refresh, settings
5m ago · ! offline                        <- meta line (only when there is something to say, or always if height allows)
✗ red          #9  CI   3m  [⧉][↗]       <- rows (LazyColumn, scrolls)
● running      #8  CI   4m  [⧉][↗]
✓ main         #1  CI   9m  [⧉][↗]
```

- **Header**: `~/{repo} $ ci` (repo name only, without owner; ellipsized if long) and, right-aligned, `[⟳]` (or `[...]` while a refresh of this repo is running) and `[⚙]` (settings, D22; `[set]` if the font lacks the glyph). No animation: RemoteViews cannot animate cheaply.
- **Meta line** parts, joined with ` · ` and dropped from the right when they don't fit:
  1. age of data: `5m ago`. When older than the stale threshold (D13): `stale 2h`.
  2. the current error from GITHUB_API.md §5 (`! offline`, `! 401 token`, `! rate limit until 14:05`, …).
  3. token expiry warning: `! token expires in 3d`.
  4. `! no token` when none is set (the row area then shows `$ ciDGET: set a token in settings`, and a tap opens settings).
  The meta line is hidden only when it would be just `Nm ago` and the widget height is below 3 lines.
- **Empty states** are written as terminal output: `no runs in the last 30 days`, `repo not selected — tap the header` (opens the repo list).

### Size classes (`SizeMode.Exact`)

`LocalSize.current` gives the exact size in dp. `lineHeight ≈ fontSize × 1.5` (measured once with `Paint.getFontMetrics`).

| Class | Condition | Content |
|---|---|---|
| `TINY` | width < 100 dp **or** height < 2 × lineHeight + padding | One large glyph (about 60% of the smaller side, max 48 sp) in the status colour, plus `!` in a corner when there is an error. It shows the worst status of the repo, or of the pinned branch if the per-widget setting says so (D13). Tap opens the representative run. The refresh button is not shown. |
| `LIST` | otherwise | Header, meta line, then a `LazyColumn` with all rows (scrolls). |

## 3. Columns

| id | Example | Kind | min | max | Default on | Default priority | Align |
|---|---|---|---|---|---|---|---|
| `STATUS` | `✗` | fixed | glyph width | | yes | 100 | left |
| `BRANCH` | `feature/snaps` | flexible | 6 | 40 | yes | 90 | left |
| `AGE` | `5m` | fixed | | 3 | yes | 70 | right |
| `RUN_NUMBER` | `#412` | fixed | | content | yes | 60 | right |
| `COPY` | `[⧉]` / `cp` | fixed | | | yes | 50 | – |
| `SHARE` | `[↗]` / `sh` | fixed | | | yes | 45 | – |
| `WORKFLOW` | `CI` | flexible | 3 | 14 | yes | 40 | left |
| `SHA` | `a1b2c3d` | fixed | | 7 | no | 30 | left |
| `DURATION` | `3m12s` | fixed | | 6 | no | 25 | right |
| `TITLE` | `Fix snaps…` | flexible | 8 | 60 | no | 20 | left |
| `ACTOR` | `rex` | flexible | 3 | 12 | no | 15 | left |
| `EVENT` | `push` | fixed | | 4 | no | 10 | left |

Formats:
- `AGE`: time since the representative run's `updatedAt`, as `now`, `59m`, `23h`, `29d`, `11mo`, `3y`.
- `DURATION`: `45s`, `3m12s`, `59m`, `5h02` (≤ 6 chars).
- `EVENT`: `push`, `pull_request→pr`, `schedule→cron`, `workflow_dispatch→man`, `merge_group→mq`, `release→rel`, anything else → first 4 chars.
- `TITLE`: `display_title`, first line only.
- Empty values (row without runs) render as blanks of the column width.

Per-widget settings for each column: enabled, order (drag to reorder), priority (high / medium / low in the UI, stored as 80 / 50 / 20; the defaults above are the initial values). Rule: `STATUS` and `BRANCH` cannot both be disabled.

### 3.1 Width fitting (pure `:core`, unit-tested)

```
charsPerLine = floor((widgetWidthDp - horizontalPaddingDp) / (fontSizeSp * fontScale * advanceRatio))
```
- `advanceRatio` is measured once: `Paint(Typeface.MONOSPACE).apply { textSize = 100f }.measureText("M") / 100`. It is about 0.6 for Droid Sans Mono.
- `fontScale` = `resources.configuration.fontScale`, because `sp` follows the user's font size setting.

Fitting input: enabled columns in user order with priority, the rows to render, and `charsPerLine`. Separator = 1 space.

1. For every column compute `content` = the longest rendered value over the rows (capped at `max`). Drop columns whose `content == 0` for all rows.
2. A fixed column needs `content` chars. A flexible column needs `min(min, content)`.
3. While `sum(needs) + separators > charsPerLine`: remove the column with the lowest priority (tie: the one later in the order). `STATUS` is removed only if it is the last column left. `BRANCH` is removed only when `STATUS + sep + 3 > charsPerLine`.
4. Spare = `charsPerLine - used`. Give it to flexible columns in descending priority, each up to its `content`.
5. Render each cell padded to its width (numbers and age right-aligned) and truncate overflowing text with `…` at the end.

Text length is counted in code points. East-Asian wide characters and emoji count as 2.

Required tests (L1–L10):
- L1: everything fits.
- L2: the lowest priority is dropped first.
- L3: tie-break by order.
- L4: `STATUS` and `BRANCH` survive to the end.
- L5: very narrow width → `STATUS` only.
- L6: spare width is distributed by priority.
- L7: truncation with `…`.
- L8: empty column dropped.
- L9: wide characters.
- L10: `charsPerLine` formula with a fontScale of 1.3.

### 3.2 Rendering a row within Glance limits

Glance `Row`/`Column`/`Box` accept **at most 10 children** (RESEARCH.md R4). So a row is **not** one `Text` per column. Adjacent text columns with the same colour role are concatenated into one `Text` segment:

- `STATUS` is its own segment, coloured by status.
- `BRANCH` uses `fg`.
- other text columns use `dim`.
- `COPY` and `SHARE` are their own clickable `Text` segments.

If more than 8 text segments would result, all non-status text columns use `fg` and merge. This keeps each row at ≤ 6 views, which also makes the widget faster to inflate.

## 4. Actions

| Target | Action | Implementation |
|---|---|---|
| Row | Open the representative run, or the branch page for `NO_RUNS` | `actionStartActivity(Intent(ACTION_VIEW, url))`. The GitHub app opens it through its App Link if installed, otherwise the browser does. No special casing. |
| `[⧉]` | Copy the run URL | `actionStartActivity` → `ActionActivity` (translucent, `noHistory`, `excludeFromRecents`, `taskAffinity=""`, no animation): `ClipboardManager.setPrimaryClip`, then a Toast *only on API ≤ 32* (API 33+ shows its own clipboard confirmation), then `finish()`. |
| `[↗]` | Share | Same `ActionActivity`: `startActivity(Intent.createChooser(ACTION_SEND text/plain, null))`, then `finish()`. Text: `CI ✗ testING/main #412 CI: https://github.com/…`, using the Unicode glyph even if the widget uses brackets. |
| `[⟳]` | Refresh this repo | `actionRunCallback<RefreshCallback>` → enqueue expedited unique work `refresh:{owner}/{repo}` with `ExistingWorkPolicy.KEEP`. Show `[...]` immediately by writing `refreshing = true` to the repo state. |
| Header prompt | Repo list (D24) | `actionRunCallback<OpenRepoListCallback>`: sets the widget's `repoListOpen` flag (DataStore) and queues the unique worker `repolist`. The widget then shows the repo list instead of the meta line and rows. Without a token the tap opens settings. |
| `[⚙]` | Settings (D22) | `actionStartActivity(WidgetConfigActivity)` (dialog theme) with the `appWidgetId`: the same activity the launcher opens from the widget's reconfigure menu (tokens, repo picker with an `owner/repo` field, columns, display, copy-from). |
| Repo row (list view) | Switch repo | `actionRunCallback<PickRepoCallback>` with the repo and token id: saves them in the widget's config, clears the flag, prunes snapshots, shows `[...]` and queues a refresh. |
| `[x]` (list view) | Back | `actionRunCallback<CloseRepoListCallback>`: clears the flag. |
| TINY widget | Open the representative run of the shown status | Same as a row. |

All `PendingIntent`s are immutable. Every `Intent` carries the `appWidgetId` and a unique data URI or request code, so the intents of different rows and widgets don't collide.

### Repo list view (D24)

```
~ $ ls repos                       [x]
2m ago
> me/ciDGET
  me/dotfiles
  org/service-api
```

Terminal-style rows, the current repo marked `> ` in the prompt colour, archived repos dim. Rows come from `RepoListStore` (`files/repos.json`): the merged `/user/repos` of every `OK` token, each entry with the id of the token that sees it. The widget only reads this cache; the worker fills it when the view is opened, after a token change and in the settings picker. While the cache is empty the view shows `$ loading…`, errors show in the meta line (`! offline`, …) and the old list stays. The view is per widget (`repo_list_open_{id}`), survives reboots and is only shown in LIST size.

## 5. Instances and configuration

- Each `appWidgetId` has its own `WidgetConfig` (ARCHITECTURE.md §4): repo, `useGlobalDefaults`, columns, font size, glyph style, theme, sort, pinned branches, filters, max rows, and the TINY source (worst-of-repo or pinned branch).
- `useGlobalDefaults = true` (the default for a new widget): display settings come from the global defaults; editing them in the widget's settings switches the flag off.
- There is no launcher icon (D22). The configuration activity is the only settings UI: it is opened when the widget is added, from the widget's long-press "reconfigure" entry and from the `[⚙]` cell in the header (fallback for launchers without that entry). Tapping the repo name opens the in-widget repo list (D24), not settings. (`android:configure`, `widgetFeatures="reconfigurable|configuration_optional"`) offers: pick a repo, *copy settings from widget …* (a list of other widget instances by repo name), or keep the global defaults.
- When a widget is removed (`onDeleted`), delete its config. Repo caches that no widget references are deleted by the next refresh.

## 6. Provider XML

`res/xml/cidget_widget_info.xml`:
- `minWidth`/`minHeight` 40dp, `targetCellWidth="4"`, `targetCellHeight="2"`, `minResizeWidth`/`minResizeHeight` 40dp, `resizeMode="horizontal|vertical"`;
- `updatePeriodMillis="0"` (WorkManager schedules refreshes);
- `widgetCategory="home_screen"`, `previewLayout` (a static layout that looks like the terminal list), `description`.
