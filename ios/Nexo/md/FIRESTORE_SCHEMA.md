# Firestore Schema

This document describes the current data structure after the multi-gym refactor.
**All old flat collections (`classes`, `workouts`, `bookings`) must be deleted — they are no longer used.**

---

## `users/{uid}`

Created on sign-up. `uid` is the Firebase Auth UID.

| Field | Type | Values |
|-------|------|--------|
| `firstName` | String | |
| `lastName` | String | |
| `email` | String | |
| `role` | String | `"user"` · `"admin"` |
| `profilePicBase64` | String | Optional. A JPEG profile photo, downscaled to a 300px-max-dimension thumbnail and compressed at quality 0.3 (`ProfileView.uploadProfilePicture`), then Base64-encoded and stored inline — no Firebase Storage bucket in use. Absent until the user uploads a photo; `AvatarView` falls back to a monogram capsule when nil. Written via `updateProfilePicture(base64String:)`. |

> To make someone an admin, set `role` to `"admin"` directly in the Firestore console.
> Platform admins inherit full class-management access and bypass the credit
> wallet check on every gym (`AppState.canManageClasses`,
> `FirebaseBackend.validateAndConsumeMembership`) — this is checked first,
> before that gym's `memberships/{gymId}.role`.

---

## `users/{uid}/memberships/{gymId}`

One document per gym the user belongs to. `gymId` is the gym's UUID string.

| Field | Type | Values |
|-------|------|--------|
| `role` | String | `"owner"` · `"coach"` · `"member"` |
| `joinedAt` | Timestamp | |

> Billing/credits used to live directly on this document (`membershipType`,
> `remainingCredits`, `membershipExpiresAt`) — that flat single-plan model
> was fully replaced by the multi-plan wallet below. No real production
> data used those fields (verified before removal), so no migration was
> needed.

---

## `users/{uid}/memberships/{gymId}/activePlans/{activePlanId}`

A member's credit wallet — one document per granted `PlanComponent`.
`activePlanId` is a Firestore auto-ID. Created by `grantPlanToMember`,
deleted by `revokeActivePlan`.

| Field | Type | Values |
|-------|------|--------|
| `planName` | String | Name of the `MembershipPlan` this item was granted from (denormalized for display — not a live reference) |
| `type` | String | `"unlimited"` · `"credits"` |
| `resetPeriod` | String | `"none"` (fixed punch card total) · `"monthly"` (recurring monthly allowance that resets each cycle) |
| `workoutType` | String? | Optional — one of this gym's own `gyms/{gymId}.workoutTypes` entries, matches `gyms/{gymId}/classes/{classId}.classType`. Absent means "all class types" |
| `creditCount` | Integer | Base credit count (e.g. 12 credits/month or 10 total credits) |
| `remainingCredits` | Integer | Remaining credits for fixed punch cards (`resetPeriod == "none"`). Decremented on booking, refunded on cancel |
| `cycleCreditsUsed` | Integer | Consumed credits in the current monthly cycle for recurring plans (`resetPeriod == "monthly"`) |
| `cycleAnchorDate` | Timestamp | When the plan was granted, used as anchor for monthly cycle boundaries |
| `lastCycleIndex` | Integer | Month index when credits were last consumed (0 for month 1, 1 for month 2, etc.) |
| `expiresAt` | Timestamp | Computed at grant time from the component's validity value/unit or overridden by custom expiration date |

