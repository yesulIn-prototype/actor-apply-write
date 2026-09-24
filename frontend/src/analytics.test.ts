import { expect, test } from 'vitest'
import { campaign, pageLocation, startAnalytics, withCampaign } from './analytics'
import type { Platform } from './platform'

const SAFARI: Platform = { os: 'ios' }
const KAKAO: Platform = { os: 'android', inApp: 'kakaotalk' }
const THREADS: Platform = { os: 'ios', inApp: 'threads' }

test('keeps the tags a shared link came with, over the app it opened in', () => {
  const search = new URLSearchParams('utm_source=threads&utm_campaign=audition&from=x&doc=1')
  expect(campaign(KAKAO, search).toString()).toBe('utm_source=threads&utm_campaign=audition')
})

test('names the in-app browser when the link had no tags', () => {
  expect(campaign(KAKAO, new URLSearchParams()).toString()).toBe('utm_source=kakaotalk&utm_medium=social')
  expect(campaign({ os: 'android', inApp: 'other' }, new URLSearchParams()).get('utm_source')).toBe('inapp')
  expect(campaign(SAFARI, new URLSearchParams()).toString()).toBe('')
})

test('carries the source to the system browser without losing the finished form', () => {
  const url = new URL(withCampaign(THREADS, 'https://apply.yesulin.art/?doc=0f8fad5b-d9cb-469f-a165-70867728950e'))
  expect(url.searchParams.get('doc')).toBe('0f8fad5b-d9cb-469f-a165-70867728950e')
  expect(url.searchParams.get('utm_source')).toBe('threads')
})

test('reports a screen by its path and source only, never the form id', () => {
  window.history.replaceState(null, '', '/?doc=0f8fad5b-d9cb-469f-a165-70867728950e&utm_source=kakaotalk')
  const tags = new URLSearchParams('utm_source=kakaotalk&utm_medium=social')
  expect(pageLocation('resume', tags)).toBe(`${window.location.origin}/resume?utm_source=kakaotalk&utm_medium=social`)
  expect(pageLocation('upload', new URLSearchParams())).toBe(`${window.location.origin}/`)
  window.history.replaceState(null, '', '/')
})

test('sends nothing outside the production address', () => {
  startAnalytics(KAKAO)
  expect((window as unknown as { dataLayer?: unknown[] }).dataLayer).toBeUndefined()
  expect(document.querySelector('script[src*="googletagmanager"]')).toBeNull()
})
