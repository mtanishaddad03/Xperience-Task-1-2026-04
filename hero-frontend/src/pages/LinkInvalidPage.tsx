/**
 * One page for every link failure — unknown, wrong kind, or a path that means nothing. It says the same thing
 * in every case and reveals nothing about what does or does not exist (INV-A4).
 */
export function LinkInvalidPage() {
  return (
    <main className="mx-auto max-w-md p-8 text-center">
      <h1 className="text-xl font-semibold text-stone-900">This link is not valid.</h1>
      <p className="mt-3 text-stone-600">
        Check that you opened the most recent message you were sent, and that the whole address was copied.
      </p>
    </main>
  )
}
