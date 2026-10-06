import { useEffect, useState } from 'react'
import { message } from '../shell'
import type { Summary, SummaryPage, UsageSnapshot } from './adminApi'
import { deleteNotice, listForms, loadUsage, restoreNotice } from './adminApi'
import { BackupPanel } from './BackupPanel'
import { STATUS_LABEL } from './status'
import { UsagePanel } from './UsagePanel'

type Listing =
  | { readonly kind: 'loading' }
  | { readonly kind: 'error'; readonly message: string }
  | { readonly kind: 'ready'; readonly result: SummaryPage }

export function FormList({ onOpen }: { onOpen: (vid: string) => void }) {
  const [listing, setListing] = useState<Listing>({ kind: 'loading' })
  const [vid, setVid] = useState('')
  const [input, setInput] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(1)
  const [revision, setRevision] = useState(0)
  const [deleted, setDeleted] = useState(false)
  const [usage, setUsage] = useState<UsageSnapshot>()
  const [usageError, setUsageError] = useState('')
  const [actionError, setActionError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let current = true
    listForms(page, query, deleted).then(
      (result) => { if (current) setListing({ kind: 'ready', result }) },
      (reason: Error) => { if (current) setListing({ kind: 'error', message: message(reason) }) },
    )

    return () => { current = false }
  }, [page, query, revision, deleted])

  useEffect(() => {
    let current = true
    loadUsage().then(
      (snapshot) => { if (current) { setUsage(snapshot); setUsageError('') } },
      (reason: Error) => { if (current) setUsageError(message(reason)) },
    )

    return () => { current = false }
  }, [revision])

  function search(next: string) {
    setListing({ kind: 'loading' })
    setQuery(next.trim())
    setPage(1)
    setRevision((value) => value + 1)
  }

  function reload() {
    setListing({ kind: 'loading' })
    setRevision((value) => value + 1)
  }

  function navigate(next: number) {
    setListing({ kind: 'loading' })
    setPage(next)
  }

  function toggleDeleted() {
    setListing({ kind: 'loading' })
    setPage(1)
    setDeleted((value) => !value)
    setActionError('')
  }

  async function changeNotice(form: Summary) {
    if (!deleted && !window.confirm(`공고 ${form.vid} (${form.title || '제목 없음'})을 삭제할까요? 새 지원은 중단되며 원본·정의는 보존돼요. 삭제된 공고 목록에서 복구할 수 있어요.`)) return

    setBusy(true)
    setActionError('')

    try {
      if (deleted) await restoreNotice(form.vid)
      else await deleteNotice(form.vid)
      reload()
    } catch (reason) {
      // no-excuse-ok: catch - operator action boundary; render a safe error, never expose credentials
      setActionError(message(reason))
    } finally {
      setBusy(false)
    }
  }

  const result = listing.kind === 'ready' ? listing.result : undefined

  return (
    <>
      <section className="admin-section">
        <h1>공고별 지원서 링크</h1>
        {usage && <UsagePanel snapshot={usage} />}
        {usageError && <p className="admin-error" role="alert">통계를 불러오지 못했어요. {usageError}</p>}
        <form className="admin-row" onSubmit={(event) => {
          event.preventDefault()

          if (/^\d{1,12}$/.test(vid)) onOpen(vid)
        }}>
          <label htmlFor="new-vid">예술in 공고 번호(vid)</label>
          <input id="new-vid" inputMode="numeric" placeholder="15" value={vid} onChange={(event) => setVid(event.target.value.trim())} />
          <button type="submit" disabled={!/^\d{1,12}$/.test(vid)}>열기</button>
        </form>
        <p className="admin-help">yesulin.art/posts/<b>15</b> 의 숫자를 넣으면 그 공고의 양식을 준비할 수 있어요.</p>
        <form className="admin-row admin-search" onSubmit={(event) => { event.preventDefault(); search(input) }}>
          <label htmlFor="form-search">vid·공고 제목 검색</label>
          <input id="form-search" type="search" placeholder="공고 번호 또는 제목" value={input} onChange={(event) => setInput(event.target.value)} />
          <button type="submit">검색</button>
          {query && <button type="button" onClick={() => { setInput(''); search('') }}>전체 보기</button>}
          <button type="button" onClick={toggleDeleted} disabled={busy}>{deleted ? '사용 중인 공고 보기' : '삭제된 공고 보기'}</button>
        </form>
        {deleted && <p className="admin-help">삭제된 공고의 원본과 정의는 보존돼요. 복구하면 삭제 전 공개 상태로 돌아가요.</p>}
        {actionError && <p className="admin-error" role="alert">{actionError}</p>}
        {listing.kind === 'loading' && <p className="admin-help" role="status">공고를 불러오는 중…</p>}
        {listing.kind === 'error' && (
          <div>
            <p className="admin-error" role="alert">{listing.message}</p>
            <button type="button" onClick={reload}>다시 시도</button>
          </div>
        )}
        {result && (
          <>
            <p className="admin-help" role="status">{query ? '검색 결과' : '전체'} {result.total}개 · {result.page} / {result.totalPages}페이지</p>
            {result.total === 0 ? <p className="admin-help">{query ? '검색 결과가 없어요.' : deleted ? '삭제된 공고가 없어요.' : '아직 준비한 공고가 없어요.'}</p> : (
              <div className="admin-list-scroll">
                <table className="admin-table admin-list-table">
                  <thead><tr><th>vid</th><th>제목</th><th>상태</th><th>공개 버전</th><th>편집 버전</th><th>이용 브라우저</th><th>생성 파일</th><th>관리</th></tr></thead>
                  <tbody>
                    {result.items.map((form) => (
                      <tr key={form.vid}>
                        <td>{deleted ? form.vid : <button type="button" className="admin-link" onClick={() => onOpen(form.vid)}>{form.vid}</button>}</td>
                        <td>{form.title || '(제목 없음)'}</td>
                        <td><span className={`admin-status admin-status-${form.status.toLowerCase()}`}>{STATUS_LABEL[form.status]}</span></td>
                        <td>{form.publishedVersion || '-'}</td><td>{form.editingVersion || '-'}</td>
                        <td>{form.usage.visitors.toLocaleString()}개</td>
                        <td className="admin-file-count">{(form.usage.hwp + form.usage.pdf).toLocaleString()}개<small>HWP {form.usage.hwp} · PDF {form.usage.pdf}</small></td>
                        <td><button type="button" className={deleted ? '' : 'admin-danger'} disabled={busy} onClick={() => void changeNotice(form)}>{deleted ? '복구' : '삭제'}</button></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <nav className="admin-row admin-pagination" aria-label="공고 목록 페이지">
              <button type="button" disabled={result.page <= 1} onClick={() => navigate(result.page - 1)}>이전</button>
              <span>{result.page} / {result.totalPages}</span>
              <button type="button" disabled={result.page >= result.totalPages} onClick={() => navigate(result.page + 1)}>다음</button>
            </nav>
          </>
        )}
      </section>
      <BackupPanel onRestored={reload} />
    </>
  )
}
