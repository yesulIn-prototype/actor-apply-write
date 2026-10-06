import { AdminApp } from './admin/AdminApp'
import { ApplyApp } from './form/ApplyApp'
import { TopBar } from './ui'
import './App.css'

/**
 * Applicants enter through a notice link; operators prepare those links in /admin.
 */
export function Root() {
  const path = window.location.pathname
  const notice = /^\/apply\/(\d{1,12})\/?$/.exec(path)

  if (notice) return <ApplyApp vid={notice[1]} />

  if (path === '/admin' || path.startsWith('/admin/')) return <AdminApp />

  return (
    <main className="app">
      <TopBar />
      <section className="content">
        <h1 className="title">공고별 지원 링크를 열어주세요</h1>
        <p className="notice-lead">예술in 공고와 함께 받은 지원 링크에서 지원서를 작성할 수 있어요.</p>
      </section>
    </main>
  )
}
