import { useEffect, useState } from 'react'
import { CheckCircle2, Clock, User as UserIcon, X } from 'lucide-react'
import { useAuth } from '../context/useAuth'
import {
  bookClass,
  cancelBooking,
  fetchAttendees,
  joinWaitlist,
  leaveWaitlist,
} from '../lib/classes'
import type { Attendee, GymClass } from '../types'

interface ClassDetailModalProps {
  gymId: string
  gymClass: GymClass
  isBooked: boolean
  isWaitlisted: boolean
  onClose: () => void
  // Called after any successful booking action so the caller (Schedule.tsx)
  // can refresh its own booked/waitlisted sets and the live class list.
  onChanged: () => void
}

function formatDateTime(date: Date): string {
  return date.toLocaleString([], {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}

// Slide-up sheet on mobile, centered card on desktop — class type/time/
// duration/description, a staff-only attendee roster, and a role-aware
// booking action. Mirrors `ClassDetailView.swift` collapsed into a single
// modal instead of a pushed screen.
export default function ClassDetailModal({
  gymId,
  gymClass,
  isBooked,
  isWaitlisted,
  onClose,
  onChanged,
}: ClassDetailModalProps) {
  const { user, canManageClasses } = useAuth()
  const [localBooked, setLocalBooked] = useState(isBooked)
  const [localWaitlisted, setLocalWaitlisted] = useState(isWaitlisted)
  const [attendees, setAttendees] = useState<Attendee[]>([])
  const [isLoadingAttendees, setIsLoadingAttendees] = useState(false)
  const [isActionLoading, setIsActionLoading] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [showCancelCard, setShowCancelCard] = useState(false)
  const [showSuccessPopup, setShowSuccessPopup] = useState(false)

  useEffect(() => {
    setLocalBooked(isBooked)
    setLocalWaitlisted(isWaitlisted)
  }, [isBooked, isWaitlisted])

  useEffect(() => {
    if (!canManageClasses) return
    let cancelled = false
    setIsLoadingAttendees(true)
    fetchAttendees(gymId, gymClass.id)
      .then((result) => {
        if (!cancelled) setAttendees(result)
      })
      .finally(() => {
        if (!cancelled) setIsLoadingAttendees(false)
      })
    return () => {
      cancelled = true
    }
  }, [gymId, gymClass.id, canManageClasses])

  const isFull = gymClass.currentAttendees >= gymClass.capacity

  const handleBook = async () => {
    if (!user) return
    setIsActionLoading(true)
    setActionError(null)
    try {
      await bookClass(gymId, gymClass.id, user.uid)
      setLocalBooked(true)
      onChanged()
      setShowSuccessPopup(true)
      setTimeout(() => setShowSuccessPopup(false), 1500)
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Failed to book')
    } finally {
      setIsActionLoading(false)
    }
  }

  const handleConfirmCancel = async () => {
    if (!user) return
    setIsActionLoading(true)
    setActionError(null)
    try {
      await cancelBooking(gymId, gymClass.id, user.uid)
      setLocalBooked(false)
      onChanged()
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Failed to cancel')
    } finally {
      setIsActionLoading(false)
      setShowCancelCard(false)
    }
  }

  const handleJoinWaitlist = async () => {
    if (!user) return
    setIsActionLoading(true)
    setActionError(null)
    try {
      await joinWaitlist(gymId, gymClass.id, user.uid)
      setLocalWaitlisted(true)
      onChanged()
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Failed to join waitlist')
    } finally {
      setIsActionLoading(false)
    }
  }

  const handleLeaveWaitlist = async () => {
    if (!user) return
    setIsActionLoading(true)
    setActionError(null)
    try {
      await leaveWaitlist(gymId, gymClass.id, user.uid)
      setLocalWaitlisted(false)
      onChanged()
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Failed to leave waitlist')
    } finally {
      setIsActionLoading(false)
    }
  }

  return (
    <>
      <div
        className="fixed inset-0 z-40 flex items-end justify-center bg-black/50 md:items-center"
        onClick={onClose}
      >
        <div
          className="flex max-h-[85vh] w-full flex-col overflow-y-auto rounded-t-3xl bg-neutral-900 p-6 md:max-w-md md:rounded-3xl"
          onClick={(event) => event.stopPropagation()}
        >
          <div className="mb-4 flex items-start justify-between gap-4">
            <div>
              <h2 className="text-xl font-bold text-white">{gymClass.classType}</h2>
              <p className="mt-1 flex items-center gap-1.5 text-sm text-neutral-400">
                <Clock size={14} />
                {formatDateTime(gymClass.startTime)} · {gymClass.durationMinutes} min
              </p>
              <p className="mt-1 flex items-center gap-1.5 text-sm text-neutral-400">
                <UserIcon size={14} />
                {gymClass.coach}
              </p>
            </div>
            <button
              type="button"
              onClick={onClose}
              className="shrink-0 rounded-full p-1.5 text-neutral-500 transition-colors hover:bg-white/5 hover:text-white"
            >
              <X size={18} />
            </button>
          </div>

          {gymClass.description && (
            <p className="mb-4 text-sm text-neutral-300">{gymClass.description}</p>
          )}

          {canManageClasses && (
            <div className="mb-4">
              <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-neutral-500">
                Attendees ({attendees.length})
              </h3>
              {isLoadingAttendees ? (
                <div className="h-16 animate-pulse rounded-xl bg-white/5" />
              ) : attendees.length === 0 ? (
                <p className="text-sm text-neutral-500">No attendees yet</p>
              ) : (
                <ul className="max-h-40 overflow-y-auto rounded-xl border border-white/10">
                  {attendees.map((attendee, index) => (
                    <li
                      key={attendee.id}
                      className={`px-3 py-2.5 text-sm text-neutral-200 ${
                        index > 0 ? 'border-t border-white/5' : ''
                      }`}
                    >
                      {attendee.name}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}

          {actionError && <p className="mb-3 text-sm text-red-400">{actionError}</p>}

          {!canManageClasses && (
            <ActionButton
              isBooked={localBooked}
              isWaitlisted={localWaitlisted}
              isFull={isFull}
              isLoading={isActionLoading}
              onBook={() => void handleBook()}
              onCancel={() => setShowCancelCard(true)}
              onJoinWaitlist={() => void handleJoinWaitlist()}
              onLeaveWaitlist={() => void handleLeaveWaitlist()}
            />
          )}
        </div>
      </div>

      {showCancelCard && (
        <CancelBookingCard
          classTitle={gymClass.classType}
          onConfirm={() => void handleConfirmCancel()}
          onKeep={() => setShowCancelCard(false)}
        />
      )}

      {showSuccessPopup && <BookingSuccessPopup classTitle={gymClass.classType} />}
    </>
  )
}

function ActionButton({
  isBooked,
  isWaitlisted,
  isFull,
  isLoading,
  onBook,
  onCancel,
  onJoinWaitlist,
  onLeaveWaitlist,
}: {
  isBooked: boolean
  isWaitlisted: boolean
  isFull: boolean
  isLoading: boolean
  onBook: () => void
  onCancel: () => void
  onJoinWaitlist: () => void
  onLeaveWaitlist: () => void
}) {
  if (isBooked) {
    return (
      <button
        type="button"
        onClick={onCancel}
        disabled={isLoading}
        className="w-full rounded-full bg-red-600 py-3 text-sm font-semibold text-white transition-colors hover:bg-red-500 disabled:opacity-50"
      >
        Cancel Booking
      </button>
    )
  }
  if (isWaitlisted) {
    return (
      <button
        type="button"
        onClick={onLeaveWaitlist}
        disabled={isLoading}
        className="w-full rounded-full bg-orange-500/20 py-3 text-sm font-semibold text-orange-400 transition-colors hover:bg-orange-500/30 disabled:opacity-50"
      >
        Leave Waitlist
      </button>
    )
  }
  if (isFull) {
    return (
      <button
        type="button"
        onClick={onJoinWaitlist}
        disabled={isLoading}
        className="w-full rounded-full bg-orange-500 py-3 text-sm font-semibold text-white transition-colors hover:bg-orange-400 disabled:opacity-50"
      >
        Join Waitlist
      </button>
    )
  }
  return (
    <button
      type="button"
      onClick={onBook}
      disabled={isLoading}
      className="w-full rounded-full bg-blue-600 py-3 text-sm font-semibold text-white transition-colors hover:bg-blue-500 disabled:opacity-50"
    >
      Book Class
    </button>
  )
}

// Centered, frosted-glass "Booked!" card. The backdrop is fully transparent
// (no dimming, per spec) and `pointer-events-none` so it never traps clicks
// on whatever's behind it during its brief 1.5s lifetime.
function BookingSuccessPopup({ classTitle }: { classTitle: string }) {
  const [isAnimating, setIsAnimating] = useState(false)

  useEffect(() => {
    const id = requestAnimationFrame(() => setIsAnimating(true))
    return () => cancelAnimationFrame(id)
  }, [])

  return (
    <div className="pointer-events-none fixed inset-0 z-50 flex items-center justify-center bg-transparent">
      <div
        className={`flex max-w-[280px] flex-col items-center gap-3 rounded-3xl border border-white/10 bg-white/5 p-6 text-center shadow-2xl backdrop-blur-md transition-all duration-300 ${
          isAnimating ? 'scale-100 opacity-100' : 'scale-75 opacity-0'
        }`}
      >
        <div className="flex h-14 w-14 items-center justify-center rounded-full bg-green-500">
          <CheckCircle2 size={28} className="text-white" />
        </div>
        <div>
          <p className="text-lg font-bold text-white">Booked!</p>
          <p className="text-sm text-neutral-300">{classTitle}</p>
        </div>
      </div>
    </div>
  )
}

// Centered cancellation confirmation. The backdrop is a near-invisible
// touch blocker (bg-black/[0.001]) — it intercepts clicks on the page
// behind it without changing the screen's brightness/contrast at all.
function CancelBookingCard({
  classTitle,
  onConfirm,
  onKeep,
}: {
  classTitle: string
  onConfirm: () => void
  onKeep: () => void
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/[0.001]">
      <div className="mx-6 flex w-full max-w-xs flex-col gap-5 rounded-3xl border border-white/10 bg-neutral-900 p-6 text-center shadow-2xl">
        <div>
          <h3 className="text-lg font-semibold text-white">Cancel Booking?</h3>
          <p className="mt-1 text-sm text-neutral-400">
            Are you sure you want to cancel your booking for {classTitle}?
          </p>
        </div>
        <div className="flex flex-col gap-2">
          <button
            type="button"
            onClick={onConfirm}
            className="rounded-full bg-red-600 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-red-500"
          >
            Cancel Booking
          </button>
          <button
            type="button"
            onClick={onKeep}
            className="rounded-full bg-white/5 py-2.5 text-sm font-semibold text-neutral-300 transition-colors hover:bg-white/10"
          >
            Keep Booking
          </button>
        </div>
      </div>
    </div>
  )
}
