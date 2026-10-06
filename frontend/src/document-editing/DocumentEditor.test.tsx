import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { DoneScreen } from '../DoneScreen'

const completed = { documentId: 'test-job', editToken: 'private-edit', file: new File(['original'], '지원서.hwp'), downloadUrl: '/completed', pdfUrl: '/completed.pdf' }

const view = {
  revision: 'a'.repeat(64), pages: [{ number: 1, width: 800, height: 1100 }], limitation: '사진은 입력 화면에서 수정해 주세요.',
  regions: [
    { id: 'p:0.0', text: '지원서 제목', boxes: [{ page: 1, x: 100, y: 20, width: 600, height: 30 }] },
    { id: 'c:0.18.0', text: '지원동기\n무대에 서고 싶습니다.', boxes: [{ page: 1, x: 100, y: 800, width: 600, height: 30 }] },
    { id: 'c:0.19.0', text: '빨간 안내문', boxes: [{ page: 1, x: 100, y: 830, width: 600, height: 100 }] },
  ],
}

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

test('moves selected answer to a numbered region and explicitly replaces the instructions in the completed file', async () => {
  // given
  const fetchMock = vi.fn(async (_url: string, init?: RequestInit) => init?.method === 'POST'
    ? new Response('edited', { headers: { 'Content-Disposition': "attachment; filename*=UTF-8''edited.hwp" } })
    : new Response(JSON.stringify(view)))

  vi.stubGlobal('fetch', fetchMock)
  const changed = vi.fn()
  render(<DoneScreen completed={completed} pdfBusy={false} onDocumentEdited={changed} onBack={vi.fn()} onMail={vi.fn()} onSave={vi.fn()} onSavePdf={vi.fn()} />)
  fireEvent.click(await screen.findByRole('button', { name: '1쪽 미리보기 확대' }))
  const zoom = screen.getByRole('dialog', { name: '1쪽 미리보기 확대' })
  fireEvent.click(within(zoom).getByRole('button', { name: '1-2 지원동기 무대에 서고 싶습니다. 수정' }))
  const sheet = screen.getByRole('dialog', { name: '1-2 수정' })
  // when: enter the answer to move, inspect the target and acknowledge replacement
  fireEvent.click(within(sheet).getByText('글을 다른 번호로 옮기기'))
  fireEvent.change(within(sheet).getByLabelText('옮길 글'), { target: { value: '무대에 서고 싶습니다.' } })
  fireEvent.change(within(sheet).getByLabelText('받을 영역 번호'), { target: { value: '1-3' } })
  expect(within(sheet).getByText('빨간 안내문')).toBeInTheDocument()
  expect(within(sheet).getByRole('button', { name: '옮기고 적용' })).toBeDisabled()
  fireEvent.click(within(sheet).getByLabelText('받을 영역의 기존 글을 지우고 옮길 글로 바꿀게요'))
  fireEvent.click(within(sheet).getByRole('button', { name: '옮기고 적용' }))
  // then: exact two-region edits, not input regeneration, with the latest file returned to delivery
  await screen.findByRole('status')
  const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')
  expect(JSON.parse(String(post?.[1]?.body))).toEqual({ revision: view.revision, changes: [
    { id: 'c:0.18.0', text: '지원동기', inputStyle: false }, { id: 'c:0.19.0', text: '무대에 서고 싶습니다.', inputStyle: true },
  ] })
  expect(changed).toHaveBeenCalledWith(expect.objectContaining({ directEdited: true, file: expect.any(File) }))
})

test('keeps a readable preview and saving available when direct editing cannot map a complex form', async () => {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => url.endsWith('/editing')
    ? new Response(JSON.stringify({ code: 'INVALID_DOCUMENT_EDIT', message: '중첩 표는 한글 앱에서 수정해 주세요.' }), { status: 400 })
    : new Response(JSON.stringify({ pages: view.pages, hotspots: [] }))))
  render(<DoneScreen completed={completed} pdfBusy={false} onDocumentEdited={vi.fn()} onBack={vi.fn()} onMail={vi.fn()} onSave={vi.fn()} onSavePdf={vi.fn()} />)
  expect(await screen.findByRole('alert')).toHaveTextContent('중첩 표')
  expect(await screen.findByRole('img', { name: '1쪽 미리보기' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '지원서 직접 수정' })).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: '한글로 저장' })).toBeEnabled()
})
