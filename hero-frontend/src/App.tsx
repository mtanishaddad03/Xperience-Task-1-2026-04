import { routeOf } from './route'
import { CreateEventPage } from './pages/CreateEventPage'
import { GuestPage } from './pages/GuestPage'
import { HostConsole } from './pages/HostConsole'
import { LinkInvalidPage } from './pages/LinkInvalidPage'

/**
 * Two surfaces with nothing in common but this switch (KD7): a management link reaches the Host Console, a
 * guest link the Guest Page, and no route leads from one to the other.
 */
export default function App() {
  const route = routeOf(window.location.pathname)

  switch (route.kind) {
    case 'create':
      return <CreateEventPage />
    case 'host':
      return <HostConsole token={route.token} />
    case 'guest':
      return <GuestPage token={route.token} />
    default:
      return <LinkInvalidPage />
  }
}
