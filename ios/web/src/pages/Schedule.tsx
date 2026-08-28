import { useCallback, useEffect, useMemo, useState } from 'react'
import { Calendar as CalendarIcon, CheckCircle2, User as UserIcon } from 'lucide-react'
import { useAuth } from '../context/useAuth'
import { fetchUserBookings, fetchUserWaitlist, observeClassesForDate } from '../lib/classes'
import ClassDetailModal from '../components/ClassDetailModal'
import type { GymClass } from '../types'

const weekdaySymbols = ['S', 'M', 'T', 'W', 'T', 'F', 'S']

function startOfWeek(date: Date): Date {
  const start = new Date(date)
  start.setHours(0, 0, 0, 0)
  start.setDate(start.getDate() - start.getDay())
  return start
}

function weekDatesFor(date: Date): Date[] {
  const start = startOfWeek(date)
  return Array.from({ length: 7 }, (_, index) => {
    const day = new Date(start)
    day.setDate(day.getDate() + index)
    return day
  })
}

function isSameDay(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  )
}

function toInputValue(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatTimeRange(gymClass: GymClass): string {
  const end = new Date(
    gymClass.startTime.getTime() + gymClass.durationMinutes * 60000,
  )
  const format = (date: Date) =>
    date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })
  return `${format(gymClass.startTime)} - ${format(end)} (${gymClass.durationMinutes} min)`
}

// Weekly calendar strip + real-time class list for the selected day —
// mirrors `ScheduleView.swift`'s `WeekDayPicker` + `ClassRow` list, opening
// `ClassDetailModal` in place of the iOS `NavigationLink`/`ClassDetailView`.
export default function Schedule() {
  const { user, currentGym } = useAuth()
  const [selectedDate, setSelectedDate] = useState(() => new Date())
  const [classes, setClasses] = useState<GymClass[]>([])
  const [bookedClassIds, setBookedClassIds] = useState<Set<string>>(new Set())
  const [waitlistedClassIds, setWaitlistedClassIds] = useState<Set<string>>(new Set())
  const [isLoading, setIsLoading] = useState(true)
  const [selectedClass, setSelectedClass] = useState<GymClass | null>(null)
  const [showDatePicker, setShowDatePicker] = useState(false)

  const weekDates = useMemo(() => weekDatesFor(selectedDate), [selectedDate])

  const refreshBookingStatus = useCallback(async () => {
    if (!currentGym || !user) return
    const [bookings, waitlist] = await Promise.all([
      fetchUserBookings(currentGym.id, user.uid),
      fetchUserWaitlist(currentGym.id, user.uid),
    ])
    setBookedClassIds(bookings)
    setWaitlistedClassIds(waitlist)
  }, [currentGym, user])

  useEffect(() => {
    void refreshBookingStatus()
  }, [refreshBookingStatus])

  useEffect(() => {
    if (!currentGym) return
    setIsLoading(true)
    const unsubscribe = observeClassesForDate(currentGym.id, selectedDate, (updated) => {
      setClasses([...updated].sort((a, b) => a.startTime.getTime() - b.startTime.getTime()))
      setIsLoading(false)
    })
    return unsubscribe
  }, [currentGym, selectedDate])

  if (!currentGym) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center gap-2 px-6 text-center">
        <p className="text-sm text-neutral-400">
          Select a gym on Home to view its schedule.
        </p>
      </div>
    )
  }

  return (
    <div className="flex flex-1 flex-col gap-6 px-6 py-6 md:px-10 md:py-10">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-white">Schedule</h1>
        <div className="relative">
          <button
            type="button"
            onClick={() => setShowDatePicker((open) => !open)}
            className="flex h-10 w-10 items-center justify-center rounded-full text-neutral-400 transition-colors hover:bg-white/5 hover:text-white"
          >
            <CalendarIcon size={20} />
          </button>
          {showDatePicker && (
            <input
              type="date"
              autoFocus
              value={toInputValue(selectedDate)}
              onChange={(event) => {
                const value = event.target.value
                if (value) {
                  const [year, month, day] = value.split('-').map(Number)
                  setSelectedDate(new Date(year, month - 1, day))
                }
                setShowDatePicker(false)
              }}
              onBlur={() => setShowDatePicker(false)}
              className="absolute right-0 z-10 mt-2 rounded-xl border border-white/10 bg-neutral-900 px-3 py-2 text-sm text-white"
            />
          )}
        </div>
      </div>

      <div className="flex gap-2 overflow-x-auto pb-1">
        {weekDates.map((date) => {
          const isActive = isSameDay(date, selectedDate)
          return (
            <button
              key={date.toISOString()}
              type="button"
              onClick={() => setSelectedDate(date)}
              className={`flex min-w-[52px] flex-col items-center gap-1 rounded-full px-3 py-2.5 text-sm transition-colors ${
                isActive
                  ? 'bg-blue-600 text-white'
                  : 'bg-white/5 text-neutral-300 hover:bg-white/10'
              }`}
            >
              <span className="text-xs font-medium opacity-80">
                {weekdaySymbols[date.getDay()]}
              </span>
              <span className="text-base font-semibold">{date.getDate()}</span>
            </button>
          )
        })}
      </div>

      {isLoading ? (
        <div className="h-32 animate-pulse rounded-2xl bg-white/5" />
      ) : classes.length === 0 ? (
        <div className="flex flex-col items-center gap-2 rounded-2xl border border-white/10 bg-neutral-900 p-8 text-center">
          <p className="text-sm text-neutral-400">
            No classes scheduled for this day.
          </p>
        </div>
      ) : (
        <ul className="flex flex-col gap-3">
          {classes.map((gymClass) => (
            <li key={gymClass.id}>
              <ClassListCard
                gymClass={gymClass}
                isBooked={bookedClassIds.has(gymClass.id)}
                onClick={() => setSelectedClass(gymClass)}
              />
            </li>
          ))}
        </ul>
      )}

      {selectedClass && (
        <ClassDetailModal
          gymId={currentGym.id}
          gymClass={selectedClass}
          isBooked={bookedClassIds.has(selectedClass.id)}
          isWaitlisted={waitlistedClassIds.has(selectedClass.id)}
          onClose={() => setSelectedClass(null)}
          onChanged={refreshBookingStatus}
        />
      )}
    </div>
  )
}

function ClassListCard({
  gymClass,
  isBooked,
  onClick,
}: {
  gymClass: GymClass
  isBooked: boolean
  onClick: () => void
}) {
  const isFull = gymClass.currentAttendees >= gymClass.capacity
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex w-full flex-col gap-2 rounded-2xl border border-white/10 bg-neutral-900 p-4 text-left transition-colors hover:border-white/20"
    >
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-medium text-neutral-500">
          {formatTimeRange(gymClass)}
        </span>
        {isBooked && (
          <span className="flex items-center gap-1 rounded-full bg-blue-600/20 px-2.5 py-1 text-xs font-semibold text-blue-400">
            <CheckCircle2 size={12} />
            Booked
          </span>
        )}
      </div>
      <span className="text-lg font-semibold text-white">{gymClass.classType}</span>
      <div className="flex items-center justify-between">
        <span className="flex items-center gap-1.5 text-sm text-neutral-400">
          <UserIcon size={14} />
          {gymClass.coach}
        </span>
        <span
          className={`rounded-full px-2.5 py-1 text-xs font-semibold ${
            isFull ? 'bg-red-500/15 text-red-400' : 'bg-green-500/15 text-green-400'
          }`}
        >
          {isFull ? 'FULL' : `${gymClass.currentAttendees}/${gymClass.capacity}`}
        </span>
      </div>
    </button>
  )
}
