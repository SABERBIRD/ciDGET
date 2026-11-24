# Testing

## 1. Unit tests (JVM, run in CI on every push)

| Module | What | Spec |
|---|---|---|
| `:core` | aggregation A1–A28 | AGGREGATION.md §7 |
| `:core` | column fitting L1–L10 | WIDGET.md §3.1 |
| `:core` | formatters: age, duration, event, glyph fallback mapping, share text | WIDGET.md §1, §3 |
| `:github` | parsing, pagination, ETag, errors, headers (MockWebServer) | GITHUB_API.md §6 |
| `:app` | `TokenStore` encrypt/decrypt round trip and corrupt-file handling (instrumented, emulator) | SECURITY.md |
| `:app` | `RefreshWorker` with a fake `GitHubClient` (WorkManager `TestListenableWorkerBuilder`, Robolectric allowed here only): error → snapshot kept, `401` → token INVALID, rate limit → skip | ARCHITECTURE.md §3 |

Rules:
- `:core` and `:github` tests must not need Android.
- No real network in tests. JSON fixtures live in `github/src/test/resources/fixtures/`, recorded from `testING` and scrubbed (no tokens, no emails).
- Every bug fix adds a test that fails without it.

## 2. Manual checks before a release

1. Fresh install → add widget → it shows `! no token`.
2. Enter token → pick `SABERBIRD/testING` → the colours match the table in §3.
3. Resize the widget from 1×1 to full screen: TINY glyph, then columns appear by priority, and names are truncated with `…`.
4. Tap a row, `[⧉]` and `[↗]` on API 31 and API 36 (V10).
5. Airplane mode → `[⟳]` → `! offline`, old data still shown, marked `stale` after 2 × interval.
6. Revoke the token on GitHub → `[⟳]` → `! 401 token`; settings show the token as revoked.
7. Pick a repo the token has no access to (typed by hand) → `! 404 repo/no access`.
8. Big repo (`microsoft/vscode`, public) → at most `maxRunPages` run pages fetched, the widget stays responsive.
9. Two widgets with different repos and columns, then copy settings from one to the other.
10. Switch the language to Ukrainian; light theme; system theme.

## 3. Fixture repository `SABERBIRD/testING` (private)

Two workflows (`CI`, `Lint`) run `.ci/run.sh`, which reads its mode for each workflow from `.ci/scenario` (`pass`, `fail`, `slow`, `cancel`, `flaky`).

| Branch | Scenario | Expected |
|---|---|---|
| `main` | CI pass, Lint pass | GREEN |
| `green` | CI pass, Lint pass | GREEN |
| `red` | CI fail | RED, representative = CI |
| `mixed` | Lint fail | RED, representative = Lint |
| `running` | CI ~15 min | YELLOW while running (re-run to repeat) |
| `cancelled` | CI cancels itself | GREY (INCOMPLETE) |
| `flaky` | CI fails on attempt 1, passes on re-run | RED → GREEN after *Re-run failed jobs* |
| `no-ci` | workflows ignore the branch | `○` GREY NO_RUNS, near the bottom; hidden with *hide branches without runs* |
| `new-commit` | green commit, then a newer `[skip ci]` commit | GREY NO_RUNS in accurate mode (old green must not leak) |
| `feature/a-very-long-branch-name-to-test-truncation-in-narrow-widgets` | pass | GREEN, truncated |

**Status (2026-11-24): working.** `testING` is public now (Actions minutes are free); private access was verified before that (V1). All branches show their intended state; the `[skip ci]` commit is on `new-commit`.

Resetting scenarios:
- `running` goes green after ~15 min. Use *Re-run all jobs* on its CI run to get yellow again.
- `flaky` goes green after one re-run. Push any new commit to the branch to get a failing attempt 1 again.

Observed: after a self-cancel, the job is `cancelled` a few seconds before the run's own `status` leaves `in_progress`. The widget shows yellow during that window, which is correct.
