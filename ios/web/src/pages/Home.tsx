import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/useAuth'
import GymSwitcher from '../components/GymSwitcher'
import MemberDashboard from '../components/MemberDashboard'
import OwnerCoachDashboard from '../components/OwnerCoachDashboard'
import type { Gym, UserRole } from '../types'

// Shown in place of a dashboard whenever no gym is active — every gym the
// user belongs to (or, for admins, every gym in the system, via the same
// admin bypass `resolveMyGyms` already applies to `myGyms`), as clickable
// cards. Mirrors `GymPickerView.swift`/`GymSwitcherSheet.swift`'s admin
// branch, collapsed into Home instead of a separate picker screen.
function GymSelector() {
  const { myGyms, isAdmin, enterGym } = useAuth()

  return (
    <div className="flex flex-1 flex-col gap-6 px-6 py-6 md:px-10 md:py-10">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold text-white">
          {isAdmin ? 'Platform Dashboard' : 'My Gyms'}
        </h1>
        <p className="text-sm text-neutral-400">
          Choose a gym to continue.
        </p>
      </div>

      {myGyms.length === 0 ? (
        <div className="flex flex-col items-center gap-2 rounded-2xl border border-white/10 bg-neutral-900 p-8 text-center">
          <p className="text-sm text-neutral-400">
            You don&apos;t have any gym memberships yet.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {myGyms.map(({ gym, role }: { gym: Gym; role: UserRole }) => (
            <button
              key={gym.id}
              type="button"
              onClick={() => enterGym(gym, role)}
              className="flex flex-col items-start gap-2 rounded-2xl border border-white/10 bg-neutral-900 p-5 text-left transition-colors hover:border-white/20 hover:bg-neutral-800"
            >
              <span className="text-lg font-semibold text-white">{gym.name}</span>
              <span className="rounded-full bg-white/5 px-2.5 py-1 text-xs font-medium text-neutral-400">
                {role}
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

// Routes to the right dashboard based on auth state — mirrors the branching
// in `HomeView.swift` (gym → GymHomeView, no gym → gym picker/Platform
// Dashboard) and `GymHomeView.swift`'s owner/coach vs member split.
export default function Home() {
  const { user, firstName, currentGym, gymRole } = useAuth()
  const navigate = useNavigate()

  if (!user) return null

  if (!currentGym) {
    return <GymSelector />
  }

  const greeting = firstName ? `${firstName}, welcome back!` : 'Welcome back!'

  return (
    <div className="flex flex-1 flex-col gap-6 px-6 py-6 md:px-10 md:py-10">
      <div className="flex flex-col gap-1">
        <GymSwitcher />
        <p className="text-sm text-neutral-500">
          {new Date().toLocaleDateString([], {
            weekday: 'long',
            month: 'long',
            day: 'numeric',
          })}
        </p>
      </div>

      <h2 className="text-xl font-bold text-white">{greeting}</h2>

      {gymRole === 'member' ? (
        <MemberDashboard
          gymId={currentGym.id}
          userId={user.uid}
          onBook={() => navigate('/schedule')}
        />
      ) : (
        <OwnerCoachDashboard
          gymId={currentGym.id}
          onCreateClass={() => navigate('/manage')}
        />
      )}
    </div>
  )
}
