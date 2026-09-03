import { Timestamp } from "firebase-admin/firestore";

export type UserRole = "owner" | "coach" | "member";
export type PlatformRole = "user" | "admin";
export type PlanType = "monthly" | "class_pass";
export type PlanComponentType = "unlimited" | "credits";
export type PlanResetPeriod = "none" | "monthly";
export type ValidityUnit = "days" | "weeks" | "months" | "years";

export interface GymClassDoc {
  title: string;
  coach: string;
  startTime: Timestamp;
  durationMinutes: number;
  capacity: number;
  currentAttendees: number;
  waitlistCount?: number;
  seriesId?: string;
  classType?: string;
  isPremium?: boolean;
  description?: string;
}

export interface ActivePlanItemDoc {
  planName: string;
  type: PlanComponentType;
  resetPeriod?: PlanResetPeriod;
  workoutType?: string;
  creditCount?: number;
  remainingCredits: number;
  cycleCreditsUsed?: number;
  cycleAnchorDate?: Timestamp;
  lastCycleIndex?: number;
  expiresAt: Timestamp;
}

export interface BookingDoc {
  userId: string;
  classId: string;
  bookedAt: Timestamp;
  activePlanId?: string | null;
  checkedIn?: boolean;
  checkedInAt?: Timestamp | null;
}

export interface WaitlistDoc {
  userId: string;
  classId: string;
  joinedAt: Timestamp;
}

export interface PlanComponentInput {
  id?: string;
  type: PlanComponentType;
  resetPeriod?: PlanResetPeriod;
  workoutType?: string | null;
  creditCount?: number;
  validityValue?: number;
  validityUnit?: ValidityUnit;
}

export interface MembershipPlanInput {
  id?: string;
  name: string;
  type?: PlanType;
  price?: number;
  components: PlanComponentInput[];
}
