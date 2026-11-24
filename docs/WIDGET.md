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
| `grey` | `#8B929A` | `#656D76` |
| `link` | `#79C0FF` | `#0550AE` |

- Theme setting (D12, per widget, "Look" screen): `DARK` (default), `LIGHT`, `SYSTEM`. `SYSTEM` uses Glance `ColorProvider(day, night)`; the others pick one palette. The 1×1 tile keeps its status fill with white text in every theme.

### Status glyphs (D10)

| Status | Unicode style (default) | Bracket style |
|---|---|---|
| RED | `✗` | `[F]` |
| YELLOW | `●` | `[R]` |
| GREEN | `✓` | `[O]` |
| GREY | `○` | `[-]` |

`○` (empty circle) is used for every grey case: no CI on the latest commit (`NO_RUNS`), cancelled, skipped only, not loaded. Branches without runs are shown by default (D21); their other columns are blank and a tap opens the branch page.

Glyph fallback: at startup, and whenever the font settings change, the app checks each glyph with `Paint(Typeface.MONOSPACE).hasGlyph(s)`. Missing glyphs fall back as `✗→x`, `●→*`, `✓→v`, `○→o`, `⧉→cp`, `↗→sh`, `⟳→r`, `…→~`. The result is cached in settings. The glyph is always paired with colour, so status is readable without colour vision.

In approximate-head mode (D8 fallback) the glyph gets a `~` suffix in the status column, for example `✓~`.

## 2. Layout

```
~/testING $ ci                    [⟳]    <- header: prompt (tap = repo list), refresh
5m ago · ! offline                        <- meta line (only when there is something to say, or always if height allows)
✗ red          #9  CI   3m  [⧉][↗]       <- rows (LazyColumn, scrolls)
● running      #8  CI   4m  [⧉][↗]
✓ main         #1  CI   9m  [⧉][↗]
```

- **Header**: `~/{repo} $ ci` (repo name only, without owner; ellipsized if long) and, right-aligned, `[⟳]` (or `[...]` while a refresh of this repo is running). No `[⚙]` in the main header (D27). No animation: RemoteViews cannot animate cheaply.
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
| `TINY` (1×1) | width < 110 dp and height < 110 dp, **or** height < 2 × lineHeight + padding | A tile filled with the status colour, a small symbol and the run number (`#412`), plus `!` in a corner when there is an error. It shows the first pinned branch (the first row if none is pinned), the branch with the newest run, or the worst status, per the setting (D28). The tap opens the run, refreshes, or copies the run link, per the setting. |
| `COLUMN` (1×2+) | width < 110 dp and height ≥ 110 dp | Blue `~$` on top (opens the wide full-screen terminal at the repo list), then per branch, flush left: symbol, run number, `[⧉]`, one under another. A tap on the symbol or number opens the run. |
| `LIST` | otherwise | Terminal screens (D28): the body scrolls, the foot (header, `[<]`, `[x]`) is pinned at the bottom of every screen. |

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

