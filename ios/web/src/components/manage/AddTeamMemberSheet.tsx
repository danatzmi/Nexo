import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { useAuth } from '../../context/useAuth'
import { fetchAllUsers } from '../../lib/users'
import { addTeamMember } from '../../lib/team'
import { addExistingUserToGym } from '../../lib/gym'
import ModeToggle from './ModeToggle'
import type { PlatformUser, UserRole } from '../../types'

type AddMode = 'search' | 'register'

interface AddTeamMemberSheetProps {
  gymId: string
  existingTeamIds: Set<string>
  onClose: () => void
  onAdded: () => void
}

function RoleButton({
  label,
  active,
  onClick,
}: {
  label: string
  active: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex-1 rounded-full px-3 py-2 text-xs font-semibold transition-colors ${
        active ? 'bg-blue-600 text-white' : 'bg-white/5 text-neutral-300 hover:bg-white/10'
      }`}
    >
      {label}
    </button>
  )
}

// Dual-mode (search existing platform users / register a brand-new account)
// sheet, mirroring AddMemberSheet, plus a role selector — gated to owners
// (and platform admins) per iOS's `canSelectRole`, since only an owner
// should be able to invite or promote another owner. Non-owners inviting
// team members are locked to 'coach'.
export default function AddTeamMemberSheet({
  gymId,
  existingTeamIds,
  onClose,
  onAdded,
}: AddTeamMemberSheetProps) {
  const { gymRole, isAdmin } = useAuth()
  const canSelectRole = gymRole === 'owner' || isAdmin

  const [mode, setMode] = useState<AddMode>('search')

  const [allUsers, setAllUsers] = useState<PlatformUser[]>([])
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedUser, setSelectedUser] = useState<PlatformUser | null>(null)
  const [selectedRole, setSelectedRole] = useState<UserRole>('coach')
  const [isAddingExisting, setIsAddingExisting] = useState(false)

  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [registerRole, setRegisterRole] = useState<UserRole>('coach')
  const [isRegistering, setIsRegistering] = useState(false)

  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchAllUsers()
      .then(setAllUsers)
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load users'))
  }, [])

  const matchingUsers = allUsers.filter((user) => {
    if (existingTeamIds.has(user.id)) return false
    if (!searchQuery) return false
    const fullName = `${user.firstName} ${user.lastName}`.toLowerCase()
    const query = searchQuery.toLowerCase()
    return fullName.includes(query) || user.email.toLowerCase().includes(query)
  })

  const handleAddExisting = async () => {
    if (!selectedUser) return
    setIsAddingExisting(true)
    setError(null)
    try {
      await addExistingUserToGym(gymId, selectedUser.id, canSelectRole ? selectedRole : 'coach')
      onAdded()
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to add team member')
      setIsAddingExisting(false)
    }
  }

  const isRegisterValid =
    firstName.trim().length > 0 && email.includes('@') && password.length >= 6

  const handleRegister = async () => {
    if (!isRegisterValid) return
    setIsRegistering(true)
    setError(null)
    try {
      await addTeamMember(
        gymId,
        firstName.trim(),
        lastName.trim(),
        email.trim(),
        password,
        canSelectRole ? registerRole : 'coach',
      )
      onAdded()
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to register team member')
      setIsRegistering(false)
    }
  }

  return (
    <div
      className="fixed inset-0 z-40 flex items-end justify-center bg-black/50 md:items-center"
      onClick={onClose}
    >
      <div
        className="flex max-h-[85vh] w-full flex-col gap-4 overflow-y-auto rounded-t-3xl bg-neutral-900 p-6 md:max-w-md md:rounded-3xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-bold text-white">Add Team Member</h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-1.5 text-neutral-500 transition-colors hover:bg-white/5 hover:text-white"
          >
            <X size={18} />
          </button>
        </div>

        <ModeToggle
          value={mode}
          onChange={setMode}
          options={[
            { value: 'search', label: 'Search Platform' },
            { value: 'register', label: 'Register New' },
          ]}
        />

        {error && <p className="text-sm text-red-400">{error}</p>}

        {mode === 'search' ? (
          <div className="flex flex-col gap-3">
            <input
              type="text"
              value={searchQuery}
              onChange={(event) => {
                setSearchQuery(event.target.value)
                setSelectedUser(null)
              }}
              placeholder="Search by name or email"
              className="w-full rounded-xl border border-white/10 bg-neutral-800 px-3 py-2.5 text-sm text-white placeholder-neutral-500"
            />
            {searchQuery && matchingUsers.length === 0 && (
              <p className="text-sm text-neutral-500">No matching users found.</p>
            )}
            <ul className="flex flex-col gap-2">
              {matchingUsers.map((user) => (
                <li key={user.id}>
                  <button
                    type="button"
                    onClick={() => {
                      setSelectedUser(user)
                      setSelectedRole('coach')
                    }}
                    className={`flex w-full items-center justify-between rounded-xl border p-3 text-left transition-colors ${
                      selectedUser?.id === user.id
                        ? 'border-blue-500 bg-blue-500/10'
                        : 'border-white/10 hover:border-white/20'
                    }`}
                  >
                    <div className="flex flex-col overflow-hidden">
                      <span className="truncate text-sm font-medium text-white">
                        {user.firstName} {user.lastName}
                      </span>
                      <span className="truncate text-xs text-neutral-500">{user.email}</span>
                    </div>
                  </button>
                </li>
              ))}
            </ul>

            {selectedUser && (
              <div className="flex flex-col gap-3 rounded-xl border border-white/10 p-3">
                {canSelectRole && (
                  <div className="flex gap-2">
                    <RoleButton
                      label="Coach"
                      active={selectedRole === 'coach'}
                      onClick={() => setSelectedRole('coach')}
                    />
                    <RoleButton
                      label="Owner"
                      active={selectedRole === 'owner'}
                      onClick={() => setSelectedRole('owner')}
                    />
                  </div>
                )}
                <button
                  type="button"
                  onClick={() => void handleAddExisting()}
                  disabled={isAddingExisting}
                  className="rounded-full bg-blue-600 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-blue-500 disabled:opacity-50"
                >
                  Add {selectedUser.firstName} to Team
                </button>
              </div>
            )}
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            <input
              type="text"
              value={firstName}
              onChange={(event) => setFirstName(event.target.value)}
              placeholder="First Name"
              className="w-full rounded-xl border border-white/10 bg-neutral-800 px-3 py-2.5 text-sm text-white placeholder-neutral-500"
            />
            <input
              type="text"
              value={lastName}
              onChange={(event) => setLastName(event.target.value)}
              placeholder="Last Name"
              className="w-full rounded-xl border border-white/10 bg-neutral-800 px-3 py-2.5 text-sm text-white placeholder-neutral-500"
            />
            <input
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="Email"
              className="w-full rounded-xl border border-white/10 bg-neutral-800 px-3 py-2.5 text-sm text-white placeholder-neutral-500"
            />
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="Password"
              className="w-full rounded-xl border border-white/10 bg-neutral-800 px-3 py-2.5 text-sm text-white placeholder-neutral-500"
            />
            {canSelectRole && (
              <div className="flex gap-2">
                <RoleButton
                  label="Coach"
                  active={registerRole === 'coach'}
                  onClick={() => setRegisterRole('coach')}
                />
                <RoleButton
                  label="Owner"
                  active={registerRole === 'owner'}
                  onClick={() => setRegisterRole('owner')}
                />
              </div>
            )}
            <button
              type="button"
              onClick={() => void handleRegister()}
              disabled={!isRegisterValid || isRegistering}
              className="rounded-full bg-blue-600 py-3 text-sm font-semibold text-white transition-colors hover:bg-blue-500 disabled:opacity-50"
            >
              {isRegistering ? 'Adding…' : 'Add Team Member'}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
