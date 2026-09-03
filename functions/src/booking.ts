import * as admin from "firebase-admin";
import { HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { GymClassDoc, ActivePlanItemDoc, BookingDoc, WaitlistDoc } from "./types";
import { isPlatformAdmin, getGymRole, isStaff, matchesGymClass, getAvailableCredits, currentCycleIndex } from "./helpers";

interface BookClassRequest {
  gymId: string;
  classId: string;
}

interface CancelBookingRequest {
  gymId: string;
  classId: string;
  onBehalfOfUserId?: string;
}

/**
 * Authoritatively books a class in a single atomic transaction:
 * Strict Read-All -> Validate -> Write-All pattern.
 */
export async function handleBookClass(request: CallableRequest<BookClassRequest>) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be authenticated to book a class.");
  }

  const userId = request.auth.uid;
  const { gymId, classId } = request.data;

  if (!gymId || !classId) {
    throw new HttpsError("invalid-argument", "gymId and classId are required.");
  }

  const db = admin.firestore();
  const now = new Date();

  // Non-transactional pre-flight role checks
  const [isAdmin, gymRole] = await Promise.all([
    isPlatformAdmin(userId),
    getGymRole(gymId, userId)
  ]);
  const isStaffMember = isStaff(gymRole, isAdmin);

  return await db.runTransaction(async (transaction) => {
    // -------------------------------------------------------------
    // PHASE 1: READ ALL
    // -------------------------------------------------------------
    const classRef = db.collection("gyms").doc(gymId).collection("classes").doc(classId);
    const existingBookingsQuery = db.collection("gyms").doc(gymId).collection("bookings")
      .where("userId", "==", userId)
      .where("classId", "==", classId);
    const activePlansRef = db.collection("users").doc(userId).collection("memberships").doc(gymId).collection("activePlans");
    const waitlistQuery = db.collection("gyms").doc(gymId).collection("waitlist")
      .where("userId", "==", userId)
      .where("classId", "==", classId);

    const [classSnap, existingBookingsSnap, activePlansSnap, waitlistSnap] = await Promise.all([
      transaction.get(classRef),
      transaction.get(existingBookingsQuery),
      isStaffMember ? Promise.resolve(null) : transaction.get(activePlansRef),
      transaction.get(waitlistQuery)
    ]);

    // -------------------------------------------------------------
    // PHASE 2: VALIDATE & CALCULATE
    // -------------------------------------------------------------
    if (!classSnap.exists) {
      throw new HttpsError("not-found", "Class not found.");
    }
    const gymClass = classSnap.data() as GymClassDoc;

    if (gymClass.startTime.toDate().getTime() <= now.getTime()) {
      throw new HttpsError("failed-precondition", "Cannot book a class that is in the past.");
    }

    if (gymClass.currentAttendees >= gymClass.capacity) {
      throw new HttpsError("resource-exhausted", "Class is full. You can join the waitlist instead.");
    }

    if (!existingBookingsSnap.empty) {
      throw new HttpsError("already-exists", "You are already booked for this class.");
    }

    let chosenPlanDoc: { id: string; ref: admin.firestore.DocumentReference; updateData: Record<string, any> } | null = null;

    if (!isStaffMember) {
      if (!activePlansSnap || activePlansSnap.empty) {
        throw new HttpsError("permission-denied", "No active membership found for this gym.");
      }

      const items = activePlansSnap.docs.map(doc => ({ id: doc.id, ref: doc.ref, data: doc.data() as ActivePlanItemDoc }));
      const matchingItems = items.filter(item => matchesGymClass(item.data, gymClass, now));
      if (matchingItems.length === 0) {
        throw new HttpsError("permission-denied", "No active membership plan covers this class.");
      }

      const unlimitedItem = matchingItems.find(item => item.data.type === "unlimited");
      if (!unlimitedItem) {
        const creditItems = matchingItems
          .filter(item => item.data.type === "credits" && getAvailableCredits(item.data, now) > 0)
          .sort((a, b) => a.data.expiresAt.toDate().getTime() - b.data.expiresAt.toDate().getTime());

        if (creditItems.length === 0) {
          throw new HttpsError("resource-exhausted", "Insufficient credits to book this class.");
        }

        const chosen = creditItems[0];
        if (chosen.data.resetPeriod === "monthly") {
          const anchor = chosen.data.cycleAnchorDate ? chosen.data.cycleAnchorDate.toDate() : chosen.data.expiresAt.toDate();
          const currIndex = currentCycleIndex(anchor, now);
          const lastIndex = chosen.data.lastCycleIndex ?? 0;
          if (currIndex !== lastIndex) {
            chosenPlanDoc = {
              id: chosen.id,
              ref: chosen.ref,
              updateData: { cycleCreditsUsed: 1, lastCycleIndex: currIndex }
            };
          } else {
            chosenPlanDoc = {
              id: chosen.id,
              ref: chosen.ref,
              updateData: { cycleCreditsUsed: admin.firestore.FieldValue.increment(1) }
            };
          }
        } else {
          chosenPlanDoc = {
            id: chosen.id,
            ref: chosen.ref,
            updateData: { remainingCredits: admin.firestore.FieldValue.increment(-1) }
          };
        }
      }
    }

    // -------------------------------------------------------------
    // PHASE 3: WRITE ALL
    // -------------------------------------------------------------
    // Create booking
    const newBookingRef = db.collection("gyms").doc(gymId).collection("bookings").doc();
    const bookingData: BookingDoc = {
      userId,
      classId,
      bookedAt: admin.firestore.Timestamp.fromDate(now),
      activePlanId: chosenPlanDoc ? chosenPlanDoc.id : null,
      checkedIn: false,
      checkedInAt: null
    };
    transaction.set(newBookingRef, bookingData);

    // Increment class attendees
    transaction.update(classRef, {
      currentAttendees: admin.firestore.FieldValue.increment(1)
    });

    // Update wallet if credits were consumed
    if (chosenPlanDoc) {
      transaction.update(chosenPlanDoc.ref, chosenPlanDoc.updateData);
    }

    // Clean up waitlist if user was waitlisted
    if (!waitlistSnap.empty) {
      for (const wDoc of waitlistSnap.docs) {
        transaction.delete(wDoc.ref);
      }
      transaction.update(classRef, {
        waitlistCount: admin.firestore.FieldValue.increment(-1 * waitlistSnap.docs.length)
      });
    }

    return {
      success: true,
      bookingId: newBookingRef.id
    };
  });
}

