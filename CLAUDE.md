# Nexo — Project Guide

An Arbox-style gym scheduling platform: class booking, roster/roles, and
workout management for boutique gyms and multi-location chains (CrossFit,
Pilates, Yoga, functional training, etc).

## Vision

- **Customers**: both solo boutique gyms and multi-location chains.
- **Members belong to multiple gyms.** A member's identity is platform-wide
  (`users/{uid}`); gym membership and role are per-gym
  (`users/{uid}/memberships/{gymId}`). Never assume a 1:1 user↔gym
  relationship anywhere in the code.
- **This is heading toward a real, dual-platform product.** iOS ships first,
  but an Android client is expected later. Implications, in priority order:
  1. **Firestore is the shared contract.** Collection names, field names,
     and types must stay simple, unambiguous, and platform-neutral
     (see `Nexo/md/FIRESTORE_SCHEMA.md`). No Swift-only encodings
     (e.g. avoid relying on `Codable` quirks that a Kotlin client couldn't
     replicate). Keep `FIRESTORE_SCHEMA.md` current — it's the source of
     truth an Android implementation would build against.
  2. **Business logic stays out of SwiftUI views.** Rules like "can this
     member book this class," "is the waitlist full," "does this booking
     conflict" belong in services/view models, not in `View` bodies. This
     doesn't get shared with Android directly, but it keeps the logic
     readable as a spec Android can mirror, and keeps it unit-testable.
  3. Don't build literal cross-platform tooling (no KMM, no React Native)
     unless explicitly asked — right now this just means *disciplined
     layering*, not a second codebase.

## Feature scope

Already built: multi-gym membership, roles (platform admin / owner / coach /
member), class scheduling (incl. recurring series), booking, waitlist,
workouts library.

On the roadmap, **not to be built proactively** — implement only when
explicitly instructed, one at a time:
- Payments / memberships (billing plans, packages, credits)
- Check-in / attendance tracking
- Push notifications (booking reminders, waitlist promotion, class changes)
- Recurring class series refinements (edit/delete "this and future")

When picking up one of these, ask for the specific behavior wanted
(e.g. payment provider, notification triggers) rather than assuming.

## Architecture

- **Pattern**: MVVM + protocol-based repository. `BackendService` (see
  `Nexo/Services/BackendService.swift`) is the single abstraction over
  Firebase — views and view models never call `Firebase*` APIs directly,
  only through this protocol. `FirebaseBackend` is the concrete
  implementation. This is also what makes unit testing possible: tests use a
  fake/mock conforming to `BackendService`.
- **State**: `AppState` (`@Observable`) holds cross-cutting session state
  (current gym, role, auth). Feature-local state lives in per-feature view
  models, not in `AppState`.
- **Organization**: one folder per feature under `Nexo/Features/`
  (`Schedule`, `Admin`, `Gym`, `Workouts`, `Authentication`, `Platform`).
  Shared domain types live in `Nexo/Models/`. Keep this structure —
  don't introduce a competing organization scheme.
- **Concurrency**: `async/await` throughout (already the convention in
  `BackendService`); no completion-handler APIs for new code.
- **Models**: plain `Codable` structs, `Identifiable` where used in lists.
  IDs are `UUID` for app-created entities (classes, workouts, gyms) and raw
  Firebase Auth UID strings for users — follow the existing pattern per
  `FIRESTORE_SCHEMA.md` rather than inventing a new ID scheme.

## Code style

- Prefer clarity over cleverness; this is a codebase a future Android
  counterpart and future contributors need to read as a spec.
- No force-unwraps (`!`) outside of contexts already established as safe in
  this codebase (e.g. `AppState.gymId` unwrapping `currentGym`, which is a
  precondition, not a guess). New code should propagate `nil`/`throws`
  instead of unwrapping speculatively.
- Errors: throw typed/descriptive errors from the service layer; surface
  user-facing messages in the view layer, not deep in services.
- Comments: only where the *why* isn't obvious from the code (see general
  house style) — don't narrate what the code already says.
- Match existing formatting/naming in a file before introducing a new style
  in it.

## Testing

See `Nexo/md/TESTING.md` for the current test inventory (what's covered,
by which file) and `MockBackendService` usage notes — update it whenever
tests are added, removed, or their purpose changes.

Tests live in the `NexoTests` target, using **Swift Testing**
(`import Testing`, `@Test`/`#expect`/`#require`), not XCTest — chosen for
native `async/await` support (every `BackendService` method is
`async throws`) and parameterized tests (`@Test(arguments:)`), which fits
role/permission-matrix style tests well. Going forward:
- New non-trivial logic (booking rules, waitlist promotion, role checks,
  view model behavior) should ship with unit tests against
  `MockBackendService` (an in-memory `BackendService` conformance in
  `NexoTests/`), not the real Firebase backend.
- Firebase-dependent integration tests (if/when needed) should run against
  the Firebase Local Emulator Suite, never against the production project.
- Don't chase 100% coverage; prioritize the logic that's easy to get subtly
  wrong (capacity/waitlist edge cases, role/permission checks, date/time
  handling for recurring classes).
- No UI test automation (XCUITest) for now — the UI is still moving fast
  enough that automated UI tests would be high-maintenance, low-value.
  Manual verification via `VERIFICATION.md` covers this stage; revisit once
  the UI stabilizes.

## Review workflow

Claude writes code; Gemini reviews it independently before it's considered done. The loop:

1. Claude makes a change.
2. Claude writes **`SUMMARY.md`** (what changed, why, and the actual diff/key code snippets) and **`VERIFICATION.md`** (step-by-step instructions on how to manually verify/test the changes in the running app) at repo root.
3. Gemini reads `SUMMARY.md` and reviews.
4. Gemini writes **`FEEDBACK.md`** at repo root: rejections/required fixes and suggested improvements.
5. Claude reads `FEEDBACK.md`, addresses it, and loops back to step 2 for the next change (overwriting `SUMMARY.md` and `VERIFICATION.md`).

These files are **rolling files**, overwritten each cycle — they reflect the current change under review, not a history log. Don't append to them or treat them as changelogs. If a durable record of a past decision is needed, that belongs in code comments, `FIRESTORE_SCHEMA.md`, or this file — not in `SUMMARY.md`/`FEEDBACK.md`.

`SUMMARY.md` should contain, every time:
- **What changed and why** — plain description of the change and the problem it solves.
- **Diff / key code** — the actual changed code, not just a description of it, so Gemini can review the real thing.

`VERIFICATION.md` should contain, every time:
- **Prerequisites** — any specific roles or setups needed to test.
- **Roster/Playbook** — step-by-step test instructions showing how to navigate and trigger the new feature in the simulator/app UI.

## Reference docs

- `Nexo/md/FIRESTORE_SCHEMA.md` — source of truth for the data model.
  Update it whenever a schema change ships.
- `Nexo/md/TESTING.md` — living test inventory and `MockBackendService`
  notes. Update it alongside any test change.
- `Nexo/md/` also has older working notes (testing guides, manual
  checklists) from earlier in the project — useful history, not guaranteed
  current. `FIRESTORE_SCHEMA.md` and this file take precedence if they
  conflict with anything older in that folder.
