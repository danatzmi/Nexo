import { doc, getDoc, setDoc, Timestamp, updateDoc } from 'firebase/firestore'
import { db } from '../firebase'
import type { UserRole } from '../types'

// Mirrors `FirebaseBackend.addExistingUserToGym(gymId:userId:role:)` —
// attaches a platform user who already has a `/users/{uid}` account to
// this gym directly (reading their name/email off that existing profile),
// instead of registering a brand-new account. The backend for the
// Members/Team "Search Platform" add flow.
export async function addExistingUserToGym(
  gymId: string,
  userId: string,
  role: UserRole,
): Promise<void> {
  const userSnap = await getDoc(doc(db, 'users', userId))
  if (!userSnap.exists()) throw new Error('User not found')
  const data = userSnap.data()
  const firstName = (data.firstName as string) ?? ''
  const lastName = (data.lastName as string) ?? ''
  const email = (data.email as string) ?? ''

  await setDoc(doc(db, 'users', userId, 'memberships', gymId), {
    role,
    joinedAt: Timestamp.fromDate(new Date()),
  })

  if (role === 'member') {
    await setDoc(doc(db, 'gyms', gymId, 'members', userId), {
      firstName,
      lastName,
      email,
      role,
      joinedAt: Timestamp.fromDate(new Date()),
    })
  } else {
    await setDoc(doc(db, 'gyms', gymId, 'team', userId), {
      role,
      firstName,
      lastName,
      email,
      addedAt: Timestamp.fromDate(new Date()),
    })
  }
}

// Mirrors `FirebaseBackend.updateGymSettings(gymId:name:workoutTypes:)`.
export async function updateGymSettings(
  gymId: string,
  name: string,
  workoutTypes: string[],
): Promise<void> {
  await updateDoc(doc(db, 'gyms', gymId), { name, workoutTypes })
}
