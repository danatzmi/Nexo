interface ConfirmAction {
  label: string
  onClick: () => void
  variant?: 'destructive' | 'default'
}

interface ConfirmDialogProps {
  title: string
  message: string
  actions: ConfirmAction[]
  onCancel: () => void
}

// Generic centered confirmation card, reused across Manage's delete/remove
// flows (classes — including the 3-way "this class only" / "this and
// future" / cancel choice for a series, members, team, plans). Takes an
// arbitrary list of actions rather than assuming exactly two buttons, since
// the class-series case needs three. A normal dimmed backdrop (`bg-black/50`)
// — these aren't the booking-flow's zero-dimming popups from Milestone 3,
// which are a different, more specific requirement scoped to booking only.
export default function ConfirmDialog({
  title,
  message,
  actions,
  onCancel,
}: ConfirmDialogProps) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
      onClick={onCancel}
    >
      <div
        className="mx-6 flex w-full max-w-xs flex-col gap-5 rounded-3xl border border-white/10 bg-neutral-900 p-6 text-center shadow-2xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div>
          <h3 className="text-lg font-semibold text-white">{title}</h3>
          <p className="mt-1 text-sm text-neutral-400">{message}</p>
        </div>
        <div className="flex flex-col gap-2">
          {actions.map((action) => (
            <button
              key={action.label}
              type="button"
              onClick={action.onClick}
              className={`rounded-full py-2.5 text-sm font-semibold transition-colors ${
                action.variant === 'destructive'
                  ? 'bg-red-600 text-white hover:bg-red-500'
                  : 'bg-white/5 text-neutral-200 hover:bg-white/10'
              }`}
            >
              {action.label}
            </button>
          ))}
          <button
            type="button"
            onClick={onCancel}
            className="rounded-full bg-white/5 py-2.5 text-sm font-semibold text-neutral-300 transition-colors hover:bg-white/10"
          >
            Cancel
          </button>
        </div>
      </div>
    </div>
  )
}
