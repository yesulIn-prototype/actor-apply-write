import type { Completed } from '../api'
import { downloadName, request } from '../api'
import type { Answers, Photos, PublicForm } from './types'
import { filledAnswers } from './types'

export async function fetchForm(vid: string): Promise<PublicForm> {
  const response = await request(`/api/forms/${vid}`, { method: 'GET' })
  return response.json() as Promise<PublicForm>
}

type Build = {
  readonly url: string
  readonly form: PublicForm
  readonly answers: Answers
  readonly photos: Photos
  /** The applicant's own job after the first build, so a fix replaces only their file. */
  readonly documentId?: string
  /** The applicant's chosen name; empty lets the server use the operator's template. */
  readonly fileName?: string
  readonly headers?: Readonly<Record<string, string>>
}

/**
 * Sends the answers (no cells: the server applies the operator's rules) and returns the finished file.
 * The response names the job, which later builds and downloads use.
 */
export async function buildForm({ url, form, answers, photos, documentId, fileName, headers }: Build): Promise<Completed> {
  const body = new FormData()
  const filled = filledAnswers(form, answers)
  body.append('request', new Blob(
    [JSON.stringify({ version: form.version, documentId: documentId ?? null, answers: filled, fileName: fileName ?? '' })],
    { type: 'application/json' },
  ))
  Object.entries(photos).forEach(([id, photo]) => {
    if (photo) body.append(`photo-${id}`, photo, photo.name)
  })
  const response = await request(url, { method: 'POST', body, headers: { ...headers } })
  const job = response.headers.get('X-Document-Id')
  if (!job) throw new Error('처리하지 못했어요. 다시 시도해주세요')
  const savedName = downloadName(response, `${form.title}.hwp`)
  const blob = await response.blob()
  return {
    documentId: job,
    file: new File([blob], savedName, { type: 'application/x-hwp' }),
    downloadUrl: `/api/documents/${job}/completed`,
    pdfUrl: `/api/documents/${job}/completed.pdf`,
  }
}
