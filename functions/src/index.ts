import * as admin from "firebase-admin";
import { onCall } from "firebase-functions/v2/https";
import { setGlobalOptions } from "firebase-functions/v2";
import { handleBookClass, handleCancelBooking } from "./booking";
import { handleJoinWaitlist, handleLeaveWaitlist } from "./waitlist";
import { handleGrantPlan, handleRevokePlan } from "./membership";

// Initialize Admin SDK once
admin.initializeApp();

// Global production settings: 30s timeout, max 20 instances (cost guard)
setGlobalOptions({
  timeoutSeconds: 30,
  maxInstances: 20
});

// Export Callable Cloud Functions (v2)
export const bookClass = onCall({ cors: true }, handleBookClass);
export const cancelBooking = onCall({ cors: true }, handleCancelBooking);
export const joinWaitlist = onCall({ cors: true }, handleJoinWaitlist);
export const leaveWaitlist = onCall({ cors: true }, handleLeaveWaitlist);
export const grantPlanToMember = onCall({ cors: true }, handleGrantPlan);
export const revokeActivePlan = onCall({ cors: true }, handleRevokePlan);
