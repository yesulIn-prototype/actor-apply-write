import { useEffect, useState } from 'react'
import { savedToken, saveToken } from './adminApi'
import { FormList } from './FormList'
import { FormEditor } from './FormEditor'
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
      <form onSubmit={(event) => { event.preventDefault();

 if (value.trim()) onToken(value.trim()) }}>
        <input type="password" aria-label="운영자 토큰" value={value} onChange={(event) => setValue(event.target.value)} autoComplete="off" />
        <button type="submit">확인</button>
      </form>
    </main>
  )
}
