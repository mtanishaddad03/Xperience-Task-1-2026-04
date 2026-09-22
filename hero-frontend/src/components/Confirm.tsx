import { useState, type ReactNode } from 'react'

/**
 * A two-step action for anything irreversible or felt by every guest (Step 11): the first click only names the
 * consequence. Nothing is sent until the second click.
 */
export function Confirm({
  label,
  consequence,
  confirmLabel,
  onConfirm,
  disabled,
  tone = 'normal',
}: {
  label: string
  consequence: ReactNode
  confirmLabel: string
  onConfirm: () => void
  disabled?: boolean
  tone?: 'normal' | 'grave'
}) {
  const [asking, setAsking] = useState(false)

  if (!asking) {
    return (
      <button
        type="button"
        className={tone === 'grave' ? 'button-grave' : 'button-secondary'}
        disabled={disabled}
        onClick={() => setAsking(true)}
      >
        {label}
      </button>
    )
  }

  return (
    <div className="rounded-lg border border-stone-300 bg-stone-50 p-3">
      <p className="text-sm text-stone-700">{consequence}</p>
      <div className="mt-3 flex gap-2">
        <button
          type="button"
          className={tone === 'grave' ? 'button-grave' : 'button-primary'}
          onClick={() => {
            setAsking(false)
            onConfirm()
          }}
        >
          {confirmLabel}
        </button>
        <button type="button" className="button-quiet" onClick={() => setAsking(false)}>
          Keep as it is
        </button>
      </div>
    </div>
  )
}
