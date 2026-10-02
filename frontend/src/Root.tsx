import App from './App.tsx'
import { AdminApp } from './admin/AdminApp'
import { ApplyApp } from './form/ApplyApp'

/**
 * Three entrances on one page: a notice link for applicants (/apply/22382), the operator's page that
 * prepares those links (/admin), and the upload flow (/).
 */
export function Root() {
  const path = window.location.pathname
  const notice = /^\/apply\/(\d{1,12})\/?$/.exec(path)
  if (notice) return <ApplyApp vid={notice[1]} />
  if (path === '/admin' || path.startsWith('/admin/')) return <AdminApp />
  return <App />
}
