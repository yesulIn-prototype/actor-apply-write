import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { ApplyApp } from './ApplyApp'
import type { PublicForm } from './types'

const form: PublicForm = {
  vid: '22382',
  version: 3,
  title: '잠자는 숲속의 공주 지원서',
  fileName: '{name}_{role}_지원서',
  sourceName: '잠자는숲속의공주.hwp',
  items: [
    { id: 'name', label: '이름', help: '', required: true, type: 'TEXT', multiline: false, maxLength: 30, options: [], min: 0, max: 0, columns: [], maxRows: 0 },
    { id: 'phone', label: '연락처', help: '', required: true, type: 'PHONE', multiline: false, maxLength: 0, options: [], min: 0, max: 0, columns: [], maxRows: 0 },
    { id: 'intro', label: '자기소개', help: '짧게 적어주세요', required: false, type: 'TEXT', multiline: true, maxLength: 800, options: [], min: 0, max: 0, columns: [], maxRows: 0 },
    {
      id: 'gender', label: '성별', help: '', required: true, type: 'SINGLE', multiline: false, maxLength: 0,
      options: [{ id: 'm', label: '남' }, { id: 'f', label: '여' }], min: 0, max: 0, columns: [], maxRows: 0,
    },
    {
      id: 'role', label: '지원 배역', help: '', required: false, type: 'MULTI', multiline: false, maxLength: 0,
      options: [{ id: 'bear', label: '곰역', output: '곰' }, { id: 'princess', label: '공주역' }, { id: 'tree', label: '나무역' }], min: 0, max: 2, columns: [], maxRows: 0,
    },
    {
      id: 'career', label: '출연 경력', help: '', required: false, type: 'ROWS', multiline: false, maxLength: 0,
      options: [], min: 0, max: 0, columns: [{ id: 'title', label: '작품명' }, { id: 'part', label: '역할' }], maxRows: 2,
    },
    { id: 'photo', label: '프로필 사진', help: '', required: false, type: 'PHOTO', multiline: false, maxLength: 0, options: [], min: 0, max: 0, columns: [], maxRows: 0 },
  ],
}

const JOB = '7907f91f-de04-418f-a284-75836cbebce5'

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    if (url === '/api/forms/22382') return new Response(JSON.stringify(form), { status: 200 })
    if (url.endsWith('/preview')) return new Response(JSON.stringify({ pages: [], hotspots: [] }), { status: 200 })
    return new Response(new Blob(['hwp']), {
      status: 200,
      headers: {
        'X-Document-Id': JOB,
        'Content-Disposition': `attachment; filename*=UTF-8''${encodeURIComponent('홍길동_곰역_지원서.hwp')}`,
      },
    })
  }))
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

function sentRequest(call: number) {
  const generate = vi.mocked(fetch).mock.calls.filter(([url]) => String(url) === '/api/forms/22382/generate')[call]
  const body = generate?.[1]?.body
  if (!(body instanceof FormData)) throw new Error(`generate call ${call} was not sent`)
  return body.get('request') as Blob
}

test('draws each item the way the operator typed it, with no upload step', async () => {
  render(<ApplyApp vid="22382" />)

  expect(await screen.findByRole('heading', { name: '잠자는 숲속의 공주 지원서' })).toBeInTheDocument()
  expect(screen.queryByLabelText('지원서 파일')).not.toBeInTheDocument()
  expect(screen.getByLabelText('연락처 *')).toHaveAttribute('type', 'tel')
  expect(screen.getByLabelText('자기소개').tagName).toBe('TEXTAREA')
  expect(screen.getByText('짧게 적어주세요')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '남' })).toHaveAttribute('aria-pressed', 'false')
  expect(screen.getByLabelText('프로필 사진 사진 선택')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '지원서 만들기' })).toBeDisabled()
})

test('stops a third pick when the operator allowed two', async () => {
  render(<ApplyApp vid="22382" />)
  const roles = within(await screen.findByRole('group', { name: '지원 배역' }))

  fireEvent.click(roles.getByRole('button', { name: '곰역' }))
  fireEvent.click(roles.getByRole('button', { name: '공주역' }))

  expect(roles.getByRole('button', { name: '나무역' })).toBeDisabled()
  expect(roles.getByRole('button', { name: '곰역' })).toHaveAttribute('aria-pressed', 'true')
})

