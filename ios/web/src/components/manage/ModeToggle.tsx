interface ModeToggleProps<T extends string> {
  value: T
  options: { value: T; label: string }[]
  onChange: (value: T) => void
}

// Shared Search/Register segmented toggle for the two "add existing user or
// register a new one" sheets (AddMemberSheet, AddTeamMemberSheet).
export default function ModeToggle<T extends string>({
  value,
  options,
  onChange,
}: ModeToggleProps<T>) {
  return (
    <div className="flex gap-2 rounded-full bg-white/5 p-1">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          onClick={() => onChange(option.value)}
          className={`flex-1 rounded-full px-3 py-2 text-xs font-semibold transition-colors ${
            value === option.value
              ? 'bg-blue-600 text-white'
              : 'text-neutral-400 hover:text-white'
          }`}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
