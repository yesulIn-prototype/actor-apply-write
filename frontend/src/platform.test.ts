import { expect, test } from 'vitest'
import { detectPlatform, externalBrowserUrl } from './platform'

const HREF = 'https://actor.example.com/?from=share'
const UA = {
  iosSafari: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1',
  androidChrome: 'Mozilla/5.0 (Linux; Android 14; SM-S921N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36',
  iosKakao: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 KAKAOTALK 10.8.5',
  androidKakao: 'Mozilla/5.0 (Linux; Android 14; SM-S921N Build/UP1A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/128.0.0.0 Mobile Safari/537.36;KAKAOTALK 2410850',
  iosThreads: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 Barcelona 350.0.0.0 (iPhone15,2; iOS 18_0; ko_KR)',
  androidThreads: 'Mozilla/5.0 (Linux; Android 14; SM-S921N Build/UP1A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/128.0.0.0 Mobile Safari/537.36 Barcelona 350.0.0.0 Android',
  androidInstagram: 'Mozilla/5.0 (Linux; Android 14; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/128.0.0.0 Mobile Safari/537.36 Instagram 350.0.0.0 Android',
}

test('treats system browsers as normal', () => {
  expect(detectPlatform(UA.iosSafari)).toEqual({ os: 'ios', inApp: undefined })
  expect(detectPlatform(UA.androidChrome)).toEqual({ os: 'android', inApp: undefined })
  expect(externalBrowserUrl(detectPlatform(UA.iosSafari), HREF)).toBeUndefined()
})

test('sends KakaoTalk on both OSes to the system browser', () => {
  for (const ua of [UA.iosKakao, UA.androidKakao]) {
    const platform = detectPlatform(ua)
    expect(platform.inApp).toBe('kakaotalk')
    expect(externalBrowserUrl(platform, HREF)).toBe(`kakaotalk://web/openExternal?url=${encodeURIComponent(HREF)}`)
  }
})

test('uses an Android intent for Threads and Instagram', () => {
  for (const ua of [UA.androidThreads, UA.androidInstagram]) {
    expect(externalBrowserUrl(detectPlatform(ua), HREF))
      .toBe('intent://actor.example.com/?from=share#Intent;scheme=https;action=android.intent.action.VIEW;end')
  }
})

test('has no escape URL for Threads on iOS, so the page shows manual guidance', () => {
  const platform = detectPlatform(UA.iosThreads)
  expect(platform).toEqual({ os: 'ios', inApp: 'threads' })
  expect(externalBrowserUrl(platform, HREF)).toBeUndefined()
})
