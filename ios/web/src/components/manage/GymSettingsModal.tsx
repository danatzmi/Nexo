import { useState } from 'react'
import { Trash2, X } from 'lucide-react'
import { useAuth } from '../../context/useAuth'
import { updateGymSettings } from '../../lib/gym'
import type { Gym } from '../../types'

interface GymSettingsModalProps {
  gym: Gym
  onClose: () => void
}

// Mirrors `GymSettingsSheet.swift`: rename the gym and manage its class-type
// list. On save, writes to Firestore then patches `AuthContext` in place
// (`updateGymInPlace`) so the rename/class-type change is reflected
// immediately everywhere without a full re-fetch.
export default function GymSettingsModal({ gym, onClose }: GymSettingsModalProps) {
  const { updateGymInPlace } = useAuth()

  const [name, setName] = useState(gym.name)
  const [workoutTypes, setWorkoutTypes] = useState<string[]>(gym.workoutTypes)
  const [newType, setNewType] = useState('')

  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const isValid = name.trim().length > 0

  const handleAddType = () => {
    const trimmed = newType.trim()
    if (!trimmed || workoutTypes.includes(trimmed)) return
    setWorkoutTypes((current) => [...current, trimmed])
    setNewType('')
  }

  const handleRemoveType = (type: string) => {
    setWorkoutTypes((current) => current.filter((existing) => existing !== type))
  }

  const handleSave = async () => {
    if (!isValid) return
    setIsSaving(true)
    setError(null)
    try {
      const trimmedName = name.trim()
      await updateGymSettings(gym.id, trimmedName, workoutTypes)
      updateGymInPlace({ ...gym, name: trimmedName, workoutTypes })
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save settings')
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
        className="flex max-h-[90vh] w-full flex-col gap-4 overflow-y-auto rounded-t-3xl bg-neutral-900 p-6 md:max-w-md md:rounded-3xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-bold text-white">Gym Settings</h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-1.5 text-neutral-500 transition-colors hover:bg-white/5 hover:text-white"
          >
            <X size={18} />
          </button>
        </div>

        <label className="flex flex-col gap-1.5">
          <span className="text-xs font-medium text-neutral-400">Gym Name</span>
          <input
            type="text"
            value={name}
            onChange={(event) => setName(event.target.value)}
            className="w-full rounded-xl border border-white/10 bg-neutral-800 px-3 py-2.5 text-sm text-white"
          />
        </label>

        <div className="flex flex-col gap-2">
          <span className="text-xs font-medium text-neutral-400">Class Types</span>
          <ul className="flex flex-col gap-1.5">
            {workoutTypes.map((type) => (
              <li
                key={type}
                className="flex items-center justify-between rounded-xl border border-white/10 px-3 py-2"
              >
                <span className="text-sm text-white">{type}</span>
                <button
                  type="button"
                  onClick={() => handleRemoveType(type)}
                  className="rounded-full p-1 text-neutral-500 transition-colors hover:bg-red-500/10 hover:text-red-400"
                >
                  <Trash2 size={14} />
                </button>
              </li>
            ))}
          </ul>
          <div className="flex gap-2">
            <input
              type="text"
              value={newType}
              onChange={(event) => setNewType(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  event.preventDefault()
                  handleAddType()
                }
              }}
              placeholder="New class type"
              className="flex-1 rounded-xl border border-white/10 bg-neutral-800 px-3 py-2.5 text-sm text-white placeholder-neutral-500"
            />
            <button
              type="button"
              onClick={handleAddType}
              className="shrink-0 rounded-xl bg-white/5 px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-white/10"
            >
              Add
            </button>
          </div>
        </div>

        {error && <p className="text-sm text-red-400">{error}</p>}

        <button
          type="button"
          onClick={() => void handleSave()}
          disabled={!isValid || isSaving}
          className="rounded-full bg-blue-600 py-3 text-sm font-semibold text-white transition-colors hover:bg-blue-500 disabled:opacity-50"
        >
          {isSaving ? 'Saving…' : 'Save Changes'}
        </button>
      </div>
    </div>
  )
}
