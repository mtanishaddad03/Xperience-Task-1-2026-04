import { useCallback, useEffect, useState } from 'react'
import { api, ApiError, LinkInvalidError, type GuestViewData, type ReplyState } from '../api'
import { formatStartTime, lockedMessage, stateInWords } from '../format'
import { LinkInvalidPage } from './LinkInvalidPage'

const CHOICES: { choice: 'YES' | 'NO' | 'MAYBE'; label: string; state: ReplyState }[] = [
  { choice: 'YES', label: 'Yes', state: 'CONFIRMED' },
  { choice: 'NO', label: 'No', state: 'DECLINED' },
  { choice: 'MAYBE', label: 'Maybe', state: 'MAYBE' },
]

/**
 * The guest surface. It shows the event and this guest's own standing — nothing about anyone else, no totals
 * and no queue position (Q6, INV-A5). Opening it changes nothing; only a button does.
 */
export function GuestPage({ token }: { token: string }) {
  const [view, setView] = useState<GuestViewData | null>(null)
  const [invalid, setInvalid] = useState(false)
  const [problem, setProblem] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      setView(await api.guestView(token))
    }
    catch (error) {
      if (error instanceof LinkInvalidError) {
        setInvalid(true)
      }
      else {
        setProblem('Something went wrong. Try again in a moment.')
      }
    }
  }, [token])

  useEffect(() => {
    void load()
  }, [load])

  if (invalid) {
    return <LinkInvalidPage />
  }
  if (!view) {
    return <main className="mx-auto max-w-lg p-8 text-stone-600">Loading…</main>
  }

  const reply = async (choice: 'YES' | 'NO' | 'MAYBE') => {
    setBusy(true)
    setProblem(null)
    try {
      await api.reply(token, choice)
      await load()
    }
    catch (error) {
      if (error instanceof LinkInvalidError) {
        setInvalid(true)
      }
      else if (error instanceof ApiError) {
        setProblem(error.body.message ?? 'Your reply could not be saved.')
        await load()
      }
      else {
        setProblem('Your reply could not be saved.')
      }
    }
    finally {
      setBusy(false)
    }
  }

  const { event } = view
  return (
    <main className="mx-auto max-w-lg p-6">
      <h1 className="text-2xl font-semibold text-stone-900">{event.title}</h1>
      <p className="mt-2 whitespace-pre-line text-stone-700">{event.description}</p>
      <dl className="mt-4 space-y-1 text-stone-700">
        <div className="flex gap-2">
          <dt className="font-medium">When</dt>
          <dd>{formatStartTime(event.startTime)}</dd>
        </div>
        <div className="flex gap-2">
          <dt className="font-medium">Where</dt>
          <dd>{event.location}</dd>
        </div>
      </dl>

      <section className="mt-6 rounded-lg border border-stone-200 p-4">
        <p className="text-lg text-stone-900">{stateInWords(view.reply.state)}</p>

        {event.repliesOpen ? (
          <>
            <p className="mt-4 text-sm text-stone-600">Can you come?</p>
            <div className="mt-2 flex gap-2">
              {CHOICES.map(({ choice, label, state }) => (
                <button
                  key={choice}
                  type="button"
                  className={view.reply.state === state ? 'button-primary' : 'button-secondary'}
                  disabled={busy}
                  onClick={() => void reply(choice)}
                >
                  {label}
                </button>
              ))}
            </div>
            <p className="mt-3 text-sm text-stone-500">You can change your answer until the event starts.</p>
          </>
        ) : (
          <p className="mt-3 text-stone-700">{lockedMessage(event.lockedReason)}</p>
        )}

        {problem && <p className="mt-3 text-sm text-red-700">{problem}</p>}
      </section>
    </main>
  )
}
