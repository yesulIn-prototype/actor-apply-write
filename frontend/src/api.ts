import type { AnalysisResponse, ApiError } from './document'
import { isChanged, outgoingValue } from './document'

export type Completed = {
  documentId: string
  file: File
  downloadUrl: string
  pdfUrl: string
}

const MESSAGES: Record<string, string> = {
  INVALID_UPLOAD: '한글 파일(.hwp, .hwpx)인지 확인해주세요',
  UPLOAD_TOO_LARGE: '파일이 너무 커요. 20MB 이하로 올려주세요',
  HWP_PROCESSING_FAILED: '이 파일은 읽을 수 없어요. 암호가 걸려 있지 않은 한글 파일인지 확인해주세요',
  DOCUMENT_NOT_FOUND: '시간이 지나 파일이 만료됐어요. 다시 올려주세요',
  INVALID_FILE_NAME: '파일 이름에 경로 기호나 특수 문자를 넣을 수 없어요',
  PDF_UNAVAILABLE: '지금은 PDF로 저장할 수 없어요. 한글 파일로 저장해주세요',
  RATE_LIMITED: '요청이 너무 많아요. 몇 분 뒤에 다시 시도해주세요',
  SERVER_BUSY: '지금 사용하는 사람이 많아요. 잠시 후 다시 시도해주세요',
}

export async function analyzeDocument(file: File): Promise<AnalysisResponse> {
  const body = new FormData()
  body.append('document', file)
  const response = await request('/api/documents/analyze', { method: 'POST', body })
  return response.json() as Promise<AnalysisResponse>
}

export async function fetchCompletedCount(): Promise<number | undefined> {
  try {
    const response = await fetch('/api/stats')
    if (!response.ok) return undefined
    const stats = (await response.json()) as { completedCount: number }
    return stats.completedCount
  } catch {
    return undefined
  }
}

export async function generateDocument(
  analysis: AnalysisResponse,
  values: Record<string, string>,
  photos: Record<string, File | undefined>,
  fileName: string,
): Promise<Completed> {
  const textFields = analysis.fields.filter((field) => field.kind === 'TEXT' && isChanged(field, values[field.id]))
  const photoFields = analysis.fields.filter((field) => field.kind === 'PHOTO' && photos[field.id])
  const body = new FormData()
  body.append(
    'request',
    new Blob(
      [JSON.stringify({
        fileName,
        textValues: textFields.map((field) => ({
          fieldId: field.id,
          address: field.address,
          // Keep inner line breaks for multi-line boxes; drop blank lines at the edges.
          value: outgoingValue(field, values[field.id].replace(/^\s*\n/, '').trimEnd()),
        })),
        photos: photoFields.map((field) => ({
          fieldId: field.id,
          address: field.address,
          fileKey: `photo-${field.id}`,
        })),
      })],
      { type: 'application/json' },
    ),
  )
  photoFields.forEach((field) => {
    const photo = photos[field.id] as File
    body.append(`photo-${field.id}`, photo, photo.name)
  })
  const response = await request(`/api/documents/${analysis.documentId}/generate`, { method: 'POST', body })
  const downloadedName = downloadName(response, analysis.fileName)
  const blob = await response.blob()
  return {
    documentId: analysis.documentId,
    file: new File([blob], downloadedName, { type: 'application/x-hwp' }),
    downloadUrl: `/api/documents/${analysis.documentId}/completed`,
    pdfUrl: `/api/documents/${analysis.documentId}/completed.pdf`,
  }
}

/**
 * Reopens a form finished in an in-app browser (Threads, Instagram…) in the system browser, where
 * saving and the share sheet work. Answers are not sent back: the finished file is what is resumed.
 */
export async function resumeDocument(documentId: string): Promise<Completed> {
  const summary = (await (await request(`/api/documents/${documentId}`, { method: 'GET' })).json()) as {
    fileName: string
    completed: boolean
  }
  if (!summary.completed) throw new Error(MESSAGES.DOCUMENT_NOT_FOUND)
  const downloadUrl = `/api/documents/${documentId}/completed`
  const blob = await (await request(downloadUrl, { method: 'GET' })).blob()
  return {
    documentId,
    file: new File([blob], summary.fileName, { type: 'application/x-hwp' }),
    downloadUrl,
    pdfUrl: `/api/documents/${documentId}/completed.pdf`,
  }
}

export type PreviewPage = { number: number; width: number; height: number }
export type Hotspot = { fieldId: string; page: number; x: number; y: number; width: number; height: number }
export type Preview = { pages: PreviewPage[]; hotspots: Hotspot[] }

/** The completed form as page images plus the areas that open a field for editing. */
export async function fetchPreview(documentId: string): Promise<Preview> {
  const response = await request(`/api/documents/${documentId}/preview`, { method: 'GET' })
  return response.json() as Promise<Preview>
}

/** `version` changes after every edit so the browser never shows a cached old page. */
export function previewPageUrl(documentId: string, page: number, version: number): string {
  return `/api/documents/${documentId}/preview/${page}?v=${version}`
}

/** Renders the PDF on the server first, so a failure shows a message instead of downloading an error. */
export async function preparePdf(completed: Completed): Promise<void> {
  const response = await request(completed.pdfUrl, { method: 'GET' })
  await response.blob()
}

async function request(url: string, init: RequestInit): Promise<Response> {
  let response: Response
  try {
    response = await fetch(url, init)
  } catch {
    throw new Error('연결이 불안정해요. 잠시 후 다시 시도해주세요')
  }
  if (!response.ok) {
    throw await responseError(response)
  }
  return response
}

async function responseError(response: Response): Promise<Error> {
  if (response.status === 413) return new Error(MESSAGES.UPLOAD_TOO_LARGE)
  try {
    const error = (await response.json()) as ApiError
    return new Error(MESSAGES[error.code] ?? '처리하지 못했어요. 다시 시도해주세요')
  } catch {
    return new Error('처리하지 못했어요. 다시 시도해주세요')
  }
}

function downloadName(response: Response, original: string): string {
  const disposition = response.headers.get('Content-Disposition') ?? ''
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  return encoded ? decodeURIComponent(encoded) : original.replace(/\.hwpx?$/i, '') + '_완성.hwp'
}
