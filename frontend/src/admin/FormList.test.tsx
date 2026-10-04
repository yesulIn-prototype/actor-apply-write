import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { FormList } from './FormList'

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

function setup() {
  const fetcher = vi.fn(async (url: string) => {
    if (url === '/api/admin/usage') return new Response(JSON.stringify({ startedAt: '2026-10-04T00:00:00Z',
      counts: { visitors: 3, hwp: 8, pdf: 2, unidentifiedJobs: 0 } }))
    const params = new URL(url, 'http://localhost').searchParams
    const page = Number(params.get('page') ?? 1)
    const query = params.get('query') ?? ''

    const items = query ? [] : Array.from({ length: page === 1 ? 20 : 5 }, (_, index) => ({
      vid: String(10024 - (page - 1) * 20 - index), title: `합성 공고 ${index}`, status: 'DRAFT',
      publishedVersion: 0, editingVersion: 1,
      usage: { visitors: 1, hwp: 2, pdf: 1, unidentifiedJobs: 0 },
    }))

    return new Response(JSON.stringify({ items, page: query ? 1 : page, pageSize: 20,
      total: query ? 0 : 25, totalPages: query ? 1 : 2 }))
  })

  vi.stubGlobal('fetch', fetcher)
  const onOpen = vi.fn()
  render(<FormList onOpen={onOpen} />)

  return { fetcher, onOpen }
}

test('loads 20 entries then requests the second page from the server', async () => {
  // given
  const { fetcher } = setup()
  expect(await screen.findByText('전체 25개 · 1 / 2페이지')).toBeVisible()
  expect(screen.getAllByRole('row')).toHaveLength(21)
  // when
  fireEvent.click(screen.getByRole('button', { name: '다음' }))
  // then
  expect(await screen.findByText('전체 25개 · 2 / 2페이지')).toBeVisible()
  expect(screen.getAllByRole('row')).toHaveLength(6)
  expect(fetcher).toHaveBeenLastCalledWith('/api/admin/forms?page=2&query=', expect.anything())
  expect(screen.getByRole('button', { name: '다음' })).toBeDisabled()
})

test('searches by vid or title and resets paging when submitted', async () => {
  // given
  const { fetcher } = setup()
  await screen.findByText('전체 25개 · 1 / 2페이지')
  fireEvent.click(screen.getByRole('button', { name: '다음' }))
  await screen.findByText('전체 25개 · 2 / 2페이지')
  // when
  fireEvent.change(screen.getByLabelText('vid·공고 제목 검색'), { target: { value: '  웃어  ' } })
  fireEvent.click(screen.getByRole('button', { name: '검색' }))
  // then
  expect(await screen.findByText('검색 결과가 없어요.')).toBeVisible()
  expect(fetcher).toHaveBeenCalledWith('/api/admin/forms?page=1&query=%EC%9B%83%EC%96%B4', expect.anything())
  fireEvent.click(screen.getByRole('button', { name: '전체 보기' }))
  expect(await screen.findByText('전체 25개 · 1 / 2페이지')).toBeVisible()
})

test('opens a listed notice or a newly entered vid', async () => {
  // given
  const { onOpen } = setup()
  // when
  fireEvent.click(await screen.findByRole('button', { name: '10024' }))
  // then
  expect(onOpen).toHaveBeenCalledWith('10024')
  fireEvent.change(screen.getByLabelText('OTR 공고 번호(vid)'), { target: { value: '22389' } })
  fireEvent.click(screen.getByRole('button', { name: '열기' }))
  expect(onOpen).toHaveBeenCalledWith('22389')
})

test('shows a request failure and can retry without stale rows', async () => {
  // given
  let failed = false

  const fetcher = vi.fn(async (url: string) => {
    if (url === '/api/admin/usage') return new Response(JSON.stringify({ startedAt: '2026-10-04T00:00:00Z',
      counts: { visitors: 0, hwp: 0, pdf: 0, unidentifiedJobs: 0 } }))

    if (!failed) {
      failed = true

      return new Response('{}', { status: 500 })
    }

    return new Response(JSON.stringify({ items: [], page: 1, pageSize: 20, total: 0, totalPages: 1 }))
  })

  vi.stubGlobal('fetch', fetcher)
  render(<FormList onOpen={vi.fn()} />)
  await screen.findByRole('alert')
  // when
  fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
  // then
  await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument())
  expect(await screen.findByText('아직 준비한 공고가 없어요.')).toBeVisible()
})

test('confirms soft deletion and restores from the deleted-notice list', async () => {
  // given
  let deleted = false

  const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)

  const fetcher = vi.fn(async (url: string, init?: RequestInit) => {
    if (url === '/api/admin/usage') return new Response(JSON.stringify({ startedAt: '2026-10-04T00:00:00Z',
      counts: { visitors: 1, hwp: 2, pdf: 1, unidentifiedJobs: 0 } }))

    if (init?.method === 'DELETE') {
      deleted = true

      return new Response(null, { status: 204 })
    }

    if (url.endsWith('/restore')) {
      deleted = false

      return new Response('{}')
    }

    const requestedDeleted = new URL(url, 'http://localhost').searchParams.get('deleted') === 'true'

    const items = requestedDeleted === deleted ? [{ vid: '10024', title: '합성공고', status: deleted ? 'DELETED' : 'PUBLISHED',
      publishedVersion: 1, editingVersion: 1, usage: { visitors: 1, hwp: 2, pdf: 1, unidentifiedJobs: 0 } }] : []

    return new Response(JSON.stringify({ items, page: 1, pageSize: 20, total: items.length, totalPages: 1 }))
  })

  vi.stubGlobal('fetch', fetcher)
  render(<FormList onOpen={vi.fn()} />)
  const remove = await screen.findByRole('button', { name: '삭제' })
  // when: cancelling must not send a delete request
  fireEvent.click(remove)
  // then
  expect(fetcher.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false)
  confirm.mockReturnValue(true)
  fireEvent.click(remove)
  expect(await screen.findByText('아직 준비한 공고가 없어요.')).toBeVisible()
  fireEvent.click(screen.getByRole('button', { name: '삭제된 공고 보기' }))
  fireEvent.click(await screen.findByRole('button', { name: '복구' }))
  expect(await screen.findByText('삭제된 공고가 없어요.')).toBeVisible()
  fireEvent.click(screen.getByRole('button', { name: '사용 중인 공고 보기' }))
  expect(await screen.findByRole('button', { name: '10024' })).toBeVisible()
  confirm.mockRestore()
})

test('shows global browser counts and the combined HWP/PDF file total', async () => {
  // given / when
  setup()
  // then
  expect(await screen.findByText('생성 파일 10개')).toBeVisible()
  expect(screen.getByText('이용 브라우저 3개')).toBeVisible()
  expect(screen.getByText('HWP 8개 · PDF 2개')).toBeVisible()
})
