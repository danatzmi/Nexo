import { useCallback, useEffect, useState } from 'react'
import { Plus, X } from 'lucide-react'
import { fetchMembers, removeMember } from '../../lib/members'
import AddMemberSheet from './AddMemberSheet'
import ConfirmDialog from '../ConfirmDialog'
import type { Member } from '../../types'

export default function MembersTab({ gymId }: { gymId: string }) {
  const [members, setMembers] = useState<Member[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showAddSheet, setShowAddSheet] = useState(false)
  const [memberToRemove, setMemberToRemove] = useState<Member | null>(null)

  const load = useCallback(async () => {
    setIsLoading(true)
    try {
      setMembers(await fetchMembers(gymId))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load members')
    } finally {
      setIsLoading(false)
    }
  }, [gymId])

  useEffect(() => {
    void load()
  }, [load])

  const handleRemove = async (member: Member) => {
    await removeMember(gymId, member.id)
    setMemberToRemove(null)
    void load()
  }

  return (
    <div className="flex flex-1 flex-col gap-4 px-6 py-6 md:px-10 md:py-10">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-neutral-400">{members.length} Members</h2>
        <button
          type="button"
          onClick={() => setShowAddSheet(true)}
          className="flex items-center gap-1.5 rounded-full bg-blue-600 px-4 py-2 text-xs font-semibold text-white transition-colors hover:bg-blue-500"
        >
          <Plus size={14} />
          Add Member
        </button>
      </div>

      {isLoading ? (
        <div className="h-32 animate-pulse rounded-2xl bg-white/5" />
      ) : error ? (
        <p className="text-sm text-red-400">{error}</p>
      ) : members.length === 0 ? (
        <div className="flex flex-col items-center gap-2 rounded-2xl border border-white/10 bg-neutral-900 p-8 text-center">
          <p className="text-sm text-neutral-400">Add members to get started.</p>
        </div>
      ) : (
        <ul className="flex flex-col gap-2">
          {members.map((member) => (
            <li
              key={member.id}
              className="flex items-center gap-4 rounded-2xl border border-white/10 bg-neutral-900 p-4"
            >
              <div className="flex flex-1 flex-col overflow-hidden">
                <span className="truncate font-semibold text-white">{member.name}</span>
                <span className="truncate text-xs text-neutral-500">{member.email}</span>
              </div>
              <button
                type="button"
                onClick={() => setMemberToRemove(member)}
                className="shrink-0 rounded-full p-2 text-neutral-500 transition-colors hover:bg-red-500/10 hover:text-red-400"
              >
                <X size={16} />
              </button>
            </li>
          ))}
        </ul>
      )}

      {showAddSheet && (
        <AddMemberSheet
          gymId={gymId}
          existingMemberIds={new Set(members.map((member) => member.id))}
          onClose={() => setShowAddSheet(false)}
          onAdded={load}
        />
      )}
      {memberToRemove && (
        <ConfirmDialog
          title={`Remove ${memberToRemove.name}?`}
          message="They will lose access to this gym. This cannot be undone."
          actions={[
            {
              label: 'Remove',
              variant: 'destructive',
              onClick: () => void handleRemove(memberToRemove),
            },
          ]}
          onCancel={() => setMemberToRemove(null)}
        />
      )}
    </div>
  )
}
