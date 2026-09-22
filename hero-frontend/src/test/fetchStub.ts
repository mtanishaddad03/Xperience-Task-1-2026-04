import { vi } from 'vitest'

export type RecordedCall = {
  url: string
  method: string
  headers: Record<string, string>
  body: unknown
}

export type StubbedResponse = { status?: number; body?: unknown }

/**
 * Replaces fetch and records every call, so a test can assert not only what was answered but what was asked —
 * including that nothing was asked at all.
 */
export function stubFetch(handler: (call: RecordedCall) => StubbedResponse) {
  const calls: RecordedCall[] = []
  const fetchStub = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
    const call: RecordedCall = {
      url: String(url),
      method: init?.method ?? 'GET',
      headers: (init?.headers as Record<string, string>) ?? {},
      body: init?.body ? JSON.parse(init.body as string) : undefined,
    }
    calls.push(call)
    const { status = 200, body = {} } = handler(call)
    return {
      ok: status >= 200 && status < 300,
      status,
      text: async () => JSON.stringify(body),
    } as Response
  })
  vi.stubGlobal('fetch', fetchStub)
  return {
    calls,
    /** Everything that was not a plain read. */
    mutating: () => calls.filter((call) => call.method !== 'GET'),
    reset: () => {
      calls.length = 0
    },
  }
}

export const hostViewFixture = {
  event: {
    title: 'Party',
    description: 'A party',
    location: 'The garden',
    startTime: '2030-10-03T16:00:00Z',
    capacity: 2,
    status: 'OPEN',
    hostVerified: true,
    repliesOpen: true,
    lockedReason: null,
  },
  counts: { confirmed: 1, waitlisted: 1, maybe: 0, declined: 0, pending: 1, placesRemaining: 1 },
  guests: [
    { email: 'first@x.com', state: 'CONFIRMED', waitlistPosition: null, invitation: 'SENT' },
    { email: 'second@x.com', state: 'WAITLISTED', waitlistPosition: 1, invitation: 'FAILED' },
    { email: 'third@x.com', state: 'PENDING', waitlistPosition: null, invitation: 'QUEUED' },
  ],
  outbox: { queued: 1, sending: 0, failed: 1, paused: false },
}

export const guestViewFixture = {
  event: {
    title: 'Party',
    description: 'A party',
    location: 'The garden',
    startTime: '2030-10-03T16:00:00Z',
    status: 'OPEN',
    repliesOpen: true,
    lockedReason: null,
  },
  reply: { state: 'PENDING' },
}
