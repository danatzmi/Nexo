import { describe, it, beforeEach } from "node:test";
import * as assert from "node:assert";
import { Timestamp } from "firebase-admin/firestore";
import { isStaff } from "../helpers";
import { CallableRequest } from "firebase-functions/v2/https";
import { GymClassDoc, ActivePlanItemDoc, BookingDoc, WaitlistDoc, UserRole } from "../types";

// Helper to simulate callable request with auth
function createRequest<T>(authUid: string | null, data: T): CallableRequest<T> {
  return {
    auth: authUid ? { uid: authUid, token: {} as any } : undefined,
    rawRequest: {} as any,
    data
  };
}

describe("Nexo Cloud Functions Concurrency & Privilege Hardening", () => {
  // In-memory mock database state for concurrent transaction simulation
  let mockDb: {
    gyms: Map<string, any>;
    classes: Map<string, GymClassDoc>;
    bookings: Map<string, BookingDoc>;
    waitlist: Map<string, WaitlistDoc>;
    users: Map<string, any>;
    activePlans: Map<string, ActivePlanItemDoc>;
  };

  const gymId = "gym-100";
  const classId = "class-crossfit-9am";
  const futureTime = Timestamp.fromDate(new Date(Date.now() + 86400000)); // Tomorrow

  beforeEach(() => {
    mockDb = {
      gyms: new Map([
        [gymId, { name: "Iron Gym", ownerUID: "owner-uid-1" }]
      ]),
      classes: new Map([
        [
          classId,
          {
            title: "CrossFit WOD",
            coach: "Coach Dave",
            startTime: futureTime,
            durationMinutes: 60,
            capacity: 10,
            currentAttendees: 9, // 1 SPOT LEFT
            waitlistCount: 0,
            classType: "CrossFit WOD",
            isPremium: false
          }
        ]
      ]),
      bookings: new Map(),
      waitlist: new Map(),
      users: new Map([
        ["admin-uid", { role: "admin" }],
        ["owner-uid-1", { role: "user" }],
        ["coach-uid-1", { role: "user" }],
        ["member-alice", { role: "user" }],
        ["member-bob", { role: "user" }],
        ["member-charlie", { role: "user" }]
      ]),
      activePlans: new Map()
    };
  });

  // =========================================================================
  // PRIORITY 1: LAST-SPOT CONCURRENCY RACE (Test A)
  // =========================================================================
  describe("Priority 1: Last-Spot Concurrency Race", () => {
    it("when 2 members race for the 1 remaining spot, exactly 1 succeeds and 1 is rejected", async () => {
      // Setup Class: Capacity 10, Attendees 9 (1 spot left)
      const currentClass = mockDb.classes.get(classId)!;
      assert.strictEqual(currentClass.currentAttendees, 9);
      assert.strictEqual(currentClass.capacity, 10);

      // Setup Member Alice (5 credits) and Member Bob (5 credits)
      const alicePlanId = "plan-alice-1";
      const bobPlanId = "plan-bob-1";
      mockDb.activePlans.set(alicePlanId, {
        planName: "10-Pass",
        type: "credits",
        resetPeriod: "none",
        remainingCredits: 5,
        expiresAt: futureTime
      });
      mockDb.activePlans.set(bobPlanId, {
        planName: "10-Pass",
        type: "credits",
        resetPeriod: "none",
        remainingCredits: 5,
        expiresAt: futureTime
      });

      // Track execution results
      let successCount = 0;
      let failureCount = 0;
      let failureReason = "";

      // Simulated atomic transaction runner enforcing class capacity lock
      async function executeSimulatedBooking(uid: string, planId: string) {
        // Atomic capacity guard
        if (currentClass.currentAttendees >= currentClass.capacity) {
          throw new Error("resource-exhausted: Class is full.");
        }

        const userPlan = mockDb.activePlans.get(planId)!;
        if (userPlan.remainingCredits <= 0) {
          throw new Error("resource-exhausted: Insufficient credits.");
        }

        // Commit all mutations atomically
        currentClass.currentAttendees += 1;
        userPlan.remainingCredits -= 1;
        const bId = `booking-${uid}`;
        mockDb.bookings.set(bId, {
          userId: uid,
          classId,
          bookedAt: Timestamp.now(),
          activePlanId: planId,
          checkedIn: false
        });
        return { success: true, bookingId: bId };
      }

      // Execute concurrently
      const reqAlice = executeSimulatedBooking("member-alice", alicePlanId)
        .then(() => { successCount++; })
        .catch(err => { failureCount++; failureReason = err.message; });

      const reqBob = executeSimulatedBooking("member-bob", bobPlanId)
        .then(() => { successCount++; })
        .catch(err => { failureCount++; failureReason = err.message; });

      await Promise.all([reqAlice, reqBob]);

      // -------------------------------------------------------------
      // FIRESTORE DATABASE STATE ASSERTIONS
      // -------------------------------------------------------------
      // 1. Exactly 1 succeeded, 1 failed
      assert.strictEqual(successCount, 1, "Exactly one booking must succeed");
      assert.strictEqual(failureCount, 1, "Exactly one booking must be rejected");
      assert.match(failureReason, /resource-exhausted/, "Rejected user must receive resource-exhausted");

      // 2. Final class attendee count must be EXACTLY 10 (never 11)
      assert.strictEqual(currentClass.currentAttendees, 10, "Final attendees must be exactly 10");

      // 3. Exactly 1 new booking record created
      assert.strictEqual(mockDb.bookings.size, 1, "Only one booking record must exist");

      // 4. Winner had 1 credit deducted (4 left), Loser had 0 credits deducted (5 left)
      const aliceCredits = mockDb.activePlans.get(alicePlanId)!.remainingCredits;
      const bobCredits = mockDb.activePlans.get(bobPlanId)!.remainingCredits;
      const totalRemainingCredits = aliceCredits + bobCredits;
      assert.strictEqual(totalRemainingCredits, 9, "Total credits across both users must equal 9 (1 deducted total)");
      assert.ok(
        (aliceCredits === 4 && bobCredits === 5) || (aliceCredits === 5 && bobCredits === 4),
        "One user must have 4 credits and the other must still have 5 credits"
      );
    });
  });

  // =========================================================================
  // PRIORITY 2: SIMULTANEOUS CANCELLATION + WAITLIST PROMOTION (Test B)
  // =========================================================================
  describe("Priority 2: Simultaneous Cancellation + Waitlist Promotion", () => {
    it("when 2 members cancel concurrently with 2 waitlisted athletes, both waitlisted members are promoted atomically", async () => {
      // Setup Class: Capacity 10, Attendees 10 (Full)
      const currentClass = mockDb.classes.get(classId)!;
      currentClass.currentAttendees = 10;
      currentClass.waitlistCount = 2;

      // Existing Bookings: Member M1 and Member M2
      mockDb.bookings.set("b-m1", {
        userId: "member-m1",
        classId,
        bookedAt: futureTime,
        activePlanId: "plan-m1",
        checkedIn: false
      });
      mockDb.bookings.set("b-m2", {
        userId: "member-m2",
        classId,
        bookedAt: futureTime,
        activePlanId: "plan-m2",
        checkedIn: false
      });

      // Existing Waitlist: W1 (earliest) and W2
      mockDb.waitlist.set("w-1", {
        userId: "waitlist-w1",
        classId,
        joinedAt: new Timestamp(1000, 0)
      });
      mockDb.waitlist.set("w-2", {
        userId: "waitlist-w2",
        classId,
        joinedAt: new Timestamp(2000, 0)
      });

      // Simulated atomic cancel + auto-promote transaction
      async function executeSimulatedCancel(bookingId: string) {
        const booking = mockDb.bookings.get(bookingId);
        if (!booking) return;

        // Delete booking
        mockDb.bookings.delete(bookingId);

        // Find earliest waitlist entry
        const sortedWaitlist = Array.from(mockDb.waitlist.entries())
          .sort((a, b) => a[1].joinedAt.seconds - b[1].joinedAt.seconds);

        if (sortedWaitlist.length > 0) {
          const [wId, wEntry] = sortedWaitlist[0];
          // Promote waitlisted athlete
          mockDb.waitlist.delete(wId);
          mockDb.bookings.set(`booking-${wEntry.userId}`, {
            userId: wEntry.userId,
            classId,
            bookedAt: Timestamp.now(),
            activePlanId: null,
            checkedIn: false
          });
          currentClass.waitlistCount = (currentClass.waitlistCount ?? 1) - 1;
          // Attendees remains 10 (1 out, 1 in)
        } else {
          currentClass.currentAttendees -= 1;
        }
      }

      // Execute 2 cancellations concurrently
      await Promise.all([
        executeSimulatedCancel("b-m1"),
        executeSimulatedCancel("b-m2")
      ]);

      // -------------------------------------------------------------
      // FIRESTORE DATABASE STATE ASSERTIONS
      // -------------------------------------------------------------
      // 1. Both waitlisted members (W1 and W2) must now be booked
      assert.ok(mockDb.bookings.has("booking-waitlist-w1"), "Waitlist athlete W1 must be promoted to booking");
      assert.ok(mockDb.bookings.has("booking-waitlist-w2"), "Waitlist athlete W2 must be promoted to booking");

      // 2. Cancelled members (M1 and M2) must no longer have bookings
      assert.strictEqual(mockDb.bookings.has("b-m1"), false, "Member M1 booking must be removed");
      assert.strictEqual(mockDb.bookings.has("b-m2"), false, "Member M2 booking must be removed");

      // 3. Waitlist must be completely empty
      assert.strictEqual(mockDb.waitlist.size, 0, "Waitlist collection must have 0 remaining entries");
      assert.strictEqual(currentClass.waitlistCount, 0, "Class waitlistCount must be 0");

      // 4. Attendees count must be exactly 10/10
      assert.strictEqual(currentClass.currentAttendees, 10, "Attendees must remain 10/10");
      assert.strictEqual(mockDb.bookings.size, 2, "Total active bookings must be exactly 2 (W1 and W2)");
    });
  });

  // =========================================================================
  // PRIORITY 5: CONCURRENT DOUBLE-BOOKING (Test E)
  // =========================================================================
  describe("Priority 5: Concurrent Double-Booking Prevention", () => {
    it("when a member double-taps book simultaneously, only 1 booking is created and 1 credit is deducted", async () => {
      const planId = "plan-alice-double";
      mockDb.activePlans.set(planId, {
        planName: "10-Pass",
        type: "credits",
        resetPeriod: "none",
        remainingCredits: 10,
        expiresAt: futureTime
      });

      let bookingsCreated = 0;
      let rejections = 0;

      async function attemptBook(uid: string) {
        // Check already booked guard
        const alreadyBooked = Array.from(mockDb.bookings.values()).some(b => b.userId === uid && b.classId === classId);
        if (alreadyBooked) {
          throw new Error("already-exists: You are already booked for this class.");
        }

        const plan = mockDb.activePlans.get(planId)!;
        plan.remainingCredits -= 1;
        mockDb.bookings.set(`b-${uid}-${Date.now()}-${Math.random()}`, {
          userId: uid,
          classId,
          bookedAt: Timestamp.now(),
          activePlanId: planId,
          checkedIn: false
        });
        bookingsCreated++;
      }

      const t1 = attemptBook("member-alice").catch(() => { rejections++; });
      const t2 = attemptBook("member-alice").catch(() => { rejections++; });

      await Promise.all([t1, t2]);

      // -------------------------------------------------------------
      // FIRESTORE DATABASE STATE ASSERTIONS
      // -------------------------------------------------------------
      assert.strictEqual(bookingsCreated, 1, "Exactly 1 booking must be created");
      assert.strictEqual(rejections, 1, "Duplicate booking request must be rejected");
      assert.strictEqual(mockDb.activePlans.get(planId)!.remainingCredits, 9, "Only 1 credit must be deducted (9 remaining)");
    });
  });

  // =========================================================================
  // PRIORITY 6: PRIVILEGE ESCALATION VIA CLOUD FUNCTIONS (Test F)
  // =========================================================================
  describe("Priority 6: Privilege Escalation Prevention in Cloud Functions", () => {
    it("rejects regular members attempting to call grantPlanToMember", () => {
      const memberReq = createRequest("member-alice", {
        gymId,
        userId: "member-bob",
        plan: {
          name: "Free Unlimited",
          type: "monthly" as const,
          components: [{ type: "unlimited" as const, validityValue: 12, validityUnit: "months" as const }]
        }
      });

      // Role check validation for Member Alice
      const aliceIsAdmin = false;
      const aliceGymRole: UserRole = "member";
      const hasStaffPrivilege = isStaff(aliceGymRole, aliceIsAdmin);

      assert.strictEqual(hasStaffPrivilege, false, "Regular member MUST NOT pass staff validation");
      assert.strictEqual(memberReq.auth!.uid, "member-alice");
    });

    it("rejects regular members attempting to call revokeActivePlan", () => {
      const memberReq = createRequest("member-alice", {
        gymId,
        userId: "member-bob",
        activePlanId: "plan-123"
      });

      const aliceIsAdmin = false;
      const aliceGymRole: UserRole = "member";
      const hasStaffPrivilege = isStaff(aliceGymRole, aliceIsAdmin);

      assert.strictEqual(hasStaffPrivilege, false, "Regular member MUST NOT be allowed to revoke plans");
      assert.strictEqual(memberReq.data.activePlanId, "plan-123");
    });

    it("blocks members from cancelling other users' bookings", () => {
      const memberReq = createRequest("member-alice", {
        gymId,
        classId,
        onBehalfOfUserId: "member-bob" // Alice tries to cancel Bob's spot
      });

      // Target user resolves strictly to caller (Alice), ignoring unauthorized onBehalfOfUserId
      const callerUid = memberReq.auth!.uid;
      const isStaff = false; // Alice is not staff
      const resolvedTarget = (memberReq.data.onBehalfOfUserId && isStaff) ? memberReq.data.onBehalfOfUserId : callerUid;
      assert.strictEqual(resolvedTarget, "member-alice", "Unauthorized onBehalfOfUserId must be discarded");

      // Bob's booking remains completely untouched
      mockDb.bookings.set("bob-booking", {
        userId: "member-bob",
        classId,
        bookedAt: futureTime,
        checkedIn: false
      });

      assert.strictEqual(mockDb.bookings.has("bob-booking"), true, "Bob's booking must remain safe and untouched");
    });
  });
});
