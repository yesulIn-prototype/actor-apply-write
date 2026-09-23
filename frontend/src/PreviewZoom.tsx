import { useEffect, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import type { Hotspot, PreviewPage as Page } from './api'
import type { FieldCandidate } from './document'
import { PreviewPage } from './PreviewPage'

type Props = {
  readonly page: Page
  readonly hotspots: readonly Hotspot[]
  readonly fields: ReadonlyMap<string, FieldCandidate>
  readonly imageUrl: string
  readonly opener: HTMLButtonElement
  readonly onClose: () => void
  readonly onEdit: (hotspot: Hotspot) => void
}

export function PreviewZoom({ page, hotspots, fields, imageUrl, opener, onClose, onEdit }: Props) {
  const [scale, setScale] = useState(1.5)
  const dialog = useRef<HTMLDivElement>(null)
  const close = useRef<HTMLButtonElement>(null)
  const movingToEdit = useRef(false)

  useEffect(() => {
    close.current?.focus()
    return () => {
      if (!movingToEdit.current && opener.isConnected) opener.focus()
    }
  }, [opener])

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      onClose()
    }
    if (event.key !== 'Tab') return
    const focusable = [...(dialog.current?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') ?? [])]
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last?.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first?.focus()
    }
  }

  return (
    <div ref={dialog} className="zoom-layer" role="dialog" aria-modal="true" aria-label={`${page.number}쪽 미리보기 확대`} onKeyDown={handleKeyDown}>
      <div className="zoom-bar">
        <span>{page.number}쪽 미리보기</span>
        <button ref={close} type="button" onClick={onClose} aria-label="확대 닫기">닫기</button>
      </div>
      <p className="zoom-hint">화면을 밀어 살펴보고, 고칠 칸을 누르세요</p>
      <div className="zoom-viewport">
        <div className="zoom-page-wrap" style={{ width: `${scale * 100}%` }}>
          <PreviewPage
            page={page}
            hotspots={hotspots}
            fields={fields}
            imageUrl={imageUrl}
            enlarged
            onEdit={(hotspot) => {
              movingToEdit.current = true
              onEdit(hotspot)
            }}
          />
        </div>
      </div>
      <div className="zoom-controls">
        <button type="button" onClick={() => setScale((value) => Math.max(1, value - 0.5))} disabled={scale <= 1} aria-label="축소하기">−</button>
        <span aria-live="polite">{Math.round(scale * 100)}%</span>
        <button type="button" onClick={() => setScale((value) => Math.min(3, value + 0.5))} disabled={scale >= 3} aria-label="확대하기">+</button>
      </div>
    </div>
  )
}
