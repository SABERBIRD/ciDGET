# Aggregation: from workflow runs to a branch status

This is the specification for the `:core` aggregation code. It is pure Kotlin (no Android, no OkHttp) and must be covered by the unit tests listed in §7.

## 1. Inputs

```kotlin
data class Run(
    val id: Long,
    val workflowId: Long,
    val workflowName: String,      // run.name
    val runNumber: Int,
    val runAttempt: Int,
    val event: String,             // "push", "pull_request", "schedule", "workflow_dispatch", ...
    val status: String,            // raw API value
    val conclusion: String?,       // raw API value, null while not completed
    val headBranch: String?,       // null for some events (e.g. tag pushes) -> run is ignored
    val headSha: String,
    val fromFork: Boolean,         // head_repository.full_name != repository.full_name
    val displayTitle: String,
    val commitMessage: String?,    // head_commit.message, first line only
    val actor: String?,            // actor.login
    val htmlUrl: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val runStartedAt: Instant?,
)

data class BranchHead(val name: String, val sha: String)   // from GET /branches

data class AggregationInput(
    val runs: List<Run>,                 // all runs fetched in this refresh (any order)
    val heads: List<BranchHead>?,        // null = branch list unavailable (fallback mode)
    val runsCoverageStart: Instant?,     // createdAt of the oldest fetched run; null if all runs were fetched
    val now: Instant,
)
```

Unknown `status`/`conclusion` strings must not crash. They map to `UNKNOWN` (grey).

## 2. Run state

Every run maps to exactly one `RunState`:

| RunState | Condition | Colour | Rank (higher = worse) |
|---|---|---|---|
| `FAILED` | `conclusion` ∈ {`failure`, `timed_out`, `startup_failure`, `action_required`} | red | 4 |
| `RUNNING` | `status` ∈ {`queued`, `in_progress`, `waiting`, `pending`, `requested`} | yellow | 3 |
| `INCOMPLETE` | `conclusion` ∈ {`cancelled`, `stale`} | grey | 2 |
| `SUCCESS` | `conclusion` = `success` | green | 1 |
| `NEUTRAL` | `conclusion` ∈ {`skipped`, `neutral`} | grey | 0 |
| `UNKNOWN` | anything else (incl. `status=completed` with null conclusion) | grey | 2 |

`RUNNING` is checked on `status` first. A run that is not `completed` is `RUNNING` (or `UNKNOWN` for an unknown status) regardless of `conclusion`.

## 3. Choosing the commit of a branch

1. Drop runs where `headBranch == null` or `fromFork == true`.
2. Group the remaining runs by `headBranch`.
3. **Head commit of a branch:**
   - **Accurate mode** (`heads != null`): `headSha = heads[branch].sha`. Branches present in `heads` but without runs exist (they matter for "show all branches"). Runs whose branch is not in `heads` (deleted branch) are dropped.
   - **Fallback mode** (`heads == null`): `headSha` = `headSha` of the run with the greatest `createdAt` (tie → greater `id`). The resulting `BranchStatus.headApproximate = true`.
4. **Runs of the commit:** runs of this branch with `run.headSha == headSha`. Runs of the same SHA on *other* branches are **not** borrowed.
5. **Coverage check** (accurate mode only): if the branch has no runs for `headSha` among the fetched runs, but `runsCoverageStart != null` (pagination stopped early), the result is `UNKNOWN_COVERAGE`. The data layer resolves it with a targeted `?head_sha=` request (see GITHUB_API.md §3.3) and runs aggregation again. If that is not possible, the branch is grey with reason `NOT_LOADED`.

## 4. Workflow de-duplication and branch colour

1. Group the commit's runs by key **(`workflowId`, `event`)** (D18).
2. In each group keep the run with the greatest `createdAt` (tie → greater `id`). Re-runs of the *same* run (`runAttempt` > 1) are already collapsed by the API: the list returns the latest attempt.
3. The branch colour comes from the kept runs:

| Kept runs | Branch status |
|---|---|
| none | `NO_RUNS` — grey |
| any `FAILED` | `RED` |
| else any `RUNNING` | `YELLOW` |
| else any `INCOMPLETE` or `UNKNOWN` | `GREY` (reason `INCOMPLETE`) |
| else at least one `SUCCESS` (others `NEUTRAL`) | `GREEN` |
| else (all `NEUTRAL`) | `GREY` (reason `NOTHING_RAN`) |

Cancelled, skipped and neutral are never success by themselves. A missing CI is never success (D17).

