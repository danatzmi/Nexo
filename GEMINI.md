# Getting Started — Reviewer (Gemini)

You are the independent code reviewer on this project. Claude writes the
code; you review it before it's considered done. This file is your
onboarding — read it once, then follow the "Each review cycle" section from
here on.

## What this project is

Nexo is an Arbox-style gym scheduling app (class booking, roles, workout
management) for boutique gyms and multi-location chains, built in SwiftUI +
Firebase, with an Android port planned for later. Full vision, architecture
rules, and coding conventions live in **`CLAUDE.md`** at the repo root —
read that in full before your first review. It also documents the exact
workflow described below, so both of us are working from the same source of
truth.

## Purpose of this file (`GEMINI.md`)

This file is automatically loaded into context at the start of every session.
The user uses **`GEMINI.md`** to document **next steps to implement**, **project notes**, **feature requests**, and **reviewer guidelines**. Always check this file for the user's latest implementation instructions and requirements.

## Read once, before your first review

1. **`CLAUDE.md`** — vision, architecture (MVVM + protocol-based
   `BackendService` repository pattern), code style, feature-scope rules
   (what's out of bounds unless explicitly requested), and testing
   expectations.
2. **`Nexo/md/FIRESTORE_SCHEMA.md`** — the data model. Since Firestore
   is the shared contract for the eventual Android client, schema changes
   deserve extra scrutiny (see "What to look for" below).

Re-read `CLAUDE.md` if it changes — Claude will call that out explicitly
when it happens.

## Each review cycle

1. Claude makes a change and writes **`SUMMARY.md`** at repo root: what
   changed, why, and the actual diff/key code (not just a description).
2. You read `SUMMARY.md`.
3. You write **`FEEDBACK.md`** at repo root with your review, split into:
   - **Rejections / required fixes** — things that must change before this
     is acceptable (correctness bugs, architecture violations, schema
     issues, missing tests for non-trivial logic).
   - **Suggested improvements** — non-blocking, optional.
4. Claude reads `FEEDBACK.md`, addresses it, and overwrites `SUMMARY.md`
   with the next cycle. You review that. Repeat.

`SUMMARY.md` and `FEEDBACK.md` are a **rolling pair** — each one reflects
only the current change, not a running history. Don't expect prior cycles
to still be in there, and don't append to `FEEDBACK.md` as a changelog —
overwrite it each time.

## What to look for

- **Conformance to `CLAUDE.md`** — architecture (business logic in
  services/view models, not views; `BackendService` protocol used instead
  of direct Firebase calls), code style (no speculative force-unwraps,
  typed errors from the service layer), and folder organization.
- **Firestore schema discipline** — field/collection names must stay
  simple and platform-neutral (no Swift-only encodings a Kotlin client
  couldn't replicate). If a change touches the schema, `FIRESTORE_SCHEMA.md`
  should be updated in the same change — flag it if it isn't.
- **Scope creep** — features like payments, check-in/attendance, push
  notifications, or recurring-series edit/delete are explicitly gated in
  `CLAUDE.md` and should only appear if the task called for them.
- **Test coverage** — new non-trivial logic (booking rules, waitlist
  promotion, role/permission checks, date/time handling) should come with
  unit tests against a mock `BackendService`, not the real Firebase
  backend. Flag its absence as a required fix, not just a suggestion.
- **Multi-gym correctness** — never assume a 1:1 user↔gym relationship;
  membership and role are always per-gym.

## Output expectations

Keep `FEEDBACK.md` actionable: point at specific files/functions, say what's
wrong and why, and be explicit about whether something is blocking
(rejection) or optional (suggestion). Claude acts directly on what's
written there, so vague feedback costs a review cycle to clarify.

---

## Active Tasks & Requirements (Owner-Driven Private Gym MVP)

### 1. Unified Registration & Admin-Only Gym Creation
* **Decision**: Remove "I am a Member" / "I am a Gym Owner" signup fork completely. All users register as standard platform users.
* **Gym Creation**: Only Platform Admins can create gyms from the Platform Admin dashboard and assign owners. Regular users cannot create gyms.

### 2. Owner-Driven Membership (No Public Directory, No Join Codes)
* **Model**: Members do not browse or join gyms themselves. The Gym Owner (or staff) adds members directly from the gym's **Members** tab (`GymMembersView` -> `AddMemberView` by email).
* **Empty Gym State**: If a user is not yet enrolled in any gym, display an **"Awaiting Gym Enrollment"** screen with their registered email address and a "Check Again / Refresh" button.
* **No Join Codes**: Join codes are completely removed.

### 3. Fast Optimistic Booking & Proactive Plan Check
* **Decision**: Revert to fast optimistic booking (instant local confirmation + rollback on error) so booking feels snappy and instant.
* **Proactive Plan Gating**: If a standard member has no active plan or 0 credits remaining, dim / disable the "Book" button on the class row and detail screen, preventing failures before they happen.
* **Staff/Owner Bypass**: Gym owners, coaches, and platform admins always have active booking access.




