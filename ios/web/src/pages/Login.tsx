import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Loader2, Lock, Mail } from 'lucide-react'
import AuthLayout from '../components/AuthLayout'
import AuthInput from '../components/AuthInput'
import { useAuth } from '../context/useAuth'

export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const isValid = email.includes('@') && password.length >= 6

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!isValid) {
      setError('Please enter a valid email and a 6+ char password')
      return
    }
    setError(null)
    setIsLoading(true)
    try {
      await login(email, password)
      navigate('/')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to sign in')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <AuthLayout>
      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
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
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />

        <Link
          to="/forgot-password"
          className="self-end text-sm font-medium text-blue-500 hover:text-blue-400"
        >
          Forgot Password?
        </Link>

        {error && <p className="text-sm text-red-400">{error}</p>}

        <button
          type="submit"
          disabled={!isValid || isLoading}
          className="mt-2 flex h-12 items-center justify-center rounded-xl bg-blue-600 text-sm font-semibold text-white transition-colors hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {isLoading ? <Loader2 size={18} className="animate-spin" /> : 'Sign In'}
        </button>

        <p className="mt-2 text-center text-sm text-neutral-400">
          New to Nexo?{' '}
          <Link to="/signup" className="font-semibold text-blue-500 hover:text-blue-400">
            Create an account
          </Link>
        </p>
      </form>
    </AuthLayout>
  )
}
