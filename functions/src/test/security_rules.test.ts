import { describe, it } from "node:test";
import * as assert from "node:assert";

// Simulation of Firestore Security Rules evaluation engine matching firestore.rules
interface RuleAuth {
  uid: string;
  token?: { admin?: boolean };
}

interface FirestoreState {
  users: Map<string, { role: string }>;
  memberships: Map<string, { role: string }>; // key: `${uid}:${gymId}`
  gyms: Map<string, { ownerUID: string }>;
}

function evaluateRule(state: FirestoreState, auth: RuleAuth | null) {
  function isAuthenticated() {
    return auth !== null;
  }

  function isUser(uid: string) {
    return isAuthenticated() && auth!.uid === uid;
  }

  function isPlatformAdmin() {
    if (!isAuthenticated()) return false;
    if (auth!.token?.admin === true) return true;
    const user = state.users.get(auth!.uid);
    return user?.role === "admin";
  }

  function getGymRole(gymId: string) {
    if (!isAuthenticated()) return null;
    const mem = state.memberships.get(`${auth!.uid}:${gymId}`);
    return mem ? mem.role : null;
  }

  function isGymOwner(gymId: string) {
    if (!isAuthenticated()) return false;
    if (isPlatformAdmin()) return true;
    if (getGymRole(gymId) === "owner") return true;
    const gym = state.gyms.get(gymId);
    return gym?.ownerUID === auth!.uid;
  }

  function isGymStaff(gymId: string) {
    if (!isAuthenticated()) return false;
    if (isPlatformAdmin() || isGymOwner(gymId)) return true;
    return getGymRole(gymId) === "coach";
  }

  function isGymMember(gymId: string) {
    if (!isAuthenticated()) return false;
    if (isPlatformAdmin() || isGymStaff(gymId)) return true;
    return getGymRole(gymId) === "member";
  }

  return {
    // users/{uid}
    canReadUser: () => isAuthenticated(),
    canCreateUser: (targetUid: string, role: string) => isUser(targetUid) && role === "user",
    canUpdateUserRole: (targetUid: string, currentRole: string, newRole: string) => {
      if (isPlatformAdmin()) return true;
      return isUser(targetUid) && currentRole === newRole;
    },

    // memberships/{gymId}
    canReadMembership: (targetUid: string, gymId: string) => isUser(targetUid) || isGymStaff(gymId),
    canUpdateMembershipRole: (gymId: string) => isGymOwner(gymId),

    // activePlans/{activePlanId}
    canReadActivePlan: (targetUid: string, gymId: string) => isUser(targetUid) || isGymStaff(gymId),
    canWriteActivePlan: (gymId: string) => isGymStaff(gymId),

    // classes/{classId}
    canReadClasses: (gymId: string) => isGymMember(gymId),
    canWriteClasses: (gymId: string) => isGymStaff(gymId),

    // bookings/{bookingId}
    canReadBookings: (gymId: string) => isGymMember(gymId),
    canWriteBookings: (gymId: string) => isGymStaff(gymId),

    // waitlist/{waitlistId}
    canReadWaitlist: (gymId: string) => isGymMember(gymId),
    canWriteWaitlist: (gymId: string) => isGymStaff(gymId),

    // team/{uid}
    canWriteTeam: (gymId: string) => isGymOwner(gymId),

    // members/{uid}/workoutLogs/{logId}
    canReadWorkoutLogs: (targetUid: string) => isUser(targetUid) || isPlatformAdmin(),
    canWriteWorkoutLogs: (targetUid: string) => isUser(targetUid) || isPlatformAdmin()
  };
}

