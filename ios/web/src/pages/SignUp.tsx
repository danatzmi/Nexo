import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Loader2, Lock, Mail, User } from 'lucide-react'
import AuthLayout from '../components/AuthLayout'
import AuthInput from '../components/AuthInput'
import { useAuth } from '../context/useAuth'

export default function SignUp() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const isValid =
    firstName.trim().length > 0 && email.includes('@') && password.length >= 6

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!isValid) {
      setError('Please fill all fields correctly')
      return
    }
    setError(null)
    setIsLoading(true)
    try {
      await register(email, password, firstName.trim(), lastName.trim())
      navigate('/')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create account')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <AuthLayout>
      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        <AuthInput
          icon={User}
          placeholder="First Name"
          autoComplete="given-name"
          value={firstName}
          onChange={(event) => setFirstName(event.target.value)}
        />
        <AuthInput
          icon={User}
          placeholder="Last Name"
          autoComplete="family-name"
          value={lastName}
          onChange={(event) => setLastName(event.target.value)}
        />
        <AuthInput
          icon={Mail}
          type="email"
          placeholder="Email"
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />
        <AuthInput
          icon={Lock}
          type="password"
          placeholder="Password"
          autoComplete="new-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />

        {error && <p className="text-sm text-red-400">{error}</p>}

        <button
          type="submit"
          disabled={!isValid || isLoading}
          className="mt-2 flex h-12 items-center justify-center rounded-xl bg-blue-600 text-sm font-semibold text-white transition-colors hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {isLoading ? <Loader2 size={18} className="animate-spin" /> : 'Create Account'}
        </button>

        <p className="mt-2 text-center text-sm text-neutral-400">
          Already have an account?{' '}
          <Link to="/login" className="font-semibold text-blue-500 hover:text-blue-400">
            Sign In
          </Link>
        </p>
      </form>
    </AuthLayout>
  )
}
