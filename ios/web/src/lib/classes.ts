import {
  addDoc,
  collection,
  deleteDoc,
  doc,
  documentId,
  getDoc,
  getDocs,
  onSnapshot,
  query,
  runTransaction,
  setDoc,
  Timestamp,
  where,
  writeBatch,
  type Unsubscribe,
} from 'firebase/firestore'
import { db } from '../firebase'
import type { Attendee, GymClass } from '../types'

function classFromDoc(id: string, data: Record<string, unknown>): GymClass {
  return {
    id,
    title: (data.title as string) ?? '',
    coach: (data.coach as string) ?? '',
    startTime: (data.startTime as Timestamp)?.toDate() ?? new Date(),
    durationMinutes: (data.durationMinutes as number) ?? 60,
    capacity: (data.capacity as number) ?? 12,
    currentAttendees: (data.currentAttendees as number) ?? 0,
    classType: (data.classType as string) ?? '',
    isPremium: (data.isPremium as boolean) ?? false,
    description: (data.description as string) ?? '',
    seriesId: (data.seriesId as string) || undefined,
  }
}

// Mirrors `FirebaseBackend.classData(for:currentAttendees:)` — the write
// shape for a class document. `currentAttendees` is a separate parameter
// (not read off `gymClass`) so callers creating a brand-new class can pass
// `0` explicitly regardless of whatever placeholder value is on the
// in-memory draft.
function classDataFor(
  gymClass: Omit<GymClass, 'id'>,
  currentAttendees: number,
): Record<string, unknown> {
  const data: Record<string, unknown> = {
    title: gymClass.title,
    coach: gymClass.coach,
    startTime: Timestamp.fromDate(gymClass.startTime),
    durationMinutes: gymClass.durationMinutes,
    capacity: gymClass.capacity,
    currentAttendees,
    classType: gymClass.classType,
    isPremium: gymClass.isPremium,
    description: gymClass.description,
  }
  if (gymClass.seriesId) data.seriesId = gymClass.seriesId
  return data
}

function dayBounds(date: Date): { start: Date; end: Date } {
  const start = new Date(date)
  start.setHours(0, 0, 0, 0)
  const end = new Date(start)
  end.setDate(end.getDate() + 1)
  return { start, end }
}

// Mirrors `FirebaseBackend.fetchClasses(gymId:for:)` — every class starting
// within the given calendar day (device-local).
export async function fetchClassesForDate(
  gymId: string,
  date: Date,
): Promise<GymClass[]> {
  const { start, end } = dayBounds(date)
  const snapshot = await getDocs(
    query(
      collection(db, 'gyms', gymId, 'classes'),
      where('startTime', '>=', Timestamp.fromDate(start)),
      where('startTime', '<', Timestamp.fromDate(end)),
    ),
  )
  return snapshot.docs.map((docSnap) => classFromDoc(docSnap.id, docSnap.data()))
}

// Real-time counterpart of `fetchClassesForDate` — mirrors
// `FirebaseBackend.observeClasses(gymId:for:onChange:)`. Returns an
// unsubscribe function.
export function observeClassesForDate(
  gymId: string,
  date: Date,
  onChange: (classes: GymClass[]) => void,
): Unsubscribe {
  const { start, end } = dayBounds(date)
  const q = query(
    collection(db, 'gyms', gymId, 'classes'),
    where('startTime', '>=', Timestamp.fromDate(start)),
    where('startTime', '<', Timestamp.fromDate(end)),
  )
  return onSnapshot(q, (snapshot) => {
    onChange(snapshot.docs.map((docSnap) => classFromDoc(docSnap.id, docSnap.data())))
  })
}

