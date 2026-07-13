# Testing

Living document — update it whenever test files are added, removed, or
their purpose changes materially. Not a rolling file like
`SUMMARY.md`/`FEEDBACK.md`; this one accumulates.

## Setup

- Target: `NexoTests`, added via Xcode's own "Unit Testing Bundle"
  wizard (not hand-edited into `project.pbxproj` — see `CLAUDE.md` for why).
- Framework: **Swift Testing** (`import Testing`, `@Test`, `#expect`,
  `#require`), not XCTest. Rationale in `CLAUDE.md`'s Testing section.
- Run: `⌘U` in Xcode, or
  ```
  xcodebuild test -project Nexo.xcodeproj -scheme Nexo -destination 'platform=iOS Simulator,name=<simulator>'
  ```

## What's actually under test — two layers, be precise about which

1. **ViewModels — real app code.** `ScheduleViewModel`,
   `ClassDetailViewModel`, `MemberDetailViewModel`, `GymMembersViewModel`,
   `WorkoutsLibraryViewModel` are the shipped classes, instantiated
   directly in tests with `MockBackendService` injected. Coordination
   logic (loading-state transitions, error-message formatting, local state
   mutation on success/failure, search/filter computed properties) is
   genuinely exercised.
2. **Backend/Firestore logic — tested via a stand-in, not the real thing.**
   `BookingTests`/`WaitlistTests`/`RoleTests` exercise `MockBackendService`
   directly. `MockBackendService` is a hand-written in-memory
   reimplementation of `FirebaseBackend`'s contract — nothing keeps them in
   sync automatically. This already caused one real miss: `FirebaseBackend.book()`
   had no capacity check until writing the mock surfaced the gap (fixed in
   the same cycle). **`FirebaseBackend` itself is never executed by any
   test today.** Closing this gap means Firebase Local Emulator Suite
   integration tests (see `CLAUDE.md`) — proposed, not yet built.

## Test inventory

| File | Suite | Tests | Covers |
|---|---|---|---|
| `BookingTests.swift` | Booking | 4 | Book with capacity, double-booking prevention, full-class rejection (`MockBackendError.classFull`), cancel decrements attendees |
| `WaitlistTests.swift` | Waitlist | 3 | Join increments `waitlistCount`, leave decrements it, cancelling a booking promotes the first waiting user in FIFO order |
| `RoleTests.swift` | Roles | 5 (parameterized) | `UserRole.canManageClasses` across owner/coach/member; `AppState.isAdmin` across platform roles |
| `ScheduleViewModelTests.swift` | ScheduleViewModel | 6 | Book/cancel/waitlist success+failure state mutation, `bookingMessage` alert state, `loadInitialData()` resolves `isLoading` |
| `ClassDetailViewModelTests.swift` | ClassDetailViewModel | 7 | Workout/attendee load success+failure, class/series delete success+failure, `didDelete` flag |
| `MemberDetailViewModelTests.swift` | MemberDetailViewModel | 4 | Booking load + upcoming/past split, cancel-on-behalf-of success+failure |
| `GymMembersViewModelTests.swift` | GymMembersViewModel | 6 | Load success+failure, search filter by name/email/no-match/empty |
| `WorkoutsLibraryViewModelTests.swift` | WorkoutsLibraryViewModel | 5 | Load+sort by name, load failure, filter by type/search/combined |

**Total: 40 tests, 0 known failures.**

## `MockBackendService`

In-memory `BackendService` conformance, state keyed per-gym the same way
Firestore is (`[gymId: [classId: GymClass]]` etc.) — runs in milliseconds,
no network. Key things to know before adding tests:

- **`errorToThrow: Error?`** — set this to make every throwing method fail
  instead of doing its normal work. This is how failure-path tests (load
  errors, action failures) are written; without it there was no way to
  simulate a backend failure.
- **`signedInUID`** — set before calling any method that reads
  `currentUID()` internally (most booking/waitlist methods).
- Mirrors `FirebaseBackend`'s actual behavior where it matters for tests
  (e.g. the capacity check, double-booking no-op, FIFO waitlist order) —
  when you change real logic in `FirebaseBackend`, check whether
  `MockBackendService` needs the matching change too. Nothing enforces
  this automatically (see the two-layers note above).

## Adding new tests

- New ViewModel → new `<Name>ViewModelTests.swift`, one `@Suite`, mark it
  `@MainActor` (ViewModels are `@MainActor`-isolated).
- New backend logic → extend `BookingTests`/`WaitlistTests` or add a new
  suite if it's a new domain area (e.g. payments, check-in).
- Prioritize the logic that's easy to get subtly wrong — capacity/waitlist
  edge cases, permission checks, date/time handling — not exhaustive
  coverage. See `CLAUDE.md`'s Testing section.
- Update the inventory table above when you add or remove a file.
