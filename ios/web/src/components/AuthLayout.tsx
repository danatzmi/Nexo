import type { ReactNode } from 'react'

// Shared header (logo + title + tagline) for Login/SignUp/ForgotPassword —
// mirrors the VStack header in AuthView.swift, factored out so it isn't
// duplicated across the three onboarding pages.
export default function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col items-center bg-[#121212] px-6 py-16 text-white">
      <div className="flex w-full max-w-sm flex-col items-center gap-2 text-center">
        <img
          src="/nexo-icon.jpg"
          alt="Nexo"
          className="h-20 w-20 rounded-2xl object-cover"
        />
        <h1 className="mt-2 text-4xl font-bold tracking-tight">Nexo</h1>
        <p className="text-sm text-neutral-400">
          Your training community, unified.
        </p>
      </div>

      <div className="mt-10 w-full max-w-sm">{children}</div>
    </div>
  )
}
