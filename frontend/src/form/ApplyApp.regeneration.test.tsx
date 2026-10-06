import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { ApplyApp } from './ApplyApp'
import type { PublicForm } from './types'

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

test('keeps direct edits on cancel and regenerates from input only after confirmation with its edit capability', async () => {
  const form: PublicForm = {
    vid: '99004', version: 1, title: '검증 지원서', sourceName: '지원서.hwp', fileName: '{name}',
    items: [{ id: 'name', label: '이름', help: '', required: true, type: 'TEXT', multiline: false,
      maxLength: 30, options: [], min: 0, max: 0, columns: [], maxRows: 0 }],
    submission: { email: '', subject: '', deadline: '', note: '' }, pdfFirst: false,
  }

  const view = {
    revision: 'a'.repeat(64), pages: [{ number: 1, width: 800, height: 1100 }], limitation: '',
    regions: [{ id: 'c:0.0.1', text: '처음 입력', boxes: [{ page: 1, x: 100, y: 100, width: 300, height: 40 }] }],
  }

  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === '/api/forms/99004') return new Response(JSON.stringify(form))

    if (url.endsWith('/editing') && init?.method !== 'POST') return new Response(JSON.stringify(view))

    return new Response(init?.method === 'POST' && url.endsWith('/editing') ? 'direct edit' : 'generated', {
      headers: { 'X-Document-Id': 'private-job', 'X-Document-Edit-Token': 'private-capability' },
    })
  })

  vi.stubGlobal('fetch', fetchMock)
  render(<ApplyApp vid="99004" />)
  fireEvent.change(await screen.findByLabelText('이름 *'), { target: { value: '처음 입력' } })
  fireEvent.click(screen.getByRole('button', { name: '지원서 만들기' }))
  await screen.findByText('지원서 파일이 만들어졌어요')
  fireEvent.click(await screen.findByRole('button', { name: '1쪽 미리보기 확대' }))
  const zoom = screen.getByRole('dialog', { name: '1쪽 미리보기 확대' })
  fireEvent.click(await within(zoom).findByRole('button', { name: '1-1 처음 입력 수정' }))
  const edit = screen.getByRole('dialog', { name: '1-1 수정' })
  fireEvent.change(within(edit).getByLabelText('이 영역의 글'), { target: { value: '직접 고친 글' } })
  fireEvent.click(within(edit).getByRole('button', { name: '적용하고 미리보기' }))
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  fireEvent.click(screen.getByRole('button', { name: '뒤로' }))
  fireEvent.change(screen.getByLabelText('이름 *'), { target: { value: '재생성 입력' } })
  fireEvent.click(screen.getByRole('button', { name: '지원서 만들기' }))
  const confirm = await screen.findByRole('alertdialog')
  expect(confirm).toHaveTextContent('직접 고친 글·삭제·이동 내용은 사라져요')
  const builds = () => fetchMock.mock.calls.filter(([url]) => url === '/api/forms/99004/generate')
  expect(builds()).toHaveLength(1)
  fireEvent.click(within(confirm).getByRole('button', { name: '수정본 유지' }))
  expect(builds()).toHaveLength(1)
  fireEvent.click(screen.getByRole('button', { name: '수정한 지원서로 돌아가기' }))
  await screen.findByText('지원서 파일이 만들어졌어요')
  fireEvent.click(screen.getByRole('button', { name: '뒤로' }))
  fireEvent.click(screen.getByRole('button', { name: '지원서 만들기' }))
  fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: '확인하고 다시 만들기' }))
  await screen.findByText('지원서 파일이 만들어졌어요')
  expect(builds()).toHaveLength(2)
  const request = builds()[1]?.[1]
  const requestHeaders = new Headers(request?.headers)

  expect(requestHeaders.get('X-Document-Edit-Token')).toBe('private-capability')
  expect(requestHeaders.get('X-Usage-Visitor')).toMatch(/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/)
  expect(requestHeaders.get('X-Usage-Visitor')).toBe(new Headers(builds()[0]?.[1]?.headers).get('X-Usage-Visitor'))

  if (!(request?.body instanceof FormData)) throw new Error('재생성 요청이 없습니다')
  const json = request.body.get('request')

  if (!(json instanceof Blob)) throw new Error('입력값 요청 파일이 없습니다')
  expect(JSON.parse(await json.text())).toMatchObject({ documentId: 'private-job', answers: { name: ['재생성 입력'] } })
})
