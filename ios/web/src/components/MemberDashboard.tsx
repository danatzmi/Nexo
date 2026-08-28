import { useEffect, useState } from 'react'
import { Clock, User as UserIcon } from 'lucide-react'
import { fetchMemberBookings } from '../lib/classes'
import type { GymClass } from '../types'

function formatDateTime(date: Date) {
  return date.toLocaleString([], {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}

interface MemberDashboardProps {
  gymId: string
  userId: string
  onBook: () => void
}

// Member layout: the earliest upcoming booking, or a "book a class" prompt.
// Mirrors `GymHomeViewModel.loadMemberData()` + `NextBookingCard` in
// `GymHomeView.swift`.
export default function MemberDashboard({
  gymId,
  userId,
  onBook,
}: MemberDashboardProps) {
  const [nextBooking, setNextBooking] = useState<GymClass | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setIsLoading(true)
    fetchMemberBookings(gymId, userId)
      .then((bookings) => {
        if (cancelled) return
        const now = new Date()
        const upcoming = bookings
          .filter((booking) => booking.startTime >= now)
          .sort((a, b) => a.startTime.getTime() - b.startTime.getTime())
        setNextBooking(upcoming[0] ?? null)
      })
      .catch((err) => {
        if (!cancelled) {
          setError(
            err instanceof Error ? err.message : 'Failed to load your dashboard',
          )
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [gymId, userId])

  if (isLoading) {
    return <div className="h-32 animate-pulse rounded-2xl bg-white/5" />
  }

  if (error) {
    return <p className="text-sm text-red-400">{error}</p>
  }

  if (nextBooking) {
    return (
      <div className="flex overflow-hidden rounded-2xl border border-white/10 bg-neutral-900">
        <div className="w-1 shrink-0 bg-blue-500" />
        <div className="flex flex-1 flex-col gap-2 p-5">
          <span className="text-xs font-semibold uppercase tracking-wide text-neutral-500">
            Next Booking
          </span>
          <span className="text-lg font-bold text-white">
            {nextBooking.classType}
          </span>
          <span className="flex items-center gap-1.5 text-sm text-neutral-300">
            <Clock size={14} />
            {formatDateTime(nextBooking.startTime)}
          </span>
          <span className="flex items-center gap-1.5 text-sm text-neutral-500">
            <UserIcon size={14} />
            {nextBooking.coach}
          </span>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col items-center gap-3 rounded-2xl border border-white/10 bg-neutral-900 p-8 text-center">
      <p className="text-sm text-neutral-400">No upcoming classes.</p>
      <button
        type="button"
        onClick={onBook}
        className="rounded-full bg-blue-600 px-5 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-blue-500"
      >
        Book a Class
      </button>
    </div>
  )
}
