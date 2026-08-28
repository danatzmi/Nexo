# Backfill Members Migration

One-time script to backfill `gyms/{gymId}/members/{uid}` documents for
members who joined a gym before that collection existed (see
`FIRESTORE_SCHEMA.md`). Not part of the app — run manually, once, from a
terminal. Safe to re-run; already-backfilled members are skipped.

## Setup

1. **Get a service account key** (admin credentials — bypasses Firestore
   security rules, so keep this private):
   Firebase Console → Project Settings → Service Accounts →
   "Generate new private key". Downloads a JSON file.

2. **Place the key** either:
   - at `scripts/backfill-members/service-account.json` (gitignored — never
     commit it), or
   - anywhere else on disk, and set
     `export GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json` before
     running the script.

3. **Install dependencies**:
   ```
   cd scripts/backfill-members
   npm install
   ```

## Run

Preview first — logs what would be written without touching the database:
```
npm run backfill:dry-run
```

Then apply for real:
```
npm run backfill
```

## What it does

For every user, reads their `memberships` subcollection. For any
membership with `role: "member"` that doesn't already have a matching
`gyms/{gymId}/members/{uid}` document, it writes one using the user's
`firstName`/`lastName`/`email` and the membership's `joinedAt`.

Memberships pointing at a `gymId` with no corresponding `/gyms` document
are skipped and logged as warnings (data integrity issue, not something
this script should silently paper over).
