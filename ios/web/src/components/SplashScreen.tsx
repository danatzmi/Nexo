import { useEffect, useState } from 'react'

// Shown while AuthContext resolves the initial auth state, in place of a
// blank screen — mirrors ContentView.swift's SplashScreen (fade + scale-up
// on appear, dark #121212 background, logo + title + quiet spinner).
export default function SplashScreen() {
  const [isAnimating, setIsAnimating] = useState(false)

  useEffect(() => {
    const id = requestAnimationFrame(() => setIsAnimating(true))
    return () => cancelAnimationFrame(id)
  }, [])

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#121212]">
      <div
        className={`flex flex-col items-center gap-4 transition-all duration-700 ease-out ${
          isAnimating ? 'scale-100 opacity-100' : 'scale-90 opacity-0'
        }`}
      >
        <img
          src="/nexo-icon.jpg"
          alt="Nexo"
          className="h-24 w-24 rounded-2xl object-cover"
        />
        <h1 className="text-3xl font-bold text-white">Nexo</h1>
        <div className="h-5 w-5 animate-spin rounded-full border-2 border-white/30 border-t-white" />
      </div>
    </div>
  )
}
