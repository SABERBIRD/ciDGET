# Decision log

Decisions agreed with the project owner. Change one only with the owner's agreement and record the change here.

| # | Decision | Reason |
|---|---|---|
| D1 | License **GPL-3.0-or-later** | Owner wants the strongest practical copyleft. AGPL adds nothing because there is no server. All dependencies are Apache-2.0, which is GPL-3.0-compatible. |
| D2 | Project name **ciDGET**, applicationId `io.github.saberbird.cidget` (debug builds: `.debug` suffix) | |
| D3 | Code, comments, commits and docs in **English**; app UI in **English and Ukrainian** | |
| D4 | **minSdk 31** (Android 12), targetSdk/compileSdk **36** | Native responsive widget sizing, `RemoteCollectionItems` lists without a `RemoteViewsService`, `reconfigurable`/`configuration_optional` widget features, simpler code. |
| D5 | Stack: Kotlin, Jetpack Glance, WorkManager, DataStore, OkHttp, kotlinx.serialization, Compose Material 3 for the settings UI; **manual DI** (no Hilt/Koin) | Minimum dependencies. |
| D6 | **One widget = one repository** | Simpler state model. |
| D7 | Token: fine-grained PAT with **Actions: read**, **Contents: read** and **Metadata: read** (automatic). Contents: read is required by `GET /branches` (verified, V2). The app still works without it, in approximate mode (D8). | Least privilege that still allows D8. Contents: read lets the token read code of the selected repos; accepted by the owner. |
| D8 | **Maximum accuracy**: the real head commit of each branch is taken from the branch list, not guessed from the newest run. A head commit without runs is grey, never the colour of an older commit. If the branch list is unavailable (403), the app falls back to "newest run" mode and the widget marks heads as approximate (`~`). | Owner chose accuracy. |
| D9 | Runs from **forks** are ignored (`head_repository.full_name != repository.full_name`) | A fork's `main` must not look like ours. |
| D10 | **Status symbols**: Unicode `✓ ✗ ● ○` by default, `[OK] [FAIL] [RUN] [--]` as an option. Any glyph missing from the device's monospace font falls back to ASCII automatically. | |
| D11 | Copy/Share buttons: icon glyphs `⧉` / `↗` if the font has them, else `cp` / `sh`. They are columns like any other (default: enabled, low priority). | |
| D12 | **Theme**: dark terminal theme by default; options *dark / light / follow system*. No Material You dynamic colour in the widget. | Keeps the terminal look. |
| D13 | Defaults: background refresh every **30 min**, network required; data marked **stale** after 2× the interval; token-expiry warning **7 days** ahead; run pages per refresh **3 × 100**; hide branches idle for more than **30 days**; max **50** rows; 1×1 mode shows **worst status of the repo**. | |
| D14 | Builds: **debug APK on every push** (workflow artifact) and a **release APK on `v*` tags** (GitHub Release). Both are signed with a **public key committed to the repo** (BUILD_AND_RELEASE.md §1); no secrets. | Owner does not want to manage keys or passwords. Updates install over the old app without losing settings. Accepted risk: a fake APK signed with the same key could replace the app if the user installs it from elsewhere, so install only from this repo's Releases. |
| D15 | github.com only, **no GitHub Enterprise** | |
| D16 | Code is written by **AI agents** from [TASKS.md](TASKS.md); every task is self-contained, with acceptance criteria. | |
| D17 | Success + skipped/neutral = green; success + cancelled/stale = grey. See [AGGREGATION.md](AGGREGATION.md) §4. | Skipped workflows are normal (path filters, conditional jobs). A cancelled workflow means the CI did not finish, so it cannot be called success. |
| D18 | Same workflow triggered by different events on one commit (e.g. `push` and `pull_request`) counts as **separate** workflows | A failing `push` run must not be hidden by a later passing `pull_request` run. |
| D19 | Test fixture repository: **`SABERBIRD/testING`**. Private access was verified on it (V1); it is made public afterwards so Actions minutes are free. | See [TESTING.md](TESTING.md). |
| D20 | Repositories with more branches than the branch-list page cap: exact heads for the branches that are displayed, approximate (`~`) for the rest (GITHUB_API.md §3.1). | Verified with `microsoft/vscode`: ~6,200 branches (62 pages), ~40,000 runs (400 pages). |
| D21 | Branches **without any runs** on their latest commit are **shown by default** as `○` (grey), placed after branches with runs; a setting hides them. | Owner's choice (overrides the brief, which hid them by default). |
