import { useState, type ReactNode } from 'react'
import { X } from 'lucide-react'
import { useAuth } from '../context/useAuth'
import { createClass, createClasses, updateClass } from '../lib/classes'
import {
  generateRecurrenceDates,
  recurrenceTypeLabels,
  type RecurrenceType,
} from '../lib/recurrence'
import type { GymClass } from '../types'

interface AddClassModalProps {
  gymId: string
  existingClass?: GymClass
  onClose: () => void
  onSaved: () => void
}

const weekdaySymbols = ['S', 'M', 'T', 'W', 'T', 'F', 'S']

function toDateTimeLocalValue(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function toDateInputValue(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function defaultEndDate(): Date {
  const date = new Date()
  date.setMonth(date.getMonth() + 3)
  return date
}

// Create/edit class form — class type dropdown, coach text input, combined
// start date/time, duration, capacity, description, and (create-only)
// recurrence: a "Repeat Class" toggle, Daily vs. Custom Weekdays, and an
// end date. Mirrors `AddClassView.swift`, adapted per the web spec's own
// field choices (a plain coach text input rather than iOS's team-sourced
// picker; only Daily/Custom recurrence, not iOS's extra Weekly/Bi-weekly/
// Monthly options).
export default function AddClassModal({
  gymId,
  existingClass,
  onClose,
  onSaved,
}: AddClassModalProps) {
  const { currentGym } = useAuth()
  const isEditMode = Boolean(existingClass)
  const availableCategories = currentGym?.workoutTypes ?? []

  const [classType, setClassType] = useState(
    existingClass?.classType ?? availableCategories[0] ?? '',
  )
  const [coach, setCoach] = useState(existingClass?.coach ?? '')
  const [startTime, setStartTime] = useState(
    existingClass?.startTime ?? new Date(Date.now() + 60 * 60 * 1000),
  )
  const [duration, setDuration] = useState(existingClass?.durationMinutes ?? 60)
  const [capacity, setCapacity] = useState(existingClass?.capacity ?? 12)
  const [description, setDescription] = useState(existingClass?.description ?? '')
  const [isPremium, setIsPremium] = useState(existingClass?.isPremium ?? false)

  const [repeatEnabled, setRepeatEnabled] = useState(false)
  const [recurrenceType, setRecurrenceType] = useState<RecurrenceType>('daily')
  const [selectedWeekdays, setSelectedWeekdays] = useState<Set<number>>(new Set())
  const [endDate, setEndDate] = useState(defaultEndDate())

  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const isValid = classType.trim().length > 0 && coach.trim().length > 0

  const toggleWeekday = (day: number) => {
    setSelectedWeekdays((current) => {
      const next = new Set(current)
      if (next.has(day)) {
        next.delete(day)
      } else {
        next.add(day)
      }
      return next
    })
  }

  const handleSave = async () => {
    if (!isValid) return
    setIsSaving(true)
    setError(null)
    try {
      if (existingClass) {
        await updateClass(gymId, {
          ...existingClass,
          title: classType,
          classType,
          coach,
          startTime,
          durationMinutes: duration,
          capacity,
          description,
          isPremium,
        })
      } else if (repeatEnabled) {
        const seriesId = crypto.randomUUID()
        const dates = generateRecurrenceDates(
          recurrenceType,
          startTime,
          endDate,
          selectedWeekdays,
        )
        await createClasses(
          gymId,
          dates.map((date) => ({
            title: classType,
            classType,
            coach,
            startTime: date,
            durationMinutes: duration,
            capacity,
            currentAttendees: 0,
            description,
            isPremium,
            seriesId,
          })),
        )
      } else {
        await createClass(gymId, {
          title: classType,
          classType,
          coach,
          startTime,
          durationMinutes: duration,
          capacity,
          currentAttendees: 0,
          description,
          isPremium,
        })
      }
      onSaved()
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save class')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <div
      className="fixed inset-0 z-40 flex items-end justify-center bg-black/50 md:items-center"
      onClick={onClose}
    >
      <div
        className="flex max-h-[90vh] w-full flex-col gap-4 overflow-y-auto rounded-t-3xl bg-neutral-900 p-6 md:max-w-lg md:rounded-3xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-bold text-white">
            {isEditMode ? 'Edit Class' : 'New Class'}
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-1.5 text-neutral-500 transition-colors hover:bg-white/5 hover:text-white"
          >
            <X size={18} />
          </button>
        </div>

        <Field label="Class Type">
          <select
            value={classType}
            onChange={(event) => setClassType(event.target.value)}
            className="w-full rounded-xl border border-white/10 bg-neutral-800 px-3 py-2.5 text-sm text-white"
          >
            {availableCategories.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
        </Field>

        <Field label="Coach">
          <input
            type="text"
            value={coach}
            onChange={(event) => setCoach(event.target.value)}
            placeholder="Coach name"
            className="w-full rounded-xl border border-white/10 bg-neutral-800 px-3 py-2.5 text-sm text-white placeholder-neutral-500"
          />
        </Field>

        <Field label="Start Date & Time">
          <input
            type="datetime-local"
            value={toDateTimeLocalValue(startTime)}
            onChange={(event) => {
              if (event.target.value) setStartTime(new Date(event.target.value))
            }}
            className="w-full rounded-xl border border-white/10 bg-neutral-800 px-3 py-2.5 text-sm text-white"
          />
        </Field>

        <div className="grid grid-cols-2 gap-3">
          <Field label="Duration (min)">
            <input
              type="number"
              min={5}
              step={5}
              value={duration}
              onChange={(event) => setDuration(Number(event.target.value))}
              className="w-full rounded-xl border border-white/10 bg-neutral-800 px-3 py-2.5 text-sm text-white"
            />
          </Field>
          <Field label="Capacity">
            <input
              type="number"
              min={1}
              value={capacity}
              onChange={(event) => setCapacity(Number(event.target.value))}
              className="w-full rounded-xl border border-white/10 bg-neutral-800 px-3 py-2.5 text-sm text-white"
            />
          </Field>
        </div>

        <Field label="Description (optional)">
          <textarea
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            rows={3}
            className="w-full resize-none rounded-xl border border-white/10 bg-neutral-800 px-3 py-2.5 text-sm text-white placeholder-neutral-500"
            placeholder="Program details, what to bring, etc."
          />
        </Field>

        <label className="flex items-center justify-between rounded-xl border border-white/10 px-3 py-2.5 text-sm font-medium text-white">
          Premium Class
          <input
            type="checkbox"
            checked={isPremium}
            onChange={(event) => setIsPremium(event.target.checked)}
            className="h-5 w-5 accent-blue-600"
          />
        </label>

        {!isEditMode && (
          <div className="flex flex-col gap-3 rounded-2xl border border-white/10 p-4">
            <label className="flex items-center justify-between text-sm font-medium text-white">
              Repeat Class
              <input
                type="checkbox"
                checked={repeatEnabled}
                onChange={(event) => setRepeatEnabled(event.target.checked)}
                className="h-5 w-5 accent-blue-600"
              />
            </label>

            {repeatEnabled && (
              <>
                <div className="flex gap-2">
                  {(['daily', 'custom'] as RecurrenceType[]).map((type) => (
                    <button
                      key={type}
                      type="button"
                      onClick={() => setRecurrenceType(type)}
                      className={`flex-1 rounded-full px-3 py-2 text-xs font-semibold transition-colors ${
                        recurrenceType === type
                          ? 'bg-blue-600 text-white'
                          : 'bg-white/5 text-neutral-300 hover:bg-white/10'
                      }`}
                    >
                      {recurrenceTypeLabels[type]}
                    </button>
                  ))}
                </div>

                {recurrenceType === 'custom' && (
                  <div className="flex justify-between gap-1">
                    {weekdaySymbols.map((symbol, day) => {
                      const isSelected = selectedWeekdays.has(day)
                      return (
                        <button
                          key={day}
                          type="button"
                          onClick={() => toggleWeekday(day)}
                          className={`flex h-9 w-9 items-center justify-center rounded-full text-xs font-semibold transition-colors ${
                            isSelected
                              ? 'bg-blue-600 text-white'
                              : 'bg-white/5 text-neutral-300 hover:bg-white/10'
                          }`}
                        >
                          {symbol}
                        </button>
                      )
                    })}
                  </div>
                )}

                <Field label="End Date">
                  <input
                    type="date"
                    value={toDateInputValue(endDate)}
                    onChange={(event) => {
                      if (event.target.value) setEndDate(new Date(event.target.value))
                    }}
                    className="w-full rounded-xl border border-white/10 bg-neutral-800 px-3 py-2.5 text-sm text-white"
                  />
                </Field>
              </>
            )}
          </div>
        )}

        {error && <p className="text-sm text-red-400">{error}</p>}

        <button
          type="button"
          onClick={() => void handleSave()}
          disabled={!isValid || isSaving}
          className="rounded-full bg-blue-600 py-3 text-sm font-semibold text-white transition-colors hover:bg-blue-500 disabled:opacity-50"
        >
          {isSaving ? 'Saving…' : isEditMode ? 'Save Changes' : 'Create Class'}
        </button>
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-xs font-medium text-neutral-400">{label}</span>
      {children}
    </label>
  )
}
