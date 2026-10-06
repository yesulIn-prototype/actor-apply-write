import { useState } from 'react'
import { message } from '../shell'
import type { Detail } from './adminApi'
import { publish, setClosed } from './adminApi'
import { STATUS_LABEL } from './status'

/** What applicants see right now, and the switch for it: publish, close, reopen. */
export function LinkPanel({ detail, onChange, onError }: {
  detail: Detail
  onChange: (detail: Detail) => void
  onError: (text: string) => void
}) {
  const [busy, setBusy] = useState(false)
  const [copied, setCopied] = useState(false)
  const link = `${window.location.origin}/apply/${detail.vid}`
  const current = detail.published[detail.published.length - 1]
  const draftPending = !detail.editingPublished && detail.editingVersion > 0
  const ready = draftPending && detail.problems.length === 0 && detail.tested

  async function run(action: () => Promise<Detail>) {
    setBusy(true)

    try {
      onChange(await action())
    } catch (reason) {
      onError(message(reason))
    } finally {
      setBusy(false)
    }
  }

  async function copy() {
    try {
      await navigator.clipboard.writeText(link)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    } catch {
      onError('복사하지 못했어요. 링크를 직접 선택해 복사해주세요')
    }
  }

  return (
    <section className="admin-section admin-link-panel">
      <h2>
        배우용 작성 링크{' '}
        <span className={`admin-status admin-status-${detail.status.toLowerCase()}`}>{STATUS_LABEL[detail.status]}</span>
      </h2>
      {current ? (
        <p className="admin-help">배우는 공개된 버전 {current}을 봐요. 이미 열어 둔 화면은 연 버전으로 끝까지 완성돼요.</p>
      ) : (
        <p className="admin-help">아직 공개하지 않았어요. 공개 전에는 이 링크로 들어와도 “찾을 수 없어요”가 보여요.</p>
      )}
      <div className="admin-row">
        <input className="admin-link-field" readOnly value={link} aria-label="배우용 작성 링크" onFocus={(event) => event.target.select()} />
        <button type="button" onClick={copy}>{copied ? '복사했어요' : '복사'}</button>
        {detail.status === 'PUBLISHED' && (
          <a className="admin-button-link" href={link} target="_blank" rel="noopener noreferrer">배우 화면 열기</a>
        )}
      </div>
      <div className="admin-row">
        {draftPending && (
          <button type="button" className="admin-primary" disabled={busy || !ready} onClick={() => run(() => publish(detail.vid))}>
            버전 {detail.editingVersion} 공개하기
          </button>
        )}
        {detail.status === 'PUBLISHED' && (
          <button type="button" disabled={busy} onClick={() => run(() => setClosed(detail.vid, true))}>공개 종료</button>
        )}
        {detail.status === 'CLOSED' && (
          <button type="button" disabled={busy} onClick={() => run(() => setClosed(detail.vid, false))}>다시 공개</button>
        )}
      </div>
      {draftPending && !ready && (
        <p className="admin-help">
          공개 조건: 원본 파일 · 문제 없는 정의 · 지금 내용으로 테스트 생성 성공
          ({detail.problems.length === 0 ? '정의 확인됨' : `정의 문제 ${detail.problems.length}개`}, {detail.tested ? '테스트 완료' : '테스트 전'})
        </p>
      )}
    </section>
  )
}
