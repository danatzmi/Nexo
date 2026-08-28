import { useState } from 'react'
import { X } from 'lucide-react'
import { savePlan } from '../../lib/plans'
import type { MembershipPlan } from '../../types'

interface AddPlanModalProps {
  gymId: string
  existingPlan?: MembershipPlan
  onClose: () => void
  onSaved: () => void
}

type PlanType = 'unlimited' | 'credits'

// Web's plan model is deliberately simplified from iOS: iOS lets a single
// plan bundle several `PlanComponent`s (each independently scoped to a
// class type, credit count, and validity window). This form only asks for
// the fields the M4 spec named — Name / Cost / Duration (months) / Type —
// and always writes exactly one `PlanComponent` with `workoutType: null`
// (all classes) and `validityUnit: 'months'`, keeping the Firestore
// document shape compatible with what iOS reads.
export default function AddPlanModal({ gymId, existingPlan, onClose, onSaved }: AddPlanModalProps) {
  const isEditMode = Boolean(existingPlan)
  const existingComponent = existingPlan?.components[0]

  const [name, setName] = useState(existingPlan?.name ?? '')
  const [price, setPrice] = useState(existingPlan?.price ?? 0)
  const [type, setType] = useState<PlanType>(existingComponent?.type ?? 'unlimited')
  const [creditCount, setCreditCount] = useState(existingComponent?.creditCount ?? 10)
  const [durationMonths, setDurationMonths] = useState(existingComponent?.validityValue ?? 1)

  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const isValid = name.trim().length > 0 && price >= 0 && durationMonths > 0

  const handleSave = async () => {
    if (!isValid) return
    setIsSaving(true)
    setError(null)
    try {
      const plan: MembershipPlan = {
        id: existingPlan?.id ?? '',
        name: name.trim(),
        price,
        components: [
          {
            id: existingComponent?.id ?? crypto.randomUUID(),
            type,
            workoutType: null,
            creditCount: type === 'credits' ? creditCount : 0,
            validityValue: durationMonths,
            validityUnit: 'months',
          },
        ],
      }
      await savePlan(gymId, plan)
      onSaved()
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save plan')
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
          <h2 className="text-xl font-bold text-white">{isEditMode ? 'Edit Plan' : 'New Plan'}</h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-1.5 text-neutral-500 transition-colors hover:bg-white/5 hover:text-white"
          >
            <X size={18} />
          </button>
        </div>

        <label className="flex flex-col gap-1.5">
          <span className="text-xs font-medium text-neutral-400">Plan Name</span>
          <input
            type="text"
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="e.g. Unlimited Monthly"
            className="w-full rounded-xl border border-white/10 bg-neutral-800 px-3 py-2.5 text-sm text-white placeholder-neutral-500"
          />
        </label>

        <div className="grid grid-cols-2 gap-3">
          <label className="flex flex-col gap-1.5">
            <span className="text-xs font-medium text-neutral-400">Cost (ILS)</span>
            <input
              type="number"
              min={0}
              value={price}
              onChange={(event) => setPrice(Number(event.target.value))}
              className="w-full rounded-xl border border-white/10 bg-neutral-800 px-3 py-2.5 text-sm text-white"
            />
          </label>
          <label className="flex flex-col gap-1.5">
            <span className="text-xs font-medium text-neutral-400">Duration (months)</span>
            <input
              type="number"
              min={1}
              value={durationMonths}
              onChange={(event) => setDurationMonths(Number(event.target.value))}
              className="w-full rounded-xl border border-white/10 bg-neutral-800 px-3 py-2.5 text-sm text-white"
            />
          </label>
        </div>

        <div className="flex flex-col gap-2">
          <span className="text-xs font-medium text-neutral-400">Type</span>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => setType('unlimited')}
              className={`flex-1 rounded-full px-3 py-2 text-xs font-semibold transition-colors ${
                type === 'unlimited'
                  ? 'bg-blue-600 text-white'
                  : 'bg-white/5 text-neutral-300 hover:bg-white/10'
              }`}
            >
              Unlimited
            </button>
            <button
              type="button"
              onClick={() => setType('credits')}
              className={`flex-1 rounded-full px-3 py-2 text-xs font-semibold transition-colors ${
                type === 'credits'
                  ? 'bg-blue-600 text-white'
                  : 'bg-white/5 text-neutral-300 hover:bg-white/10'
              }`}
            >
              Fixed Credits
            </button>
          </div>
        </div>

        {type === 'credits' && (
          <label className="flex flex-col gap-1.5">
            <span className="text-xs font-medium text-neutral-400">Credit Count</span>
            <input
              type="number"
              min={1}
              value={creditCount}
              onChange={(event) => setCreditCount(Number(event.target.value))}
              className="w-full rounded-xl border border-white/10 bg-neutral-800 px-3 py-2.5 text-sm text-white"
            />
          </label>
        )}

        {error && <p className="text-sm text-red-400">{error}</p>}

        <button
          type="button"
          onClick={() => void handleSave()}
          disabled={!isValid || isSaving}
          className="rounded-full bg-blue-600 py-3 text-sm font-semibold text-white transition-colors hover:bg-blue-500 disabled:opacity-50"
        >
          {isSaving ? 'Saving…' : isEditMode ? 'Save Changes' : 'Create Plan'}
        </button>
      </div>
    </div>
  )
}
