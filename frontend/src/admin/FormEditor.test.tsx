import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import type { Detail } from './adminApi'
import { FormEditor } from './FormEditor'

const EMPTY: Detail = {
  vid: '99007', status: 'DRAFT', published: [], editingVersion: 0, editingPublished: false,
  originalName: '', hasSource: false, definition: '', problems: [], tested: false, cells: [], standard: false,
}

const SPEC = '{ "base": "standard-v1", "title": "합성 공고 지원서", "extras": [] }'

const STANDARD: Detail = {
  ...EMPTY, editingVersion: 1, originalName: 'standard-v1.hwp', hasSource: true, definition: SPEC, standard: true,
}

function setup(detail: Detail = EMPTY, failure = false) {
  const fetcher = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === '/api/admin/standard') return new Response(JSON.stringify({ base: 'standard-v1', extras: [] }))

    if (url.endsWith('/layout')) return new Response(JSON.stringify({ pages: [], cells: [] }))

    if (init?.method === 'PUT') {
      return failure
        ? new Response(JSON.stringify({ code: 'FORM_NOT_READY', message: '공고 제목을 확인해주세요' }), { status: 409 })
        : new Response(JSON.stringify(STANDARD))
    }

    if (url.endsWith('/form')) {
      return new Response(JSON.stringify({ code: 'FORM_NOT_READY', message: '시험용 배우 화면' }), { status: 409 })
    }

    return new Response(JSON.stringify(detail))
  })

  vi.stubGlobal('fetch', fetcher)
  render(<FormEditor vid={EMPTY.vid} />)

  return fetcher
}

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

test('saves standard settings from the JSON editor without choosing a setup screen or uploading a source', async () => {
  // given
  const fetcher = setup()
  const editor = await screen.findByLabelText('양식 정의 JSON')
  // when
  fireEvent.change(editor, { target: { value: SPEC } })
  fireEvent.click(screen.getByRole('button', { name: '저장하고 확인' }))
  // then
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/admin/forms/99007/definition', expect.objectContaining({
    method: 'PUT', body: SPEC,
  })))
  expect(await screen.findByText('지원서가 만들어졌어요. 아래에서 테스트 생성을 해보세요.')).toBeVisible()
  expect(screen.getByLabelText('양식 정의 JSON')).toHaveValue(SPEC)
  expect(screen.queryByRole('button', { name: /표준 지원서로 시작/ })).not.toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: '1. 표준 지원서 설정' })).not.toBeInTheDocument()
  expect(fetcher.mock.calls.some(([url]) => url.endsWith('/source') || url === '/api/admin/standard')).toBe(false)
})

test('opens existing standard settings directly and retains published-version guidance', async () => {
  // given
  setup({ ...STANDARD, status: 'PUBLISHED', published: [1], editingPublished: true })
  // when
  const editor = await screen.findByLabelText('양식 정의 JSON')
  // then
  expect(editor).toBeVisible()
  expect(editor).toHaveValue(SPEC)
  expect(screen.getByText(/공개 중인 버전을 고치면 새 버전/)).toBeVisible()
  expect(screen.queryByRole('radiogroup', { name: '지원 배역' })).not.toBeInTheDocument()
})

test('retains the pasted JSON and displays server validation errors when saving fails', async () => {
  // given
  setup(EMPTY, true)
  const editor = await screen.findByLabelText('양식 정의 JSON')
  // when
  fireEvent.change(editor, { target: { value: SPEC } })
  fireEvent.click(screen.getByRole('button', { name: '저장하고 확인' }))
  // then
  expect(await screen.findByRole('alert')).toHaveTextContent('공고 제목을 확인해주세요')
  expect(editor).toHaveValue(SPEC)
})

test('keeps the uploaded source and cell layout workflow for a notice with its own form', async () => {
  // given
  const definition = '{ "title": "지정 양식", "items": [], "outputs": [] }'
  setup({ ...EMPTY, editingVersion: 1, hasSource: true, originalName: 'sample-notice.hwp', definition })
  // when
  const editor = await screen.findByLabelText('양식 정의 JSON')
  // then
  expect(editor).toHaveValue(definition)
  expect(screen.getByText('현재 파일: sample-notice.hwp (버전 1)')).toBeVisible()
  expect(screen.getByRole('heading', { name: '2. 칸 위치 확인' })).toBeVisible()
  expect(screen.getByRole('heading', { name: '4. 테스트 입력으로 확인' })).toBeVisible()
})
