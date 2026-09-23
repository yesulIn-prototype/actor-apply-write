import { useEffect, useState } from 'react'

const DURATION_MS = 900

function withoutAnimation(): boolean {
  return typeof window.requestAnimationFrame !== 'function'
    || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true
}

export function AnimatedCount({ target }: { readonly target: number }) {
  const [animated, setAnimated] = useState(0)
  const staticValue = withoutAnimation()

  useEffect(() => {
    if (staticValue) return

    let startedAt: number | undefined
    let frame = 0
    const tick = (now: number) => {
      startedAt ??= now
      const progress = Math.min((now - startedAt) / DURATION_MS, 1)
      const eased = 1 - (1 - progress) ** 3
      setAnimated(Math.round(target * eased))
      if (progress < 1) frame = window.requestAnimationFrame(tick)
    }
    frame = window.requestAnimationFrame(tick)
    return () => window.cancelAnimationFrame(frame)
  }, [target, staticValue])

  const value = staticValue ? target : animated
  return <strong className="counter-number" data-testid="completed-count" aria-hidden="true">{value.toLocaleString('ko-KR')}개</strong>
}
