import type { InputHTMLAttributes } from 'react'
import type { LucideIcon } from 'lucide-react'

interface AuthInputProps extends InputHTMLAttributes<HTMLInputElement> {
  icon: LucideIcon
}

// A rounded card input with a leading Lucide icon, no separate label — the
// web counterpart to AuthInputField.swift's minimalist auth aesthetic.
export default function AuthInput({ icon: Icon, ...props }: AuthInputProps) {
  return (
    <div className="flex items-center gap-3 rounded-xl bg-neutral-800/60 px-4 py-3.5">
      <Icon size={18} className="shrink-0 text-neutral-400" />
      <input
        {...props}
        className="w-full bg-transparent text-sm text-white placeholder-neutral-500 outline-none"
      />
    </div>
  )
}
