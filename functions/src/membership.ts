import * as admin from "firebase-admin";
import { HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { MembershipPlanInput, ActivePlanItemDoc, ValidityUnit } from "./types";
import { isPlatformAdmin, getGymRole, isStaff } from "./helpers";

interface GrantPlanRequest {
  gymId: string;
  userId: string;
  plan: MembershipPlanInput;
  customExpiresAtMillis?: number;
}

interface RevokePlanRequest {
  gymId: string;
  userId: string;
  activePlanId: string;
}

function computeExpiry(now: Date, value: number, unit?: ValidityUnit): Date {
  const result = new Date(now);
  switch (unit) {
    case "days":
      result.setDate(result.getDate() + value);
      break;
    case "weeks":
      result.setDate(result.getDate() + value * 7);
      break;
    case "years":
      result.setFullYear(result.getFullYear() + value);
      break;
    case "months":
    default:
      result.setMonth(result.getMonth() + value);
      break;
  }
  return result;
}

export async function handleGrantPlan(request: CallableRequest<GrantPlanRequest>) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be authenticated.");
  }

  const callerUid = request.auth.uid;
  const { gymId, userId, plan, customExpiresAtMillis } = request.data;

  if (!gymId || !userId || !plan) {
    throw new HttpsError("invalid-argument", "gymId, userId, and plan are required.");
  }

  const [isAdmin, gymRole] = await Promise.all([
    isPlatformAdmin(callerUid),
    getGymRole(gymId, callerUid)
  ]);

  if (!isStaff(gymRole, isAdmin)) {
    throw new HttpsError("permission-denied", "Only gym staff or platform admins can grant membership plans.");
  }

  const db = admin.firestore();
  const batch = db.batch();
  const now = new Date();

  const activePlansRef = db.collection("users").doc(userId).collection("memberships").doc(gymId).collection("activePlans");

  for (const component of plan.components) {
    const expiresAt = customExpiresAtMillis
      ? new Date(customExpiresAtMillis)
      : computeExpiry(now, component.validityValue ?? 1, component.validityUnit ?? "months");

    const itemDoc: ActivePlanItemDoc = {
      planName: plan.name,
      type: component.type,
      resetPeriod: component.resetPeriod ?? "none",
      workoutType: component.workoutType || undefined,
      creditCount: component.creditCount ?? 0,
      remainingCredits: component.resetPeriod === "none" ? (component.type === "unlimited" ? 0 : (component.creditCount ?? 0)) : 0,
      cycleCreditsUsed: 0,
      cycleAnchorDate: admin.firestore.Timestamp.fromDate(now),
      lastCycleIndex: 0,
      expiresAt: admin.firestore.Timestamp.fromDate(expiresAt)
    };

    const newDocRef = activePlansRef.doc();
    batch.set(newDocRef, itemDoc);
  }

  await batch.commit();

  return { success: true };
}

export async function handleRevokePlan(request: CallableRequest<RevokePlanRequest>) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be authenticated.");
  }

  const callerUid = request.auth.uid;
  const { gymId, userId, activePlanId } = request.data;

  if (!gymId || !userId || !activePlanId) {
    throw new HttpsError("invalid-argument", "gymId, userId, and activePlanId are required.");
  }

  const [isAdmin, gymRole] = await Promise.all([
    isPlatformAdmin(callerUid),
    getGymRole(gymId, callerUid)
  ]);

  if (!isStaff(gymRole, isAdmin)) {
    throw new HttpsError("permission-denied", "Only gym staff or platform admins can revoke membership plans.");
  }

  const db = admin.firestore();
  await db.collection("users").doc(userId).collection("memberships").doc(gymId).collection("activePlans").doc(activePlanId).delete();

  return { success: true };
}
