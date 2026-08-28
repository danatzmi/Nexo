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

> **Verifying "0 warnings" — use `test` or `build-for-testing`, not
> `build`.** Plain `xcodebuild ... build` only compiles the app target;
> the `NexoTests` target (and any warnings in it) is silently skipped.
> Also, an *incremental* build can under- or over-report warnings for
> files that didn't recompile — for an authoritative check, diff against a
> `build-for-testing` run after touching/rebuilding the relevant files, not
> a cached incremental one.

## What's actually under test — two layers, be precise about which

1. **ViewModels — real app code.** `ScheduleViewModel`,
   `ClassDetailViewModel`, `MemberDetailViewModel`, `GymMembersViewModel`
   are the shipped classes, instantiated directly in tests with
   `MockBackendService` injected. Coordination
   logic (loading-state transitions, error-message formatting, local state
   mutation on success/failure, search/filter computed properties) is
   genuinely exercised.
2. **Backend/Firestore logic — tested via a stand-in, not the real thing.**
   `BookingTests`/`WaitlistTests`/`RoleTests`/`MembershipTests`/
   `ScheduleManagementTests` exercise `MockBackendService` directly.
   `MockBackendService` is a hand-written in-memory
   reimplementation of `FirebaseBackend`'s contract — nothing keeps them in
   sync automatically. This already caused one real miss: `FirebaseBackend.book()`
   had no capacity check until writing the mock surfaced the gap (fixed in
   the same cycle). **`FirebaseBackend` itself is never executed by any
   test today.** Closing this gap means Firebase Local Emulator Suite
   integration tests (see `CLAUDE.md`) — proposed, not yet built.

## Test inventory

