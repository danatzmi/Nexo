import { useState } from 'react'
import { ChevronDown, LayoutGrid } from 'lucide-react'
import { useAuth } from '../context/useAuth'
import type { Gym, UserRole } from '../types'

const roleLabel: Record<UserRole, string> = {
  owner: 'Owner',
  coach: 'Coach',
  member: 'Member',
}

// The active gym's name, shown as a prominent header trigger — tapping it
// opens a dropdown listing every gym the user belongs to (mirrors the
// `Menu` in `GymHomeView.swift`'s `gymBannerCard`). Renders nothing if
// there's no active gym (callers only mount this once inside a gym context).
export default function GymSwitcher() {
  const { myGyms, currentGym, isAdmin, enterGym, leaveGym } = useAuth()
  const [isOpen, setIsOpen] = useState(false)

  if (!currentGym) return null

  const handleSelect = (gym: Gym, role: UserRole) => {
    enterGym(gym, role)
    setIsOpen(false)
  }

  return (
    <div className="relative inline-block">
      <button
        type="button"
        onClick={() => setIsOpen((open) => !open)}
        className="flex items-center gap-2 text-3xl font-bold tracking-tight text-white"
      >
        {currentGym.name}
        <ChevronDown size={22} className="text-neutral-400" />
      </button>

      {isOpen && (
        <>
          {/* Invisible full-screen backdrop — click anywhere outside the
              menu to close it. */}
          <div
            className="fixed inset-0 z-10"
            onClick={() => setIsOpen(false)}
          />
          <div className="absolute left-0 z-20 mt-2 w-72 overflow-hidden rounded-2xl border border-white/10 bg-neutral-900 shadow-2xl">
            <ul className="max-h-72 overflow-y-auto py-1">
              {myGyms.map(({ gym, role }) => (
                <li key={gym.id}>
                  <button
                    type="button"
                    onClick={() => handleSelect(gym, role)}
                    className={`flex w-full items-center px-4 py-3 text-left text-sm transition-colors hover:bg-white/5 ${
                      gym.id === currentGym.id ? 'text-white' : 'text-neutral-300'
                    }`}
                  >
                    {gym.name}{' '}
                    <span className="ml-1 text-neutral-500">
                      ({roleLabel[role]})
                    </span>
                  </button>
                </li>
              ))}
            </ul>

            {isAdmin && (
              <>
                <div className="border-t border-white/10" />
                <button
                  type="button"
                  onClick={() => {
                    leaveGym()
                    setIsOpen(false)
                  }}
                  className="flex w-full items-center gap-2 px-4 py-3 text-left text-sm font-medium text-blue-400 transition-colors hover:bg-white/5"
                >
                  <LayoutGrid size={16} />
                  Platform Dashboard
                </button>
              </>
            )}
          </div>
        </>
      )}
    </div>
  )
}
