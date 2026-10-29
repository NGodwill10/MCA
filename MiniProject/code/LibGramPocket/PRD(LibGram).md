# LibGramPocket — Consolidated Product Requirements

**Release scope:** Phases 1–8  
**Platform:** Java 21, Swing, plain-JDK build  
**Product type:** Local personal book-collection and lending application

## 1. Product overview

LibGramPocket is a private, account-based desktop application for managing owned books, contacts, lending, borrowing, returns, pricing, payments, and account-local notifications. Each account has isolated CSV storage and can use the application without a network connection.

This document combines the original product requirements with the Contacts, Settings and Pricing, Lend Flow and Returns, Borrow Flow, Notifications, and Polish/Hardening specifications.

## 2. Accounts and privacy

- Users sign up and sign in locally.
- Store credentials as salted PBKDF2 password hashes.
- Require passwords of at least eight characters.
- Keep books, contacts, loans, settings, and notifications isolated under the signed-in account.
- Provide account password changes and reject unsafe account-folder names.
- Refuse a second process using the same storage folder.

## 3. Core user journeys

1. Sign up or sign in to a private account.
2. Maintain a personal catalog of owned books.
3. Maintain contacts who may lend books to or borrow books from the user.
4. Configure pricing and remembered preferences.
5. Record books lent to contacts and receive them back.
6. Record books borrowed from contacts and return them to lenders.
7. Review charges, payments, due dates, and account-local activity.
8. Use alerts, menu badges, and the notification panel to manage upcoming work.

## 4. Functional requirements

### Personal catalog

- Support create, view, update, delete, search, and inventory operations for owned books.
- Enforce ISBN uniqueness and valid copy counts.
- Distinguish owned and borrowed books in the catalog.
- Preserve returned borrowed records when the history filter is enabled.
- Repair over-lent owned-book inventory to zero available copies.

### Contacts

- Support contact creation, editing, deletion, search, and detail views.
- Validate required contact data and reject duplicates.
- Provide a reusable contact picker for lending and borrowing.
- Show contact-specific lent books, borrowed books, balances, and history.
- Limit list previews while retaining full details in the contact view.

### Lend flow

- Select a contact and one or more owned books through a guided lending cart.
- Validate inventory, duplicate selections, active loans, dates, and pricing.
- Record itemized charges and a pricing snapshot.
- Support receiving returns and applying one return date to selected loans.
- Provide active-lend and lend-history views.

### Borrow flow

- Select a contact and a book to borrow from that lender.
- Preserve lender identity, pricing snapshot, due dates, and loan history.
- Show active borrows and borrow history.
- Support returning borrowed books to the lender.
- Keep borrowed items visible in the catalog while active and label their ownership clearly.

### Pricing, charges, and payments

- Provide a settings pricing form for lend and borrow terms.
- Support remembered pricing preferences and disabled pricing terms.
- Treat a zero penalty cap as uncapped.
- Calculate charges consistently in the service layer.
- Preserve the pricing terms used when a loan was created.
- Record payments and show outstanding amounts.

### Notifications and alerts

- Store account-local notifications in `notifications.txt`.
- Record events even when alert scanning is disabled.
- Show context-sensitive activity in the right panel.
- Provide menu badges and periodic scanning for due, overdue, and owed alerts.
- Allow alert behavior to be configured per account.

### Settings and preferences

- Provide settings for pricing, defaults, alerts, and remembered workflow preferences.
- Persist the last open menu and catalog sort per account.
- Keep settings private to the current account.

### About and account tools

- Provide a Change Password action in the account menu.
- Provide Help → About with application version, Java version, and active storage location.

## 5. User interface requirements

- Use a clear Swing main frame with navigation, catalog screens, contact manager, lending and borrowing workspaces, return dialogs, settings, notifications, and account tools.
- Use compact, guided pickers and carts for lend/borrow flows.
- Keep validation messages specific and close to the invalid action.
- Make active versus historical loans visually distinct.
- Show empty states, ownership badges, menu badges, search, sorting, and filters consistently.
- Keep the interface usable for both a small personal collection and the documented large demo dataset.

## 6. Data model and storage

Global account data is stored in `storage/users.txt`. Each account stores private files below `storage/users/<username>/`:

- `books.txt` — owned and borrowed catalog records
- `contacts.txt` — people connected to the account
- `loans.txt` — lend and borrow history
- `settings.txt` — pricing and preferences
- `notifications.txt` — account-local events and alerts

Atomic writes must preserve the prior file, maintain `.bak` recovery copies, and retain damaged files with a timestamped `.corrupt-<timestamp>` suffix. A dated backup is created at first sign-in each day, with the five newest backups retained per file. Logs are retained for fourteen days.

## 7. Architecture and technical constraints

- Java 21 and standard-library Swing only.
- Separate UI, services, models, and data/repository layers.
- Keep lending, borrowing, pricing, account, notification, and recovery rules in services.
- Build with `javac -Xlint:all` and package an executable `LibGramPocket.jar` with `Main` as the entry point.
- Maintain plain-JDK test mains and an automated smoke/regression suite.
- Preserve interfaces and event boundaries that could support future online community features.

## 8. Delivery phases and acceptance

- **Phase 1:** sign-in, private storage, and base panels.
- **Phase 2:** owned-book CRUD and search.
- **Phase 3:** contacts, validation, search, details, and contact picker.
- **Phase 4:** settings, pricing form, and remembered preferences.
- **Phase 5:** lending, returns, charges, payments, and inventory consistency.
- **Phase 6:** borrowing, lender returns, and borrow history.
- **Phase 7:** notifications, menu badges, due/owed alerts, and periodic scanning.
- **Phase 8:** recovery, backups, logging, account security, UX polish, performance checks, demo data, packaging, and end-to-end verification.

The release is presentation-ready when the application builds cleanly, the full discovered test suite passes, account isolation and recovery work, the JAR launches with `Main`, and the end-to-end lend/borrow/return scenario can be demonstrated using fictional data.

## 9. Out of scope and future work

- Online community features and synchronization.
- Remote identity or payment providers.
- Installer and desktop-shortcut packaging.
- Lost-book workflow for books that are not returned.

## 10. Presentation acceptance checklist

- Run `build.bat` or `sh build.sh` from the project directory.
- Confirm the test summary reports no failures and creates `LibGramPocket.jar`.
- Launch with `run.bat` or `./run.sh`.
- Demonstrate sign-up/login, catalog, contacts, pricing, lend, borrow, returns, notifications, password change, and About.
- Use a disposable or clearly marked demo storage folder for presentation data.
