# LibGramPocket

LibGramPocket is a Java 21 Swing app for a personal book collection. Phase 8 hardens storage, adds account password changes and About, and packages the application. Phase 7 adds per-account notifications, due/owed alerts, menu badges, and a periodic scanner. Phase 6 adds borrowing from contacts and returns to lenders; Phase 5 adds lending, returns, itemized charges, payments, and inventory repair. Phase 4 adds settings and remembered preferences; Phase 3 adds contacts; Phase 2 adds owned-book CRUD and search; Phase 1 establishes sign-in and private per-account storage.

## Requirements

- JDK 21

## Build and run

From this directory, build and test the release JAR with `build.bat` on Windows or `sh build.sh` on Linux/macOS. To run tests only, use `test\run-tests.bat` or `sh test/run-tests.sh`. Both runners compile every plain-JDK test main with `-Xlint:all` and print a pass/fail summary.

After building, use `run.bat` on Windows or `./run.sh` on Linux/macOS. Storage is resolved beside the compiled application or JAR, independent of the current working directory. A development-only `--storage <folder>` argument selects alternate storage, for example `run.bat --storage storage-demo`.

Sign up to create an account. Each account has its own CSV files under `storage/users/<username>/`; account credentials are stored in `storage/users.txt` as salted PBKDF2 hashes. Passwords must contain at least 8 characters. The title-strip account menu provides Change password. To reset local data, close the app and remove `storage/`.

## Project layout

```text
src/
  Main.java
  data/       CSV utilities, account paths, and repositories
  model/      Book, Contact, Loan, pricing, and account records
  services/   Authentication, catalog, contacts, lending, charge rules, and search
  ui/         Swing screens, lend/borrow/return flows, notification log, contact manager, and layout
storage/      Runtime data, created on first launch
```

## Demo data

Run the development-only generator after building:

```sh
java -cp out DemoData
java -cp out DemoData --large
```

It creates fictional account `demo` in a separate `storage-demo/` folder. The demo password is `demo-password`. The default dataset contains 80 books, 20 contacts, and 120 loans; `--large` contains 5,000 books, 500 contacts, and 10,000 loans. The generator refuses to overwrite an existing demo folder. Start the app against it with `run.bat --storage storage-demo` or `./run.sh --storage storage-demo`.

## Backups and recovery

On the first sign-in of each day, existing account `.txt` files are copied to `storage/users/<username>/backups/`. The newest five dated backups are retained per file. Atomic writes also maintain a `.bak` recovery copy. If a file cannot be parsed, the app tries its `.bak` and reports the file it recovered. If both copies are unreadable, both are retained with a `.corrupt-<timestamp>` suffix and that dataset starts empty; neither damaged file is overwritten. To restore a dated snapshot manually, close the app, preserve the current file, copy the chosen backup to the account folder under the original filename, then restart and verify the data.

Logs are written to `storage/logs/libgrampocket-YYYY-MM-DD.log` and retained for 14 days. Account data, backups, logs, and generated demo data are local and should not be shared unless intentionally anonymized.

## First run

1. Build the JAR and sign up with a safe username and a password of at least 8 characters.
2. Add contacts and books, then configure pricing and alert defaults in Settings.
3. Record lends and borrows from their respective menus; the right panel shows account-local activity.
4. Use the account menu to change the password. Help → About shows app version, Java version, and active storage location.

## Assumptions

- Usernames used for account folders are restricted to 1–32 ASCII letters, digits, underscores, or hyphens.
- Disabled pricing terms preserve their remembered rates; a zero penalty cap means uncapped.
- A return action applies one return date to all selected loans.
- Over-lent owned-book inventory is repaired to zero available copies.
- New accounts start with charges disabled.
- Lend and Borrow each remember their last-confirmed pricing before falling back to defaults.
- Contact list previews are limited to 48 characters; full details remain available in the contact view.
- The last open menu and catalog sort are saved per account.
- Backups are taken at first sign-in each day, with five dated copies retained per file.
- A second process using the same storage folder is refused.
- Demo data is fictional and uses a separate `storage-demo/` directory.
- The initial release version is `1.0.0`; the small demo set uses 80 books, 20 contacts, and 120 loans.
- Performance targets are guidelines for an ordinary laptop, not guarantees.

## Future ideas

- Online community features that connect contacts to LibGram users.
- A lost-book workflow for books that are not returned.

## Smoke test

The test scripts discover and run every `*Test` main, including storage-path verification and account isolation.

Settings and preferences are stored per account in `storage/users/<username>/settings.txt`.
Loans are stored per account in `storage/users/<username>/loans.txt`. Lend and return operations update the loan and book repositories together; startup reconciles owned-book availability against active lends.

Borrowed books are visible in the catalog while active and have a Borrowed ownership badge. The catalog's Show borrowed (incl. returned) filter also reveals archived records after return. Borrowing and returning preserve the lender, pricing snapshot, and loan history per account. Notifications are stored in each account's `notifications.txt`; scanner alerts are disabled when that account's `alerts.enabled` setting is false, while event entries continue to be recorded.
