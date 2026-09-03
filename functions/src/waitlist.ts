import * as admin from "firebase-admin";
import { HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { GymClassDoc, WaitlistDoc } from "./types";
import { isPlatformAdmin, getGymRole, isStaff } from "./helpers";

interface JoinWaitlistRequest {
  gymId: string;
  classId: string;
}

interface LeaveWaitlistRequest {
  gymId: string;
  classId: string;
  onBehalfOfUserId?: string;
}

/**
 * Authoritatively joins the waitlist:
 * 1. Checks class existence, capacity, past-time gating.
 * 2. Verifies class is actually full.
 * 3. Verifies user is not already booked or waitlisted.
 * 4. Adds to waitlist and increments waitlistCount on class.
 */
export async function handleJoinWaitlist(request: CallableRequest<JoinWaitlistRequest>) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be authenticated to join the waitlist.");
  }

  const userId = request.auth.uid;
  const { gymId, classId } = request.data;

  if (!gymId || !classId) {
    throw new HttpsError("invalid-argument", "gymId and classId are required.");
  }

  const db = admin.firestore();
  const now = new Date();

  return await db.runTransaction(async (transaction) => {
    const classRef = db.collection("gyms").doc(gymId).collection("classes").doc(classId);
    const classSnap = await transaction.get(classRef);
    if (!classSnap.exists) {
      throw new HttpsError("not-found", "Class not found.");
    }
    const gymClass = classSnap.data() as GymClassDoc;

    if (gymClass.startTime.toDate().getTime() <= now.getTime()) {
      throw new HttpsError("failed-precondition", "Cannot join waitlist for a class in the past.");
    }

    if (gymClass.currentAttendees < gymClass.capacity) {
      throw new HttpsError("failed-precondition", "Class has open spots. Book directly instead of joining the waitlist.");
    }

    // Check existing booking
    const bookingQuery = db.collection("gyms").doc(gymId).collection("bookings")
      .where("userId", "==", userId)
      .where("classId", "==", classId);
    const bookingSnap = await transaction.get(bookingQuery);
    if (!bookingSnap.empty) {
      throw new HttpsError("already-exists", "You are already booked for this class.");
    }

    // Check existing waitlist
    const waitlistQuery = db.collection("gyms").doc(gymId).collection("waitlist")
      .where("userId", "==", userId)
      .where("classId", "==", classId);
    const waitlistSnap = await transaction.get(waitlistQuery);
    if (!waitlistSnap.empty) {
      throw new HttpsError("already-exists", "You are already on the waitlist for this class.");
    }

    // Create waitlist document
    const newWaitlistRef = db.collection("gyms").doc(gymId).collection("waitlist").doc();
    const waitlistData: WaitlistDoc = {
      userId,
      classId,
      joinedAt: admin.firestore.Timestamp.fromDate(now)
    };
    transaction.set(newWaitlistRef, waitlistData);

    // Increment waitlistCount
    transaction.update(classRef, {
      waitlistCount: admin.firestore.FieldValue.increment(1)
    });

    return {
      success: true,
      waitlistId: newWaitlistRef.id
    };
  });
}

/**
 * Authoritatively removes a user from the waitlist:
 * 1. Finds and deletes the waitlist record.
 * 2. Decrements waitlistCount on class.
 */
export async function handleLeaveWaitlist(request: CallableRequest<LeaveWaitlistRequest>) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be authenticated to leave the waitlist.");
  }

  const callerUid = request.auth.uid;
  const { gymId, classId, onBehalfOfUserId } = request.data;

  if (!gymId || !classId) {
    throw new HttpsError("invalid-argument", "gymId and classId are required.");
  }

  const db = admin.firestore();

  const [isAdmin, gymRole] = await Promise.all([
    isPlatformAdmin(callerUid),
    getGymRole(gymId, callerUid)
  ]);
  const isStaffMember = isStaff(gymRole, isAdmin);
  const targetUserId = (onBehalfOfUserId && isStaffMember) ? onBehalfOfUserId : callerUid;

  return await db.runTransaction(async (transaction) => {
    const waitlistQuery = db.collection("gyms").doc(gymId).collection("waitlist")
      .where("userId", "==", targetUserId)
      .where("classId", "==", classId);
    const waitlistSnap = await transaction.get(waitlistQuery);

    if (waitlistSnap.empty) {
      return { success: true, message: "User is not on the waitlist." };
    }

    for (const doc of waitlistSnap.docs) {
      transaction.delete(doc.ref);
    }

    const classRef = db.collection("gyms").doc(gymId).collection("classes").doc(classId);
    transaction.update(classRef, {
      waitlistCount: admin.firestore.FieldValue.increment(-1 * waitlistSnap.docs.length)
    });

    return {
      success: true
    };
  });
}
