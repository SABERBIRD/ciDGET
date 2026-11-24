# GitHub API usage

All network code lives in the `:github` module (pure JVM, OkHttp + kotlinx.serialization). It knows nothing about Android or widgets.

## 1. Common request rules

| Header | Value |
|---|---|
| `Authorization` | `Bearer <token>` (added by an interceptor; never logged) |
| `Accept` | `application/vnd.github+json` |
| `X-GitHub-Api-Version` | `2022-11-28` (a constant; bump only after checking the changelog) |
| `User-Agent` | `ciDGET/<versionName>` (GitHub rejects requests without one) |
| `Cache-Control` | `no-cache` (forces OkHttp to revalidate with `If-None-Match` instead of serving its 60 s cached copy) |

- Base URL `https://api.github.com` (D15: no Enterprise).
- OkHttp `Cache` in `cacheDir/http`, 10 MB. GitHub sends `ETag` and `Cache-Control: private, max-age=60`, so OkHttp stores responses and sends `If-None-Match` itself. A `304` is returned to the caller as the cached `200`; detect it with `response.networkResponse?.code == 304`. Conditional requests answered with `304` do not count against the rate limit. Verify this with V5 in RESEARCH.md.
- The cache must be **evicted when the token is replaced or deleted**, because it holds private repository data.
- Timeouts: connect 10 s, read 20 s, call 30 s. No automatic retries inside OkHttp (`retryOnConnectionFailure = true` only).
- JSON: `Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }`, decoded as a stream from the response body. Every DTO field that the API may omit is nullable.
- Pagination: follow the `Link: <…>; rel="next"` header. Never build page URLs by hand except for `page=1`.

## 2. Endpoints

| Purpose | Request | Permission (fine-grained PAT) |
|---|---|---|
| Repos the token can see | `GET /user/repos?per_page=100&sort=pushed` (all pages, max 10) | Metadata: read. Returns the granted private repos **plus all public repos of the owner** (V3). |
| Check one repo typed by hand | `GET /repos/{owner}/{repo}` | Metadata: read |
| Branch heads | `GET /repos/{owner}/{repo}/branches?per_page=100` (max 5 pages = 500 branches) | **Contents: read** (V2: `403` with `x-accepted-github-permissions: contents=read` without it). Public repos work without it. |
| Head of one branch | `GET /repos/{owner}/{repo}/branches/{branch}` (URL-encode the name) | Contents: read |
| Workflow runs | `GET /repos/{owner}/{repo}/actions/runs?per_page=100&exclude_pull_requests=true` | Actions: read |
| Runs of one commit | `GET /repos/{owner}/{repo}/actions/runs?head_sha={sha}&per_page=100&exclude_pull_requests=true` | Actions: read |

`exclude_pull_requests=true` only empties the `pull_requests` array, which we do not use, so payloads are smaller.

### DTO fields actually used

`workflow_runs[]`: `id`, `name`, `workflow_id`, `run_number`, `run_attempt`, `event`, `status`, `conclusion`, `head_branch`, `head_sha`, `display_title`, `head_commit.message`, `actor.login`, `html_url`, `created_at`, `updated_at`, `run_started_at`, `head_repository.full_name`, `repository.full_name`. Top level: `total_count`.

`branches[]`: `name`, `commit.sha`.

`/user/repos[]`: `full_name`, `private`, `archived`, `pushed_at`.

Map DTOs to `:core` models (`Run`, `BranchHead`) in `:github`. `:core` never sees DTOs.

## 3. Refresh algorithm for one repository

Input: `owner/repo`, settings (`maxRunPages`, `hideIdleDays`, `hideBranchesWithoutRuns`, pinned branches, `maxRows`).

