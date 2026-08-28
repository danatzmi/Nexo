import {
  collection,
  deleteDoc,
  doc,
  getDocs,
  setDoc,
} from 'firebase/firestore'
import { db } from '../firebase'
import type { MembershipPlan, PlanComponent } from '../types'

function planComponentFromData(data: Record<string, unknown>): PlanComponent | null {
  const type = data.type as PlanComponent['type'] | undefined
  if (!type) return null
  return {
    id: (data.id as string) ?? crypto.randomUUID(),
    type,
    workoutType: (data.workoutType as string | undefined) ?? null,
    creditCount: (data.creditCount as number) ?? 0,
    validityValue: (data.validityValue as number) ?? 1,
    validityUnit: (data.validityUnit as PlanComponent['validityUnit']) ?? 'months',
  }
}

function planComponentData(component: PlanComponent): Record<string, unknown> {
  const data: Record<string, unknown> = {
    id: component.id,
    type: component.type,
    creditCount: component.creditCount,
    validityValue: component.validityValue,
    validityUnit: component.validityUnit,
  }
  if (component.workoutType) data.workoutType = component.workoutType
  return data
}

function planFromDoc(id: string, data: Record<string, unknown>): MembershipPlan {
  const rawComponents = (data.components as Array<Record<string, unknown>> | undefined) ?? []
  return {
    id,
    name: (data.name as string) ?? '',
    price: (data.price as number) ?? 0,
    components: rawComponents
      .map(planComponentFromData)
      .filter((component): component is PlanComponent => component !== null),
  }
}

// Mirrors `FirebaseBackend.fetchMembershipPlans(gymId:)`.
export async function fetchMembershipPlans(gymId: string): Promise<MembershipPlan[]> {
  const snapshot = await getDocs(collection(db, 'gyms', gymId, 'membershipPlans'))
  return snapshot.docs.map((docSnap) => planFromDoc(docSnap.id, docSnap.data()))
}

// Mirrors `FirebaseBackend.createMembershipPlan`/`updateMembershipPlan` —
// both `setData` the whole document, so one function covers create and
// update (matching the iOS pair's identical bodies).
export async function savePlan(gymId: string, plan: MembershipPlan): Promise<void> {
  const ref = plan.id
    ? doc(db, 'gyms', gymId, 'membershipPlans', plan.id)
    : doc(collection(db, 'gyms', gymId, 'membershipPlans'))
  await setDoc(ref, {
    name: plan.name,
    price: plan.price,
    components: plan.components.map(planComponentData),
  })
}

// Mirrors `FirebaseBackend.deleteMembershipPlan(gymId:planId:)`.
export async function deleteMembershipPlan(gymId: string, planId: string): Promise<void> {
  await deleteDoc(doc(db, 'gyms', gymId, 'membershipPlans', planId))
}
