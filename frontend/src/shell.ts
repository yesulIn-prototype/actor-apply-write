import { useCallback, useEffect, useRef, useState } from 'react'
import { startAnalytics, withCampaign } from './analytics'
import type { Platform } from './platform'
import { externalBrowserUrl, opensExternallyOnLoad } from './platform'

const TRIED_EXTERNAL = 'yesulin.triedExternalBrowser'

/**
 * In-app browsers (KakaoTalk, Threads on Android…) can't save files; hand the page to the system
 * browser right away. Only once per visit: if it bounces back, the page stays usable with the notice.
 * The visit is counted where the page ends up, not in the app it leaves.
 */
export function useLeaveInAppBrowser(platform: Platform) {
  useEffect(() => {
    if (opensExternallyOnLoad(platform) && !once(TRIED_EXTERNAL)) {
      const external = externalBrowserUrl(platform, withCampaign(platform, window.location.href))
      if (external) {
        window.location.href = external
        return
      }
    }
    startAnalytics(platform)
  }, [platform])
}

/** "?doc=<id>": a form finished in an in-app browser, handed over to the system browser. */
export function resumeLink(documentId: string): string {
  return `${window.location.origin}${window.location.pathname}?doc=${documentId}`
}

/** True when this visit already did it; the first call records it. With storage blocked it reports true. */
function once(key: string): boolean {
  try {
    if (window.sessionStorage.getItem(key)) return true
    window.sessionStorage.setItem(key, '1')
  } catch {
    // Without storage the redirect could loop through the fallback page; skip it.
    return true
  }
  return false
}

export function useToast(): [string, (text: string) => void] {
  const [text, setText] = useState('')
  const timer = useRef<number>(undefined)
  const show = useCallback((next: string) => {
    window.clearTimeout(timer.current)
    setText(next)
    timer.current = window.setTimeout(() => setText(''), 3000)
  }, [])
  return [text, show]
}

export function message(reason: unknown): string {
  return reason instanceof Error ? reason.message : '처리하지 못했어요. 다시 시도해주세요'
}
