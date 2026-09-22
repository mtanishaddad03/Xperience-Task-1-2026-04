import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import App from '../App'
import { PATHS } from '../api'
import { guestViewFixture, hostViewFixture, stubFetch } from './fetchStub'

const HOST_TOKEN = 'hostToken_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
const GUEST_TOKEN = 'guestToken_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'

function renderAt(pathname: string) {
  window.history.pushState({}, '', pathname)
  return render(<App />)
}

const linkInvalid = { status: 404, body: { error: 'LINK_INVALID', message: 'This link is not valid.' } }

describe('the guest page', () => {
  it('reads and changes nothing when it is opened', async () => {
    const fetchStub = stubFetch(() => ({ body: guestViewFixture }))

    renderAt(`/i/${GUEST_TOKEN}`)
    await screen.findByText('Party')

    expect(fetchStub.calls).toHaveLength(1)
    expect(fetchStub.calls[0].method).toBe('GET')
    expect(fetchStub.calls[0].url).toBe(PATHS.guestEvent)
    expect(fetchStub.mutating()).toHaveLength(0)
  })

  it('replies only when a button is pressed', async () => {
    const fetchStub = stubFetch((call) =>
      call.method === 'PUT' ? { body: { state: 'CONFIRMED' } } : { body: guestViewFixture },
    )
    renderAt(`/i/${GUEST_TOKEN}`)
    await screen.findByText('Party')
    expect(fetchStub.mutating()).toHaveLength(0)

    await userEvent.click(screen.getByRole('button', { name: 'Yes' }))

    await waitFor(() => expect(fetchStub.mutating()).toHaveLength(1))
    expect(fetchStub.mutating()[0].method).toBe('PUT')
    expect(fetchStub.mutating()[0].url).toBe(PATHS.guestReply)
    expect(fetchStub.mutating()[0].body).toEqual({ choice: 'YES' })
  })

  it('shows the guest their own standing and nothing about anyone else (Q6)', async () => {
    stubFetch(() => ({ body: { ...guestViewFixture, reply: { state: 'WAITLISTED' } } }))

    const { container } = renderAt(`/i/${GUEST_TOKEN}`)
    await screen.findByText('Party')

    expect(screen.getByText('You are on the waiting list.')).toBeInTheDocument()
    const shown = container.textContent ?? ''
    expect(shown).not.toMatch(/position/i)
    expect(shown).not.toMatch(/confirmed:|waitlisted:|guests|coming|places/i)
    expect(shown).not.toContain('first@x.com')
    expect(shown).not.toContain('@')
  })

  it('offers no reply and gives the reason when replies are locked', async () => {
    stubFetch(() => ({
      body: {
        event: { ...guestViewFixture.event, status: 'CLOSED', repliesOpen: false, lockedReason: 'CLOSED' },
        reply: { state: 'CONFIRMED' },
      },
    }))

    renderAt(`/i/${GUEST_TOKEN}`)

    expect(await screen.findByText('The host has closed this event to further replies.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Yes' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'No' })).not.toBeInTheDocument()
  })
})

