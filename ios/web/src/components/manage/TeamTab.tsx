import { useCallback, useEffect, useState } from 'react'
import { Plus } from 'lucide-react'
import { fetchTeam } from '../../lib/team'
import AddTeamMemberSheet from './AddTeamMemberSheet'
import type { TeamMember } from '../../types'

export default function TeamTab({ gymId }: { gymId: string }) {
  const [team, setTeam] = useState<TeamMember[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showAddSheet, setShowAddSheet] = useState(false)

  const load = useCallback(async () => {
    setIsLoading(true)
    try {
      setTeam(await fetchTeam(gymId))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load team')
    } finally {
      setIsLoading(false)
    }
  }, [gymId])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <div className="flex flex-1 flex-col gap-4 px-6 py-6 md:px-10 md:py-10">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-semibold text-neutral-400">{team.length} Team Members</h2>
        <button
          type="button"
          onClick={() => setShowAddSheet(true)}
          className="flex items-center gap-1.5 rounded-full bg-blue-600 px-4 py-2 text-xs font-semibold text-white transition-colors hover:bg-blue-500"
        >
          <Plus size={14} />
          Add Team Member
        </button>
      </div>

      {isLoading ? (
        <div className="h-32 animate-pulse rounded-2xl bg-white/5" />
      ) : error ? (
        <p className="text-sm text-red-400">{error}</p>
      ) : team.length === 0 ? (
        <div className="flex flex-col items-center gap-2 rounded-2xl border border-white/10 bg-neutral-900 p-8 text-center">
          <p className="text-sm text-neutral-400">Add coaches and owners to get started.</p>
        </div>
      ) : (
        <ul className="flex flex-col gap-2">
          {team.map((member) => (
            <li
              key={member.id}
              className="flex items-center gap-4 rounded-2xl border border-white/10 bg-neutral-900 p-4"
            >
              <div className="flex flex-1 flex-col overflow-hidden">
                <span className="truncate font-semibold text-white">
                  {member.firstName} {member.lastName}
                </span>
                <span className="truncate text-xs text-neutral-500">{member.email}</span>
              </div>
              <span className="shrink-0 rounded-full bg-white/5 px-2.5 py-1 text-xs font-medium capitalize text-neutral-400">
                {member.role}
              </span>
            </li>
          ))}
        </ul>
      )}

      {showAddSheet && (
        <AddTeamMemberSheet
          gymId={gymId}
          existingTeamIds={new Set(team.map((member) => member.id))}
          onClose={() => setShowAddSheet(false)}
          onAdded={load}
        />
      )}
    </div>
  )
}
