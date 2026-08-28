import { useEffect, useState, type ReactNode } from 'react'
import {
  createUserWithEmailAndPassword,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut as firebaseSignOut,
  type User,
} from 'firebase/auth'
import { collection, doc, getDoc, getDocs, setDoc } from 'firebase/firestore'
import { auth, db } from '../firebase'
import { AuthContext } from './authContextValue'
import type { Gym, GymMembership, PlatformRole, UserRole } from '../types'

const CURRENT_GYM_KEY = 'nexo-current-gym-id'

function gymFromDoc(id: string, data: Record<string, unknown>): Gym {
  return {
    id,
    name: (data.name as string) ?? '',
    ownerUID: (data.ownerUID as string) ?? '',
    workoutTypes: (data.workoutTypes as string[]) ?? [],
  }
}

// Mirrors `resolveMyGyms` in `ContentView.swift`: platform admins see every
// gym in the system (as if they owned each one), everyone else sees only
// their own explicit memberships.
async function resolveMyGyms(
  uid: string,
  platformRole: PlatformRole,
): Promise<GymMembership[]> {
  if (platformRole === 'admin') {
    const snapshot = await getDocs(collection(db, 'gyms'))
    return snapshot.docs.map((gymDoc) => ({
      gym: gymFromDoc(gymDoc.id, gymDoc.data()),
      role: 'owner' as UserRole,
    }))
  }

  const membershipsSnapshot = await getDocs(
    collection(db, 'users', uid, 'memberships'),
  )
  const memberships: GymMembership[] = []
  for (const membershipDoc of membershipsSnapshot.docs) {
    const role = membershipDoc.data().role as UserRole | undefined
    if (!role) continue
    const gymDoc = await getDoc(doc(db, 'gyms', membershipDoc.id))
    if (!gymDoc.exists()) continue
    memberships.push({ gym: gymFromDoc(gymDoc.id, gymDoc.data()), role })
  }
  return memberships
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [firstName, setFirstName] = useState('')
  const [platformRole, setPlatformRole] = useState<PlatformRole>('user')
  const [myGyms, setMyGyms] = useState<GymMembership[]>([])
  const [currentGym, setCurrentGym] = useState<Gym | null>(null)
  const [gymRole, setGymRole] = useState<UserRole>('member')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (firebaseUser) => {
      setLoading(true)

      if (!firebaseUser) {
        setUser(null)
        setFirstName('')
        setPlatformRole('user')
        setMyGyms([])
        setCurrentGym(null)
        setGymRole('member')
        setLoading(false)
        return
      }

      setUser(firebaseUser)

      const profileSnap = await getDoc(doc(db, 'users', firebaseUser.uid))
      const resolvedRole =
        (profileSnap.data()?.role as PlatformRole | undefined) ?? 'user'
      setPlatformRole(resolvedRole)
      setFirstName((profileSnap.data()?.firstName as string | undefined) ?? '')

      const gyms = await resolveMyGyms(firebaseUser.uid, resolvedRole)
      setMyGyms(gyms)

      // Auto-select: restore the last-entered gym from localStorage, or
      // enter automatically if this user only belongs to one — mirrors
      // `AppState.autoSelectGym()`. Admins land on a gym picker instead
      // (parity with iOS's `if resolvedRole != .admin`).
      if (resolvedRole !== 'admin') {
        const savedGymId = localStorage.getItem(CURRENT_GYM_KEY)
        const saved = savedGymId
          ? gyms.find((membership) => membership.gym.id === savedGymId)
          : undefined
        if (saved) {
          setCurrentGym(saved.gym)
          setGymRole(saved.role)
        } else if (gyms.length === 1) {
          setCurrentGym(gyms[0].gym)
          setGymRole(gyms[0].role)
        }
      }

      setLoading(false)
    })

    return unsubscribe
  }, [])

  const login = async (email: string, password: string) => {
    await signInWithEmailAndPassword(auth, email, password)
  }

  const register = async (
    email: string,
    password: string,
    firstName: string,
    lastName: string,
  ) => {
    const credential = await createUserWithEmailAndPassword(auth, email, password)
    await setDoc(doc(db, 'users', credential.user.uid), {
      firstName,
      lastName,
      email,
      role: 'user',
    })
  }

  const logout = async () => {
    await firebaseSignOut(auth)
  }

  const enterGym = (gym: Gym, role: UserRole) => {
    setCurrentGym(gym)
    setGymRole(role)
    localStorage.setItem(CURRENT_GYM_KEY, gym.id)
  }

  const leaveGym = () => {
    setCurrentGym(null)
    setGymRole('member')
  }

  const updateGymInPlace = (gym: Gym) => {
    setCurrentGym((current) => (current?.id === gym.id ? gym : current))
    setMyGyms((gyms) =>
      gyms.map((membership) =>
        membership.gym.id === gym.id ? { ...membership, gym } : membership,
      ),
    )
  }

  const isAdmin = platformRole === 'admin'
  const canManageClasses = isAdmin || gymRole === 'owner' || gymRole === 'coach'

  return (
    <AuthContext.Provider
      value={{
        user,
        firstName,
        platformRole,
        myGyms,
        currentGym,
        gymRole,
        loading,
        isAdmin,
        canManageClasses,
        login,
        register,
        logout,
        enterGym,
        leaveGym,
        updateGymInPlace,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}
