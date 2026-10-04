// @vitest-environment-options {"url":"https://apply.yesulin.art/apply/22382?doc=private&utm_source=threads"}
import { afterEach, beforeEach, expect, test, vi } from 'vitest'

beforeEach(() => vi.resetModules())

afterEach(() => {
  delete window.dataLayer
  document.querySelectorAll('script[src*="googletagmanager"]').forEach((script) => script.remove())
})

test('initializes production analytics once with Arguments commands and a private-safe location', async () => {
  // Given a production page with a private document id.
  const { startAnalytics } = await import('./analytics')

  // When initialization is requested twice.
  startAnalytics({ os: 'ios' })
  startAnalytics({ os: 'ios' })

  // Then one script and the original gtag command format are retained.
  expect(document.querySelectorAll('script[src*="googletagmanager"]')).toHaveLength(1)
  expect(window.dataLayer).toHaveLength(3)

  for (const command of window.dataLayer ?? []) {
    expect(Object.prototype.toString.call(command)).toBe('[object Arguments]')
  }

  expect(window.dataLayer?.[1]).toEqual(expect.objectContaining({
    0: 'set',
    1: { page_location: 'https://apply.yesulin.art/apply/fill?utm_source=threads', page_title: '공고 지원서 작성' },
  }))
  expect(window.dataLayer?.[2]).toEqual(expect.objectContaining({
    0: 'config',
    2: { send_page_view: false, allow_google_signals: false, allow_ad_personalization_signals: false },
  }))
})

test('keeps the existing shared analytics queue when initialization runs', async () => {
  // Given an existing queue owned by another script.
  const existing = ['existing command']
  Object.defineProperty(window, 'dataLayer', { value: existing, writable: true, configurable: true })
  const { startAnalytics } = await import('./analytics')

  // When analytics starts.
  startAnalytics({ os: 'android' })

  // Then the same queue and its previous content are preserved.
  expect(window.dataLayer).toBe(existing)
  expect(window.dataLayer?.[0]).toBe('existing command')
  expect(window.dataLayer).toHaveLength(4)
})