test('sends answers by item, never cells, and rebuilds the same applicant file after an edit', async () => {
  render(<ApplyApp vid="22382" />)
  fireEvent.change(await screen.findByLabelText('이름 *'), { target: { value: '홍길동' } })
  fireEvent.change(screen.getByLabelText('연락처 *'), { target: { value: '010-1234-5678' } })
  fireEvent.click(screen.getByRole('button', { name: '여' }))
  fireEvent.click(screen.getByRole('button', { name: '곰역' }))

  fireEvent.click(screen.getByRole('button', { name: '지원서 만들기' }))

  expect(await screen.findByText('지원서 파일이 만들어졌어요')).toBeInTheDocument()
  expect(screen.getByText('홍길동_곰역_지원서.hwp')).toBeInTheDocument()
  expect(JSON.parse(await sentRequest(0).text())).toEqual({
    version: 3,
    documentId: null,
    answers: { name: ['홍길동'], phone: ['010-1234-5678'], gender: ['f'], role: ['bear'] },
    fileName: '홍길동_곰_지원서.hwp',
  })
  fireEvent.click(screen.getByRole('button', { name: '뒤로' }))
  fireEvent.click(await screen.findByRole('button', { name: '지원서 만들기' }))
  await screen.findByText('지원서 파일이 만들어졌어요')
  await waitFor(async () => expect(JSON.parse(await sentRequest(1).text()).documentId).toBe(JOB))
})

test('takes a rows item row by row up to the limit and sends only the rows written in', async () => {
  render(<ApplyApp vid="22382" />)
  const career = within(await screen.findByRole('group', { name: '출연 경력' }))
  fireEvent.change(career.getByLabelText('작품명'), { target: { value: '햄릿' } })
  fireEvent.click(career.getByRole('button', { name: '+ 줄 더하기 (1/2)' }))
  expect(career.getByRole('button', { name: '2줄까지 쓸 수 있어요' })).toBeDisabled()
  fireEvent.change(screen.getByLabelText('이름 *'), { target: { value: '홍길동' } })
  fireEvent.change(screen.getByLabelText('연락처 *'), { target: { value: '01012345678' } })
  fireEvent.click(screen.getByRole('button', { name: '여' }))

  fireEvent.click(screen.getByRole('button', { name: '지원서 만들기' }))

  await screen.findByText('지원서 파일이 만들어졌어요')
  expect(JSON.parse(await sentRequest(0).text()).answers.career).toEqual(['햄릿', ''])
})

test('offers the operator template as the file name and sends the name the applicant typed instead', async () => {
  render(<ApplyApp vid="22382" />)
  const name = await screen.findByLabelText('완성 파일 이름')
  expect(name).toHaveValue('잠자는숲속의공주_완성.hwp')
  fireEvent.change(screen.getByLabelText('이름 *'), { target: { value: '홍길동' } })
  fireEvent.click(within(screen.getByRole('group', { name: '지원 배역' })).getByRole('button', { name: '곰역' }))
  expect(name).toHaveValue('홍길동_곰_지원서.hwp')
  fireEvent.change(screen.getByLabelText('연락처 *'), { target: { value: '01012345678' } })
  fireEvent.click(screen.getByRole('button', { name: '여' }))

  fireEvent.change(name, { target: { value: '홍길동_최종본.hwp' } })
  fireEvent.click(screen.getByRole('button', { name: '지원서 만들기' }))

  await screen.findByText('지원서 파일이 만들어졌어요')
  expect(JSON.parse(await sentRequest(0).text()).fileName).toBe('홍길동_최종본.hwp')
})

test('explains a closed notice instead of showing the form', async () => {
  vi.mocked(fetch).mockImplementation(async () => new Response(
    JSON.stringify({ code: 'FORM_CLOSED', message: '지원서 작성이 마감된 공고입니다.' }), { status: 410 }))
  render(<ApplyApp vid="22382" />)

  expect(await screen.findByText('지원서 작성이 마감된 공고예요')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '지원서 만들기' })).not.toBeInTheDocument()
})

test('in an in-app browser, asks for the system browser and hands the finished file over by its id', async () => {
  vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(
    'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Barcelona 350.0')
  window.history.replaceState(null, '', '/apply/22382')
  render(<ApplyApp vid="22382" />)
  expect(await screen.findByText('Safari에서 열어주세요')).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('이름 *'), { target: { value: '홍길동' } })
  fireEvent.change(screen.getByLabelText('연락처 *'), { target: { value: '01012345678' } })
  fireEvent.click(screen.getByRole('button', { name: '남' }))

  fireEvent.click(screen.getByRole('button', { name: '지원서 만들기' }))

  await screen.findByText('지원서 파일이 만들어졌어요')
  await waitFor(() => expect(window.location.search).toContain(`doc=${JOB}`))
  expect(window.location.pathname).toBe('/')
  expect(screen.getByRole('link', { name: '브라우저에서 이어하기' })).toHaveAttribute('href', expect.stringContaining(`/?doc=${JOB}`))
  window.history.replaceState(null, '', '/')
  vi.restoreAllMocks()
})
