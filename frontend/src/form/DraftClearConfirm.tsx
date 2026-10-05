import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Button } from '../ui'

export function DraftClearConfirm({ onCancel, onConfirm }: {
  readonly onCancel: () => void
  readonly onConfirm: () => Promise<void>
}) {
  const dialog = useRef<HTMLDivElement>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const opener = document.activeElement
    dialog.current?.querySelector<HTMLButtonElement>('button')?.focus()

    return () => { if (opener instanceof HTMLElement && opener.isConnected) opener.focus() }
  }, [])

  return createPortal(<div className="sheet-layer">
    <div className="sheet-scrim" aria-hidden="true" onClick={() => { if (!busy) onCancel() }} />
    <div ref={dialog} className="sheet" role="alertdialog" aria-modal="true" aria-labelledby="draft-clear-title"
      onKeyDown={(event) => {
        if (event.key === 'Escape' && !busy) { event.preventDefault(); onCancel() }

        if (event.key !== 'Tab') return

        const buttons = dialog.current?.querySelectorAll('button')

        if (event.shiftKey && document.activeElement === buttons?.[0]) { event.preventDefault(); buttons?.[1]?.focus() }
        else if (!event.shiftKey && document.activeElement === buttons?.[1]) { event.preventDefault(); buttons?.[0]?.focus() }
      }}>
      <div className="sheet-body">
        <h2 className="sheet-title" id="draft-clear-title">저장된 내용을 지울까요?</h2>
        <p className="done-note">이 공고의 저장된 내용과 현재 입력, 직접 정한 파일 이름을 지워요. 다른 공고와 선택한 사진은 그대로예요.</p>
      </div>
      <div className="sheet-actions">
        <Button variant="secondary" disabled={busy} onClick={onCancel}>계속 작성하기</Button>
        <Button loading={busy} onClick={() => { setBusy(true); void onConfirm() }}>확인하고 지우기</Button>
      </div>
    </div>
  </div>, document.body)
}
