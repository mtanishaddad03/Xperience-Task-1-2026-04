/**
 * The one place a request is built.
 *
 * The link token travels only in the Authorization header: never in a path, never in a query string, so no
 * request log can hold it. Paths here are fixed strings for the same reason.
 */

export const PATHS = {
  createEvent: '/api/events',
  hostEvent: '/api/host/event',
  hostVerify: '/api/host/verify',
  hostInvitations: '/api/host/invitations',
  hostResend: '/api/host/invitations/resend',
  hostClose: '/api/host/close',
  hostCancel: '/api/host/cancel',
  guestEvent: '/api/guest',
  guestReply: '/api/guest/reply',
} as const

/** The link is not valid — the server says no more than that, and neither do we. */
export class LinkInvalidError extends Error {}

/** Anything the server refused with a reason the page can show. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: ApiErrorBody,
  ) {
    super(body.error)
  }
}

export type ApiErrorBody = {
  error: string
  message?: string
  fields?: Record<string, string>
  invalid?: string[]
  reason?: string
  limit?: number
  usedInWindow?: number
  requested?: number
}

export type LockReason = 'CANCELLED' | 'CLOSED' | 'STARTED' | null

export type ReplyState = 'PENDING' | 'CONFIRMED' | 'WAITLISTED' | 'DECLINED' | 'MAYBE'

export type MessageStatus = 'QUEUED' | 'SENDING' | 'SENT' | 'FAILED' | 'SKIPPED' | null

export type HostView = {
  event: {
    title: string
    description: string
    location: string
    startTime: string
    capacity: number | null
    status: 'OPEN' | 'CLOSED' | 'CANCELLED'
    hostVerified: boolean
    repliesOpen: boolean
    lockedReason: LockReason
  }
  counts: {
    confirmed: number
    waitlisted: number
    maybe: number
    declined: number
    pending: number
    placesRemaining: number | null
  }
  guests: {
    email: string
    state: ReplyState
    waitlistPosition: number | null
    invitation: MessageStatus
  }[]
  outbox: { queued: number; sending: number; failed: number; paused: boolean }
}

export type GuestViewData = {
  event: {
    title: string
    description: string
    location: string
    startTime: string
    status: 'OPEN' | 'CLOSED' | 'CANCELLED'
    repliesOpen: boolean
    lockedReason: LockReason
  }
  reply: { state: ReplyState }
}

export type NewEvent = {
  title: string
  description: string
  location: string
  startTime: string
  capacity: number | null
  hostEmail: string
}

async function request<T>(method: string, path: string, token?: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  const response = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await response.text()
  const parsed = text ? (JSON.parse(text) as unknown) : {}
  if (response.ok) {
    return parsed as T
  }
  const error = parsed as ApiErrorBody
  if (response.status === 404 && error.error === 'LINK_INVALID') {
    throw new LinkInvalidError()
  }
  throw new ApiError(response.status, error)
}

export const api = {
  createEvent: (event: NewEvent) => request<{ message: string }>('POST', PATHS.createEvent, undefined, event),

  hostView: (token: string) => request<HostView>('GET', PATHS.hostEvent, token),
  verifyHost: (token: string) => request<{ hostVerified: boolean }>('POST', PATHS.hostVerify, token),
  invite: (token: string, emails: string[]) =>
    request<{ invited: number; alreadyInvited: number }>('POST', PATHS.hostInvitations, token, { emails }),
  resend: (token: string, email: string) => request<unknown>('POST', PATHS.hostResend, token, { email }),
  close: (token: string) => request<unknown>('POST', PATHS.hostClose, token),
  cancel: (token: string) => request<unknown>('POST', PATHS.hostCancel, token),

  guestView: (token: string) => request<GuestViewData>('GET', PATHS.guestEvent, token),
  reply: (token: string, choice: 'YES' | 'NO' | 'MAYBE') =>
    request<{ state: ReplyState }>('PUT', PATHS.guestReply, token, { choice }),
}