1. For every column compute `content` = the longest rendered value over the rows (capped at the user's `maxChars` for that column if set (1–80, D26), otherwise at `max`). Drop columns whose `content == 0` for all rows.
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
| `[⟳]` | Refresh the repo list and every repo some widget shows | `UiCallback` (`kind = refresh`) → expedited unique work `refresh:{owner}/{repo}` per repo (`KEEP`) and `repolist`. Shows `[...]` immediately. |
| Header prompt | Repo list (D24) | `UiCallback` (`view = REPOS`): stores the view in the widget's config and queues the unique worker `repolist`. Without a token the tap opens settings. |
| `[⚙]` (repo list foot) | Settings (D22, D27, D28) | `UiCallback` (`view = SETTINGS`): the settings are a screen of the widget itself. |
| Repo row (list view) | Switch repo | `UiCallback` (`pick`) with the repo and token id: one config write (repo, token, back to the branch list), prunes snapshots, shows `[...]` and queues a refresh. |
| `[x]` | Back to the branch list | `UiCallback` (`view = BRANCHES`). |
| `[Paste token]` | Save the token on the clipboard | `ActionActivity` (invisible): reads the clipboard once the window has focus, validates and saves the token, clears the clipboard, shows a toast, finishes. |
| `[How to create a token↗]`, `[Repo↗]` | Open a page | `ACTION_VIEW` of the docs URL. |
| `[Delete ciDGET]` | Uninstall | `ACTION_DELETE` for the package: the system's uninstall dialog. |
| `~$` (COLUMN) and an empty TINY | Wide terminal | `WidgetConfigActivity` with the wide flag: the same screens full screen. |
| TINY widget | Per the 1×1 setting (D28) | Open the run, refresh, or copy the run link. |

All `PendingIntent`s are immutable. Every `Intent` carries the `appWidgetId` and a unique data URI or request code, so the intents of different rows and widgets don't collide.

### Repo list view (D24)

```
~ $ ls repos                  [⚙] [x]
2m ago
> me/ciDGET
  me/dotfiles
  org/service-api
```

The repo list is a screen like any other (its foot holds `~ $ ls repos`, `[⚙]`, `[x]` and the age of the list). Rows are terminal-style, the current repo marked `> ` in the prompt colour, archived repos dim. Rows come from `RepoListStore` (`files/repos.json`): the merged `/user/repos` of every `OK` token, each entry with the id of the token that sees it. The widget only reads this cache; the worker fills it when the view is opened, after a token change and in the settings picker. While the cache is empty the view shows `$ loading…`, errors show in the meta line (`! offline`, …) and the old list stays. The view is per widget (`WidgetConfig.view`), survives reboots and is only shown in LIST size.

## 5. Instances and configuration

- Each `appWidgetId` has its own `WidgetConfig` (ARCHITECTURE.md §4): repo, token id, the screen it shows, the selected column, and the display settings (columns with on/priority/max chars, font size, glyph style, language, sort, pinned branches, filters, max rows, the 1×1 source and tap). There are **no global defaults** (D28); "Reset to defaults" restores one widget's display settings.
- The settings are screens of the widget in the terminal look (D25, D28): `[<]`/`[x]` and the title are a foot pinned to the bottom; items are `[buttons]`, `[x]` switches and `(*)` radios; buttons wrap whole.
- Screens: settings (tokens with expiry warnings and the last refresh error, columns, list, look, refresh, reset, repo link, delete), columns shown, order, width, priority, sort & filter (sort key, hide idle after N days, max rows, hide branches without runs), pinned branches, look (font, theme, symbols with a legend), 1×1, background refresh (app-wide: interval or off, Wi-Fi only, not on low battery, token expiry warning days).
- `[Delete]` on a token and `[Reset to defaults]` ask first: a line `Delete token …1c05? [Yes] [No]` appears under the item (D29). Changing screen drops the question. In *order* and *priority* a tap selects a column and `[↑ Up] [↓ Down]` move it; both list only the columns that are shown. In *width* `auto` becomes `[−] n [+]`, a tap on `n` returns to auto.
- There is no launcher icon (D22). The system's "widget settings" (`android:configure`, `widgetFeatures="reconfigurable|configuration_optional"`) opens `WidgetConfigActivity`: for a big enough widget it finishes at once without a window and, in the background, switches the widget to its settings (or to the repo list for a new widget without a repo) and redraws it, again after 1.5 s because the first update of a widget with a configuration activity may be dropped while the launcher places it; for a small widget it shows the same screens full screen, starting from the stored config. It ignores widget ids that do not belong to this app.
- When a widget is removed (`onDeleted`), delete its config. Repo caches that no widget references are deleted by the next refresh.

## 6. Provider XML

`res/xml/cidget_widget_info.xml`:
- `minWidth` 110dp, `minHeight` 40dp, `targetCellWidth="3"`, `targetCellHeight="2"`, `minResizeWidth`/`minResizeHeight` 40dp, `resizeMode="horizontal|vertical"`;
- `initialLayout` and `previewLayout` = `widget_initial` (the empty dark terminal with `~ $ ci`), so a new widget never shows Glance's "Loading" box;
- `updatePeriodMillis="0"` (WorkManager schedules refreshes);
- `widgetCategory="home_screen"`, `description`.