describe('the host console', () => {
  it('verifies the host only when the button is clicked (U8)', async () => {
    const unverified = {
      ...hostViewFixture,
      event: { ...hostViewFixture.event, hostVerified: false },
    }
    const fetchStub = stubFetch((call) => (call.method === 'GET' ? { body: unverified } : { body: {} }))

    renderAt(`/m/${HOST_TOKEN}`)
    await screen.findByText('Party')

    expect(fetchStub.calls.some((call) => call.url === PATHS.hostVerify)).toBe(false)
    expect(fetchStub.mutating()).toHaveLength(0)

    await userEvent.click(screen.getByRole('button', { name: /this is my email/i }))

    await waitFor(() => expect(fetchStub.calls.filter((c) => c.url === PATHS.hostVerify)).toHaveLength(1))
    expect(fetchStub.calls.filter((c) => c.url === PATHS.hostVerify)[0].method).toBe('POST')
  })

  it('closes only after the consequence is confirmed', async () => {
    const fetchStub = stubFetch((call) => (call.method === 'GET' ? { body: hostViewFixture } : { body: {} }))
    renderAt(`/m/${HOST_TOKEN}`)
    await screen.findByText('Party')

    await userEvent.click(screen.getByRole('button', { name: 'Close the event' }))

    expect(fetchStub.mutating()).toHaveLength(0)
    expect(screen.getByText(/nobody will be able to change their reply/i)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Yes, close it' }))

    await waitFor(() => expect(fetchStub.mutating()).toHaveLength(1))
    expect(fetchStub.mutating()[0].url).toBe(PATHS.hostClose)
  })

  it('cancels only after the consequence is confirmed', async () => {
    const fetchStub = stubFetch((call) => (call.method === 'GET' ? { body: hostViewFixture } : { body: {} }))
    renderAt(`/m/${HOST_TOKEN}`)
    await screen.findByText('Party')

    await userEvent.click(screen.getByRole('button', { name: 'Cancel the event' }))

    expect(fetchStub.mutating()).toHaveLength(0)
    expect(screen.getByText(/will be told the event is cancelled/i)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Yes, cancel it' }))

    await waitFor(() => expect(fetchStub.mutating()).toHaveLength(1))
    expect(fetchStub.mutating()[0].url).toBe(PATHS.hostCancel)
  })

  it('resends only after the consequence is confirmed (KD13)', async () => {
    const fetchStub = stubFetch((call) => (call.method === 'GET' ? { body: hostViewFixture } : { body: {} }))
    renderAt(`/m/${HOST_TOKEN}`)
    await screen.findByText('Party')

    const resendButtons = screen.getAllByRole('button', { name: 'Resend' })
    await userEvent.click(resendButtons[0])

    expect(fetchStub.mutating()).toHaveLength(0)
    expect(screen.getByText(/current link will stop working/i)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Yes, send a new link' }))

    await waitFor(() => expect(fetchStub.mutating()).toHaveLength(1))
    expect(fetchStub.mutating()[0].url).toBe(PATHS.hostResend)
    expect(fetchStub.mutating()[0].body).toEqual({ email: 'first@x.com' })
  })

  it('offers a resend only for a sent or failed invitation', async () => {
    stubFetch(() => ({ body: hostViewFixture }))
    renderAt(`/m/${HOST_TOKEN}`)
    await screen.findByText('Party')

    // first@x.com is SENT and second@x.com is FAILED; third@x.com is still QUEUED.
    expect(screen.getAllByRole('button', { name: 'Resend' })).toHaveLength(2)
  })

  it('shows the counts, the guests and the backlog', async () => {
    stubFetch(() => ({ body: { ...hostViewFixture, outbox: { queued: 3, sending: 0, failed: 1, paused: true } } }))
    const { container } = renderAt(`/m/${HOST_TOKEN}`)
    await screen.findByText('Party')

    expect(screen.getByText('first@x.com')).toBeInTheDocument()
    expect(screen.getByText('third@x.com')).toBeInTheDocument()
    expect(screen.getByTestId('count-confirmed')).toHaveTextContent('1')
    expect(screen.getByTestId('count-pending')).toHaveTextContent('1')
    expect(container.textContent).toMatch(/paused/i)
    expect(container.textContent).toMatch(/waiting to be sent/i)
  })

  it('shows the lock reason when replies are closed', async () => {
    stubFetch(() => ({
      body: {
        ...hostViewFixture,
        event: { ...hostViewFixture.event, status: 'CLOSED', repliesOpen: false, lockedReason: 'CLOSED' },
      },
    }))

    renderAt(`/m/${HOST_TOKEN}`)

    expect(await screen.findByText(/closed this event to further replies/i)).toBeInTheDocument()
  })
})

describe('what the pages never do', () => {
  it('renders host-written text as text, never as markup', async () => {
    const attack = '<img src=x onerror="alert(1)">'
    stubFetch(() => ({
      body: { ...guestViewFixture, event: { ...guestViewFixture.event, title: attack, description: attack } },
    }))

    const { container } = renderAt(`/i/${GUEST_TOKEN}`)
    // Both the title and the description carry it, and both show the characters themselves.
    expect(await screen.findAllByText(attack)).toHaveLength(2)

    expect(container.querySelector('img')).toBeNull()
    expect(container.innerHTML).toContain('&lt;img')
  })

  it('never shows the token', async () => {
    stubFetch(() => ({ body: hostViewFixture }))
    const { container } = renderAt(`/m/${HOST_TOKEN}`)
    await screen.findByText('Party')

    expect(container.innerHTML).not.toContain(HOST_TOKEN)
  })

  it('never shows the token in an error either', async () => {
    stubFetch(() => linkInvalid)
    const { container } = renderAt(`/m/${HOST_TOKEN}`)
    await screen.findByText(/this link is not valid/i)

    expect(container.innerHTML).not.toContain(HOST_TOKEN)
  })

  it('never puts the token in a URL', async () => {
    const fetchStub = stubFetch((call) =>
      call.method === 'GET' ? { body: hostViewFixture } : { body: {} },
    )
    renderAt(`/m/${HOST_TOKEN}`)
    await screen.findByText('Party')
    await userEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    await waitFor(() => expect(fetchStub.calls.length).toBeGreaterThan(1))

    const allowed: string[] = Object.values(PATHS)
    for (const call of fetchStub.calls) {
      expect(call.url).not.toContain(HOST_TOKEN)
      expect(call.url).not.toContain('token')
      expect(allowed).toContain(call.url)
      expect(call.headers.Authorization).toBe(`Bearer ${HOST_TOKEN}`)
    }
  })

  it('shows one generic page for every invalid link, whichever page asked', async () => {
    stubFetch(() => linkInvalid)
    const host = renderAt(`/m/${HOST_TOKEN}`)
    await screen.findByText(/this link is not valid/i)
    const fromHostPage = host.container.innerHTML
    host.unmount()

    const guest = renderAt(`/i/${GUEST_TOKEN}`)
    await screen.findByText(/this link is not valid/i)
    const fromGuestPage = guest.container.innerHTML
    guest.unmount()

    const unknown = renderAt('/something/else')
    const fromUnknownPath = unknown.container.innerHTML

    expect(fromGuestPage).toBe(fromHostPage)
    expect(fromUnknownPath).toBe(fromHostPage)
    expect(fromHostPage).not.toContain('Party')
    expect(fromHostPage).not.toContain('event')
  })
})

describe('the create page', () => {
  it('is the only page that needs no link, and shows no link back', async () => {
    const fetchStub = stubFetch(() => ({ status: 202, body: { message: 'Check your email for the management link.' } }))
    renderAt('/')

    await userEvent.type(screen.getByLabelText('Title'), 'Wedding')
    await userEvent.type(screen.getByLabelText('Description'), 'Our wedding')
    await userEvent.type(screen.getByLabelText('Location'), 'The barn')
    await userEvent.type(screen.getByLabelText(/start/i), '2030-10-03T16:00')
    await userEvent.type(screen.getByLabelText(/your email/i), 'host@example.com')
    expect(fetchStub.mutating()).toHaveLength(0)

    await userEvent.click(screen.getByRole('button', { name: 'Create the event' }))

    await waitFor(() => expect(fetchStub.mutating()).toHaveLength(1))
    const call = fetchStub.mutating()[0]
    expect(call.url).toBe(PATHS.createEvent)
    expect(call.headers.Authorization).toBeUndefined()
    // The browser turns the host's local entry into an absolute instant (D16).
    const sent = call.body as { startTime: string; capacity: number | null }
    expect(new Date(sent.startTime).toISOString()).toBe(sent.startTime)
    expect(sent.capacity).toBeNull()
    expect(await screen.findByText(/check your email/i)).toBeInTheDocument()
    expect(screen.queryByText(/\/m\//)).not.toBeInTheDocument()
  })

  it('shows what the server says is wrong', async () => {
    stubFetch(() => ({ status: 400, body: { error: 'VALIDATION', fields: { startTime: 'must be in the future' } } }))
    renderAt('/')

    await userEvent.type(screen.getByLabelText('Title'), 'Wedding')
    await userEvent.type(screen.getByLabelText('Description'), 'Our wedding')
    await userEvent.type(screen.getByLabelText('Location'), 'The barn')
    await userEvent.type(screen.getByLabelText(/start/i), '2020-10-03T16:00')
    await userEvent.type(screen.getByLabelText(/your email/i), 'host@example.com')
    await userEvent.click(screen.getByRole('button', { name: 'Create the event' }))

    expect(await screen.findByText(/must be in the future/i)).toBeInTheDocument()
  })
})

vi.mock('../format', async () => {
  const actual = await vi.importActual<typeof import('../format')>('../format')
  // A fixed zone keeps the rendered text stable wherever the tests run.
  return { ...actual, formatStartTime: () => 'Thursday 3 October 2030 at 16:00 UTC' }
})
