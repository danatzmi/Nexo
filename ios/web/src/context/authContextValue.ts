import { createContext } from 'react'
import type { User } from 'firebase/auth'
import type { Gym, GymMembership, PlatformRole, UserRole } from '../types'

export interface AuthContextValue {
  user: User | null
  // The signed-in user's first name, read from their `/users/{uid}` profile
  // doc — for dashboard greetings. Empty until that doc loads.
  firstName: string
  platformRole: PlatformRole
  myGyms: GymMembership[]
  currentGym: Gym | null
  gymRole: UserRole
  loading: boolean
  isAdmin: boolean
  // Unified class-management permission, mirroring `AppState.canManageClasses`.
  canManageClasses: boolean
  login: (email: string, password: string) => Promise<void>
  register: (
    email: string,
    password: string,
    firstName: string,
    lastName: string,
  ) => Promise<void>
  logout: () => Promise<void>
  enterGym: (gym: Gym, role: UserRole) => void
  leaveGym: () => void
  // Patches `currentGym` (if it matches) and the corresponding `myGyms`
  // entry in place after a settings save — mirrors `GymSettingsSheet.swift`'s
  // manual sync of `appState.currentGym`/`appState.myGyms`, so a rename or
  // class-type edit appears immediately everywhere in the UI without a
  // full re-fetch.
  updateGymInPlace: (gym: Gym) => void
}

export const AuthContext = createContext<AuthContextValue | undefined>(
  undefined,
)
