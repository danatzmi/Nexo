import { useCallback, useEffect, useState } from 'react'
import { Plus } from 'lucide-react'
import { deleteMembershipPlan, fetchMembershipPlans } from '../../lib/plans'
import AddPlanModal from './AddPlanModal'
import ConfirmDialog from '../ConfirmDialog'
import type { MembershipPlan } from '../../types'

function planSubtitle(plan: MembershipPlan): string {
  const component = plan.components[0]
  if (!component) return `₪${plan.price}`
  const type =
    component.type === 'unlimited' ? 'Unlimited' : `${component.creditCount} credits`
  return `₪${plan.price} · ${type} · ${component.validityValue} mo`
}

export default function PlansTab({ gymId }: { gymId: string }) {
  const [plans, setPlans] = useState<MembershipPlan[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showAddModal, setShowAddModal] = useState(false)
  const [planToEdit, setPlanToEdit] = useState<MembershipPlan | null>(null)
  const [planToDelete, setPlanToDelete] = useState<MembershipPlan | null>(null)

  const load = useCallback(async () => {
    setIsLoading(true)
    try {
      setPlans(await fetchMembershipPlans(gymId))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load plans')
    } finally {
      setIsLoading(false)
    }
  }, [gymId])

  useEffect(() => {
    void load()
  }, [load])

  const handleDelete = async (plan: MembershipPlan) => {
    await deleteMembershipPlan(gymId, plan.id)
    setPlanToDelete(null)
    void load()
  }

  return (
    <div className="flex flex-1 flex-col gap-4 px-6 py-6 md:px-10 md:py-10">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-neutral-400">{plans.length} Plans</h2>
        <button
          type="button"
          onClick={() => setShowAddModal(true)}
          className="flex items-center gap-1.5 rounded-full bg-blue-600 px-4 py-2 text-xs font-semibold text-white transition-colors hover:bg-blue-500"
        >
          <Plus size={14} />
          Add Plan
        </button>
      </div>

      {isLoading ? (
        <div className="h-32 animate-pulse rounded-2xl bg-white/5" />
      ) : error ? (
        <p className="text-sm text-red-400">{error}</p>
      ) : plans.length === 0 ? (
        <div className="flex flex-col items-center gap-2 rounded-2xl border border-white/10 bg-neutral-900 p-8 text-center">
          <p className="text-sm text-neutral-400">Create a membership plan to get started.</p>
        </div>
      ) : (
        <ul className="flex flex-col gap-2">
          {plans.map((plan) => (
            <li
              key={plan.id}
              className="flex items-center gap-4 rounded-2xl border border-white/10 bg-neutral-900 p-4"
            >
              <div className="flex flex-1 flex-col overflow-hidden">
                <span className="truncate font-semibold text-white">{plan.name}</span>
                <span className="truncate text-xs text-neutral-500">{planSubtitle(plan)}</span>
              </div>
              <button
                type="button"
                onClick={() => setPlanToEdit(plan)}
                className="shrink-0 rounded-full px-3 py-1.5 text-xs font-medium text-neutral-400 transition-colors hover:bg-white/5 hover:text-white"
              >
                Edit
              </button>
              <button
                type="button"
                onClick={() => setPlanToDelete(plan)}
                className="shrink-0 rounded-full px-3 py-1.5 text-xs font-medium text-red-400 transition-colors hover:bg-red-500/10"
              >
                Delete
              </button>
            </li>
          ))}
        </ul>
      )}

      {showAddModal && (
        <AddPlanModal gymId={gymId} onClose={() => setShowAddModal(false)} onSaved={load} />
      )}
      {planToEdit && (
        <AddPlanModal
          gymId={gymId}
          existingPlan={planToEdit}
          onClose={() => setPlanToEdit(null)}
          onSaved={load}
        />
      )}
      {planToDelete && (
        <ConfirmDialog
          title={`Delete "${planToDelete.name}"?`}
          message="This cannot be undone."
          actions={[
            {
              label: 'Delete',
              variant: 'destructive',
              onClick: () => void handleDelete(planToDelete),
            },
          ]}
          onCancel={() => setPlanToDelete(null)}
        />
      )}
    </div>
  )
}
