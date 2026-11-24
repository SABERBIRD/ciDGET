# ciDGET

A home-screen Android widget that shows the GitHub Actions status of every branch of a repository — private ones included — in a terminal-style list.

```
~/testING $ ci                         [⟳]
updated 2m ago
✗ red          #9   CI    5241ecf  3m
● running      #8   CI    745bf98  4m
✓ main         #1   CI    e4bb352  9m
○ cancelled    #5   CI    bc9123b  9m
```

- Tap a row to open the run, `[⧉]` copies the run link, `[↗]` shares it.
- Resizes from 1×1 (one big status symbol) to full screen.
- No server, no OAuth app: a read-only fine-grained Personal Access Token is stored encrypted on the device.
- Not on Google Play: APKs are built by GitHub Actions and installed manually.

**Status: design phase.** No app code exists yet. The design is in [`docs/`](docs/) and the implementation tasks are in [`docs/TASKS.md`](docs/TASKS.md).

## Documentation

| Document | For |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Modules, data flow, storage, threading |
| [docs/AGGREGATION.md](docs/AGGREGATION.md) | Exact rules for turning workflow runs into a branch colour |
| [docs/GITHUB_API.md](docs/GITHUB_API.md) | Endpoints, headers, ETag, pagination, error mapping |
| [docs/WIDGET.md](docs/WIDGET.md) | Widget UI, sizes, columns, width fitting, actions |
| [docs/SECURITY.md](docs/SECURITY.md) | Token storage and handling rules |
| [docs/RESEARCH.md](docs/RESEARCH.md) | Platform constraints (Glance, clipboard, WorkManager) and open verifications |
| [docs/TESTING.md](docs/TESTING.md) | Test plan and the `testING` fixture repository |
| [docs/BUILD_AND_RELEASE.md](docs/BUILD_AND_RELEASE.md) | Gradle setup, CI, signing, releases |
| [docs/TASKS.md](docs/TASKS.md) | Implementation tasks with acceptance criteria |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Decision log |
| [docs/user/INSTALL.md](docs/user/INSTALL.md) | Installing the APK |
| [docs/user/TOKEN.md](docs/user/TOKEN.md) | Creating the GitHub token |
| [AGENTS.md](AGENTS.md) | Rules for anyone (human or AI) writing code here |

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
