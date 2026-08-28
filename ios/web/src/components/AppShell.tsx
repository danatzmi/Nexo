import { NavLink, Outlet } from 'react-router-dom'
import { Calendar, Home, LogOut, User, Wrench, type LucideIcon } from 'lucide-react'
import { useAuth } from '../context/useAuth'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  end?: boolean
}

// The authenticated shell. Mobile (<md): a persistent frosted-glass bottom
// tab bar. Desktop (md+): a left sidebar with icon+label nav. Both render
// from the same `navItems` list so the tab set never drifts between the two
// layouts. Sign Out lives in the sidebar footer on desktop and on the
// Profile page on mobile (the sidebar is hidden there) — the bottom tab bar
// itself stays to exactly the 4 tabs.
export default function AppShell() {
  const { canManageClasses, logout } = useAuth()

  const navItems: NavItem[] = [
    { to: '/', label: 'Home', icon: Home, end: true },
    { to: '/schedule', label: 'Schedule', icon: Calendar },
    ...(canManageClasses
      ? [{ to: '/manage', label: 'Manage', icon: Wrench }]
      : []),
    { to: '/profile', label: 'Profile', icon: User },
  ]

  return (
    <div className="flex min-h-screen bg-[#121212] text-white">
      <aside className="hidden w-60 shrink-0 flex-col border-r border-white/5 px-4 py-6 md:flex">
        <span className="mb-8 px-3 text-lg font-semibold tracking-tight">
          Nexo
        </span>
        <nav className="flex flex-1 flex-col gap-1">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-blue-600 text-white'
                    : 'text-neutral-400 hover:bg-white/5 hover:text-white'
                }`
              }
            >
              <item.icon size={18} />
              {item.label}
            </NavLink>
          ))}
        </nav>
        <button
          type="button"
          onClick={() => void logout()}
          className="mt-4 flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-neutral-500 transition-colors hover:bg-white/5 hover:text-white"
        >
          <LogOut size={18} />
          Sign Out
        </button>
      </aside>

      <div className="flex flex-1 flex-col">
        <main className="flex flex-1 flex-col pb-20 md:pb-0">
          <Outlet />
        </main>

        <nav className="fixed inset-x-0 bottom-0 z-30 flex items-center justify-around border-t border-white/5 bg-[#121212]/80 py-2 backdrop-blur-md md:hidden">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `flex flex-col items-center gap-1 px-4 py-1.5 text-xs font-medium transition-colors ${
                  isActive ? 'text-blue-400' : 'text-neutral-500'
                }`
              }
            >
              <item.icon size={22} />
              {item.label}
            </NavLink>
          ))}
        </nav>
      </div>
    </div>
  )
}
