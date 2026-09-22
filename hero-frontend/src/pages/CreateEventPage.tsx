import { useState } from 'react'
import { api, ApiError } from '../api'

/**
 * The open path (U1): the only request that carries no link. The host enters the start time in their own
 * time zone and the browser converts it to an absolute instant (D16). Nothing is returned but a message —
 * the management link travels only by email (D9).
 */
export function CreateEventPage() {
  const [form, setForm] = useState({
    title: '',
    description: '',
    location: '',
    startTime: '',
    capacity: '',
    hostEmail: '',
  })
  const [fields, setFields] = useState<Record<string, string>>({})
  const [problem, setProblem] = useState<string | null>(null)
  const [sent, setSent] = useState(false)
  const [busy, setBusy] = useState(false)

  const set = (name: keyof typeof form) => (event: { target: { value: string } }) =>
    setForm({ ...form, [name]: event.target.value })

  const submit = async () => {
    setBusy(true)
    setProblem(null)
    setFields({})
    try {
      await api.createEvent({
        title: form.title,
        description: form.description,
        location: form.location,
        // A local entry such as 2030-10-03T16:00 becomes the instant it means where the host is.
        startTime: form.startTime ? new Date(form.startTime).toISOString() : '',
        capacity: form.capacity.trim() === '' ? null : Number(form.capacity),
        hostEmail: form.hostEmail,
      })
      setSent(true)
    }
    catch (error) {
      if (error instanceof ApiError) {
        setFields(error.body.fields ?? {})
        setProblem(error.body.message ?? 'The event could not be created.')
      }
      else {
        setProblem('The event could not be created.')
      }
    }
    finally {
      setBusy(false)
    }
  }

  if (sent) {
    return (
      <main className="mx-auto max-w-md p-8">
        <h1 className="text-xl font-semibold text-stone-900">Check your email</h1>
        <p className="mt-3 text-stone-700">
          We have sent a link for managing your event to {form.hostEmail}. Open it to confirm your address and
          invite your guests. Keep it: it is the only way back to your event.
        </p>
      </main>
    )
  }

  return (
    <main className="mx-auto max-w-md p-6">
      <h1 className="text-2xl font-semibold text-stone-900">Create an event</h1>
      <div className="mt-6 space-y-4">
        <Field label="Title" name="title" value={form.title} onChange={set('title')} error={fields.title} />
        <Field
          label="Description"
          name="description"
          value={form.description}
          onChange={set('description')}
          error={fields.description}
          multiline
        />
        <Field
          label="Location"
          name="location"
          value={form.location}
          onChange={set('location')}
          error={fields.location}
        />
        <Field
          label="Start (your time zone)"
          name="startTime"
          type="datetime-local"
          value={form.startTime}
          onChange={set('startTime')}
          error={fields.startTime}
        />
        <Field
          label="Most guests (leave empty for no limit)"
          name="capacity"
          type="number"
          value={form.capacity}
          onChange={set('capacity')}
          error={fields.capacity}
        />
        <Field
          label="Your email address"
          name="hostEmail"
          type="email"
          value={form.hostEmail}
          onChange={set('hostEmail')}
          error={fields.hostEmail}
        />
      </div>
      <button type="button" className="button-primary mt-6" disabled={busy} onClick={() => void submit()}>
        Create the event
      </button>
      {problem && <p className="mt-4 text-sm text-red-700">{problem}</p>}
    </main>
  )
}

function Field({
  label,
  name,
  value,
  onChange,
  error,
  type = 'text',
  multiline,
}: {
  label: string
  name: string
  value: string
  onChange: (event: { target: { value: string } }) => void
  error?: string
  type?: string
  multiline?: boolean
}) {
  return (
    <div>
      <label className="block text-sm font-medium text-stone-800" htmlFor={name}>
        {label}
      </label>
      {multiline ? (
        <textarea
          id={name}
          className="mt-1 w-full rounded-md border border-stone-300 p-2"
          rows={3}
          value={value}
          onChange={onChange}
        />
      ) : (
        <input
          id={name}
          type={type}
          className="mt-1 w-full rounded-md border border-stone-300 p-2"
          value={value}
          onChange={onChange}
        />
      )}
      {error && <p className="mt-1 text-sm text-red-700">{error}</p>}
    </div>
  )
}
