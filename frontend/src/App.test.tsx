import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import App from './App'

const analysis = {
  documentId: 'b3bcb48a-72b8-4dd9-9277-8cdce606233c',
  fileName: '오디션지원서.hwp',
  expiresAt: '2026-09-22T13:00:00Z',
  tableCount: 2,
  cellCount: 42,
  fields: [
    {
      id: 'text-0-0-1',
      label: '이름',
      kind: 'TEXT',
      address: { tableIndex: 0, rowIndex: 0, cellIndex: 1 },
      currentText: '',
      confidence: 0.98,
      warning: '',
      multiline: false,
      style: 'BLANK',
      hint: '',
      group: '',
      row: 0,
      rowName: '',
      column: '',
    },
    {
      id: 'text-0-10-0',
      label: '경력사항',
      kind: 'TEXT',
      address: { tableIndex: 0, rowIndex: 10, cellIndex: 0 },
      currentText: 'X 학교작품 제외 X',
      confidence: 0.8,
      warning: '',
      multiline: true,
      style: 'GUIDE',
      hint: '',
      group: '',
      row: 0,
      rowName: '',
      column: '',
    },
    {
      id: 'photo-1-0-0',
      label: '사진1',
      kind: 'PHOTO',
      address: { tableIndex: 1, rowIndex: 0, cellIndex: 0 },
      currentText: '사진1',
      confidence: 1,
      warning: '',
      multiline: false,
      style: 'BLANK',
      hint: '',
      group: '',
      row: 0,
      rowName: '',
      column: '',
    },
  ],
}

function completedResponse() {
  return new Response(new Blob(['completed']), {
    status: 200,
    headers: {
      'Content-Type': 'application/x-hwp',
      'Content-Disposition': `attachment; filename*=UTF-8''${encodeURIComponent('오디션지원서_완성.hwp')}`,
    },
  })
}

async function uploadAndFill() {
  render(<App />)
  const input = screen.getByLabelText('지원서 파일')
  fireEvent.change(input, { target: { files: [new File(['hwp'], '오디션지원서.hwp')] } })
  fireEvent.change(await screen.findByLabelText('이름'), { target: { value: '테스트배우' } })
  fireEvent.click(screen.getByRole('button', { name: '완성하기' }))
  await screen.findByText('지원서 파일이 만들어졌어요')
}

function apiCalls() {
  return vi.mocked(fetch).mock.calls.map(([url]) => String(url)).filter((url) => url !== '/api/stats')
}

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    if (url === '/api/stats') return new Response(JSON.stringify({ completedCount: 1234 }), { status: 200 })
    if (url.endsWith('/analyze')) return new Response(JSON.stringify(analysis), { status: 200 })
    if (url.endsWith('/preview')) return new Response(JSON.stringify({
      pages: [{ number: 1, width: 800, height: 1100 }],
      hotspots: [{ fieldId: 'text-0-0-1', page: 1, x: 100, y: 100, width: 200, height: 40 }],
    }), { status: 200 })
    if (url.endsWith('/completed.pdf')) return new Response(new Blob(['%PDF-']), { status: 200, headers: { 'Content-Type': 'application/pdf' } })
    return completedResponse()
  }))
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  Reflect.deleteProperty(navigator, 'canShare')
  Reflect.deleteProperty(navigator, 'share')
})

test('shows fields after upload and keeps the CTA disabled until something is filled', async () => {
  render(<App />)
  fireEvent.change(screen.getByLabelText('지원서 파일'), { target: { files: [new File(['hwp'], '오디션지원서.hwp')] } })

  expect(await screen.findByText('채워주세요', { exact: false })).toBeInTheDocument()
  expect(screen.getByLabelText('이름')).toHaveAttribute('autocomplete', 'name')
  expect(screen.getByLabelText('사진1 사진 선택')).toBeInTheDocument()
  expect(screen.getByLabelText('경력사항').tagName).toBe('TEXTAREA')
  expect(screen.getByLabelText('이름').tagName).toBe('INPUT')
  expect(screen.getByRole('button', { name: '완성하기' })).toBeDisabled()
})