| File | Suite | Tests | Covers |
|---|---|---|---|
| `BookingTests.swift` | Booking | 7 | Book with capacity, double-booking prevention, full-class rejection (`MockBackendError.classFull`), cancel decrements attendees, double-cancel (double-tap) doesn't drive attendees negative, past-class booking rejection (`classInPast`), current/future class booking unaffected |
| `WaitlistTests.swift` | Waitlist | 7 | Join increments `waitlistCount`, leave decrements it, cancelling a booking promotes the first waiting user in FIFO order, past-class waitlist-join rejection (`classInPast`), current/future class waitlist-join unaffected, `fetchWaitlistPosition` reports 1-based position + total ordered by join time, returns `nil` for a non-waitlisted user |
| `RoleTests.swift` | Roles | 10 (parameterized) | `UserRole.canManageClasses` across owner/coach/member; `AppState.isAdmin` across platform roles; `AppState.canManageClasses` across the full platform-admin/owner/coach/member hierarchy; `AppState.gymId` doesn't crash with no gym selected and still returns the real id once one is |
| `ScheduleViewModelTests.swift` | ScheduleViewModel | 15 | Book/cancel/waitlist success+failure state mutation, `bookingMessage` alert state, `loadInitialData()` resolves `isLoading`, past-class double-guard on `bookClass`/`joinWaitlist` (sets `bookingMessage`, leaves state unmutated), immediate local `classes` array sync (`currentAttendees`/`waitlistCount`) on all 4 actions without waiting for a reload, `bookingSuccessMessage` set on success (for the booking toast) / left nil on failure, `waitlistSuccessMessage` set on a successful waitlist join (for the "Waitlisted!" popup) |
| `ClassDetailViewModelTests.swift` | ClassDetailViewModel | 16 | Attendee load success+failure, class/series delete success+failure, `didDelete` flag; `loadBookingStatus` reflects an existing booking; `book`/`cancelBooking`/`joinWaitlist`/`leaveWaitlist` success (optimistic `isBooked`/`isWaitlisted`/`currentAttendees`/`waitlistCount` mutation) and failure (revert + `bookingMessage`), past-class booking rejection, `waitlistSuccessMessage` set on a successful waitlist join, `loadBookingStatus` populates `waitlistPosition`/`waitlistTotal` when waitlisted and leaves them nil/0 otherwise |
| `MemberDetailViewModelTests.swift` | MemberDetailViewModel | 11 | Booking load + upcoming/past split, cancel-on-behalf-of success+failure, wallet load, grant-plan/revoke-active-plan state updates, `removeMember` success (deletes member + wallet, returns `true`)/failure (`false` + `errorMessage`) |
| `GymMembersViewModelTests.swift` | GymMembersViewModel | 6 | Load success+failure, search filter by name/email/no-match/empty |
| `MembershipTests.swift` | Membership Plans & Credit Wallet | 20 | Booking resolution priority (unlimited before credits, earliest-expiry credits first), class-type matching/restriction, `noActiveMembership`/`insufficientCredits` failures, expired-item exclusion, owner/coach bypass, platform-admin bypass (even with no gym role and an empty wallet), refund-to-correct-item on cancel, plan CRUD (`createMembershipPlan`/`fetchMembershipPlans`/`updateMembershipPlan`/`deleteMembershipPlan`/`grantPlanToMember`/`revokeActivePlan`), premium class gating (generic plan blocked from premium class, explicit-type plan authorizes it, standard classes unaffected) |
| `ScheduleManagementTests.swift` | Schedule Management | 5 | `copySchedule` duplicates with correct offset + reset counts, no-op on empty source week, ignores out-of-range classes; `updateClassSeries` applies the edited template's fields to every occurrence on/after the edited one while preserving each occurrence's own date (only time-of-day shifts) and its own `currentAttendees`/`waitlistCount` (not overwritten by the template), leaves earlier occurrences untouched, no-op when nothing matches the series |
| `ProfileTests.swift` | Profile | 4 | `updateProfilePicture` sets `profilePicBase64` on the signed-in user (readable back via `fetchUserProfile`), throws `notAuthenticated` with no signed-in user; `fetchAttendees` carries a booked user's `profilePicBase64` through onto the returned `Member` when set, leaves it `nil` when the user has no photo |
| `TeamTests.swift` | Team Management | 8 | `addTeamMember` persists the requested role (`owner`, `coach`); `updateTeamMemberRole` changes an existing member's role; `removeTeamMember` removes exactly that member, others untouched; `addExistingUserToGym` writes a `members` record for `.member` (not `team`), a `team` record for `.coach`/`.owner` (not `members`), and throws `userNotFound` for an unknown `userId` |
| `MembershipPlansViewModelTests.swift` | MembershipPlansViewModel | 4 | `updatePlan`/`deletePlan` success+failure state updates (reload-with-changes, errorMessage on failure, exact-item removal on delete, state untouched on delete failure) |
| `TeamMemberDetailViewModelTests.swift` | TeamMemberDetailViewModel | 6 | `isSelf` true/false (signed-in user vs. another team member); `updateRole`/`removeTeamMember` success+failure state updates |
| `GymHomeViewModelTests.swift` | GymHomeViewModel | 11 | Owner/admin: today's classes + `totalBookingsToday`, load failure. Coach: today's classes filtered to ones matching the signed-in user's profile name. Member: `nextBooking` picks the earliest upcoming booking (or stays nil with none), load failure, `activePlans` populates from the member's credit wallet (and stays empty with none). `userDisplayName` (dashboard greeting) populates from the signed-in user's profile across all three roles |
| `AuthenticationTests.swift` | Authentication | 2 | `sendPasswordReset` succeeds for a registered user's email, throws `userNotFound` for an unregistered one |
| `GymManagementTests.swift` | Gym Settings & Cascade Deletion | 7 | `updateGymSettings` updates name + `workoutTypes` together; `deleteGym` removes the gym document, cascades across classes/team/members/membershipPlans, cascades across bookings/waitlist/wallet (`activePlans`) for that gym, and leaves a different gym's data untouched; `createGym` reuses an existing platform user's account (by email) instead of minting a duplicate when the owner's email is already registered, and still registers a new user when it isn't |
| `ForgotPasswordViewModelTests.swift` | ForgotPasswordViewModel | 4 | `sendResetLink` success (`didSucceed`) + failure (`errorMessage`) state updates, `isValid` across empty/malformed/plausible email, no-op when email is invalid |
| `ClassRecurrenceTests.swift` | Class Recurrence Date Generation | 8 | `generateRecurrenceDates` (the free function backing `AddClassView`'s series generation, extracted out of the View so it's directly testable) across all `RecurrenceType` cases: `.none` returns just the start time, `.daily`/`.weekly`/`.biweekly`/`.monthly` produce the right occurrence counts over a fixed window, `.custom` includes only dates landing on the selected weekdays (and produces nothing when no weekdays are selected), and a recurring type with `start > end` produces no occurrences (unlike `.none`) |
| `GymSwitcherResolutionTests.swift` | Gym Switcher Resolution | 2 | `resolveMyGyms` (the free function backing `ContentView.loadAfterAuth()`'s gym-list load, extracted out of the View so it's directly testable): platform admins load every available gym as switcher entries (each mapped to `.owner`, bypassing explicit memberships entirely); non-admins load only their own explicit memberships with their real role |
| `LogbookTests.swift` | Logbook | 26 | `calculateWeeklyAveragePreviousMonth` (free function backing the "Weekly Avg (<month>)" stat, extracted for testability): averages logged dates over the calendar month before `referenceDate`'s month, ignores dates outside that month (gap months, the reference's own month), is `0.0` with no dates in the previous month or no dates at all; `previousMonthName` (free function backing the same stat's dynamic month label): returns the previous month's full localized name, wraps correctly across a year boundary (January → December); `formattedWeeklyAveragePrevMonth` formats to 1 decimal place with no `"/wk"`/unit suffix; `personalRecords` (free function backing the PR cards): picks the highest logged `score` per movement, ties broken by earliest date, a nil score counts as 0 so a scored entry always beats a scoreless one; `WorkoutLog.formattedDetail` (the value line shown on `MovementCard`/`MovementHistorySheet`): combines score + reps × sets when present, omits whichever of them is nil without a stray "0" or empty label, falls back to `"Logged"` when all three are nil; `MockBackendService` CRUD — `addWorkoutLog`/`fetchWorkoutLogs` round-trip, per-user scoping (another member's logs aren't visible), `deleteWorkoutLog` removes exactly that log; `LogbookViewModel` — `load()` populates `workoutLogs` + filters `pastBookings` to already-started classes, `addLog`/`updateLog`/`deleteLog` optimistic update + backend persistence + rollback-with-`errorMessage` on failure (including `addLog`/`updateLog` with all-nil score/reps/sets, and `updateLog` clearing previously-set fields back to nil), `displayedMovements` lists every logged movement/activity name alphabetically with no fixed baseline list (gym-agnostic) |

**Total: 179 tests, 0 known failures, 0 warnings.**

> **The `@MainActor`-on-suite gap is now closed.** Model types (`GymClass`,
> `ActivePlanItem`, `MembershipPlan`, ...) are implicitly `@MainActor`-
> isolated (module-wide `SWIFT_DEFAULT_ACTOR_ISOLATION`), so *any* test
> suite that constructs or reads their properties needs `@MainActor` on
> the `@Suite` — not just suites that test `@MainActor` ViewModels.
> `BookingTests`, `WaitlistTests`, and `MembershipTests` used to be
> exceptions (documented as a "known warning, not fixed" gap) until
> `MembershipTests` made the gap large enough to be worth actually fixing
> instead of tolerating. All three are `@MainActor` now — see "Adding new
> tests" below.

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
- **`grantUnlimitedForTesting(gymId:userId:)`** — one-line convenience for
  tests that need `book()` to succeed but aren't testing billing
  themselves (most `BookingTests`/`WaitlistTests`/ViewModel tests). For
  tests that actually exercise wallet matching/priority/expiry, build
  `ActivePlanItem`s directly and insert into `mock.activePlans` — see
  `MembershipTests.swift`.
- Mirrors `FirebaseBackend`'s actual behavior where it matters for tests
  (e.g. the capacity check, double-booking no-op, FIFO waitlist order) —
  when you change real logic in `FirebaseBackend`, check whether
  `MockBackendService` needs the matching change too. Nothing enforces
  this automatically (see the two-layers note above).

## Adding new tests

- **Always mark new `@Suite` types `@MainActor`.** Not just ViewModel
  suites — any suite touching app model types needs it, since those types
  are implicitly `@MainActor`-isolated (see the callout above). Skipping
  this produces a pile of "main actor-isolated property cannot be accessed"
  warnings that are easy to mistake for noise and ignore.
- New ViewModel → new `<Name>ViewModelTests.swift`, one `@Suite`.
- New backend logic → extend `BookingTests`/`WaitlistTests` or add a new
  suite if it's a new domain area (e.g. payments, check-in).
- Prioritize the logic that's easy to get subtly wrong — capacity/waitlist
  edge cases, permission checks, date/time handling — not exhaustive
  coverage. See `CLAUDE.md`'s Testing section.
- **`GymClass(startTime: Date())` is not "a bookable class" — it's already
  in the past by the time `book()`/`joinWaitlist()` actually run.** Since
  Past Class Gating (`FBError`/`MockBackendError.classInPast`), any fixture
  meant to be bookable needs a startTime with real headroom, e.g.
  `Date().addingTimeInterval(3600)`. This broke several existing fixtures
  when the gating was added (`BookingTests`, `WaitlistTests`,
  `MembershipTests`, `ScheduleViewModelTests`, `ClassDetailViewModelTests`)
  — all now use a future `startTime`. For a fixture that specifically needs
  a **past** booking as pre-existing state (not as the action under test —
  e.g. testing past-bookings display), use
  `MockBackendService.seedBookingForTesting(gymId:classId:userId:)`, which
  bypasses `book()`'s gating entirely, rather than calling `book()` on an
  already-past class.
- Update the inventory table above when you add or remove a file.
