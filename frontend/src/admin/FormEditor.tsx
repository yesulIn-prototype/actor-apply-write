import { useCallback, useEffect, useState } from 'react'
import { message } from '../shell'
import type { Detail } from './adminApi'
import { loadForm, uploadSource } from './adminApi'
import { DefinitionPanel } from './DefinitionPanel'
import { LayoutPanel } from './LayoutPanel'
import { LinkPanel } from './LinkPanel'
import { TestPanel } from './TestPanel'

/**
 * One notice's form, in the order the operator works: upload the form, read cell addresses off it,
 * write the definition, test-build, publish the link.
 */
export function FormEditor({ vid }: { vid: string }) {
  const [detail, setDetail] = useState<Detail>()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  // Bumped whenever the source or definition changes, so panels that rendered the old one reload.
  const [revision, setRevision] = useState(0)

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
      {detail && (
        <>
          <LinkPanel detail={detail} onChange={show} onError={setError} />

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
          {detail.hasSource && detail.definition && (
            <TestPanel key={revision} vid={vid} detail={detail} revision={revision} onTested={() => loadForm(vid).then(setDetail)} />
          )}
        </>
      )}
    </div>
  )
}
