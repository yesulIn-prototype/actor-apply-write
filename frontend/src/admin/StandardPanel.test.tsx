import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import type { Detail } from './adminApi'
import { StandardPanel } from './StandardPanel'

const NEW_NOTICE: Detail = {
  vid: '22381', status: 'DRAFT', published: [], editingVersion: 0, editingPublished: false, originalName: '',
  hasSource: false, definition: '', problems: [], tested: false, cells: [], standard: false,
}

const CATALOG = {
  base: 'standard-v1',
  extras: [
    { key: 'video', label: '영상 링크', type: 'text', operatorOptions: false },
    { key: 'auditionDates', label: '오디션 가능 날짜', type: 'multi', operatorOptions: true },
  ],
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

test('turns what the operator fills in into the notice settings and saves them', async () => {
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    if (url === '/api/admin/standard') return new Response(JSON.stringify(CATALOG), { status: 200 })
    return new Response(JSON.stringify({ ...NEW_NOTICE, standard: true, definition: String(init?.body) }), { status: 200 })
  }))
  const onSaved = vi.fn()
  render(<StandardPanel detail={NEW_NOTICE} onSaved={onSaved} onError={vi.fn()} />)

  fireEvent.change(screen.getByLabelText(/공고 제목/), { target: { value: '꼬마박사 장영실 배우 지원서' } })
  fireEvent.click(screen.getByLabelText('배역 목록에서 고르기'))
  fireEvent.change(screen.getByLabelText('배역 목록'), { target: { value: '장영실 (남)\n아리 (여)\n' } })
  fireEvent.click(screen.getByLabelText('현재 출연 작품 묻기'))
  fireEvent.click(await screen.findByLabelText('오디션 가능 날짜'))
  fireEvent.change(screen.getByLabelText('오디션 가능 날짜 선택지'), { target: { value: '10월 9일\n10월 10일' } })
  fireEvent.change(screen.getByLabelText('받는 이메일'), { target: { value: 'audition@example.com' } })
  fireEvent.click(screen.getByRole('button', { name: '저장하고 지원서 만들기' }))

  await waitFor(() => expect(onSaved).toHaveBeenCalled())
  const save = vi.mocked(fetch).mock.calls.find(([, init]) => init?.method === 'PUT')
  expect(save?.[0]).toBe('/api/admin/forms/22381/definition')
  expect(JSON.parse(String(save?.[1]?.body))).toEqual({
    base: 'standard-v1',
    title: '꼬마박사 장영실 배우 지원서',
    fileName: '{name}_지원서',
    roles: ['장영실 (남)', '아리 (여)'],
    drop: ['current'],
    extras: [{ use: 'auditionDates', options: ['10월 9일', '10월 10일'] }],
    submission: { email: 'audition@example.com' },
  })
})
