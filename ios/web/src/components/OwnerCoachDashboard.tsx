import { useEffect, useState } from 'react'
import { Calendar, CheckCircle2, Plus } from 'lucide-react'
import { fetchClassesForDate } from '../lib/classes'
import type { GymClass } from '../types'

function formatTime(date: Date) {
  return date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
}

function CapacityBadge({ gymClass }: { gymClass: GymClass }) {
  const isFull = gymClass.currentAttendees >= gymClass.capacity
  return (
    <span
      className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold ${
        isFull ? 'bg-red-500/15 text-red-400' : 'bg-green-500/15 text-green-400'
      }`}
    >
      {isFull ? 'FULL' : `${gymClass.currentAttendees}/${gymClass.capacity}`}
    </span>
  )
}

interface OwnerCoachDashboardProps {
  gymId: string
  onCreateClass: () => void
}

// Owner/Coach layout: a "Total Bookings Today" scoreboard plus today's full
// class list with capacity pills. Mirrors `GymHomeViewModel.loadOwnerData()`
// and `ownerDashboard`/`scoreboardCard` in `GymHomeView.swift`, simplified
// to the single merged dashboard this milestone asked for (no separate
// coach-only "classes I teach" filter — that's an iOS-only distinction not
// carried over here).
export default function OwnerCoachDashboard({
  gymId,
  onCreateClass,
}: OwnerCoachDashboardProps) {
  const [todayClasses, setTodayClasses] = useState<GymClass[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setIsLoading(true)
    fetchClassesForDate(gymId, new Date())
      .then((classes) => {
        if (!cancelled) {
          setTodayClasses(
            [...classes].sort((a, b) => a.startTime.getTime() - b.startTime.getTime()),
          )
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(
            err instanceof Error ? err.message : "Failed to load today's classes",
          )
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [gymId])

  if (isLoading) {
    return <div className="h-32 animate-pulse rounded-2xl bg-white/5" />
  }

  if (error) {
    return <p className="text-sm text-red-400">{error}</p>
  }

  const totalBookingsToday = todayClasses.reduce(
    (sum, gymClass) => sum + gymClass.currentAttendees,
    0,
  )

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-center rounded-2xl border border-white/10 bg-neutral-900 py-6">
        <div className="flex flex-col items-center gap-1">
          <CheckCircle2 size={20} className="text-neutral-500" />
          <span className="text-3xl font-light text-white">
            {totalBookingsToday}
          </span>
          <span className="text-xs text-neutral-500">Total Bookings Today</span>
        </div>
      </div>

      <div className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-semibold text-neutral-300">
            Today&apos;s Schedule
          </h3>
          <button
            type="button"
            onClick={onCreateClass}
            className="flex items-center gap-1.5 rounded-full bg-blue-600 px-4 py-2 text-xs font-semibold text-white transition-colors hover:bg-blue-500"
          >
            <Plus size={14} />
            Schedule a Class
          </button>
        </div>

        {todayClasses.length === 0 ? (
          <div className="flex flex-col items-center gap-2 rounded-2xl border border-white/10 bg-neutral-900 p-8 text-center">
            <Calendar size={28} className="text-neutral-600" />
            <p className="text-sm text-neutral-400">No classes scheduled today.</p>
          </div>
        ) : (
          <ul className="flex flex-col gap-2">
            {todayClasses.map((gymClass) => (
              <li
                key={gymClass.id}
                className="flex items-center gap-4 rounded-2xl border border-white/10 bg-neutral-900 p-4"
              >
                <span className="shrink-0 rounded-full border border-white/10 px-3 py-1 text-xs font-semibold text-neutral-300">
                  {formatTime(gymClass.startTime)}
                </span>
                <div className="flex flex-1 flex-col overflow-hidden">
                  <span className="truncate font-semibold text-white">
                    {gymClass.classType}
                  </span>
                  <span className="truncate text-xs text-neutral-500">
                    {gymClass.coach}
                  </span>
                </div>
                <CapacityBadge gymClass={gymClass} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