/**
 * Authoritatively cancels a booking:
 * Strict Read-All -> Validate -> Write-All pattern.
 * Atomically deletes booking, refunds credit, and promotes #1 waitlisted member.
 */
export async function handleCancelBooking(request: CallableRequest<CancelBookingRequest>) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be authenticated to cancel a booking.");
  }

  const callerUid = request.auth.uid;
  const { gymId, classId, onBehalfOfUserId } = request.data;

  if (!gymId || !classId) {
    throw new HttpsError("invalid-argument", "gymId and classId are required.");
  }

  const db = admin.firestore();
  const now = new Date();

  // Role validation
  const [isAdmin, gymRole] = await Promise.all([
    isPlatformAdmin(callerUid),
    getGymRole(gymId, callerUid)
  ]);
  const isStaffMember = isStaff(gymRole, isAdmin);
  const targetUserId = (onBehalfOfUserId && isStaffMember) ? onBehalfOfUserId : callerUid;

  return await db.runTransaction(async (transaction) => {
    // -------------------------------------------------------------
    // PHASE 1: READ ALL
    // -------------------------------------------------------------
    const bookingsQuery = db.collection("gyms").doc(gymId).collection("bookings")
      .where("userId", "==", targetUserId)
      .where("classId", "==", classId);
    const classRef = db.collection("gyms").doc(gymId).collection("classes").doc(classId);
    const waitlistQuery = db.collection("gyms").doc(gymId).collection("waitlist")
      .where("classId", "==", classId);

    const [bookingsSnap, classSnap, waitlistSnap] = await Promise.all([
      transaction.get(bookingsQuery),
      transaction.get(classRef),
      transaction.get(waitlistQuery)
    ]);

    if (!classSnap.exists) {
      throw new HttpsError("not-found", "Class not found.");
    }

    if (bookingsSnap.empty) {
      return { success: true, message: "No active booking found to cancel." };
    }

    const bookingDoc = bookingsSnap.docs[0];
    const bookingData = bookingDoc.data() as BookingDoc;

    // If a plan was consumed, read that active plan document before writing
    let planSnap: admin.firestore.DocumentSnapshot | null = null;
    let planRef: admin.firestore.DocumentReference | null = null;
    if (bookingData.activePlanId) {
      planRef = db.collection("users").doc(targetUserId)
        .collection("memberships").doc(gymId)
        .collection("activePlans").doc(bookingData.activePlanId);
      planSnap = await transaction.get(planRef);
    }

    // -------------------------------------------------------------
    // PHASE 2: VALIDATE & CALCULATE
    // -------------------------------------------------------------
    let refundUpdate: Record<string, any> | null = null;
    if (planSnap && planSnap.exists && planRef) {
      const planData = planSnap.data() as ActivePlanItemDoc;
      if (planData.type === "credits") {
        if (planData.resetPeriod === "monthly") {
          const currentUsed = planData.cycleCreditsUsed ?? 0;
          if (currentUsed > 0) {
            refundUpdate = { cycleCreditsUsed: admin.firestore.FieldValue.increment(-1) };
          }
        } else {
          refundUpdate = { remainingCredits: admin.firestore.FieldValue.increment(1) };
        }
      }
    }

    const hasWaitlistPromotion = !waitlistSnap.empty;
    let waitlistEntryDoc: admin.firestore.QueryDocumentSnapshot | null = null;
    if (hasWaitlistPromotion) {
      const sorted = [...waitlistSnap.docs].sort((a, b) => {
        const aTime = (a.data() as WaitlistDoc).joinedAt?.toMillis() ?? 0;
        const bTime = (b.data() as WaitlistDoc).joinedAt?.toMillis() ?? 0;
        return aTime - bTime;
      });
      waitlistEntryDoc = sorted[0];
    }

    // -------------------------------------------------------------
    // PHASE 3: WRITE ALL
    // -------------------------------------------------------------
    // 1. Delete cancelled booking
    transaction.delete(bookingDoc.ref);

    // 2. Refund credit if applicable
    if (refundUpdate && planRef) {
      transaction.update(planRef, refundUpdate);
    }

    // 3. Handle Waitlist Promotion or Decrement Count
    let promotedUserId: string | null = null;
    if (waitlistEntryDoc) {
      const waitlistEntry = waitlistEntryDoc.data() as WaitlistDoc;
      promotedUserId = waitlistEntry.userId;

      // Create new booking for promoted user
      const promotedBookingRef = db.collection("gyms").doc(gymId).collection("bookings").doc();
      const promotedBookingData: BookingDoc = {
        userId: waitlistEntry.userId,
        classId,
        bookedAt: admin.firestore.Timestamp.fromDate(now),
        activePlanId: null, // Promoted from waitlist
        checkedIn: false,
        checkedInAt: null
      };
      transaction.set(promotedBookingRef, promotedBookingData);

      // Delete waitlist doc
      transaction.delete(waitlistEntryDoc.ref);

      // Decrement waitlistCount on class (currentAttendees stays constant)
      transaction.update(classRef, {
        waitlistCount: admin.firestore.FieldValue.increment(-1)
      });
    } else {
      // No waitlist: decrement currentAttendees
      transaction.update(classRef, {
        currentAttendees: admin.firestore.FieldValue.increment(-1)
      });
    }

    return {
      success: true,
      promotedUserId
    };
  });
}

