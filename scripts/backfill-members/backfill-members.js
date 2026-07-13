#!/usr/bin/env node
'use strict';

// One-time migration: walks every user's memberships, and for any
// membership with role "member" that doesn't yet have a matching
// gyms/{gymId}/members/{uid} document, writes one. Safe to re-run —
// already-backfilled members are skipped.
//
// Usage:
//   npm install
//   npm run backfill:dry-run   # preview, no writes
//   npm run backfill           # apply

const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');
const path = require('path');

const DRY_RUN = process.argv.includes('--dry-run');

const keyPath = process.env.GOOGLE_APPLICATION_CREDENTIALS
  || path.join(__dirname, 'service-account.json');

let serviceAccount;
try {
  serviceAccount = require(keyPath);
} catch (err) {
  console.error(`Could not load service account credentials from ${keyPath}`);
  console.error('See README.md for how to download one from the Firebase Console.');
  process.exit(1);
}

initializeApp({ credential: cert(serviceAccount) });
const db = getFirestore();

const BATCH_LIMIT = 400; // Firestore batch cap is 500; leave headroom

async function main() {
  console.log(`Starting members backfill${DRY_RUN ? ' (DRY RUN — no writes)' : ''}...\n`);

  const gymsSnapshot = await db.collection('gyms').get();
  const gymMemberSets = new Map(); // gymId -> Set<uid> of members already present

  for (const gymDoc of gymsSnapshot.docs) {
    const membersSnapshot = await db.collection('gyms').doc(gymDoc.id).collection('members').get();
    gymMemberSets.set(gymDoc.id, new Set(membersSnapshot.docs.map(d => d.id)));
  }
  console.log(`Found ${gymMemberSets.size} gym(s).`);

  const usersSnapshot = await db.collection('users').get();
  console.log(`Found ${usersSnapshot.size} user(s).\n`);

  let batch = db.batch();
  let batchCount = 0;
  let backfilled = 0;
  let skippedExisting = 0;
  let skippedOrphanGym = 0;

  const commitBatch = async () => {
    if (batchCount === 0) return;
    if (!DRY_RUN) await batch.commit();
    batch = db.batch();
    batchCount = 0;
  };

  for (const userDoc of usersSnapshot.docs) {
    const uid = userDoc.id;
    const userData = userDoc.data();

    const membershipsSnapshot = await db.collection('users').doc(uid).collection('memberships').get();

    for (const membershipDoc of membershipsSnapshot.docs) {
      const gymId = membershipDoc.id;
      const membership = membershipDoc.data();
      if (membership.role !== 'member') continue;

      const existingMembers = gymMemberSets.get(gymId);
      if (existingMembers === undefined) {
        skippedOrphanGym++;
        console.warn(`  SKIP ${uid}: membership references gym ${gymId}, which has no /gyms document.`);
        continue;
      }
      if (existingMembers.has(uid)) {
        skippedExisting++;
        continue;
      }

      const memberData = {
        firstName: userData.firstName || '',
        lastName: userData.lastName || '',
        email: userData.email || '',
        role: 'member',
        joinedAt: membership.joinedAt || Timestamp.now()
      };

      console.log(`  BACKFILL ${uid} (${memberData.firstName} ${memberData.lastName}) -> gym ${gymId}`);

      const ref = db.collection('gyms').doc(gymId).collection('members').doc(uid);
      batch.set(ref, memberData);
      batchCount++;
      backfilled++;
      existingMembers.add(uid); // guard against double-writing within this run

      if (batchCount >= BATCH_LIMIT) {
        await commitBatch();
      }
    }
  }

  await commitBatch();

  console.log('\nDone.');
  console.log(`  Backfilled:                ${backfilled}`);
  console.log(`  Already present (skipped): ${skippedExisting}`);
  console.log(`  Orphaned membership refs:  ${skippedOrphanGym}`);

  if (DRY_RUN) {
    console.log('\nThis was a dry run — no data was written. Re-run without --dry-run to apply.');
  }
}

main().catch(err => {
  console.error('\nBackfill failed:', err);
  process.exit(1);
});
