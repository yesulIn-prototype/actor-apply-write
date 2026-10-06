import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { Button } from '../ui'

export function RegenerationConfirm({ onCancel, onConfirm }: { readonly onCancel: () => void; readonly onConfirm: () => void }) {
  const dialog = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const opener = document.activeElement
    dialog.current?.querySelector<HTMLButtonElement>('button')?.focus()

    return () => { if (opener instanceof HTMLElement && opener.isConnected) opener.focus() }
  }, [])

  return createPortal(<div className="sheet-layer">
    <div className="sheet-scrim" aria-hidden="true" onClick={onCancel} />
    <div ref={dialog} className="sheet" role="alertdialog" aria-modal="true" aria-labelledby="regen-title" aria-describedby="regen-description"
      onKeyDown={(event) => {
        if (event.key === 'Escape') { event.preventDefault(); onCancel() }

        if (event.key !== 'Tab') return
        const buttons = dialog.current?.querySelectorAll('button')

        if (event.shiftKey && document.activeElement === buttons?.[0]) { event.preventDefault(); buttons?.[1]?.focus() }
        else if (!event.shiftKey && document.activeElement === buttons?.[1]) { event.preventDefault(); buttons?.[0]?.focus() }
      }}>
      <div className="sheet-body">
        <h2 className="sheet-title" id="regen-title">지원서를 다시 만들까요?</h2>
        <p className="done-note" id="regen-description">입력값과 사진으로 새로 만들면, 완성 화면에서 직접 고친 글·삭제·이동 내용은 사라져요. 현재 입력값으로 다시 만들까요?</p>
      </div>
      <div className="sheet-actions"><Button variant="secondary" onClick={onCancel}>수정본 유지</Button><Button onClick={onConfirm}>확인하고 다시 만들기</Button></div>
    </div>
  </div>, document.body)
}
