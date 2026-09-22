/** The stored start time is an absolute instant; it is shown in the reader's own zone, with the zone named (D16). */
export function formatStartTime(startTime: string): string {
  const date = new Date(startTime)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  // Named components, not dateStyle/timeStyle: those cannot be combined with timeZoneName.
  return new Intl.DateTimeFormat(undefined, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    timeZoneName: 'short',
  }).format(date)
}

/** What the guest is told when replies are locked, in the same words on both pages. */
export function lockedMessage(reason: string | null | undefined): string {
  switch (reason) {
    case 'CANCELLED':
      return 'This event has been cancelled.'
    case 'CLOSED':
      return 'The host has closed this event to further replies.'
    case 'STARTED':
      return 'This event has already started, so replies are closed.'
    default:
      return ''
  }
}

export function stateInWords(state: string): string {
  switch (state) {
    case 'CONFIRMED':
      return 'You have a place.'
    case 'WAITLISTED':
      return 'You are on the waiting list.'
    case 'DECLINED':
      return 'You have said you cannot come.'
    case 'MAYBE':
      return 'You have said maybe.'
    default:
      return 'You have not replied yet.'
  }
}
