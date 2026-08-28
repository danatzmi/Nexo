import { collection, doc, getDocs, setDoc, Timestamp } from 'firebase/firestore'
import { db } from '../firebase'
import { createUserAccount } from './createUserAccount'
import type { TeamMember, UserRole } from '../types'

function teamMemberFromDoc(id: string, data: Record<string, unknown>): TeamMember | null {
  const role = data.role as UserRole | undefined
  if (!role) return null
  return {
    id,
    firstName: (data.firstName as string) ?? '',
    lastName: (data.lastName as string) ?? '',
    email: (data.email as string) ?? '',
    role,
  }
}

// Mirrors `FirebaseBackend.fetchTeam(gymId:)`.
export async function fetchTeam(gymId: string): Promise<TeamMember[]> {
  const snapshot = await getDocs(collection(db, 'gyms', gymId, 'team'))
  return snapshot.docs
    .map((docSnap) => teamMemberFromDoc(docSnap.id, docSnap.data()))
    .filter((member): member is TeamMember => member !== null)
}

// Mirrors `FirebaseBackend.addTeamMember(gymId:firstName:lastName:email:password:role:)`.
export async function addTeamMember(
  gymId: string,
  firstName: string,
  lastName: string,
  email: string,
  password: string,
  role: UserRole,
): Promise<void> {
  const newUid = await createUserAccount(email, password, firstName, lastName)

  await setDoc(doc(db, 'users', newUid, 'memberships', gymId), {
    role,
    joinedAt: Timestamp.fromDate(new Date()),
  })
  await setDoc(doc(db, 'gyms', gymId, 'team', newUid), {
    role,
    firstName,
    lastName,
    email,
    addedAt: Timestamp.fromDate(new Date()),
  })
}
