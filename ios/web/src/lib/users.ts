import { collection, getDocs } from 'firebase/firestore'
import { db } from '../firebase'
import type { PlatformUser } from '../types'

// Mirrors `FirebaseBackend.fetchAllUsers()` — every platform user, for the
// Members/Team "Search Platform" directory search.
export async function fetchAllUsers(): Promise<PlatformUser[]> {
  const snapshot = await getDocs(collection(db, 'users'))
  return snapshot.docs.map((docSnap) => {
    const data = docSnap.data()
    return {
      id: docSnap.id,
      firstName: (data.firstName as string) ?? '',
      lastName: (data.lastName as string) ?? '',
      email: (data.email as string) ?? '',
    }
  })
}
