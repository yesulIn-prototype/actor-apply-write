const KEY = 'yesulin.usageVisitor'

const UUID = /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/

let fallback = ''

/** Random browser identity only; denied storage falls back to this page's lifetime. */
export function usageVisitor(): string {
  try {
    const saved = localStorage.getItem(KEY)

    if (saved && UUID.test(saved)) return saved
    const visitor = randomId()
    localStorage.setItem(KEY, visitor)

    return visitor
  } catch {
    // no-excuse-ok: catch - browser storage may be blocked; generation must remain available
    if (!fallback) fallback = randomId()

    return fallback
  }
}

function randomId(): string {
  const hex = Array.from(crypto.getRandomValues(new Uint8Array(16)), (byte) => byte.toString(16).padStart(2, '0')).join('')

  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join('-')
}
