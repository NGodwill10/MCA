# LibGram — Consolidated Product Requirements

**Release scope:** Phases 1–9  
**Platform:** Java 21, Swing, plain-JDK build  
**Product type:** Local library-management application for staff-operated collections

## 1. Product overview

LibGram is a desktop library-management system for managing books, members, loans, returns, reservations, charges, payments, settings, and operational notifications. It is designed to work offline with durable CSV storage while keeping service boundaries suitable for a future online implementation.

The release combines the original product requirements with the Lending UI improvement and Phases 3–9 specifications.

## 2. Users and permissions

- Staff users authenticate through the local account store.
- Administrators manage application settings and administrative configuration.
- Members are library patrons represented in the member directory; they do not log into the staff application.
- Permission checks are enforced in services, not only in the UI.

## 3. Core user journeys

1. Sign in and open the main Swing workspace.
2. Search and maintain the book catalog.
3. Create, update, deactivate, and inspect members.
4. Select a member and one or more books through the lending workspace.
5. Validate and record a loan with due dates and inventory updates.
6. Return, replace, or mark books lost; calculate and collect applicable charges.
7. Reserve unavailable books and manage the reservation queue.
8. Review contextual notifications and operational alerts.
9. Configure defaults and alert behavior from the administrator-only settings panel.

## 4. Functional requirements

### Authentication and accounts

- Provide a login screen and staff account management.
- Store credentials as salted PBKDF2 password hashes.
- Enforce a minimum password length of eight characters.
- Prevent multiple processes from using the same storage directory.

### Book catalog

- Support create, read, update, delete, search, and inventory operations for books.
- Enforce ISBN uniqueness and valid copy counts.
- Show availability and reservation state in list and detail views.
- Keep catalog, loan, reservation, and lost-book state consistent.

### Members

- Support member creation, editing, deactivation, deletion, search, and detail views.
- Validate required fields and reject duplicate email addresses and phone numbers.
- Display active loans, dues, payments, and reservations in member context.

### Lending and returns

- Provide a compact member pane, book pane, and lending cart.
- Allow multi-book lending after validating member status, inventory, existing loans, and pricing rules.
- Compute due dates from configured settings.
- Support return, replacement-card, overdue, and lost-book workflows.
- Update loan status and book availability atomically from the service layer.

### Charges and payments

- Maintain a durable charges ledger and payment history.
- Calculate overdue, lost, replacement, and other configured charges using a pricing snapshot.
- Allow staff to collect payments and view outstanding dues.
- Preserve billing history when pricing settings change.

### Reservations

- Allow members to join a book’s pending queue.
- Promote the next eligible reservation when a copy becomes available.
- Support Ready, Pending, and Closed views, expiry, search, and cancellation rules.
- Prevent a reserved copy from being lent to an ineligible member.

### Notifications and alerts

- Record contextual activity and business events in `notifications.txt`.
- Display notifications relevant to the current menu or record.
- Provide alert scanning for overdue loans, ready reservations, and other configured events.
- Allow administrators to configure alert behavior and retain notification history safely.

### Settings

- Provide an administrator-only settings panel.
- Persist defaults for lending, charges, reservations, notifications, and alert scanning.
- Apply remembered settings consistently across launches.

## 5. User interface requirements

- Use a clear Swing workspace with navigation, context-sensitive right-panel activity, list views, search, filters, and detail forms.
- Keep primary workflows interactive and reversible where practical.
- Show validation errors close to the relevant control.
- Keep lending and return actions understandable as a sequence: choose member, choose books, review, confirm.
- Provide empty states, loading-safe behavior, and readable confirmation/error dialogs.
- Preserve selected menu, sorting, and useful workflow state where specified.

## 6. Data model and persistence

The application stores local CSV-style text files under `storage/`:

- `users.txt` — staff accounts and password hashes
- `books.txt` — catalog and inventory
- `members.txt` — member records
- `loans.txt` — active and historical loans
- `charges.txt` — dues ledger
- `payments.txt` — payment records
- `reservations.txt` — reservation queue and status
- `settings.txt` — key/value configuration
- `notifications.txt` — activity and alert records

Writes must use temporary-file replacement and maintain recovery backups. Corrupt files must be preserved and recovered from `.bak` where possible. Daily backups retain the five newest copies per file.

## 7. Architecture and technical constraints

- Java 21 and standard-library Swing only.
- Separate UI, services, models, and data/repository layers.
- Keep business rules in services and repositories, not in Swing event handlers.
- Use interfaces and event listeners where they improve future online readiness.
- Build with `javac -Xlint:all`; package as an executable `LibGram.jar` with `Main` as the entry point.
- Maintain a plain-JDK regression suite covering unit, workflow, recovery, permissions, invariants, and performance smoke tests.

## 8. Delivery phases and acceptance

- **Phase 1:** application structure, authentication, storage, and base panels.
- **Phase 2:** book CRUD, search, and staff accounts.
- **Phase 3:** member management and validation.
- **Phase 4:** administrator settings and defaults.
- **Phase 5:** lending with validation and inventory consistency.
- **Phase 6:** returns, lost books, charges, dues, and payments.
- **Phase 7:** reservations, queues, expiry, and availability.
- **Phase 8:** notification log, timers, right-panel alerts, and alert settings.
- **Phase 9:** hardening, recovery, logging, security review, UX polish, performance checks, demo data, packaging, and end-to-end verification.

The release is presentation-ready when the application builds cleanly, the full regression suite passes, the JAR launches with `Main`, storage recovery and single-instance behavior work, and the end-to-end demo scenario can be completed without data inconsistency.

## 9. Out of scope and future work

- Online synchronization and multi-site deployment.
- Installer and desktop-shortcut packaging.
- Administrative export/import wizard.
- Broader integrations and remote identity providers.

## 10. Presentation acceptance checklist

- Run `build.bat` or `build.sh` from the project directory.
- Confirm the build reports all tests passing and creates `LibGram.jar`.
- Launch with `run.bat` or `run.sh`.
- Demonstrate login, catalog, member, lending, return, dues, reservation, notification, and settings flows.
- Use a disposable or clearly marked demo storage folder for presentation data.
