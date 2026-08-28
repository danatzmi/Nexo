import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ArrowLeft, CheckCircle2, Loader2, Mail } from 'lucide-react'
import { sendPasswordResetEmail } from 'firebase/auth'
import { auth } from '../firebase'
import AuthLayout from '../components/AuthLayout'
import AuthInput from '../components/AuthInput'

export default function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [showToast, setShowToast] = useState(false)

  const isValid = email.includes('@')

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!isValid) return
    setError(null)
    setIsLoading(true)
    try {
      await sendPasswordResetEmail(auth, email)
      setShowToast(true)
      setTimeout(() => setShowToast(false), 3000)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to send reset email')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <AuthLayout>
      <Link
        to="/login"
        className="mb-6 inline-flex items-center gap-1 text-sm text-neutral-400 hover:text-white"
      >
        <ArrowLeft size={16} />
        Back to Sign In
      </Link>

      <div className="mb-6 text-center">
        <h2 className="text-xl font-semibold">Reset Password</h2>
        <p className="mt-1 text-sm text-neutral-400">
          Enter your email address and we&apos;ll send you a link to reset your
          password.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        <AuthInput
          icon={Mail}
          type="email"
          placeholder="Email"
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
        />

        {error && <p className="text-sm text-red-400">{error}</p>}

        <button
          type="submit"
          disabled={!isValid || isLoading}
          className="mt-2 flex h-12 items-center justify-center rounded-xl bg-blue-600 text-sm font-semibold text-white transition-colors hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {isLoading ? <Loader2 size={18} className="animate-spin" /> : 'Send Reset Link'}
        </button>
      </form>

      {showToast && (
        <div className="fixed left-1/2 top-6 z-50 flex -translate-x-1/2 items-center gap-2 rounded-full bg-neutral-900 px-4 py-2.5 text-sm font-medium text-white shadow-lg ring-1 ring-white/10">
          <CheckCircle2 size={18} className="text-green-500" />
          Reset email sent! Check your inbox.
        </div>
      )}
    </AuthLayout>
  )
}
