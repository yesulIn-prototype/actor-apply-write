type ApiError = { readonly code: string; readonly message: string }

export type Completed = {
  documentId: string
  file: File
  downloadUrl: string
  pdfUrl: string
  /** Kept only in the creating page's memory; resume links are read-only. */
  editToken?: string
  directEdited?: boolean
}

const MESSAGES: Record<string, string> = {
  INVALID_UPLOAD: '한글 파일(.hwp, .hwpx)인지 확인해주세요',
  UPLOAD_TOO_LARGE: '파일이 너무 커요. 20MB 이하로 올려주세요',
  HWP_PROCESSING_FAILED: '이 파일은 읽을 수 없어요. 암호가 걸려 있지 않은 한글 파일인지 확인해주세요',
  DOCUMENT_NOT_FOUND: '시간이 지나 파일이 만료됐어요. 공고 링크에서 다시 작성해주세요',
  INVALID_FILE_NAME: '파일 이름에 경로 기호나 특수 문자를 넣을 수 없어요',
  PDF_UNAVAILABLE: '지금은 PDF로 저장할 수 없어요. 한글 파일로 저장해주세요',
  RATE_LIMITED: '요청이 너무 많아요. 몇 분 뒤에 다시 시도해주세요',
  SERVER_BUSY: '지금 사용하는 사람이 많아요. 잠시 후 다시 시도해주세요',
  FORM_NOT_FOUND: '이 공고의 지원서 작성 링크를 찾을 수 없어요. 링크를 다시 확인해주세요',
  FORM_CLOSED: '지원서 작성이 마감된 공고예요',
  FORM_CHANGED: '지원서 양식이 바뀌었어요. 새로고침해서 다시 작성해주세요',
  ADMIN_UNAUTHORIZED: '운영자 토큰이 맞지 않아요',
  ADMIN_DISABLED: '서버에 운영자 토큰이 설정되어 있지 않아요',
}

/** Codes whose server message is written for the reader as is: a missing answer, a definition problem. */
const SERVER_WORDED = new Set(['INVALID_ANSWER', 'FORM_NOT_READY', 'INVALID_BACKUP', 'INVALID_DOCUMENT_EDIT', 'DOCUMENT_EDIT_CONFLICT', 'DOCUMENT_EDIT_FORBIDDEN'])

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
export type Hotspot = { fieldId: string; page: number; x: number; y: number; width: number; height: number; marker?: string }
export type Preview = { pages: PreviewPage[]; hotspots: Hotspot[] }

/** The completed form as page images plus the areas that open a field for editing. */
export async function fetchPreview(documentId: string): Promise<Preview> {
  const response = await request(`/api/documents/${documentId}/preview`, { method: 'GET' })
  return response.json() as Promise<Preview>
}

/** `version` changes after every edit so the browser never shows a cached old page. */
export function previewPageUrl(documentId: string, page: number, version: number | string): string {
  return `/api/documents/${documentId}/preview/${page}?v=${version}`
}

/** Renders the PDF on the server first, so a failure shows a message instead of downloading an error. */
export async function preparePdf(completed: Completed): Promise<void> {
  const response = await request(completed.pdfUrl, { method: 'GET' })
  await response.blob()
}

export async function request(url: string, init: RequestInit): Promise<Response> {
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
    if (SERVER_WORDED.has(error.code)) return new Error(error.message)
    return new Error(MESSAGES[error.code] ?? '처리하지 못했어요. 다시 시도해주세요')
  } catch {
    return new Error('처리하지 못했어요. 다시 시도해주세요')
  }
}

export function downloadName(response: Response, original: string): string {
  const disposition = response.headers.get('Content-Disposition') ?? ''
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  return encoded ? decodeURIComponent(encoded) : original.replace(/\.hwpx?$/i, '') + '_완성.hwp'
}
