import { useEffect, useState } from 'react'
import { message } from '../shell'
import type { Summary } from './adminApi'
import { listForms, savedToken, saveToken } from './adminApi'
import { BackupPanel } from './BackupPanel'
import { FormEditor } from './FormEditor'
import { STATUS_LABEL } from './status'
import './admin.css'

/** Where the operator is: the list, or one notice's form ("/admin/forms/22382"). */
function route(): string | undefined {
  return /^\/admin\/forms\/(\d{1,12})\/?$/.exec(window.location.pathname)?.[1]
}

/**
 * The operator's page. Applicants never come here: they get /apply/<vid> once a form is published.
 * Every call carries the operator token; the server refuses the page's API without it.
 */
export function AdminApp() {
  const [token, setToken] = useState(savedToken)
  const [vid, setVid] = useState(route)

  useEffect(() => {
    document.title = '지원서 링크 관리 | 예술in'
    const onPop = () => setVid(route())
    window.addEventListener('popstate', onPop)
    return () => window.removeEventListener('popstate', onPop)
  }, [])

  function open(next?: string) {
    window.history.pushState(null, '', next ? `/admin/forms/${next}` : '/admin')
    setVid(next)
  }

  if (!token) return <TokenGate onToken={(value) => { saveToken(value); setToken(value) }} />
  return (
    <main className="admin">
      <header className="admin-header">
        <button type="button" className="admin-home" onClick={() => open()}>지원서 링크 관리</button>
        <button type="button" className="admin-link" onClick={() => { saveToken(''); setToken('') }}>토큰 지우기</button>
      </header>
      {vid ? <FormEditor vid={vid} /> : <FormList onOpen={open} />}
    </main>
  )
}

function TokenGate({ onToken }: { onToken: (token: string) => void }) {
  const [value, setValue] = useState('')
  return (
    <main className="admin admin-gate">
      <h1>운영자 확인</h1>
      <p>서버에 설정한 운영자 토큰(YESULIN_ADMIN_TOKEN)을 넣어주세요. 이 탭을 닫으면 지워져요.</p>
      <form onSubmit={(event) => { event.preventDefault(); if (value.trim()) onToken(value.trim()) }}>
        <input type="password" aria-label="운영자 토큰" value={value} onChange={(event) => setValue(event.target.value)} autoComplete="off" />
        <button type="submit">확인</button>
      </form>
    </main>
  )
}

function FormList({ onOpen }: { onOpen: (vid: string) => void }) {
  const [forms, setForms] = useState<readonly Summary[]>()
  const [error, setError] = useState('')
  const [vid, setVid] = useState('')

  function load() {
    listForms().then(setForms).catch((reason) => setError(message(reason)))
  }

  useEffect(load, [])

  return (
    <>
      <section className="admin-section">
        <h1>공고별 지원서 링크</h1>
        <form className="admin-row" onSubmit={(event) => { event.preventDefault(); if (/^\d{1,12}$/.test(vid)) onOpen(vid) }}>
          <label htmlFor="new-vid">OTR 공고 번호(vid)</label>
          <input id="new-vid" inputMode="numeric" placeholder="22382" value={vid} onChange={(event) => setVid(event.target.value.trim())} />
          <button type="submit" disabled={!/^\d{1,12}$/.test(vid)}>열기</button>
        </form>
        <p className="admin-help">otr.co.kr/audition/?vid=<b>22382</b> 의 숫자를 넣으면 그 공고의 양식을 준비할 수 있어요.</p>
        {error && <p className="admin-error" role="alert">{error}</p>}
        {forms && forms.length === 0 && <p className="admin-help">아직 준비한 공고가 없어요.</p>}
        {forms && forms.length > 0 && (
          <table className="admin-table">
            <thead><tr><th>vid</th><th>제목</th><th>상태</th><th>공개 버전</th><th>편집 버전</th></tr></thead>
            <tbody>
              {forms.map((form) => (
                <tr key={form.vid}>
                  <td><button type="button" className="admin-link" onClick={() => onOpen(form.vid)}>{form.vid}</button></td>
                  <td>{form.title || '(제목 없음)'}</td>
                  <td><span className={`admin-status admin-status-${form.status.toLowerCase()}`}>{STATUS_LABEL[form.status]}</span></td>
                  <td>{form.publishedVersion || '-'}</td>
                  <td>{form.editingVersion || '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
      <BackupPanel onRestored={load} />
    </>
  )
}
