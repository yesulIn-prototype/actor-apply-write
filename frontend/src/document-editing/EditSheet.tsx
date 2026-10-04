import { useEffect, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { Button } from '../ui'
import type { Change, NumberedRegion } from './api'
import { movedChanges } from './api'

export function EditSheet({ region, regions, opener, onClose, onApply }: {
  readonly region: NumberedRegion
  readonly regions: readonly NumberedRegion[]
  readonly opener: HTMLButtonElement
  readonly onClose: () => void
  readonly onApply: (changes: readonly Change[]) => Promise<boolean>
}) {
  const [value, setValue] = useState(region.text)
  const [inputStyle, setInputStyle] = useState(!region.id.startsWith('p:'))
  const [moving, setMoving] = useState(false)
  const [moveText, setMoveText] = useState(region.text)
  const [targetNumber, setTargetNumber] = useState('')
  const [acknowledged, setAcknowledged] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const sheet = useRef<HTMLDivElement>(null)
  const textarea = useRef<HTMLTextAreaElement>(null)
  const target = regions.find((candidate) => candidate.number === targetNumber.trim() && candidate.id !== region.id)

  useEffect(() => {
    textarea.current?.focus()
    return () => { if (opener.isConnected) opener.focus() }
  }, [opener])

  function close() { if (!busy) onClose() }
  function keyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') { event.preventDefault(); close() }
    if (event.key !== 'Tab') return
    const elements = [...(sheet.current?.querySelectorAll<HTMLElement>('input:not(:disabled), textarea:not(:disabled), button:not(:disabled), summary') ?? [])]
    const first = elements[0], last = elements[elements.length - 1]
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
  }

  async function apply() {
    setError('')
    try {
      const changes = moving && target ? movedChanges(region, target, moveText) : [{ id: region.id, text: value, inputStyle }]
      setBusy(true)
      if (await onApply(changes)) onClose()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '수정하지 못했어요') }
    finally { setBusy(false) }
  }

  function beginMove() {
    const input = textarea.current
    setMoveText(input && input.selectionEnd > input.selectionStart ? value.slice(input.selectionStart, input.selectionEnd) : region.text)
    setMoving((shown) => !shown)
  }

  return <div className="sheet-layer">
    <div className="sheet-scrim" aria-hidden="true" onClick={close} />
    <div ref={sheet} className="sheet document-edit-sheet" role="dialog" aria-modal="true" aria-label={`${region.number} 수정`} onKeyDown={keyDown}>
      <span className="sheet-handle" aria-hidden="true" />
      <div className="sheet-body">
        <h2 className="sheet-title">{region.number} 글 수정</h2>
        <p className="edit-help">글을 고치거나 비우고 적용하세요. 표 크기와 위치는 유지돼요.</p>
        <fieldset disabled={busy}>
          <label className="edit-label" htmlFor="region-text">이 영역의 글</label>
          <textarea ref={textarea} id="region-text" rows={5} maxLength={20000} value={value} disabled={moving} onChange={(event) => setValue(event.target.value)} />
          {!moving && <>
            <button className="edit-small-button" type="button" onClick={() => setValue('')}>글 비우기</button>
            <label className="edit-check"><input type="checkbox" checked={inputStyle} onChange={(event) => setInputStyle(event.target.checked)} />입력 글자를 검정·보통 굵기로 맞추기</label>
          </>}
          <button className="edit-small-button" type="button" aria-expanded={moving} onClick={beginMove}>{moving ? '옮기기 취소' : '글을 다른 번호로 옮기기'}</button>
          {moving && <div className="edit-move">
            <p className="edit-help">원래 글에서 옮길 부분만 적어주세요. 받을 영역의 글은 교체돼요.</p>
            <label className="edit-label" htmlFor="move-text">옮길 글</label>
            <textarea id="move-text" rows={3} maxLength={20000} value={moveText} onChange={(event) => setMoveText(event.target.value)} />
            <label className="edit-label" htmlFor="target-number">받을 영역 번호</label>
            <input id="target-number" placeholder="예: 1-19" value={targetNumber} onChange={(event) => { setTargetNumber(event.target.value); setAcknowledged(false) }} />
            {target && <><p className="edit-label">{target.number} 현재 글</p><pre className="edit-target-text">{target.text || '(빈 영역)'}</pre>
              <label className="edit-check"><input type="checkbox" checked={acknowledged} onChange={(event) => setAcknowledged(event.target.checked)} />받을 영역의 기존 글을 지우고 옮길 글로 바꿀게요</label></>}
            {targetNumber && !target && <p className="edit-help">다른 영역의 번호를 정확히 입력해 주세요.</p>}
          </div>}
        </fieldset>
        {error && <p className="edit-error" role="alert">{error}</p>}
      </div>
      <div className="sheet-actions">
        <Button variant="secondary" disabled={busy} onClick={close}>닫기</Button>
        <Button loading={busy} disabled={moving && (!target || !acknowledged || !moveText)} onClick={apply}>{moving ? '옮기고 적용' : '적용하고 미리보기'}</Button>
      </div>
    </div>
  </div>
}
