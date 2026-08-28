import { LogOut } from 'lucide-react'
import { useAuth } from '../context/useAuth'

export default function Profile() {
  const { logout } = useAuth()

  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-4 px-6 text-center">
      <h1 className="text-2xl font-semibold text-white">Profile</h1>
      <p className="text-sm text-neutral-400">
        Profile & bookings wiring goes here.
      </p>
      {/* AppShell's sidebar already has a Sign Out control on desktop, but
          the sidebar is hidden on mobile — this is the only sign-out path
          there, so it stays regardless of screen size. */}
      <button
        type="button"
        onClick={() => void logout()}
        className="mt-4 flex items-center gap-2 rounded-full border border-white/10 px-5 py-2.5 text-sm font-medium text-red-400 transition-colors hover:bg-white/5"
      >
        <LogOut size={16} />
        Sign Out
      </button>
    </div>
  )
}
