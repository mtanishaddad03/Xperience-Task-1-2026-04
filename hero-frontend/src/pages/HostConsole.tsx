import { useCallback, useEffect, useState } from 'react'
import { api, ApiError, LinkInvalidError, type HostView } from '../api'
import { formatStartTime, lockedMessage } from '../format'
import { Confirm } from '../components/Confirm'
import { LinkInvalidPage } from './LinkInvalidPage'

const STATE_LABELS: Record<string, string> = {
  CONFIRMED: 'Has a place',
  WAITLISTED: 'Waiting list',
  DECLINED: 'Cannot come',
  MAYBE: 'Maybe',
  PENDING: 'No reply yet',
}

const INVITATION_LABELS: Record<string, string> = {
  QUEUED: 'Waiting to be sent',
  SENDING: 'Sending',
  SENT: 'Sent',
  FAILED: 'Failed',
  SKIPPED: 'Not sent',
}

/** The host surface: the live picture, and the actions only the holder of the management link may take. */
export function HostConsole({ token }: { token: string }) {
  const [view, setView] = useState<HostView | null>(null)
  const [invalid, setInvalid] = useState(false)
  const [problem, setProblem] = useState<string | null>(null)
  const [invalidAddresses, setInvalidAddresses] = useState<string[]>([])
  const [notice, setNotice] = useState<string | null>(null)
  const [addresses, setAddresses] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      setView(await api.hostView(token))
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

  const act = async (action: () => Promise<unknown>, done?: string) => {
    setBusy(true)
    setProblem(null)
    setInvalidAddresses([])
    setNotice(null)
    try {
      await action()
      if (done) {
        setNotice(done)
      }
    }
    catch (error) {
      if (error instanceof LinkInvalidError) {
        setInvalid(true)
        return
      }
      if (error instanceof ApiError) {
        setProblem(error.body.message ?? 'That did not work.')
        setInvalidAddresses(error.body.invalid ?? [])
      }
      else {
        setProblem('That did not work.')
      }
    }
    finally {
      setBusy(false)
      await load()
    }
  }

  if (invalid) {
    return <LinkInvalidPage />
  }
  if (!view) {
    return <main className="mx-auto max-w-3xl p-8 text-stone-600">Loading…</main>
  }

  const { event, counts, guests, outbox } = view

  const invite = () => {
    const emails = addresses
      .split(/[\n,;]+/)
      .map((line) => line.trim())
      .filter(Boolean)
    if (emails.length === 0) {
      setProblem('Add at least one email address.')
      return
    }
    void act(async () => {
      const result = await api.invite(token, emails)
      setAddresses('')
      setNotice(
        `Invited ${result.invited}. ${result.alreadyInvited} of the addresses had already been invited.`,
      )
    })
  }

  return (
    <main className="mx-auto max-w-3xl p-6">
      <header>
        <h1 className="text-2xl font-semibold text-stone-900">{event.title}</h1>
        <p className="mt-2 whitespace-pre-line text-stone-700">{event.description}</p>
        <p className="mt-2 text-stone-700">
          {formatStartTime(event.startTime)} · {event.location}
        </p>
        {!event.repliesOpen && (
          <p className="mt-3 rounded-md bg-stone-100 p-3 text-stone-800">{lockedMessage(event.lockedReason)}</p>
        )}
      </header>

      {!event.hostVerified && (
        <section className="mt-6 rounded-lg border border-amber-300 bg-amber-50 p-4">
          <h2 className="font-medium text-stone-900">Confirm your email address</h2>
          <p className="mt-1 text-sm text-stone-700">
            You cannot invite anyone until you confirm that this address is yours.
          </p>
          <button
            type="button"
            className="button-primary mt-3"
            disabled={busy}
            onClick={() => void act(() => api.verifyHost(token), 'Your email address is confirmed.')}
          >
            Yes, this is my email address
          </button>
        </section>
      )}

      <section className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-3">
        <Count label="Coming" value={counts.confirmed} testId="count-confirmed" />
        <Count label="Waiting list" value={counts.waitlisted} testId="count-waitlisted" />
        <Count label="Maybe" value={counts.maybe} testId="count-maybe" />
        <Count label="Cannot come" value={counts.declined} testId="count-declined" />
        <Count label="No reply yet" value={counts.pending} testId="count-pending" />
        {counts.placesRemaining !== null && (
          <Count label="Places left" value={counts.placesRemaining} testId="count-places" />
        )}
      </section>

      <section className="mt-8">
        <h2 className="font-medium text-stone-900">Invite guests</h2>
        <textarea
          className="mt-2 w-full rounded-md border border-stone-300 p-2"
          rows={3}
          value={addresses}
          aria-label="Email addresses"
          placeholder="One address per line"
          onChange={(e) => setAddresses(e.target.value)}
        />
        <button type="button" className="button-primary mt-2" disabled={busy} onClick={invite}>
          Send invitations
        </button>
        {invalidAddresses.length > 0 && (
          <div className="mt-3 rounded-md bg-red-50 p-3 text-sm text-red-800">
            <p>Nothing was sent. These addresses cannot be used:</p>
            <ul className="mt-1 list-inside list-disc">
              {invalidAddresses.map((address) => (
                <li key={address}>{address}</li>
              ))}
            </ul>
          </div>
        )}
      </section>

      <section className="mt-8">
        <h2 className="font-medium text-stone-900">Guests</h2>
        <table className="mt-2 w-full text-left text-sm">
          <thead className="text-stone-500">
            <tr>
              <th className="py-1">Email</th>
              <th className="py-1">Reply</th>
              <th className="py-1">Invitation</th>
              <th className="py-1"></th>
            </tr>
          </thead>
          <tbody>
            {guests.map((guest) => (
              <tr key={guest.email} className="border-t border-stone-200 align-top">
                <td className="py-2">{guest.email}</td>
                <td className="py-2">
                  {STATE_LABELS[guest.state]}
                  {guest.waitlistPosition !== null && ` (${guest.waitlistPosition})`}
                </td>
                <td className="py-2">{guest.invitation ? INVITATION_LABELS[guest.invitation] : ''}</td>
                <td className="py-2">
                  {(guest.invitation === 'SENT' || guest.invitation === 'FAILED') && (
                    <Confirm
                      label="Resend"
                      consequence={
                        <>
                          This sends a new link to {guest.email}. The guest&rsquo;s current link will stop working.
                        </>
                      }
                      confirmLabel="Yes, send a new link"
                      disabled={busy}
                      onConfirm={() => void act(() => api.resend(token, guest.email), 'A new invitation was queued.')}
                    />
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="mt-8 rounded-lg border border-stone-200 p-4">
        <h2 className="font-medium text-stone-900">Messages</h2>
        <p className="mt-1 text-sm text-stone-700">
          {outbox.queued} waiting to be sent · {outbox.sending} sending · {outbox.failed} failed
        </p>
        {outbox.paused && (
          <p className="mt-2 text-sm text-amber-800">
            Sending is paused. Nothing is lost; messages will go out when it starts again.
          </p>
        )}
      </section>

      <section className="mt-8 flex flex-wrap items-start gap-3">
        <button type="button" className="button-quiet" disabled={busy} onClick={() => void load()}>
          Refresh
        </button>
        {event.status === 'OPEN' && (
          <Confirm
            label="Close the event"
            consequence="Nobody will be able to change their reply after this, including saying they cannot come. The event still goes ahead."
            confirmLabel="Yes, close it"
            disabled={busy}
            onConfirm={() => void act(() => api.close(token), 'The event is closed to further replies.')}
          />
        )}
        {event.status !== 'CANCELLED' && (
          <Confirm
            label="Cancel the event"
            tone="grave"
            consequence="This cannot be undone. Every guest who was sent an invitation will be told the event is cancelled, and no one will be able to reply."
            confirmLabel="Yes, cancel it"
            disabled={busy}
            onConfirm={() => void act(() => api.cancel(token), 'The event is cancelled.')}
          />
        )}
      </section>

      {notice && <p className="mt-4 text-sm text-stone-700">{notice}</p>}
      {problem && <p className="mt-4 text-sm text-red-700">{problem}</p>}
    </main>
  )
}

function Count({ label, value, testId }: { label: string; value: number; testId: string }) {
  return (
    <div className="rounded-lg border border-stone-200 p-3">
      <div className="text-2xl font-semibold text-stone-900" data-testid={testId}>
        {value}
      </div>
      <div className="text-sm text-stone-600">{label}</div>
    </div>
  )
}
