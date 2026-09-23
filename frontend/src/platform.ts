export type InAppBrowser = 'kakaotalk' | 'threads' | 'instagram' | 'facebook' | 'naver' | 'line' | 'other'

export type Platform = {
  os: 'ios' | 'android' | 'other'
  inApp?: InAppBrowser
}

const IN_APP_PATTERNS: [InAppBrowser, RegExp][] = [
  ['kakaotalk', /KAKAOTALK/i],
  ['threads', /Barcelona/i],
  ['instagram', /Instagram/i],
  ['facebook', /FBAN|FBAV|FB_IAB/i],
  ['naver', /NAVER\(inapp/i],
  ['line', /\bLine\//i],
  ['other', /DaumApps|everytimeApp|; wv\)/i],
]

export function detectPlatform(userAgent: string): Platform {
  const os = /iPhone|iPad|iPod/i.test(userAgent) ? 'ios' : /Android/i.test(userAgent) ? 'android' : 'other'
  const inApp = IN_APP_PATTERNS.find(([, pattern]) => pattern.test(userAgent))?.[0]
  return { os, inApp }
}

/**
 * In-app browsers cannot reliably save files or open the share sheet.
 * Returns a URL that reopens the current page in the system browser, when one exists.
 */
export function externalBrowserUrl(platform: Platform, href: string): string | undefined {
  if (!platform.inApp) return undefined
  if (platform.inApp === 'kakaotalk') {
    return `kakaotalk://web/openExternal?url=${encodeURIComponent(href)}`
  }
  if (platform.inApp === 'line') {
    const url = new URL(href)
    url.searchParams.set('openExternalBrowser', '1')
    return url.toString()
  }
  if (platform.os === 'android') {
    const url = new URL(href)
    const scheme = url.protocol.replace(':', '')
    return `intent://${url.host}${url.pathname}${url.search}#Intent;scheme=${scheme};action=android.intent.action.VIEW;end`
  }
  return undefined
}