describe("Nexo Security Rules Adversarial & Isolation Test Suite", () => {
  const state: FirestoreState = {
    users: new Map([
      ["admin-1", { role: "admin" }],
      ["owner-gym-a", { role: "user" }],
      ["coach-gym-a", { role: "user" }],
      ["member-gym-a", { role: "user" }],
      ["member-gym-b", { role: "user" }],
      ["malicious-user", { role: "user" }]
    ]),
    memberships: new Map([
      ["owner-gym-a:gym-a", { role: "owner" }],
      ["coach-gym-a:gym-a", { role: "coach" }],
      ["member-gym-a:gym-a", { role: "member" }],
      ["member-gym-b:gym-b", { role: "member" }]
    ]),
    gyms: new Map([
      ["gym-a", { ownerUID: "owner-gym-a" }],
      ["gym-b", { ownerUID: "owner-gym-b" }]
    ])
  };

  // =========================================================================
  // PRIORITY 3: CROSS-GYM ISOLATION (Test C)
  // =========================================================================
  describe("Priority 3: Cross-Gym Isolation", () => {
    it("member of Gym A is BLOCKED from reading Gym B classes and bookings", () => {
      const authA = { uid: "member-gym-a" };
      const rulesA = evaluateRule(state, authA);

      // Can read Gym A
      assert.strictEqual(rulesA.canReadClasses("gym-a"), true, "Member A can read Gym A classes");
      assert.strictEqual(rulesA.canReadBookings("gym-a"), true, "Member A can read Gym A bookings");

      // BLOCKED from reading Gym B
      assert.strictEqual(rulesA.canReadClasses("gym-b"), false, "Member A MUST NOT read Gym B classes");
      assert.strictEqual(rulesA.canReadBookings("gym-b"), false, "Member A MUST NOT read Gym B bookings");
      assert.strictEqual(rulesA.canReadWaitlist("gym-b"), false, "Member A MUST NOT read Gym B waitlist");
    });

    it("member cannot read another member's workout logs or PRs across any gym", () => {
      const authAlice = { uid: "member-gym-a" };
      const rulesAlice = evaluateRule(state, authAlice);

      // Alice can read her own workout logs
      assert.strictEqual(rulesAlice.canReadWorkoutLogs("member-gym-a"), true);

      // Alice CANNOT read Bob's logs (even in the same gym or different gym)
      assert.strictEqual(rulesAlice.canReadWorkoutLogs("member-gym-b"), false, "Must block cross-user logbook access");
    });

    it("unauthenticated user is BLOCKED from reading any gym data", () => {
      const rulesUnauth = evaluateRule(state, null);
      assert.strictEqual(rulesUnauth.canReadClasses("gym-a"), false);
      assert.strictEqual(rulesUnauth.canReadBookings("gym-a"), false);
      assert.strictEqual(rulesUnauth.canReadActivePlan("member-gym-a", "gym-a"), false);
    });
  });

  // =========================================================================
  // PRIORITY 4: ADVERSARIAL DIRECT WRITES (Test D)
  // =========================================================================
  describe("Priority 4: Adversarial Direct Writes Protection", () => {
    it("regular member attempting direct write to classes is REJECTED", () => {
      const authMember = { uid: "member-gym-a" };
      const rules = evaluateRule(state, authMember);

      // Direct write to classes (tampering with attendees or capacity) is BLOCKED
      assert.strictEqual(rules.canWriteClasses("gym-a"), false, "Direct class writes must be blocked for members");
    });

    it("regular member attempting direct write to credit wallet is REJECTED", () => {
      const authMember = { uid: "member-gym-a" };
      const rules = evaluateRule(state, authMember);

      // Direct write to wallet (adding free credits) is BLOCKED
      assert.strictEqual(rules.canWriteActivePlan("gym-a"), false, "Direct wallet writes must be blocked for members");
    });

    it("regular member attempting direct write to bookings is REJECTED", () => {
      const authMember = { uid: "member-gym-a" };
      const rules = evaluateRule(state, authMember);

      // Direct write to bookings (bypassing function) is BLOCKED
      assert.strictEqual(rules.canWriteBookings("gym-a"), false, "Direct booking writes must be blocked for members");
    });

    it("user attempting to elevate role from user to admin on users/{uid} is REJECTED", () => {
      const authAttacker = { uid: "malicious-user" };
      const rules = evaluateRule(state, authAttacker);

      // Attacker trying to set role: "admin"
      const allowed = rules.canUpdateUserRole("malicious-user", "user", "admin");
      assert.strictEqual(allowed, false, "Self-escalation to admin role must be strictly rejected");
    });

    it("coach attempting to modify team owner is REJECTED (only Gym Owner or Platform Admin)", () => {
      const authCoach = { uid: "coach-gym-a" };
      const rules = evaluateRule(state, authCoach);

      assert.strictEqual(rules.canWriteTeam("gym-a"), false, "Coach cannot mutate team ownership");
    });

    it("gym owner IS allowed to manage classes, wallet, and team for their own gym", () => {
      const authOwner = { uid: "owner-gym-a" };
      const rules = evaluateRule(state, authOwner);

      assert.strictEqual(rules.canWriteClasses("gym-a"), true, "Owner can manage classes in own gym");
      assert.strictEqual(rules.canWriteActivePlan("gym-a"), true, "Owner can grant/revoke plans in own gym");
      assert.strictEqual(rules.canWriteTeam("gym-a"), true, "Owner can manage team in own gym");

      // But owner of Gym A CANNOT manage Gym B!
      assert.strictEqual(rules.canWriteClasses("gym-b"), false, "Owner of Gym A must not write to Gym B");
      assert.strictEqual(rules.canWriteTeam("gym-b"), false, "Owner of Gym A must not manage Gym B team");
    });
  });
});
