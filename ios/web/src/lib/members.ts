import { collection, doc, getDocs, setDoc, Timestamp, writeBatch } from 'firebase/firestore'
import { db } from '../firebase'
import { createUserAccount } from './createUserAccount'
import type { Member } from '../types'

function memberFromDoc(id: string, data: Record<string, unknown>): Member {
  const firstName = (data.firstName as string) ?? ''
  const lastName = (data.lastName as string) ?? ''
  return {
    id,
    name: `${firstName} ${lastName}`.trim(),
    email: (data.email as string) ?? '',
    joinedAt: (data.joinedAt as Timestamp | undefined)?.toDate() ?? null,
  }
}

// Mirrors `FirebaseBackend.fetchMembers(gymId:)`.
export async function fetchMembers(gymId: string): Promise<Member[]> {
  const snapshot = await getDocs(collection(db, 'gyms', gymId, 'members'))
  return snapshot.docs.map((docSnap) => memberFromDoc(docSnap.id, docSnap.data()))
}

// Mirrors `FirebaseBackend.addMember(gymId:firstName:lastName:email:password:)`
// — registers a brand-new Firebase Auth account (via the secondary-app
// helper, so the admin's own session is untouched), then writes the
// membership + member record.
export async function addMember(
  gymId: string,
  firstName: string,
  lastName: string,
  email: string,
  password: string,
): Promise<void> {
  const newUid = await createUserAccount(email, password, firstName, lastName)

  const joinedAt = Timestamp.fromDate(new Date())
  await setDoc(doc(db, 'users', newUid, 'memberships', gymId), {
    role: 'member',
    joinedAt,
  })
  await setDoc(doc(db, 'gyms', gymId, 'members', newUid), {
    firstName,
    lastName,
    email,
    role: 'member',
    joinedAt,
  })
}

// Mirrors `FirebaseBackend.removeMember(gymId:userId:)` — deletes the
// member record, their membership doc, and every item in their credit
// wallet for this gym, all in one batch.
export async function removeMember(gymId: string, userId: string): Promise<void> {
  const activePlansSnapshot = await getDocs(
    collection(db, 'users', userId, 'memberships', gymId, 'activePlans'),
  )
  const batch = writeBatch(db)
  activePlansSnapshot.docs.forEach((docSnap) => batch.delete(docSnap.ref))
  batch.delete(doc(db, 'users', userId, 'memberships', gymId))
  batch.delete(doc(db, 'gyms', gymId, 'members', userId))
  await batch.commit()
}
