import type { Completed, PreviewPage } from '../api'
import { request } from '../api'
import { buildForm } from '../form/api'
import type { Answers, Photos, PublicForm } from '../form/types'

const TOKEN_KEY = 'yesulin.adminToken'

export type Status = 'DRAFT' | 'PUBLISHED' | 'CLOSED'

export type Summary = {
  readonly vid: string
  readonly title: string
  readonly status: Status
  readonly publishedVersion: number
  readonly editingVersion: number
}

export type Cell = {
  readonly address: string
  readonly text: string
  readonly row: number
  readonly column: number
  readonly rowSpan: number
  readonly columnSpan: number
}

export type Detail = {
  readonly vid: string
  readonly status: Status
  readonly published: readonly number[]
  readonly editingVersion: number
  readonly editingPublished: boolean
  readonly originalName: string
  readonly hasSource: boolean
  readonly definition: string
  readonly problems: readonly string[]
  readonly tested: boolean
  readonly cells: readonly Cell[]
}

export type Box = {
  readonly address: string
  readonly page: number
  readonly x: number
  readonly y: number
  readonly width: number
  readonly height: number
}

export type Layout = { readonly pages: readonly PreviewPage[]; readonly cells: readonly Box[] }

/** Kept for this tab only: closing it signs the operator out. */
export function savedToken(): string {
  try {
    return window.sessionStorage.getItem(TOKEN_KEY) ?? ''
  } catch {
    return ''
  }
}

export function saveToken(token: string) {
  try {
    window.sessionStorage.setItem(TOKEN_KEY, token)
  } catch {
    // Without storage the token lives until the page reloads.
  }
}

function auth(): Record<string, string> {
  return { Authorization: `Bearer ${savedToken()}` }
}

async function json<T>(url: string, init: RequestInit = { method: 'GET' }): Promise<T> {
  const response = await request(url, { ...init, headers: { ...auth(), ...init.headers } })
  return response.json() as Promise<T>
}

export const listForms = () => json<readonly Summary[]>('/api/admin/forms')

export const loadForm = (vid: string) => json<Detail>(`/api/admin/forms/${vid}`)

export function uploadSource(vid: string, file: File): Promise<Detail> {
  const body = new FormData()
  body.append('document', file)
  return json<Detail>(`/api/admin/forms/${vid}/source`, { method: 'POST', body })
}

export const saveDefinition = (vid: string, definition: string) => json<Detail>(`/api/admin/forms/${vid}/definition`, {
  method: 'PUT',
  body: definition,
  headers: { 'Content-Type': 'application/json' },
})

export const loadLayout = (vid: string) => json<Layout>(`/api/admin/forms/${vid}/layout`)

/** Page images need the token, so they come through fetch and show as blob: URLs. */
export async function layoutPageUrl(vid: string, page: number): Promise<string> {
  const response = await request(`/api/admin/forms/${vid}/layout/${page}`, { method: 'GET', headers: auth() })
  return URL.createObjectURL(await response.blob())
}

export const loadEditingForm = (vid: string) => json<PublicForm>(`/api/admin/forms/${vid}/form`)

export function testBuild(vid: string, form: PublicForm, answers: Answers, photos: Photos): Promise<Completed> {
  return buildForm({ url: `/api/admin/forms/${vid}/test`, form, answers, photos, headers: auth() })
}

export const publish = (vid: string) => json<Detail>(`/api/admin/forms/${vid}/publish`, { method: 'POST' })

export const setClosed = (vid: string, closed: boolean) =>
  json<Detail>(`/api/admin/forms/${vid}/${closed ? 'close' : 'reopen'}`, { method: 'POST' })

/** @param skipped notices the server already had, left as they were */
export type Restored = { readonly restored: readonly string[]; readonly skipped: readonly string[] }

/** The backup needs the token, so it comes through fetch and is saved from a blob: URL. */
export async function downloadBackup(): Promise<{ readonly url: string; readonly name: string }> {
  const response = await request('/api/admin/backup', { method: 'GET', headers: auth() })
  const disposition = response.headers.get('Content-Disposition') ?? ''
  const name = /filename="?([^";]+)"?/i.exec(disposition)?.[1] ?? 'yesulin-forms.zip'
  return { url: URL.createObjectURL(await response.blob()), name }
}

export function restoreBackup(file: File): Promise<Restored> {
  const body = new FormData()
  body.append('backup', file)
  return json<Restored>('/api/admin/backup', { method: 'POST', body })
}