// Mirrors `FirebaseBackend.fetchMemberBookings(gymId:userId:)` — every class
// this member currently has a booking for. Chunked to respect Firestore's
// 30-value cap on `in` queries.
export async function fetchMemberBookings(
  gymId: string,
  userId: string,
): Promise<GymClass[]> {
  const bookingsSnapshot = await getDocs(
    query(
      collection(db, 'gyms', gymId, 'bookings'),
      where('userId', '==', userId),
    ),
  )
  const classIds = bookingsSnapshot.docs
    .map((docSnap) => docSnap.data().classId as string | undefined)
    .filter((id): id is string => Boolean(id))

  if (classIds.length === 0) return []

  const classes: GymClass[] = []
  for (let i = 0; i < classIds.length; i += 30) {
    const chunk = classIds.slice(i, i + 30)
    const snapshot = await getDocs(
      query(
        collection(db, 'gyms', gymId, 'classes'),
        where(documentId(), 'in', chunk),
      ),
    )
    classes.push(
      ...snapshot.docs.map((docSnap) => classFromDoc(docSnap.id, docSnap.data())),
    )
  }

  return classes.sort((a, b) => b.startTime.getTime() - a.startTime.getTime())
}

// Mirrors `FirebaseBackend.adjustClassCounter` — a transaction so a
// concurrent write (e.g. another booking landing at the same moment) can't
// push the counter negative or double-count. `Math.max(0, ...)` clamps the
// same way the iOS fix does.
async function adjustClassCounter(
  gymId: string,
  classId: string,
  field: 'currentAttendees' | 'waitlistCount',
  delta: number,
): Promise<void> {
  const ref = doc(db, 'gyms', gymId, 'classes', classId)
  await runTransaction(db, async (transaction) => {
    const snap = await transaction.get(ref)
    const current = (snap.data()?.[field] as number | undefined) ?? 0
    transaction.update(ref, { [field]: Math.max(0, current + delta) })
  })
}

// Mirrors `FirebaseBackend.fetchUserBookings(gymId:)` — the set of class
// IDs this user currently has a booking for.
export async function fetchUserBookings(
  gymId: string,
  userId: string,
): Promise<Set<string>> {
  const snapshot = await getDocs(
    query(collection(db, 'gyms', gymId, 'bookings'), where('userId', '==', userId)),
  )
  return new Set(
    snapshot.docs
      .map((docSnap) => docSnap.data().classId as string | undefined)
      .filter((id): id is string => Boolean(id)),
  )
}

// Mirrors `FirebaseBackend.fetchUserWaitlist(gymId:)`.
export async function fetchUserWaitlist(
  gymId: string,
  userId: string,
): Promise<Set<string>> {
  const snapshot = await getDocs(
    query(collection(db, 'gyms', gymId, 'waitlist'), where('userId', '==', userId)),
  )
  return new Set(
    snapshot.docs
      .map((docSnap) => docSnap.data().classId as string | undefined)
      .filter((id): id is string => Boolean(id)),
  )
}

// Mirrors the core mechanics of `FirebaseBackend.book(gymId:classId:)` —
// guards against double-booking, a past class, and a full class, then
// writes a booking doc and bumps `currentAttendees`.
//
// Deliberately NOT ported: `validateAndConsumeMembership` (the credit
// wallet / membership-plan gating). No membership-plan concept exists on
// the web client yet — there's no UI to grant, view, or manage a wallet —
// so every booking here is unconditionally authorized, same posture as an
// unlimited plan or staff bypass on iOS. Worth building once the web app
// gets a Membership Plans surface.
export async function bookClass(
  gymId: string,
  classId: string,
  userId: string,
): Promise<void> {
  const existing = await getDocs(
    query(
      collection(db, 'gyms', gymId, 'bookings'),
      where('userId', '==', userId),
      where('classId', '==', classId),
    ),
  )
  if (!existing.empty) return

  const classSnap = await getDoc(doc(db, 'gyms', gymId, 'classes', classId))
  if (!classSnap.exists()) throw new Error('Class not found')
  const gymClass = classFromDoc(classSnap.id, classSnap.data())
  if (gymClass.startTime < new Date()) {
    throw new Error('This class has already started')
  }
  if (gymClass.currentAttendees >= gymClass.capacity) {
    throw new Error('This class is full')
  }

  await addDoc(collection(db, 'gyms', gymId, 'bookings'), {
    userId,
    classId,
    bookedAt: Timestamp.fromDate(new Date()),
  })
  await adjustClassCounter(gymId, classId, 'currentAttendees', 1)
}

