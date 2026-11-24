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
| D7 | Token: fine-grained PAT, **Actions: read** + **Metadata: read**; **Contents: read** is allowed *only if* verification shows it is required for the branch list (see D8) | Least privilege. |
| D8 | **Maximum accuracy**: the real head commit of each branch is taken from the branch list, not guessed from the newest run. A head commit without runs is grey, never the colour of an older commit. If the branch list is unavailable (403), the app falls back to "newest run" mode and the widget marks heads as approximate (`~`). | Owner chose accuracy. |
| D9 | Runs from **forks** are ignored (`head_repository.full_name != repository.full_name`) | A fork's `main` must not look like ours. |
| D10 | **Status symbols**: Unicode `✓ ✗ ● ○` by default, `[OK] [FAIL] [RUN] [--]` as an option. Any glyph missing from the device's monospace font falls back to ASCII automatically. | |
| D11 | Copy/Share buttons: icon glyphs `⧉` / `↗` if the font has them, else `cp` / `sh`. They are columns like any other (default: enabled, low priority). | |
| D12 | **Theme**: dark terminal theme by default; options *dark / light / follow system*. No Material You dynamic colour in the widget. | Keeps the terminal look. |
| D13 | Defaults: background refresh every **30 min**, network required; data marked **stale** after 2× the interval; token-expiry warning **7 days** ahead; run pages per refresh **3 × 100**; hide branches idle for more than **30 days**; max **50** rows; 1×1 mode shows **worst status of the repo**. | |
| D14 | Builds: **debug APK on every push** (workflow artifact) and a **signed release APK on `v*` tags** (GitHub Release). The owner has no keystore yet; the docs explain how to make one. | |
| D15 | github.com only, **no GitHub Enterprise** | |
| D16 | Code is written by **AI agents** from [TASKS.md](TASKS.md); every task is self-contained, with acceptance criteria. | |
| D17 | Success + skipped/neutral = green; success + cancelled/stale = grey. See [AGGREGATION.md](AGGREGATION.md) §4. | Skipped workflows are normal (path filters, conditional jobs). A cancelled workflow means the CI did not finish, so it cannot be called success. |
| D18 | Same workflow triggered by different events on one commit (e.g. `push` and `pull_request`) counts as **separate** workflows | A failing `push` run must not be hidden by a later passing `pull_request` run. |
| D19 | Test fixture repository: private **`SABERBIRD/testING`** | See [TESTING.md](TESTING.md). |
