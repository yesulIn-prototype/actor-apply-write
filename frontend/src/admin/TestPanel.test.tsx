import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import type { FormItem, PublicForm } from '../form/types'
import type { Detail } from './adminApi'
import { TestPanel } from './TestPanel'

const ITEM: FormItem = {
  id: 'name', label: '이름', help: '', required: false, type: 'TEXT', multiline: false,
  maxLength: 30, options: [], min: 0, max: 0, columns: [], maxRows: 0,
}
const FORM: PublicForm = {
  vid: '99003', version: 1, title: '테스트 지원서', fileName: '{name}_지원서', sourceName: 'sample.hwp',
  submission: { email: '', subject: '', deadline: '', note: '' }, pdfFirst: false,
  items: [ITEM, { ...ITEM, id: 'phone', label: '연락처', type: 'PHONE' },
    { ...ITEM, id: 'short', label: '짧은 글', maxLength: 2 },
    { ...ITEM, id: 'intro', label: '소개', multiline: true },
    { ...ITEM, id: 'role', label: '배역', type: 'SINGLE', options: [{ id: 'a', label: '배역 A' }, { id: 'b', label: '배역 B' }] },
    { ...ITEM, id: 'days', label: '날짜', type: 'MULTI', min: 2, max: 2, options: [{ id: 'mon', label: '월요일' }, { id: 'tue', label: '화요일' }] },
    { ...ITEM, id: 'career', label: '경력', type: 'ROWS', columns: [{ id: 'year', label: '연도' }, { id: 'work', label: '작품' }], maxRows: 20 },
    { ...ITEM, id: 'photo', label: '사진', type: 'PHOTO' }],
}
const DETAIL: Detail = {
  vid: '99003', status: 'DRAFT', published: [], editingVersion: 1, editingPublished: false,
  originalName: 'sample.hwp', hasSource: true, definition: '{}', problems: [], tested: false, cells: [], standard: false,
}

function setup() {
  const fetcher = vi.fn(async (url: string) => {
    if (url.endsWith('/form')) return new Response(JSON.stringify(FORM))
    return new Response(JSON.stringify({ code: 'INVALID_ANSWER', message: '사진을 넣어주세요' }), { status: 400 })
  })
  vi.stubGlobal('fetch', fetcher)
  const view = render(<TestPanel vid="99003" detail={DETAIL} revision={0} onTested={vi.fn()} />)
  return { ...view, fetcher }
}

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

test('fills non-photo fields and the first choice when the editing form loads', async () => {
  setup()
  expect(await screen.findByLabelText('이름')).toHaveValue('예시 입력 1')
  expect(screen.getByLabelText('연락처')).toHaveValue('010-0000-0000')
  expect(screen.getByLabelText('짧은 글')).toHaveValue('예시')
  expect(screen.getByLabelText('소개')).toHaveValue('예시 입력 4')
  expect(screen.getByRole('button', { name: '배역 A' })).toHaveAttribute('aria-pressed', 'true')
  expect(screen.getByRole('button', { name: '배역 B' })).toHaveAttribute('aria-pressed', 'false')
  expect(screen.getByRole('button', { name: '월요일' })).toHaveAttribute('aria-pressed', 'true')
  expect(screen.getByRole('button', { name: '화요일' })).toHaveAttribute('aria-pressed', 'false')
  expect(screen.getByLabelText('연도')).toHaveValue('예시 입력 7-1')
  expect(screen.getByLabelText('작품')).toHaveValue('예시 입력 7-2')
  expect(document.querySelector('input[type="file"]')).toHaveValue('')
})

test('keeps explicit edits and cleared choices when the parent refreshes', async () => {
  const { rerender } = setup()
  fireEvent.change(await screen.findByLabelText('이름'), { target: { value: '' } })
  fireEvent.change(screen.getByLabelText('소개'), { target: { value: '직접 고친 소개' } })
  fireEvent.click(screen.getByRole('button', { name: '배역 A' }))
  rerender(<TestPanel vid="99003" detail={{ ...DETAIL, tested: true }} revision={0} onTested={vi.fn()} />)
  expect(screen.getByLabelText('이름')).toHaveValue('')
  expect(screen.getByLabelText('소개')).toHaveValue('직접 고친 소개')
  expect(screen.getByRole('button', { name: '배역 A' })).toHaveAttribute('aria-pressed', 'false')
})

test('sends the displayed examples with operator edits and no photo when generating', async () => {
  const { fetcher } = setup()
  fireEvent.change(await screen.findByLabelText('이름'), { target: { value: '테스트 배우' } })
  fireEvent.click(screen.getByRole('button', { name: '테스트 생성' }))
  await waitFor(() => expect(fetcher).toHaveBeenCalledWith('/api/admin/forms/99003/test', expect.anything()))
  const call = vi.mocked(fetch).mock.calls.find(([url]) => url === '/api/admin/forms/99003/test')
  const body = call?.[1]?.body
  expect(body).toBeInstanceOf(FormData)
  if (!(body instanceof FormData)) return
  expect([...body.keys()]).toEqual(['request'])
  const request = body.get('request')
  if (!(request instanceof Blob)) throw new TypeError('request must be a Blob')
  const reader = new FileReader()
  const text = await new Promise<string>((resolve) => {
    reader.onload = () => resolve(String(reader.result))
    reader.readAsText(request)
  })
  expect(JSON.parse(text).answers).toMatchObject({ name: ['테스트 배우'], phone: ['010-0000-0000'], role: ['a'], career: ['예시 입력 7-1', '예시 입력 7-2'] })
})
