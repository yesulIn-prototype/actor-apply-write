import { AdminApp } from './admin/AdminApp'
import { ApplyApp } from './form/ApplyApp'
import { HomePage } from './home/HomePage'
import './App.css'

/**
 * Applicants enter through a notice link; operators prepare those links in /admin.
 */
export function Root() {
  const path = window.location.pathname
  const notice = /^\/apply\/(\d{1,12})\/?$/.exec(path)

  if (notice) return <ApplyApp vid={notice[1]} />

  if (path === '/admin' || path.startsWith('/admin/')) return <AdminApp />

  return <HomePage />
}
