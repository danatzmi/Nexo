import { describe, it } from "node:test";
import * as assert from "node:assert";
import { Timestamp } from "firebase-admin/firestore";
import { currentCycleIndex, getAvailableCredits, matchesGymClass } from "../helpers";
import { ActivePlanItemDoc, GymClassDoc } from "../types";

describe("Cloud Functions Helpers & Credit Wallet Logic", () => {
  describe("currentCycleIndex", () => {
    it("returns 0 for dates within the same month", () => {
      const anchor = new Date(2026, 0, 15); // Jan 15, 2026
      const now = new Date(2026, 0, 20); // Jan 20, 2026
      assert.strictEqual(currentCycleIndex(anchor, now), 0);
    });

    it("returns 1 when one month has elapsed", () => {
      const anchor = new Date(2026, 0, 15); // Jan 15, 2026
      const now = new Date(2026, 1, 16); // Feb 16, 2026
      assert.strictEqual(currentCycleIndex(anchor, now), 1);
    });

    it("returns 0 when day-of-month has not reached anchor day in next calendar month", () => {
      const anchor = new Date(2026, 0, 15); // Jan 15, 2026
      const now = new Date(2026, 1, 10); // Feb 10, 2026 (< 15th)
      assert.strictEqual(currentCycleIndex(anchor, now), 0);
    });

    it("calculates across year boundaries", () => {
      const anchor = new Date(2025, 11, 15); // Dec 15, 2025
      const now = new Date(2026, 0, 15); // Jan 15, 2026
      assert.strictEqual(currentCycleIndex(anchor, now), 1);
    });
  });

  describe("getAvailableCredits", () => {
    it("returns 0 for expired plans", () => {
      const now = new Date();
      const past = new Date(now.getTime() - 86400000);
      const item: ActivePlanItemDoc = {
        planName: "Expired Plan",
        type: "credits",
        resetPeriod: "none",
        creditCount: 10,
        remainingCredits: 10,
        expiresAt: Timestamp.fromDate(past)
      };
      assert.strictEqual(getAvailableCredits(item, now), 0);
    });

    it("returns 0 for unlimited plans", () => {
      const now = new Date();
      const future = new Date(now.getTime() + 86400000 * 30);
      const item: ActivePlanItemDoc = {
        planName: "Unlimited Plan",
        type: "unlimited",
        remainingCredits: 0,
        expiresAt: Timestamp.fromDate(future)
      };
      assert.strictEqual(getAvailableCredits(item, now), 0);
    });

    it("returns remaining credits for fixed punch card", () => {
      const now = new Date();
      const future = new Date(now.getTime() + 86400000 * 30);
      const item: ActivePlanItemDoc = {
        planName: "10-Class Pass",
        type: "credits",
        resetPeriod: "none",
        creditCount: 10,
        remainingCredits: 7,
        expiresAt: Timestamp.fromDate(future)
      };
      assert.strictEqual(getAvailableCredits(item, now), 7);
    });

    it("calculates available credits in current active cycle for monthly reset plan", () => {
      const now = new Date(2026, 0, 20); // Jan 20
      const anchor = new Date(2026, 0, 15); // Jan 15
      const future = new Date(2027, 0, 15); // Jan 15, 2027
      const item: ActivePlanItemDoc = {
        planName: "12-Class Monthly",
        type: "credits",
        resetPeriod: "monthly",
        creditCount: 12,
        remainingCredits: 0,
        cycleCreditsUsed: 4,
        cycleAnchorDate: Timestamp.fromDate(anchor),
        lastCycleIndex: 0,
        expiresAt: Timestamp.fromDate(future)
      };
      assert.strictEqual(getAvailableCredits(item, now), 8); // 12 - 4 = 8
    });

    it("automatically resets credits to full quota when entering a new cycle even with stale lastCycleIndex", () => {
      const now = new Date(2026, 1, 16); // Feb 16 (Cycle index 1)
      const anchor = new Date(2026, 0, 15); // Jan 15
      const future = new Date(2027, 0, 15);
      const item: ActivePlanItemDoc = {
        planName: "12-Class Monthly",
        type: "credits",
        resetPeriod: "monthly",
        creditCount: 12,
        remainingCredits: 0,
        cycleCreditsUsed: 12, // Exhausted all 12 in cycle 0
        cycleAnchorDate: Timestamp.fromDate(anchor),
        lastCycleIndex: 0, // Stale cycle index 0
        expiresAt: Timestamp.fromDate(future)
      };
      // In cycle 1, should report all 12 available credits!
      assert.strictEqual(getAvailableCredits(item, now), 12);
    });
  });

  describe("matchesGymClass", () => {
    const future = Timestamp.fromDate(new Date(Date.now() + 86400000 * 30));

    it("general plan matches standard class", () => {
      const item: ActivePlanItemDoc = {
        planName: "General Plan",
        type: "unlimited",
        workoutType: undefined,
        remainingCredits: 0,
        expiresAt: future
      };
      const gymClass: GymClassDoc = {
        title: "CrossFit WOD",
        coach: "Alex",
        startTime: future,
        durationMinutes: 60,
        capacity: 12,
        currentAttendees: 5,
        classType: "CrossFit WOD",
        isPremium: false
      };
      assert.strictEqual(matchesGymClass(item, gymClass), true);
    });

    it("general plan does NOT match premium class", () => {
      const item: ActivePlanItemDoc = {
        planName: "General Plan",
        type: "unlimited",
        workoutType: undefined,
        remainingCredits: 0,
        expiresAt: future
      };
      const premiumClass: GymClassDoc = {
        title: "Special Workshop",
        coach: "Alex",
        startTime: future,
        durationMinutes: 90,
        capacity: 8,
        currentAttendees: 2,
        classType: "Workshop",
        isPremium: true
      };
      assert.strictEqual(matchesGymClass(item, premiumClass), false);
    });

    it("specific category plan matches only matching classType", () => {
      const item: ActivePlanItemDoc = {
        planName: "Pilates Only",
        type: "credits",
        workoutType: "Pilates",
        remainingCredits: 5,
        expiresAt: future
      };
      const pilatesClass: GymClassDoc = {
        title: "Pilates Mat",
        coach: "Sarah",
        startTime: future,
        durationMinutes: 50,
        capacity: 10,
        currentAttendees: 3,
        classType: "Pilates",
        isPremium: true // Even if premium, matches because workoutType matches
      };
      const crossfitClass: GymClassDoc = {
        title: "CrossFit",
        coach: "Sarah",
        startTime: future,
        durationMinutes: 60,
        capacity: 12,
        currentAttendees: 3,
        classType: "CrossFit",
        isPremium: false
      };
      assert.strictEqual(matchesGymClass(item, pilatesClass), true);
      assert.strictEqual(matchesGymClass(item, crossfitClass), false);
    });
  });
});
