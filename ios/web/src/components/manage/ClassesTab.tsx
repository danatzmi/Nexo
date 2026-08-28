import { useCallback, useEffect, useState } from 'react'
import { Plus } from 'lucide-react'
import { deleteClass, deleteClassSeries, fetchAllClasses } from '../../lib/classes'
import AddClassModal from '../AddClassModal'
import ConfirmDialog from '../ConfirmDialog'
import type { GymClass } from '../../types'

function formatDateTime(date: Date): string {
  return date.toLocaleString([], {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}

export default function ClassesTab({ gymId }: { gymId: string }) {
  const [classes, setClasses] = useState<GymClass[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showAddModal, setShowAddModal] = useState(false)
  const [classToEdit, setClassToEdit] = useState<GymClass | null>(null)
  const [classToDelete, setClassToDelete] = useState<GymClass | null>(null)

  const load = useCallback(async () => {
    setIsLoading(true)
    try {
      setClasses(await fetchAllClasses(gymId))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load classes')
    } finally {
      setIsLoading(false)
    }
  }, [gymId])

  useEffect(() => {
    void load()
  }, [load])

  const handleDeleteThis = async (gymClass: GymClass) => {
    await deleteClass(gymId, gymClass.id)
    setClassToDelete(null)
    void load()
  }

  const handleDeleteSeries = async (gymClass: GymClass) => {
    if (!gymClass.seriesId) return
    await deleteClassSeries(gymId, gymClass.seriesId, gymClass.startTime)
    setClassToDelete(null)
    void load()
  }

  return (
    <div className="flex flex-1 flex-col gap-4 px-6 py-6 md:px-10 md:py-10">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-neutral-400">{classes.length} Classes</h2>
        <button
          type="button"
          onClick={() => setShowAddModal(true)}
          className="flex items-center gap-1.5 rounded-full bg-blue-600 px-4 py-2 text-xs font-semibold text-white transition-colors hover:bg-blue-500"
        >
          <Plus size={14} />
          Add Class
        </button>
      </div>

      {isLoading ? (
        <div className="h-32 animate-pulse rounded-2xl bg-white/5" />
      ) : error ? (
        <p className="text-sm text-red-400">{error}</p>
      ) : classes.length === 0 ? (
        <div className="flex flex-col items-center gap-2 rounded-2xl border border-white/10 bg-neutral-900 p-8 text-center">
          <p className="text-sm text-neutral-400">Create your first class to get started.</p>
        </div>
      ) : (
        <ul className="flex flex-col gap-2">
          {classes.map((gymClass) => {
            const isFull = gymClass.currentAttendees >= gymClass.capacity
            return (
              <li
                key={gymClass.id}
                className="flex items-center gap-4 rounded-2xl border border-white/10 bg-neutral-900 p-4"
              >
                <div className="flex flex-1 flex-col overflow-hidden">
                  <span className="truncate font-semibold text-white">{gymClass.title}</span>
                  <span className="truncate text-xs text-neutral-500">
                    {formatDateTime(gymClass.startTime)} · {gymClass.coach}
                  </span>
                </div>
                <span
                  className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold ${
                    isFull ? 'bg-red-500/15 text-red-400' : 'bg-green-500/15 text-green-400'
                  }`}
                >
                  {isFull ? 'FULL' : `${gymClass.currentAttendees}/${gymClass.capacity}`}
                </span>
                <button
                  type="button"
                  onClick={() => setClassToEdit(gymClass)}
                  className="shrink-0 rounded-full px-3 py-1.5 text-xs font-medium text-neutral-400 transition-colors hover:bg-white/5 hover:text-white"
                >
                  Edit
                </button>
                <button
                  type="button"
                  onClick={() => setClassToDelete(gymClass)}
                  className="shrink-0 rounded-full px-3 py-1.5 text-xs font-medium text-red-400 transition-colors hover:bg-red-500/10"
                >
                  Delete
                </button>
              </li>
            )
          })}
        </ul>
      )}

      {showAddModal && (
        <AddClassModal gymId={gymId} onClose={() => setShowAddModal(false)} onSaved={load} />
      )}
      {classToEdit && (
        <AddClassModal
          gymId={gymId}
          existingClass={classToEdit}
          onClose={() => setClassToEdit(null)}
          onSaved={load}
        />
      )}
      {classToDelete && (
        <ConfirmDialog
          title={`Delete "${classToDelete.title}"?`}
          message={
            classToDelete.seriesId
              ? 'This class is part of a recurring series.'
              : 'This cannot be undone.'
          }
          actions={
            classToDelete.seriesId
              ? [
                  {
                    label: 'Delete This Class Only',
                    variant: 'destructive',
                    onClick: () => void handleDeleteThis(classToDelete),
                  },
                  {
                    label: 'Delete This & Future Classes',
                    variant: 'destructive',
                    onClick: () => void handleDeleteSeries(classToDelete),
                  },
                ]
              : [
                  {
                    label: 'Delete',
                    variant: 'destructive',
                    onClick: () => void handleDeleteThis(classToDelete),
                  },
                ]
          }
          onCancel={() => setClassToDelete(null)}
        />
      )}
    </div>
  )
}
