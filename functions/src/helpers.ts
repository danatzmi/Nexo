import * as admin from "firebase-admin";
import { ActivePlanItemDoc, GymClassDoc, UserRole, PlatformRole } from "./types";

export async function isPlatformAdmin(uid: string): Promise<boolean> {
  const userDoc = await admin.firestore().collection("users").doc(uid).get();
  const role = userDoc.data()?.role as PlatformRole | undefined;
  return role === "admin";
}

export async function getGymRole(gymId: string, uid: string): Promise<UserRole | null> {
  const membershipDoc = await admin.firestore()
    .collection("users").doc(uid)
    .collection("memberships").doc(gymId).get();
  
  if (membershipDoc.exists) {
    return (membershipDoc.data()?.role as UserRole) || "member";
  }
  
  const gymDoc = await admin.firestore().collection("gyms").doc(gymId).get();
  if (gymDoc.data()?.ownerUID === uid) {
    return "owner";
  }

  return null;
}

export function isStaff(role: UserRole | null, isAdmin: boolean): boolean {
  return isAdmin || role === "owner" || role === "coach";
}

export function currentCycleIndex(anchorDate: Date, now: Date = new Date()): number {
  let months = (now.getFullYear() - anchorDate.getFullYear()) * 12;
  months -= anchorDate.getMonth();
  months += now.getMonth();
  if (now.getDate() < anchorDate.getDate()) {
    months -= 1;
  }
  return Math.max(0, months);
}

export function getAvailableCredits(item: ActivePlanItemDoc, now: Date = new Date()): number {
  if (item.expiresAt.toDate().getTime() < now.getTime()) {
    return 0;
  }
  if (item.type === "unlimited") {
    return 0;
  }
  if (item.resetPeriod === "monthly") {
    const anchor = item.cycleAnchorDate ? item.cycleAnchorDate.toDate() : item.expiresAt.toDate();
    const currIndex = currentCycleIndex(anchor, now);
    const lastIndex = item.lastCycleIndex ?? 0;
    const used = currIndex === lastIndex ? (item.cycleCreditsUsed ?? 0) : 0;
    const total = item.creditCount ?? 0;
    return Math.max(0, total - used);
  }
  return Math.max(0, item.remainingCredits ?? 0);
}

export function matchesGymClass(item: ActivePlanItemDoc, gymClass: GymClassDoc, now: Date = new Date()): boolean {
  if (item.expiresAt.toDate().getTime() < now.getTime()) {
    return false;
  }
  if (gymClass.isPremium) {
    return item.workoutType === gymClass.classType;
  }
  return !item.workoutType || item.workoutType === gymClass.classType;
}