// Mirrors `FirebaseBackend.removeBooking` — deletes the booking, then
// promotes the earliest-joined waiting user if there is one (mirroring the
// FIFO promotion), otherwise decrements `currentAttendees`. Credit refunds
// are skipped for the same reason `bookClass` skips wallet consumption.
export async function cancelBooking(
  gymId: string,
  classId: string,
  userId: string,
): Promise<void> {
  const bookingsSnap = await getDocs(
    query(
      collection(db, 'gyms', gymId, 'bookings'),
      where('userId', '==', userId),
      where('classId', '==', classId),
    ),
  )
  if (bookingsSnap.empty) return
  await Promise.all(bookingsSnap.docs.map((docSnap) => deleteDoc(docSnap.ref)))

  const waitlistSnap = await getDocs(
    query(collection(db, 'gyms', gymId, 'waitlist'), where('classId', '==', classId)),
  )
  const sorted = [...waitlistSnap.docs].sort((a, b) => {
    const aTime = (a.data().joinedAt as Timestamp | undefined)?.toMillis() ?? 0
    const bTime = (b.data().joinedAt as Timestamp | undefined)?.toMillis() ?? 0
    return aTime - bTime
  })

  const first = sorted[0]
  if (first) {
    const waitingUserId = first.data().userId as string
    await addDoc(collection(db, 'gyms', gymId, 'bookings'), {
      userId: waitingUserId,
      classId,
      bookedAt: Timestamp.fromDate(new Date()),
    })
    await deleteDoc(first.ref)
    await adjustClassCounter(gymId, classId, 'waitlistCount', -1)
  } else {
    await adjustClassCounter(gymId, classId, 'currentAttendees', -1)
  }
}

// Mirrors `FirebaseBackend.joinWaitlist(gymId:classId:)`.
export async function joinWaitlist(
  gymId: string,
  classId: string,
  userId: string,
): Promise<void> {
  const classSnap = await getDoc(doc(db, 'gyms', gymId, 'classes', classId))
  if (!classSnap.exists()) throw new Error('Class not found')
  const gymClass = classFromDoc(classSnap.id, classSnap.data())
  if (gymClass.startTime < new Date()) {
    throw new Error('This class has already started')
  }

  const existing = await getDocs(
    query(
      collection(db, 'gyms', gymId, 'waitlist'),
      where('userId', '==', userId),
      where('classId', '==', classId),
    ),
  )
  if (!existing.empty) return

  await addDoc(collection(db, 'gyms', gymId, 'waitlist'), {
    userId,
    classId,
    joinedAt: Timestamp.fromDate(new Date()),
  })
  await adjustClassCounter(gymId, classId, 'waitlistCount', 1)
}

// Mirrors `FirebaseBackend.leaveWaitlist(gymId:classId:)`.
export async function leaveWaitlist(
  gymId: string,
  classId: string,
  userId: string,
): Promise<void> {
  const entries = await getDocs(
    query(
      collection(db, 'gyms', gymId, 'waitlist'),
      where('userId', '==', userId),
      where('classId', '==', classId),
    ),
  )
  if (entries.empty) return
  await Promise.all(entries.docs.map((docSnap) => deleteDoc(docSnap.ref)))
  await adjustClassCounter(gymId, classId, 'waitlistCount', -1)
}

