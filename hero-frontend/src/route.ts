/**
 * Two link routes and the open path. The token lives in the page URL — that URL is the credential — and is
 * read once, held in memory, and never stored or displayed.
 */
export type Route =
  | { kind: 'create' }
  | { kind: 'host'; token: string }
  | { kind: 'guest'; token: string }
  | { kind: 'invalid' }

export function routeOf(pathname: string): Route {
  const parts = pathname.split('/').filter(Boolean)
  if (parts.length === 0) {
    return { kind: 'create' }
  }
  if (parts.length === 2 && parts[1].length > 0) {
    if (parts[0] === 'm') {
      return { kind: 'host', token: parts[1] }
    }
    if (parts[0] === 'i') {
      return { kind: 'guest', token: parts[1] }
    }
  }
  // Anything else is treated exactly like a link that does not work.
  return { kind: 'invalid' }
}
