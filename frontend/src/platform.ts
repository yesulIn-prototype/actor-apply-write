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
    // If no app takes the intent, the webview loads the fallback: this same page, not an error screen.
    return `intent://${url.host}${url.pathname}${url.search}#Intent;scheme=${scheme};action=android.intent.action.VIEW;`
      + `S.browser_fallback_url=${encodeURIComponent(href)};end`
  }
  if (platform.os === 'ios') {
    // iOS 17+ opens Safari for x-safari-https:; older versions and some apps ignore it, so guidance stays.
    return `x-safari-${href}`
  }
  return undefined
}

/**
 * Leaves the in-app browser as soon as the page opens where that works without a tap: KakaoTalk's own
 * scheme and Android intents. iOS apps only honour a tap on the Safari link.
 */
export function opensExternallyOnLoad(platform: Platform): boolean {
  return platform.inApp === 'kakaotalk' || (platform.inApp !== undefined && platform.os === 'android')
}