test('rejects non-HWP files before uploading', async () => {
  render(<App />)
  fireEvent.change(screen.getByLabelText('지원서 파일'), { target: { files: [new File(['x'], 'resume.pdf')] } })

  expect(await screen.findByText('한글(HWP) 파일만 올릴 수 있어요')).toBeInTheDocument()
  expect(apiCalls()).toEqual([])
})

test('shares the completed HWP through the share sheet', async () => {
  const share = vi.fn().mockResolvedValue(undefined)
  Object.defineProperty(navigator, 'canShare', { configurable: true, value: () => true })
  Object.defineProperty(navigator, 'share', { configurable: true, value: share })
  await uploadAndFill()

  fireEvent.click(screen.getByRole('button', { name: '메일로 보내기' }))

  await waitFor(() => expect(share).toHaveBeenCalledOnce())
  const [file] = share.mock.calls[0][0].files as File[]
  expect(file.name).toBe('오디션지원서_완성.hwp')
})

test('uses the chosen output name for generation and saving', async () => {
  const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
  const base = vi.mocked(fetch).getMockImplementation()!
  vi.mocked(fetch).mockImplementation(async (url, init) => String(url).endsWith('/generate')
    ? new Response(new Blob(['completed']), {
      status: 200,
      headers: { 'Content-Disposition': `attachment; filename*=UTF-8''${encodeURIComponent('김배우_극단지원.hwp')}` },
    })
    : base(url, init))
  render(<App />)
  fireEvent.change(screen.getByLabelText('지원서 파일'), { target: { files: [new File(['hwp'], '오디션지원서.hwp')] } })
  const outputName = await screen.findByLabelText('완성 파일 이름')
  expect(outputName).toHaveValue('오디션지원서_완성.hwp')
  fireEvent.change(outputName, { target: { value: '김배우_극단지원.hwp' } })
  fireEvent.change(screen.getByLabelText('이름'), { target: { value: '김배우' } })

  fireEvent.click(screen.getByRole('button', { name: '완성하기' }))
  await screen.findByText('지원서 파일이 만들어졌어요')
  const generateCall = vi.mocked(fetch).mock.calls.find(([url]) => String(url).endsWith('/generate'))
  const body = generateCall?.[1]?.body as FormData
  const request = body.get('request') as Blob
  expect(JSON.parse(await request.text())).toMatchObject({ fileName: '김배우_극단지원.hwp' })

  fireEvent.click(screen.getByRole('button', { name: '한글로 저장' }))
  const anchor = click.mock.contexts[0] as HTMLAnchorElement
  expect(anchor.download).toBe('김배우_극단지원.hwp')
})

test('falls back to saving from a real URL when sharing is unavailable', async () => {
  const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
  await uploadAndFill()

  fireEvent.click(screen.getByRole('button', { name: '메일로 보내기' }))

  expect(await screen.findByText('파일을 저장했어요. 메일에 첨부해서 보내주세요')).toBeInTheDocument()
  const anchor = click.mock.contexts[0] as HTMLAnchorElement
  expect(anchor.getAttribute('href')).toBe(`/api/documents/${analysis.documentId}/completed`)
  expect(anchor.download).toBe('오디션지원서_완성.hwp')
})

test('shows the completed count, privacy note and KakaoTalk inquiry on the first screen', async () => {
  render(<App />)

  expect(await screen.findByText('1,234개')).toBeInTheDocument()
  expect(screen.getByText('입력한 정보는 지원서 작성에만 쓰이고 30분 뒤 자동 삭제돼요')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /카카오톡 문의/ })).toHaveAttribute('href', 'https://pf.kakao.com/_pbTBX')
  expect(screen.getByText('in')).toBeInTheDocument()
})