## 5. Representative run

The run shown in the row (number, workflow, link, duration…) is the run that decided the colour:

| Status | Representative run |
|---|---|
| RED | the newest `FAILED` run (by `createdAt`, tie → `id`) |
| YELLOW | the newest `RUNNING` run |
| GREY (INCOMPLETE) | the newest `INCOMPLETE`/`UNKNOWN` run |
| GREEN | the newest `SUCCESS` run |
| GREY (NOTHING_RAN) | the newest run |
| NO_RUNS | none; the row links to `https://github.com/{owner}/{repo}/tree/{branch}` |

## 6. Output

```kotlin
enum class Status { RED, YELLOW, GREEN, GREY }

data class BranchStatus(
    val branch: String,
    val status: Status,
    val greyReason: GreyReason?,      // NO_RUNS, INCOMPLETE, NOTHING_RAN, NOT_LOADED
    val headSha: String?,
    val headApproximate: Boolean,
    val representative: Run?,
    val workflowCount: Int,           // number of kept workflow groups
    val lastActivity: Instant?,       // max(updatedAt) of the branch's runs (any commit); used for sorting and the "idle N days" filter
)
```

Duration of the representative run: `updatedAt - runStartedAt` when completed, `now - runStartedAt` while running, `null` if `runStartedAt` is missing.

### Sorting and filtering (also pure `:core`)

Applied after aggregation, in this order:

1. Drop branches with `NO_RUNS` unless "show all branches" is on.
2. Drop branches whose `lastActivity` is older than *hide idle after N days* (pinned branches are never dropped; branches with no activity at all count as idle only when "show all branches" is on).
3. Sort: pinned branches first, in the order they were pinned. Then the remaining branches by the chosen key:
   - `RECENT` (default): `lastActivity` descending, nulls last;
   - `STATUS`: RED, YELLOW, GREY, GREEN, then `RECENT`;
   - `NAME`: case-insensitive natural order.
4. Truncate to *max rows*.
5. "Worst status of the repo" (1×1 mode) is computed over the list **after step 2 and before step 4**, with rank RED > YELLOW > GREY > GREEN. An empty list is GREY.

## 7. Required unit tests

Each row is one test (or more). Fixtures are built with a small test DSL, not JSON.

| # | Case | Expected |
|---|---|---|
| A1 | Two workflows, both success | GREEN, representative = newer success |
| A2 | CI success, Lint failure | RED, representative = Lint |
| A3 | CI failure, Lint in_progress | RED (failure beats running) |
| A4 | CI success, Lint queued | YELLOW, representative = Lint |
| A5 | Same workflow run twice on one SHA (old failure, new success) | GREEN |
| A6 | Same workflow run twice on one SHA (old success, new failure) | RED |
| A7 | Run with `run_attempt = 2`, success | GREEN (attempt collapse is the API's job; the code must not care) |
| A8 | Same workflow, `push` failure and `pull_request` success | RED (D18) |
| A9 | All cancelled | GREY / INCOMPLETE |
| A10 | Success + cancelled | GREY / INCOMPLETE |
| A11 | Success + skipped | GREEN |
| A12 | All skipped | GREY / NOTHING_RAN |
| A13 | Success + neutral | GREEN |
| A14 | `stale`, `action_required`, `startup_failure`, `timed_out` | GREY, RED, RED, RED |
| A15 | Unknown conclusion `"foo"` / unknown status `"bar"` | GREY, no exception |
| A16 | No runs for the branch | NO_RUNS; hidden unless "show all branches" |
| A17 | Accurate mode: head SHA has no runs, an older SHA is green | NO_RUNS (old green must not leak) |
| A18 | Fallback mode: newest run's SHA chosen, `headApproximate = true` | |
| A19 | New red commit on top of a green one | RED |
| A20 | New commit running on top of a red one | YELLOW |
| A21 | Fork run with `head_branch = "main"` | ignored |
| A22 | Same SHA on two branches, runs only on branch A | B is NO_RUNS |
| A23 | Run with `head_branch = null` | ignored |
| A24 | Coverage: head SHA not found, pagination stopped early | `UNKNOWN_COVERAGE` signalled |
| A25 | Sorting: pinned first, then each sort key, ties stable | |
| A26 | Idle filter with pinned branch older than N days | pinned kept |
| A27 | Max rows truncation does not change worst-of-repo | |
| A28 | Duration: completed, running, missing `runStartedAt` | |
