# LibGram

LibGram is a Java 21 Swing library management app. Phase 9 hardens the Phase 1–8 application for release with recovery-aware CSV storage, rolling backups, single-instance locking, centralized logs, packaging, demo data, and a plain-JDK regression suite.

## Requirements

- JDK 21

## Build, test and run

Windows: run `build.bat`, then `run.bat`. Linux/macOS: run `./build.sh`, then `./run.sh`. The build creates `LibGram.jar` with `Main` as its entry point.

Run tests directly with `test\run-tests.bat` or `./test/run-tests.sh`. The suite compiles with `-Xlint:all`, runs the phase regression tests, and returns a non-zero exit code on failure. The detailed Phase 6 workflow test can also be run directly with `java -cp out PhaseSixWorkflowTest`.

The suite includes charge-boundary, CSV, recovery, permission-matrix, migration, invariant-sequence and performance-smoke checks in addition to the phase service and workflow tests. The performance smoke test exercises the documented 10,000-book, 2,000-member, 20,000-loan and 30,000-charge data sizes.

The first launch asks for an administrator account. A development storage folder can be selected with `java -jar LibGram.jar --storage storage-demo`.

## Demo data

`java -cp out DemoData` creates a small fake dataset. `java -cp out DemoData --large` creates the performance dataset (10,000 books, 2,000 members, 20,000 loans and 30,000 charges). Both use `storage-demo/` and clearly fake names such as `Demo Member 1`.

## Data and backups

Runtime data is in `storage/`, logs are in `storage/logs/`, and rolling daily backups are in `storage/backups/`. The five newest daily backups are retained per file. To restore one, stop LibGram and copy the desired backup over the corresponding data file before restarting. An unreadable CSV first attempts its adjacent `.bak`; if that also fails, the bad file is preserved as `.corrupt-<timestamp>` and the app starts with an empty dataset for that file.

Only one LibGram process may use a storage folder at a time. Writes use a temporary file and atomic replacement, so disk errors leave the prior file intact. Passwords are salted PBKDF2 hashes and must contain at least eight characters.

## Project layout

```text
src/       application, model, CSV repositories, services and Swing UI
test/      plain-JDK regression tests and test runners
storage/   runtime data, logs and backups
out/       compiled classes
```

## Assumptions

- Daily backups are retained as the newest five files per data file.
- The application refuses a second process using the same storage folder.
- `--storage` and `DemoData` are development aids and are not shown in the UI.
- Passwords must contain at least eight characters wherever they are set.
- Performance targets are guidelines for an ordinary laptop.
- A lost loan stores the date it was marked lost in its existing return-date field to preserve the current CSV format.

## Implementation audit

- Phases 1 through 9 are implemented in this project; `LibGramPocket` is a separate project and is not part of this audit.
- Reservations can be created from the lending screen or the Reservations screen using selectable members and books. The Reservations screen supports Ready/Pending/Closed filtering and member/book search.
- Notification View actions switch to the related menu before opening the referenced screen.
- `PhaseSixBillingTest` now explicitly uses a disabled-rent pricing snapshot for its disabled-rent boundary case; the product behavior follows the snapshot rule and is covered by `PhaseSixWorkflowTest`.

## Future ideas

- Add an installer for desktop shortcuts.
- Add an export/import wizard for administrators.