// Mirrors `FirebaseBackend.fetchAllClasses(gymId:)` — every class in the
// gym, ordered by start time, for the Manage → Classes list.
export async function fetchAllClasses(gymId: string): Promise<GymClass[]> {
  const snapshot = await getDocs(collection(db, 'gyms', gymId, 'classes'))
  return snapshot.docs
    .map((docSnap) => classFromDoc(docSnap.id, docSnap.data()))
    .sort((a, b) => a.startTime.getTime() - b.startTime.getTime())
}

// Mirrors `FirebaseBackend.createClass(gymId:_:)`.
export async function createClass(
  gymId: string,
  gymClass: Omit<GymClass, 'id'>,
): Promise<void> {
  const ref = doc(collection(db, 'gyms', gymId, 'classes'))
  await setDoc(ref, classDataFor(gymClass, 0))
}

// Mirrors `FirebaseBackend.createClasses(gymId:_:)` — a single batch write
// for every occurrence in a recurring series, all sharing the same
// `seriesId` (generated once by the caller before calling this).
export async function createClasses(
  gymId: string,
  classes: Array<Omit<GymClass, 'id'>>,
): Promise<void> {
  const batch = writeBatch(db)
  for (const gymClass of classes) {
    const ref = doc(collection(db, 'gyms', gymId, 'classes'))
    batch.set(ref, classDataFor(gymClass, 0))
  }
  await batch.commit()
}

// Mirrors `FirebaseBackend.updateClass(gymId:_:)`.
export async function updateClass(gymId: string, gymClass: GymClass): Promise<void> {
  const ref = doc(db, 'gyms', gymId, 'classes', gymClass.id)
  await setDoc(ref, classDataFor(gymClass, gymClass.currentAttendees))
}

// Mirrors `FirebaseBackend.deleteClass(gymId:classId:)`.
export async function deleteClass(gymId: string, classId: string): Promise<void> {
  await deleteDoc(doc(db, 'gyms', gymId, 'classes', classId))
}

// Mirrors `FirebaseBackend.deleteClassSeries(gymId:seriesId:from:)` — every
// class in the series starting on/after `from` (not the whole series, so
// past occurrences of an edited-going-forward series are left alone).
export async function deleteClassSeries(
  gymId: string,
  seriesId: string,
  from: Date,
): Promise<void> {
  const snapshot = await getDocs(
    query(collection(db, 'gyms', gymId, 'classes'), where('seriesId', '==', seriesId)),
  )
  const toDelete = snapshot.docs.filter((docSnap) => {
    const startTime = (docSnap.data().startTime as Timestamp | undefined)?.toDate()
    return startTime ? startTime >= from : false
  })
  const batch = writeBatch(db)
  toDelete.forEach((docSnap) => batch.delete(docSnap.ref))
  await batch.commit()
}

// Mirrors `FirebaseBackend.fetchAttendees(gymId:classId:)` — every booked
// user's name/email for a class, for the staff-only attendee roster.
export async function fetchAttendees(
  gymId: string,
  classId: string,
): Promise<Attendee[]> {
  const bookingsSnap = await getDocs(
    query(collection(db, 'gyms', gymId, 'bookings'), where('classId', '==', classId)),
  )
  const userIds = bookingsSnap.docs
    .map((docSnap) => docSnap.data().userId as string | undefined)
    .filter((id): id is string => Boolean(id))
  if (userIds.length === 0) return []

  const attendees: Attendee[] = []
  for (let i = 0; i < userIds.length; i += 30) {
    const chunk = userIds.slice(i, i + 30)
    const snapshot = await getDocs(
      query(collection(db, 'users'), where(documentId(), 'in', chunk)),
    )
    attendees.push(
      ...snapshot.docs.map((docSnap) => {
        const data = docSnap.data()
        const firstName = (data.firstName as string) ?? ''
        const lastName = (data.lastName as string) ?? ''
        return {
          id: docSnap.id,
          name: `${firstName} ${lastName}`.trim(),
          email: (data.email as string) ?? '',
        }
      }),
    )
  }
  return attendees
}