### 3.1 Branch heads
1. Unless the repo is marked `branchesForbidden` (cached flag, re-checked once per 24 h and on every token change), fetch all branch pages.
2. A `403` here is **not an error**: set `branchesForbidden`, continue in fallback mode (`heads = null`).
3. **Page cap reached** (a `next` link still exists after 5 pages): the list is partial (`headsComplete = false`). After the first aggregation pass, fetch `GET /branches/{branch}` for the branches that will be **displayed** (after filters, sorting and `maxRows`) and pinned branches whose head is still unknown, at most **20** per refresh (ETag'd). Any other branch stays in approximate mode (D20). Example: `microsoft/vscode` has about 62 pages of branches.

### 3.2 Runs
1. Fetch page 1.
2. Fetch the next page only if **all** of these hold:
   - a `next` link exists;
   - fewer than `maxRunPages` pages have been fetched;
   - the oldest run fetched so far is newer than `now - hideIdleDays`;
   - accurate mode: at least one branch from `heads` has no run on its head SHA yet. Fallback mode: always.
3. `runsCoverageStart` = `createdAt` of the oldest fetched run if the loop stopped while a `next` link still existed, else `null`.

### 3.3 Targeted fill-in (accurate mode only)
After a first aggregation pass, for the branches that would be **displayed** (after filters, sorting and `maxRows`) and are flagged `UNKNOWN_COVERAGE`, plus the branch whose head SHA equals the SHA of the oldest fetched run (its runs may continue on the next page), request `?head_sha={sha}`. Do at most **10** such requests per refresh; they use ETags too. Merge the results (dedupe by run `id`) and aggregate again. Branches still unresolved become grey `NOT_LOADED`.

### 3.4 Request budget
Worst case per refresh: 5 (branches) + 20 (single branches, only for huge repos) + 3 (runs) + 10 (targeted) = 38 requests. The idle case is usually 2 requests answered with `304`. The limit for a PAT is 5,000 requests/hour.

## 4. Response headers to record

| Header | Use |
|---|---|
| `github-authentication-token-expiration` | Token expiry, shown in settings and as a widget warning *N* days ahead. Observed format (V4): `2026-12-24 15:32:55 UTC`. Parse `yyyy-MM-dd HH:mm:ss z`, and also accept a numeric offset (`Z`) defensively. Absent = no expiry. |
| `x-ratelimit-remaining`, `x-ratelimit-reset` (epoch seconds), `x-ratelimit-limit`, `x-ratelimit-resource` | Rate-limit state. Before a background refresh, skip it if the saved `remaining == 0` and `reset` is in the future. |
| `retry-after` (seconds) | Secondary rate limit: wait exactly this long. |
| `x-accepted-github-permissions` | Logged in debug builds only. Useful to diagnose a 403. |

## 5. Error mapping

Each request returns `ApiResult<T>` = `Ok(value, meta)` or `Err(error, meta)`. No exceptions escape `:github`.

| Condition | `ApiError` | Widget text | Behaviour |
|---|---|---|---|
| `UnknownHostException`, `ConnectException`, no network | `Offline` | `! offline` | keep cache; WorkManager retries on next connectivity |
| `SocketTimeoutException`, `InterruptedIOException` | `Timeout` | `! timeout` | keep cache; retry with backoff |
| `401` | `TokenInvalid` | `! 401 token` | stop all background refreshes until the token changes; settings shows "token revoked or expired" |
| `403`/`429` and `x-ratelimit-remaining: 0` | `RateLimited(until = reset)` | `! rate limit until 14:05` | skip refreshes until `reset` |
| `403`/`429` with `retry-after` | `RateLimited(until = now + retry-after)` | same | same |
| `429` without either header | `RateLimited(until = now + 60 s)` | same | same |
| other `403` | `Forbidden` | `! 403 no access` | for `/branches` → fallback mode, not shown |
| `404` | `NotFound` | `! 404 repo/no access` | GitHub answers 404 for private repos the token cannot see |
| `5xx` | `Server(code)` | `! github 5xx` | keep cache; retry with backoff |
| other `4xx` | `Http(code)` | `! http 422` | keep cache; no retry |
| JSON parse failure | `Parse` | `! bad response` | keep cache; log the endpoint (never the body) in debug builds |

Errors never delete the cached snapshot. The snapshot carries the last error and when it happened (see ARCHITECTURE.md §4).

## 6. Tests (`:github`, MockWebServer)

- Parsing: a run list fixture recorded from `testING` (token scrubbed). It must contain unknown fields, null `conclusion`, null `head_branch` and a fork run.
- Pagination: `Link` header followed, and the stop conditions of §3.2 (each one separately).
- ETag: the second identical request carries `If-None-Match`, a `304` yields the cached body, and `networkResponse.code == 304`.
- `Cache-Control: no-cache` request header is present.
- Each row of the §5 table, including the 403-with-remaining-0 vs plain-403 distinction and `retry-after` parsing.
- Token expiry header: both formats, missing header.
- The `Authorization` header is never present in anything the module logs.
