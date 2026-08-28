import { deleteApp, initializeApp } from 'firebase/app'
import { createUserWithEmailAndPassword, getAuth, signOut } from 'firebase/auth'
import { doc, setDoc } from 'firebase/firestore'
import { db, firebaseConfig } from '../firebase'

// Mirrors `FirebaseBackend.createUserAccount(email:password:firstName:lastName:)`'s
// `secondaryAuth()` trick: registering a new Firebase Auth user via the
// app's normal `auth` instance would also sign the admin performing this
// action into that brand-new account on this tab (the JS SDK has exactly
// one active session per `Auth` instance) — there's no way to create a
// user "on the side" using the primary instance. Spinning up a second,
// throwaway `FirebaseApp` (same project config, a disposable name) with
// its own `Auth` instance lets the new account get created and its
// `/users/{uid}` profile written without ever touching the admin's own
// signed-in session, then the temporary app is torn down.
export async function createUserAccount(
  email: string,
  password: string,
  firstName: string,
  lastName: string,
): Promise<string> {
  const secondaryApp = initializeApp(firebaseConfig, `secondary-${Date.now()}`)
  const secondaryAuth = getAuth(secondaryApp)
  try {
    const credential = await createUserWithEmailAndPassword(secondaryAuth, email, password)
    const uid = credential.user.uid
    await setDoc(doc(db, 'users', uid), { firstName, lastName, email, role: 'user' })
    return uid
  } finally {
    await signOut(secondaryAuth).catch(() => {})
    await deleteApp(secondaryApp).catch(() => {})
  }
}
