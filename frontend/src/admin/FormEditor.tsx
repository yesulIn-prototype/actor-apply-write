import { useCallback, useEffect, useState } from 'react'
import { message } from '../shell'
import type { Detail } from './adminApi'
import { loadForm, uploadSource } from './adminApi'
import { DefinitionPanel } from './DefinitionPanel'
import { LayoutPanel } from './LayoutPanel'
import { LinkPanel } from './LinkPanel'
import { StandardPanel } from './StandardPanel'
import { TestPanel } from './TestPanel'

/**
 * One notice's form, in the order the operator works. A notice with its own form: upload it, read cell
 * addresses off it, write the definition. A notice without one: set up the standard form. Then test-build and
 * publish the link.
 */
export function FormEditor({ vid }: { vid: string }) {
  const [detail, setDetail] = useState<Detail>()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  // Bumped whenever the source or definition changes, so panels that rendered the old one reload.
  const [revision, setRevision] = useState(0)
  // A new notice: which way the operator chose before anything is saved.
  const [start, setStart] = useState<Way>()

  const show = useCallback((next: Detail) => {
    setDetail(next)
    setError('')
  }, [])

  /** The source or the definition changed: the drawn form and the test screen are out of date. */
  const changed = useCallback((next: Detail) => {
    show(next)
    setRevision((value) => value + 1)
  }, [show])

  useEffect(() => {
    loadForm(vid).then(show).catch((reason) => setError(message(reason)))
  }, [vid, show])

  const way = detail ? flow(detail, start) : 'choose'

  async function upload(file: File) {
    setBusy(true)
    try {
      changed(await uploadSource(vid, file))
    } catch (reason) {
      setError(message(reason))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="admin-editor">
      <h1>공고 {vid}</h1>
      <p className="admin-help">
        OTR 공고: <a href={`https://otr.co.kr/audition/?vid=${vid}`} target="_blank" rel="noopener noreferrer">otr.co.kr/audition/?vid={vid}</a>
      </p>
      {error && <p className="admin-error" role="alert">{error}</p>}
      {detail && <LinkPanel detail={detail} onChange={show} onError={setError} />}
      {detail && way === 'choose' && <StartChoice onPick={setStart} />}
      {detail && way === 'standard' && (
        <>
          <StandardPanel key={`${detail.editingVersion}:${detail.definition}`} detail={detail} onSaved={changed} onError={setError} />
          <details className="admin-advanced">
            <summary>설정 JSON 직접 고치기</summary>
            <DefinitionPanel key={`json:${detail.editingVersion}:${detail.definition}`} detail={detail} onSaved={changed} onError={setError} settings />
          </details>
        </>
      )}
      {detail && way === 'hwp' && (
        <>
          <section className="admin-section">
            <h2>1. 원본 지원서</h2>
            <p className="admin-help">
              {detail.hasSource ? `현재 파일: ${detail.originalName} (버전 ${detail.editingVersion})` : '공고에 첨부된 HWP/HWPX 지원서를 올려주세요.'}
              {detail.editingPublished && ' · 공개 중인 버전이라, 바꾸면 새 버전으로 저장돼요.'}
            </p>
            <label className="admin-file">
              {busy ? '올리는 중…' : detail.hasSource ? '다른 파일로 바꾸기' : '파일 고르기'}
              <input
                type="file"
                accept=".hwp,.hwpx"
                disabled={busy}
                onChange={(event) => {
                  const file = event.target.files?.[0]
                  event.target.value = ''
                  if (file) void upload(file)
                }}
              />
            </label>
          </section>

          {detail.hasSource && <LayoutPanel vid={vid} cells={detail.cells} revision={revision} />}
          <DefinitionPanel key={`${detail.editingVersion}:${detail.definition}`} detail={detail} onSaved={changed} onError={setError} />
        </>
      )}
      {detail && detail.hasSource && detail.definition && (
        <TestPanel key={revision} vid={vid} detail={detail} revision={revision} onTested={() => loadForm(vid).then(setDetail)} />
      )}
    </div>
  )
}

type Way = 'hwp' | 'standard'

/** A saved notice keeps its way; a new one waits for the operator's choice. */
function flow(detail: Detail, start: Way | undefined): Way | 'choose' {
  if (detail.standard) return 'standard'
  if (detail.hasSource || detail.definition) return 'hwp'
  return start ?? 'choose'
}

function StartChoice({ onPick }: { onPick: (way: Way) => void }) {
  return (
    <section className="admin-section">
      <h2>1. 이 공고의 지원서</h2>
      <p className="admin-help">OTR 공고에 지원서 파일(HWP)이 첨부돼 있나요?</p>
      <div className="admin-row">
        <button type="button" onClick={() => onPick('hwp')}>있어요 · 그 파일 올리기</button>
        <button type="button" className="admin-primary" onClick={() => onPick('standard')}>없어요 · 표준 지원서로 시작</button>
      </div>
      <p className="admin-help">지원서 없이 프로필을 이메일로 받는 공고는 예술in 표준 지원서에 이 공고의 배역·질문·제출 방법을 더해 링크를 만들어요.</p>
    </section>
  )
}
