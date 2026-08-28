import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { fetchAllUsers } from '../../lib/users'
import { addMember } from '../../lib/members'
import { addExistingUserToGym } from '../../lib/gym'
import ModeToggle from './ModeToggle'
import type { PlatformUser } from '../../types'

type AddMode = 'search' | 'register'

interface AddMemberSheetProps {
  gymId: string
  existingMemberIds: Set<string>
  onClose: () => void
  onAdded: () => void
}

export default function AddMemberSheet({
  gymId,
  existingMemberIds,
  onClose,
  onAdded,
}: AddMemberSheetProps) {
  const [mode, setMode] = useState<AddMode>('search')

  const [allUsers, setAllUsers] = useState<PlatformUser[]>([])
  const [searchQuery, setSearchQuery] = useState('')
  const [addingUserId, setAddingUserId] = useState<string | null>(null)

  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [isRegistering, setIsRegistering] = useState(false)

  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchAllUsers()
      .then(setAllUsers)
      .catch((err) => setError(err instanceof Error ? err.message : 'Failed to load users'))
  }, [])

  const matchingUsers = allUsers.filter((user) => {
    if (existingMemberIds.has(user.id)) return false
    if (!searchQuery) return false
    const fullName = `${user.firstName} ${user.lastName}`.toLowerCase()
    const query = searchQuery.toLowerCase()
    return fullName.includes(query) || user.email.toLowerCase().includes(query)
  })

  const handleAddExisting = async (user: PlatformUser) => {
    setAddingUserId(user.id)
    setError(null)
    try {
      await addExistingUserToGym(gymId, user.id, 'member')
      onAdded()
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to add member')
      setAddingUserId(null)
    }
  }

  const isRegisterValid =
    firstName.trim().length > 0 && email.includes('@') && password.length >= 6

  const handleRegister = async () => {
    if (!isRegisterValid) return
    setIsRegistering(true)
    setError(null)
    try {
      await addMember(gymId, firstName.trim(), lastName.trim(), email.trim(), password)
      onAdded()
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to register member')
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
          <h2 className="text-xl font-bold text-white">Add Member</h2>
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
              onChange={(event) => setSearchQuery(event.target.value)}
              placeholder="Search by name or email"
              className="w-full rounded-xl border border-white/10 bg-neutral-800 px-3 py-2.5 text-sm text-white placeholder-neutral-500"
            />
            {searchQuery && matchingUsers.length === 0 && (
              <p className="text-sm text-neutral-500">No matching users found.</p>
            )}
            <ul className="flex flex-col gap-2">
              {matchingUsers.map((user) => (
                <li
                  key={user.id}
                  className="flex items-center gap-3 rounded-xl border border-white/10 p-3"
                >
                  <div className="flex flex-1 flex-col overflow-hidden">
                    <span className="truncate text-sm font-medium text-white">
                      {user.firstName} {user.lastName}
                    </span>
                    <span className="truncate text-xs text-neutral-500">{user.email}</span>
                  </div>
                  <button
                    type="button"
                    onClick={() => void handleAddExisting(user)}
                    disabled={addingUserId === user.id}
                    className="shrink-0 rounded-full bg-blue-600 px-3 py-1.5 text-xs font-semibold text-white transition-colors hover:bg-blue-500 disabled:opacity-50"
                  >
                    Add Member
                  </button>
                </li>
              ))}
            </ul>
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
            <button
              type="button"
              onClick={() => void handleRegister()}
              disabled={!isRegisterValid || isRegistering}
              className="rounded-full bg-blue-600 py-3 text-sm font-semibold text-white transition-colors hover:bg-blue-500 disabled:opacity-50"
            >
              {isRegistering ? 'Adding…' : 'Add Member'}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
