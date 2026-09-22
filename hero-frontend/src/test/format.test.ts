import { describe, expect, it } from 'vitest'
import { formatStartTime, lockedMessage, stateInWords } from '../format'

/** The real function, not a stand-in: a bad set of Intl options throws only when it actually runs. */
describe('showing the start time', () => {
  it('puts an instant into the reader own zone, with the zone named (D16)', () => {
    const shown = formatStartTime('2026-11-14T16:30:00Z')

    // The reader's own locale decides the words, so only what must be there is asserted.
    expect(shown).not.toBe('')
    expect(shown).toMatch(/2026/)
    expect(shown).toMatch(/\d{1,2}[:.]\d{2}/)
    // The zone has to be visible: the same instant reads differently in another one.
    expect(shown).toMatch(/GMT|UTC|[A-Z]{2,5}/)
    expect(shown).not.toBe(formatStartTime('2026-11-14T18:30:00Z'))
  })

  it('says nothing rather than something wrong when the value is not a time', () => {
    expect(formatStartTime('not a time')).toBe('')
  })
})

describe('the words shown to a guest', () => {
  it('names every lock reason', () => {
    expect(lockedMessage('CANCELLED')).toMatch(/cancelled/i)
    expect(lockedMessage('CLOSED')).toMatch(/closed/i)
    expect(lockedMessage('STARTED')).toMatch(/started/i)
    expect(lockedMessage(null)).toBe('')
  })

  it('names every reply state', () => {
    expect(stateInWords('CONFIRMED')).toMatch(/place/i)
    expect(stateInWords('WAITLISTED')).toMatch(/waiting list/i)
    expect(stateInWords('DECLINED')).toMatch(/cannot come/i)
    expect(stateInWords('MAYBE')).toMatch(/maybe/i)
    expect(stateInWords('PENDING')).toMatch(/not replied/i)
  })
})
