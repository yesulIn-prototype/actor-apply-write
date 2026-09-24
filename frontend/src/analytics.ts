import type { Platform } from './platform'

/** GA4 web stream of apply.yesulin.art. Public by design: every page load carries it. */
const MEASUREMENT_ID = 'G-DDJ8ZGPF8Q'
/** Local runs, tunnels and the Railway address stay out of the reports. */
const PRODUCTION_HOST = 'apply.yesulin.art'
const CAMPAIGN_KEYS = ['utm_source', 'utm_medium', 'utm_campaign', 'utm_content', 'utm_term']

export type Screen = 'upload' | 'fill' | 'done' | 'resume'

/**
 * The app is one address, so each screen is reported as its own page to show where applicants stop.
 * "resume" is a form finished in an in-app browser and reopened here: counting it as done would count
 * that form twice.
 */
const SCREENS: Record<Screen, { path: string; title: string }> = {
  upload: { path: '/', title: '지원서 올리기' },
  fill: { path: '/fill', title: '지원서 작성' },
  done: { path: '/done', title: '완성' },
  resume: { path: '/resume', title: '브라우저에서 이어하기' },
}

/** The link's own tags, read before the app rewrites the address. */
const landing = new URLSearchParams(window.location.search)

type Gtag = (...args: unknown[]) => void
let gtag: Gtag | undefined
let tags = new URLSearchParams()
let lastScreen: Screen | undefined

/**
 * Where the visit came from, as UTM tags. A link shared with its own tags keeps them; otherwise an
 * in-app browser names its app. KakaoTalk sends no referrer, and the hop to the system browser drops
 * both the referrer and the app, so without this those visits would all count as direct.
 */
export function campaign(platform: Platform, search: URLSearchParams = landing): URLSearchParams {
  const result = new URLSearchParams()
  CAMPAIGN_KEYS.forEach((key) => {
    const value = search.get(key)
    if (value) result.set(key, value)
  })
  if (!result.has('utm_source') && platform.inApp) {
    result.set('utm_source', platform.inApp === 'other' ? 'inapp' : platform.inApp)
    result.set('utm_medium', 'social')
  }
  return result
}

/** `href` carrying the visit's source, so the page reopened in the system browser is counted right. */
export function withCampaign(platform: Platform, href: string): string {
  const url = new URL(href)
  campaign(platform).forEach((value, key) => url.searchParams.set(key, value))
  return url.toString()
}

/**
 * The address reported for a screen: the screen's path and the source tags only. The real address can
 * hold `?doc=<id>`, which alone downloads the applicant's finished form, so it never leaves the page.
 */
export function pageLocation(screen: Screen, source: URLSearchParams): string {
  const query = source.toString()
  return `${window.location.origin}${SCREENS[screen].path}${query ? `?${query}` : ''}`
}

export function startAnalytics(platform: Platform) {
  if (gtag || !MEASUREMENT_ID || window.location.hostname !== PRODUCTION_HOST) return
  const page = window as unknown as { dataLayer?: unknown[] }
  const dataLayer = (page.dataLayer ??= [])
  gtag = function () {
    // gtag.js reads commands only as Arguments objects, not arrays.
    // oxlint-disable-next-line prefer-rest-params
    dataLayer.push(arguments)
  }
  tags = campaign(platform)
  gtag('js', new Date())
  gtag('set', { page_location: pageLocation('upload', tags), page_title: SCREENS.upload.title })
  gtag('config', MEASUREMENT_ID, {
    send_page_view: false,
    allow_google_signals: false,
    allow_ad_personalization_signals: false,
  })
  const script = document.createElement('script')
  script.async = true
  script.src = `https://www.googletagmanager.com/gtag/js?id=${MEASUREMENT_ID}`
  document.head.appendChild(script)
}

export function trackScreen(screen: Screen) {
  if (!gtag || screen === lastScreen) return
  const previous = lastScreen
  lastScreen = screen
  // Set for every later event too, including the ones gtag.js sends on its own.
  gtag('set', {
    page_location: pageLocation(screen, tags),
    page_title: SCREENS[screen].title,
    ...(previous && { page_referrer: pageLocation(previous, tags) }),
  })
  gtag('event', 'page_view')
}

/** Never pass answers, names or file names: only fixed labels and the app's own messages. */
export function track(event: string, params?: Record<string, string>) {
  gtag?.('event', event, params)
}