> `book()` matches a class's `classType` against a member's active,
> non-expired items (`workoutType == nil` or equal to the class's type) —
> unless the class is premium (`isPremium == true`), in which case only an
> item with `workoutType` explicitly equal to the class's type matches; a
> generic `workoutType == nil` item does not authorize booking a premium
> class. Priority: any matching `unlimited` item wins first (no credit
> consumed); otherwise the matching `credits` item with the **earliest**
> `expiresAt` and `availableCredits() > 0` is consumed (incrementing
> `cycleCreditsUsed` for monthly recurring plans or decrementing
> `remainingCredits` for fixed punch cards). No matching active items →
> `noActiveMembership`; exhausted credits items → `insufficientCredits`.
> Owners/coaches (and platform admins) bypass this check entirely regardless of wallet contents.

---

## `gyms/{gymId}/membershipPlans/{planId}`

Purchasable package templates a gym owner defines. `planId` is a UUID
string. Granting one to a member (`grantPlanToMember`) creates one
`activePlans` item per component, with `expiresAt` computed from today
(or overridden by a custom expiration date if specified at grant time).
`updateMembershipPlan` overwrites the whole document; `deleteMembershipPlan`
removes it. Neither touches `activePlans` items already granted from this
template — those are independent, denormalized copies (see `planName` on
`activePlanItem`), so editing or deleting a plan template never retroactively
changes what current members already have in their wallet.

| Field | Type | Notes |
|-------|------|-------|
| `name` | String | e.g. `"Gold Membership"`, `"12-Class Monthly"`, `"10-Class Pass"` |
| `type` | String | `"monthly"` · `"class_pass"` |
| `price` | Double | |
| `components` | Array | Each entry: `{ id, type, resetPeriod, workoutType?, creditCount, validityValue, validityUnit }` — same shape as `PlanComponent` |

---

## `gyms/{gymId}`

Created only by a Platform Admin (`createGym`), who assigns the owner by
email — there is no self-serve gym creation and no public join directory.
A member gets access to a gym only when its owner (or a coach) adds them
directly by email from the gym's Members tab. `gymId` is a UUID string.

| Field | Type | Notes |
|-------|------|-------|
| `name` | String | Display name of the gym |
| `ownerUID` | String | Firebase Auth UID of the gym owner. `createGym` first checks `users` for a doc matching the given owner email — if a platform user already exists with that email (e.g. they're already a member/owner elsewhere), their existing UID is reused instead of registering a duplicate Auth account, and their existing `firstName`/`lastName` are used for the new gym's `team` record rather than whatever was typed into the create-gym form. Only registers a brand-new Auth user when the email is unrecognized. |
| `workoutTypes` | Array\<String\> | This gym's own class type names — no longer a fixed enum. Defaults to `["CrossFit WOD", "HIIT", "Strength Training", "Cardio", "Yoga", "Pilates"]` (`WorkoutCategory.defaults`) for gyms without this field (older gyms predate it) and for newly created gyms. Managed via `GymSettingsSheet` (`updateGymSettings`, alongside `name`) — reachable from the gym switcher menu for an Owner/Admin, or from the Platform Dashboard's gym list swipe action for any gym as a platform admin. Referenced by `classType` (below). |
| `city` | String | Optional physical city/location (e.g. `"Tel Aviv, Israel"`). |
| `createdAt` | Timestamp | |

> There is no join-code-based joining, no public join directory, and no
> gym approval workflow — every gym is admin-created and live immediately.
> A user with no gym membership sees an "Awaiting Gym Enrollment" screen
> until an owner adds them by email (`GymMembersView` → `AddMemberView` on
> iOS, the equivalent Add Member flow on Android). Gym docs carry no
> `joinCode` or `status` field; the `gymCodes/{joinCode}` lookup collection
> has been removed entirely (any old data in it is orphaned and unused).

---

## `gyms/{gymId}/classes/{classId}`

`classId` is a UUID string.

| Field | Type | Notes |
|-------|------|-------|
| `title` | String | Free text at the schema level, but `AddClassView` (the only current write path) always sets it equal to `classType` — the Title field was removed from the class builder UI in favor of deriving it from the selected category. Still an independent field, not a computed one, so a future write path (or Android client) could set it to something else. |
| `coach` | String | Display name of the coach — free text, not a UID reference |
| `startTime` | Timestamp | |
| `durationMinutes` | Integer | |
| `capacity` | Integer | Max number of attendees |
| `currentAttendees` | Integer | Maintained by book/cancel operations |
| `waitlistCount` | Integer | Maintained by join/leave-waitlist operations (`adjustClassCounter`). Defaults to `0` if absent (older classes predate this field). |
| `seriesId` | String | Optional UUID string. Present when this class was created as part of a recurring series (`AddClassView`'s Repeat options); absent for one-off classes. Occurrences sharing a `seriesId` are otherwise independent documents — editing/deleting "this and future" (`updateClassSeries`/`deleteClassSeries`) is a batch operation over matching documents, not a reference to a separate series template. |
| `classType` | String | One of this gym's own `gyms/{gymId}.workoutTypes` entries (e.g. `"CrossFit WOD"`, `"Yoga"`, or any custom category the gym has added) — no longer a fixed enum. Defaults to `"CrossFit WOD"` if absent (older classes predate this field). Purely descriptive/filterable — no longer affects booking authorization (see `activePlans` above). |
| `isPremium` | Boolean | Defaults to `false` if absent (older classes predate this field). Labeled "Requires Additional Pay" in the class editor. Display-only — no longer affects booking authorization; the credit wallet stopped gating by class type when `MembershipPlan`/`ActivePlanItem` were simplified (see `activePlans` above). |
| `description` | String | Free-text description of what this class slot is about — e.g. "Open gym, bring your own program" or today's programming notes. Defaults to `""` if absent (older classes predate this field). Shown on `ClassDetailView` whenever non-empty. |

> Classes created via `copySchedule` never carry over the source class's
> `seriesId` — a copy is a standalone occurrence, not a member of the
> original recurring series. `classType` and `isPremium` are carried over.

> **Coach matching is by name, not UID.** `GymHomeViewModel.loadCoachData()`
> (the "My Classes Today" list on a coach's Home tab) filters today's
> classes to `classType.coach == <signed-in coach's fullName>` — there's no
> UID field on this document to join against a coach's `team` entry. This
> means a class silently won't show up on a coach's home screen if their
> `coach` text doesn't exactly match their current profile name (e.g. a
> class created before a name change, or a typo when the class was
> created). Worth a real `coachId: String` field if this becomes a problem
> in practice — not done here since it'd mean a migration for existing
> class documents.

> **`updateClassSeries(gymId:seriesId:from:updatedTemplate:)`** — the "This
> & Future Classes" edit option in `AddClassView`'s confirmation dialog
> (the counterpart to `deleteClassSeries`'s "this and future" delete
> option). Batch-updates every document sharing `seriesId` whose
> `startTime` is on or after `from` (the occurrence being edited, so it and
> everything later in the series). Applies `updatedTemplate`'s title/coach/
> duration/capacity/isPremium/description/classType to each, but **each
> occurrence keeps its own date** — only the time-of-day shifts, taken from
> `updatedTemplate.startTime`. `currentAttendees`/`waitlistCount` are also
> preserved per-occurrence (read fresh from each document, not copied from
> the template), since those are roster state, not part of the edited
> template.

> **Past Class Gating**: `book()` and `joinWaitlist()` both reject a class
> whose `startTime` has already passed, throwing
> `FBError.classInPast`/`MockBackendError.classInPast`. Checked both in
> `ScheduleViewModel` (fails fast client-side with `bookingMessage` set,
> before any backend round-trip) and in `FirebaseBackend`/`MockBackendService`
> themselves (the actual enforcement — the ViewModel check is a UX
> shortcut, not the source of truth). `ScheduleView`'s `ClassRow` hides all
> booking/cancel/waitlist controls entirely for a past class, leaving it as
> a read-only, still-navigable row.

---

## `gyms/{gymId}/team/{uid}`

One document per owner or coach. Written when admin creates a gym, or via
`addTeamMember` when an existing owner/coach adds a team member.

| Field | Type | Values |
|-------|------|--------|
| `role` | String | `"owner"` · `"coach"` |
| `firstName` | String | |
| `lastName` | String | |
| `email` | String | |
| `addedAt` | Timestamp | |

> `addTeamMember(gymId:firstName:lastName:email:password:role:)` lets an
> owner add another team member as either role. Coaches can only add
> coaches — `AddTeamMemberView` hides the role picker and forces `.coach`
> for callers whose `gymRole != .owner`. **This is a UI-layer gate only**:
> `FirebaseBackend.addTeamMember` itself does not check the caller's role,
> so a coach calling it directly (bypassing the view) could currently pass
> `role: .owner`. No Firestore security rules exist yet in this project, so
> nothing enforces this server-side.

> `updateTeamMemberRole(gymId:userId:role:)` updates `role` on both this
> document and `users/{uid}/memberships/{gymId}`.
> `removeTeamMember(gymId:userId:)` deletes both documents, revoking gym
> access entirely (their platform-level `users/{uid}` account is untouched).
> `TeamMemberDetailView` disables both role changes and removal when the
> target is the signed-in user themselves — same UI-layer-only caveat as
> above, to avoid an owner/admin locking themselves out or a gym ending up
> with no owner/coach at all.

> **`addExistingUserToGym(gymId:userId:role:)`** is a second write path
> onto this document (and `members`, below) — it attaches a platform user
> who **already has a `users/{uid}` account** (e.g. a member of another
> gym) to this gym directly, reading their `firstName`/`lastName`/`email`
> off their existing profile instead of collecting them again. Writes here
> when `role` is `.coach` or `.owner`; writes to `members` instead when
> `role` is `.member`. This is the backend for `AddTeamMemberView`'s and
> `AddMemberView`'s "Search" mode — the counterpart to `addTeamMember`/
> `addMember`'s "New Account" mode, which still registers a brand-new Auth
> user from scratch. Neither mode checks whether the target user already
> has a membership doc for this gym under a different role — searching for
> and re-adding an existing team member/member would silently overwrite
> their role rather than being blocked.

---

## `gyms/{gymId}/members/{uid}`

Denormalized snapshot of every member who has joined the gym, written when
`joinGym` or `addMember` runs (or `addExistingUserToGym` with `role:
.member`). `uid` is the Firebase Auth UID (matches
`users/{uid}/memberships/{gymId}`). Only members are stored here — owners
and coaches live in `gyms/{gymId}/team`, not this collection.

| Field | Type | Values |
|-------|------|--------|
| `firstName` | String | |
| `lastName` | String | |
| `email` | String | |
| `role` | String | `"member"` |
| `joinedAt` | Timestamp | |
| `profilePicBase64` | String | Optional, same encoding as `users/{uid}.profilePicBase64`. Not currently denormalized here at write time (`addMember`/`joinGym`/`addExistingUserToGym` never set it) — `fetchMembers` reads it as an optional field only in case a future write path adds it. The live, always-current source is `fetchAttendees`, which reads straight from `users/{uid}` rather than this snapshot, so the class-attendee avatar list is never stale even though this field is currently unused. |

> Members who joined before this collection existed won't have a document
> here until they re-join. No backfill has been run yet.

> `removeMember(gymId:userId:)` deletes this document, the member's
> `users/{uid}/memberships/{gymId}` document, and every item in their
> `activePlans` subcollection (their credit wallet for this gym) — all in
> one batch. Their platform-level `users/{uid}` account and any bookings
> they made are untouched (bookings become orphaned, same as when a class
> is deleted — no cleanup pass exists for either case yet).

---

## `gyms/{gymId}/members/{uid}/workoutLogs/{logId}`

A member's personal activity log for this gym — the Logbook tab's
"Logbook" segment. Personal, not denormalized/shared: nested under the
member's own `members/{uid}` document rather than a gym-wide collection,
and every read/write (`fetchWorkoutLogs`/`addWorkoutLog`/
`updateWorkoutLog`/`deleteWorkoutLog`) is scoped to the signed-in user's
own UID — there's no path for a member to read another member's logs, or
for staff to see them. `logId` is a client-generated UUID string
(`WorkoutLog.id`).

| Field | Type | Notes |
|-------|------|-------|
| `movement` | String | Free text — any activity or movement name the member types in. Fully generic and gym-agnostic; there is no fixed/baseline list on the client. |
| `score` | Double? | Optional generic numeric value — weight (kg), duration, rounds, or any other quantifiable score depending on the activity. Field is absent from the document when the member leaves it blank (an edit that clears it re-writes the whole document without the key, not `null`). |
| `reps` | Integer? | Optional, same absent-when-blank behavior as `score`. |
| `sets` | Integer? | Optional, same absent-when-blank behavior as `score`. |
| `date` | Timestamp | When the session happened (member-supplied, defaults to today in the log-entry sheet) — not `createdAt`. |

> Not cleaned up by `removeMember`/`deleteGym`'s cascades — deleting a
> member or gym leaves their `workoutLogs` subcollection orphaned, same
> accepted gap as bookings orphaned on member/class removal noted above.

---

## `gyms/{gymId}/bookings/{bookingId}`

`bookingId` is auto-generated by Firestore.

| Field | Type | Notes |
|-------|------|-------|
| `userId` | String | Firebase Auth UID of the member |
| `classId` | String | UUID string matching the class document ID |
| `bookedAt` | Timestamp | |
| `activePlanId` | String? | The `activePlans` item consumed to authorize this booking, if a credits-type plan was used. Absent if booked via an unlimited plan or by an owner/coach (nothing to refund on cancel either way). |
| `checkedIn` | Boolean? | Optional (defaults to false if absent). Set to `true` when a coach/owner marks attendance at the door. |
| `checkedInAt` | Timestamp? | Optional. Timestamp when attendance check-in was confirmed. |

> `currentAttendees` on the class document is incremented/decremented automatically when a booking is created or deleted. Do not modify it manually unless resyncing.
> On cancellation, if `activePlanId` is present and that item's `type` is `"credits"`, its `remainingCredits` is refunded by 1.
> `removeBooking` (backing both `cancelBooking` overloads) returns early if
> no matching booking document exists — this guards against a double-tap
> (or any other duplicate cancel call) decrementing `currentAttendees`/
> refunding a credit a second time for a booking that was already removed
> by the first call. Same guard in `MockBackendService.performCancelBooking`.

---

## Setup Order

When setting up a fresh environment:

1. Create a user account via the app (sign up)
2. In Firestore console, set `role: "admin"` on that user's document
3. Sign out and sign back in — you now have app admin access
4. Use "Create a Gym" in the gym picker, enter the owner's email
5. The owner signs in and sees the gym in their picker
