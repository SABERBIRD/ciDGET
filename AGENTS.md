# Rules for contributors and AI agents

Read these before writing code. Short version: follow the docs, keep `:core` pure, never leak the token, test everything in `:core` and `:github`.

## Read first
1. [docs/TASKS.md](docs/TASKS.md): your task and its acceptance criteria.
2. [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), then the spec your task points to.
3. [docs/DECISIONS.md](docs/DECISIONS.md). Do not reverse a decision on your own; ask the owner and record the change.

## Hard rules
- **Token:** never log it, print it, put it in exceptions, test fixtures, resources, `BuildConfig` or commits. See [docs/SECURITY.md](docs/SECURITY.md).
- **`:core` and `:github` stay free of Android.** No `android.*` or `androidx.*` imports there.
- **No new dependencies** unless they are listed in ARCHITECTURE.md §5. If you need one, stop and ask. It must be GPL-3.0-compatible.
- **No network or `Thread.sleep` in tests.** Use MockWebServer and fake clocks (`kotlinx.datetime.Clock` is injected everywhere `now` is needed).
- **The widget never touches the network.** It reads local stores only.
- Do not use `EncryptedSharedPreferences`, `updatePeriodMillis > 0`, `RemoteViewsService` or Hilt/Koin.
- Every user-visible string is in `values/` **and** `values-uk/`.
- Keep the spec in sync: if the code has to differ from a doc, update the doc in the same PR and say why in the PR description.

## Style
- Kotlin official code style, explicit API mode in `:core` and `:github`.
- Small files; one public concept per file in `:core`.
- No `!!` outside tests. Errors are values (`ApiResult`), not exceptions, across module boundaries.
- KDoc on public declarations in `:core` and `:github`.

## Workflow
- One task = one branch = one PR. Name branches `t<N>-<slug>` (e.g. `t2-aggregation`).
- `./gradlew check` must pass before you push.
- The PR description lists the task's acceptance criteria with how each was verified. Include screenshots for UI.
- Commit messages are in English, imperative mood ("Add aggregation for re-runs").
- License header is not required in each file; the project is GPL-3.0-or-later (see LICENSE).
