// Shared types mirroring Nexo/md/FIRESTORE_SCHEMA.md — keep field names and
// value shapes in sync with the iOS models (Gym.swift, UserRole.swift,
// PlatformRole.swift). Firestore is the shared contract across clients.

export type PlatformRole = 'user' | 'admin'
export type UserRole = 'owner' | 'coach' | 'member'

export interface Gym {
  id: string
  name: string
  ownerUID: string
  workoutTypes: string[]
}

export interface GymMembership {
  gym: Gym
  role: UserRole
}

export interface GymClass {
  id: string
  title: string
  coach: string
  startTime: Date
  durationMinutes: number
  capacity: number
  currentAttendees: number
  classType: string
  isPremium: boolean
  description: string
  seriesId?: string
}

export interface Attendee {
  id: string
  name: string
  email: string
}

// A platform-wide user account (`/users/{uid}`) — used by the Members/Team
// "Search Platform" directory search, independent of any gym membership.
export interface PlatformUser {
  id: string
  firstName: string
  lastName: string
  email: string
}

// A gym member (`/gyms/{gymId}/members/{uid}`).
export interface Member {
  id: string
  name: string
  email: string
  joinedAt: Date | null
}

// A gym staff record (`/gyms/{gymId}/team/{uid}`).
export interface TeamMember {
  id: string
  firstName: string
  lastName: string
  email: string
  role: UserRole
}

export type PlanComponentType = 'unlimited' | 'credits'
export type ValidityUnit = 'days' | 'weeks' | 'months' | 'years'

export interface PlanComponent {
  id: string
  type: PlanComponentType
  // null means "all class types" — matches `PlanComponent.workoutType`
  // being optional on iOS.
  workoutType: string | null
  creditCount: number
  validityValue: number
  validityUnit: ValidityUnit
}

export interface MembershipPlan {
  id: string
  name: string
  price: number
  components: PlanComponent[]
}