test('hides the counter when stats are unavailable', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 500 })))
  render(<App />)

  await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/stats'))
  expect(screen.queryByText(/지원서를 완성했어요/)).not.toBeInTheDocument()
})

test('renders the PDF on the server before downloading it', async () => {
  const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
  await uploadAndFill()

  fireEvent.click(screen.getByRole('button', { name: 'PDF로 저장' }))

  expect(await screen.findByText('PDF를 저장했어요')).toBeInTheDocument()
  expect(fetch).toHaveBeenCalledWith(`/api/documents/${analysis.documentId}/completed.pdf`, { method: 'GET' })
  const anchor = click.mock.contexts[0] as HTMLAnchorElement
  expect(anchor.getAttribute('href')).toBe(`/api/documents/${analysis.documentId}/completed.pdf`)
  expect(anchor.download).toBe('오디션지원서_완성.pdf')
})

test('does not download an error page when PDF rendering fails', async () => {
  const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
  await uploadAndFill()
  const base = vi.mocked(fetch).getMockImplementation()!
  vi.mocked(fetch).mockImplementation(async (url, init) => String(url).endsWith('.pdf')
    ? new Response(JSON.stringify({ code: 'HWP_PROCESSING_FAILED', message: 'x' }), { status: 422 })
    : base(url, init))

  fireEvent.click(screen.getByRole('button', { name: 'PDF로 저장' }))

  expect(await screen.findByText('PDF로 만들지 못했어요. 한글 파일로 저장해주세요')).toBeInTheDocument()
  expect(click).not.toHaveBeenCalled()
})

test('shows the finished form and edits a field by tapping it on the preview', async () => {
  await uploadAndFill()

  expect(await screen.findByAltText('1쪽 미리보기')).toHaveAttribute('src', `/api/documents/${analysis.documentId}/preview/1?v=0`)
  fireEvent.click(screen.getByRole('button', { name: '이름 수정' }))
  const sheet = screen.getByRole('dialog', { name: '이름 수정' })
  expect(sheet).toBeInTheDocument()

  fireEvent.change(screen.getByLabelText('이름'), { target: { value: '고친배우' } })
  fireEvent.click(screen.getByRole('button', { name: '수정하기' }))

  expect(await screen.findByText('수정했어요')).toBeInTheDocument()
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(await screen.findByAltText('1쪽 미리보기')).toHaveAttribute('src', `/api/documents/${analysis.documentId}/preview/1?v=1`)
  const generateCalls = vi.mocked(fetch).mock.calls.filter(([url]) => String(url).endsWith('/generate'))
  expect(generateCalls).toHaveLength(2)
})

test('closing the edit sheet puts the old answer back', async () => {
  await uploadAndFill()
  fireEvent.click(await screen.findByRole('button', { name: '이름 수정' }))
  fireEvent.change(screen.getByLabelText('이름'), { target: { value: '버릴값' } })

  fireEvent.click(screen.getByRole('button', { name: '닫기' }))
  fireEvent.click(screen.getByRole('button', { name: '이름 수정' }))

  expect(screen.getByLabelText('이름')).toHaveValue('테스트배우')
})

test('offers an accessible field list and keeps focus inside the edit sheet', async () => {
  await uploadAndFill()
  const entry = await screen.findByRole('button', { name: '이름 항목 수정' })
  entry.focus()
  fireEvent.click(entry)

  const dialog = screen.getByRole('dialog', { name: '이름 수정' })
  expect(screen.getByLabelText('이름')).toHaveFocus()
  expect(dialog.parentElement?.previousElementSibling).toHaveAttribute('inert')

  screen.getByRole('button', { name: '수정하기' }).focus()
  fireEvent.keyDown(dialog, { key: 'Tab' })
  expect(screen.getByLabelText('이름')).toHaveFocus()
  fireEvent.keyDown(dialog, { key: 'Escape' })
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(entry).toHaveFocus()
})
