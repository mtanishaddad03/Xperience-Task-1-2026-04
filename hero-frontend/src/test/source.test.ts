import { describe, expect, it } from 'vitest'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'

/** Rules that must hold for the source itself, not for one rendered case. */

const SRC = join(process.cwd(), 'src')

/** The pages and everything they use — the tests themselves name these rules, so they are not scanned. */
function sourceFiles(directory: string = SRC): string[] {
  return readdirSync(directory).flatMap((entry) => {
    const path = join(directory, entry)
    if (statSync(path).isDirectory()) {
      return entry === 'test' ? [] : sourceFiles(path)
    }
    return /\.(ts|tsx|css|html)$/.test(entry) ? [path] : []
  })
}

describe('the pages themselves', () => {
  it('never set HTML from a string: host-written text is always text', () => {
    const offenders = sourceFiles(SRC).filter((file) =>
      readFileSync(file, 'utf8').includes('dangerouslySetInnerHTML'),
    )
    expect(offenders).toEqual([])
  })

  it('load nothing from any other origin — the page URL is the credential', () => {
    const files = [...sourceFiles(SRC), join(process.cwd(), 'index.html')]
    const offenders = files.filter((file) => /https?:\/\//.test(readFileSync(file, 'utf8')))
    expect(offenders).toEqual([])
  })

  it('ask the browser not to pass the page URL on as a referrer', () => {
    const html = readFileSync(join(process.cwd(), 'index.html'), 'utf8')
    expect(html).toContain('name="referrer"')
    expect(html).toContain('content="no-referrer"')
  })

  it('keep the token out of stored state, where it would outlive the page', () => {
    const offenders = sourceFiles(SRC).filter((file) => {
      const text = readFileSync(file, 'utf8')
      return /localStorage|sessionStorage|document\.cookie/.test(text)
    })
    expect(offenders).toEqual([])
  })

  it('build every request in one place, so no other code can reach fetch', () => {
    const offenders = sourceFiles(SRC)
      .filter((file) => !file.endsWith(join('src', 'api.ts')))
      .filter((file) => !file.includes(join('src', 'test')))
      .filter((file) => /\bfetch\(/.test(readFileSync(file, 'utf8')))
    expect(offenders).toEqual([])
  })
})
