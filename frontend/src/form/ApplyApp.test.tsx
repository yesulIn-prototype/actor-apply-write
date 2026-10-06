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
  submission: { email: '', subject: '', deadline: '', note: '' },
  pdfFirst: false,
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
  window.history.replaceState(null, '', '/')
})

test('reopens a completed notice file from its own notice link without uploading or restoring answers', async () => {
  window.history.replaceState(null, '', `/apply/22382?doc=${JOB}`)
  vi.mocked(fetch).mockImplementation(async (url) => {
    const path = String(url)

    if (path === '/api/forms/22382') return new Response(JSON.stringify(form))

    if (path === `/api/documents/${JOB}`) return new Response(JSON.stringify({ fileName: '테스트_지원서.hwp', completed: true }))

    if (path.endsWith('/preview')) return new Response(JSON.stringify({ pages: [{ number: 1, width: 800, height: 1100 }], hotspots: [{ fieldId: 'name', page: 1, x: 0, y: 0, width: 100, height: 20 }] }))

    return new Response(new Blob(['hwp']))
  })
  render(<ApplyApp vid="22382" />)
  expect(await screen.findByText('지원서 파일이 만들어졌어요')).toBeInTheDocument()
  expect(screen.queryByLabelText('지원서 파일')).not.toBeInTheDocument()
  expect(screen.queryByLabelText('이름 *')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '이름 수정' })).not.toBeInTheDocument()
  expect(vi.mocked(fetch).mock.calls.some(([url]) => String(url) === '/api/forms/22382')).toBe(false)
  expect(vi.mocked(fetch).mock.calls.some(([url]) => String(url) === `/api/documents/${JOB}/completed`)).toBe(true)
})

test('shows an expired notice file message without falling back to upload', async () => {
  window.history.replaceState(null, '', `/apply/22382?doc=${JOB}`)
  vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({ code: 'DOCUMENT_NOT_FOUND' }), { status: 404 }))
  render(<ApplyApp vid="22382" />)
  expect(await screen.findByRole('alert')).toHaveTextContent('공고 링크에서 다시 작성해주세요')
  expect(screen.queryByLabelText('지원서 파일')).not.toBeInTheDocument()
})

function sentRequest(call: number) {
  const generate = vi.mocked(fetch).mock.calls.filter(([url]) => String(url) === '/api/forms/22382/generate')[call]
  const body = generate?.[1]?.body

  if (!(body instanceof FormData)) throw new Error(`generate call ${call} was not sent`)

  const value = body.get('request')

  if (!(value instanceof Blob)) throw new Error('generate request was not a Blob')

  return value
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
  fireEvent.change(await screen.findByLabelText('이름 *'), { target: { value: '홍길동' } })
  fireEvent.change(screen.getByLabelText('연락처 *'), { target: { value: '01012345678' } })
  fireEvent.click(screen.getByRole('button', { name: '여' }))

  fireEvent.click(screen.getByRole('button', { name: '지원서 만들기' }))

  await screen.findByText('지원서 파일이 만들어졌어요')
  expect(JSON.parse(await sentRequest(0).text()).answers.career).toEqual(['햄릿', ''])
})

test('shows where to send, with the mail subject filled from the answers', async () => {
  const withSubmission = {
    ...form,
    submission: { email: 'audition@example.com', subject: '숲속공주_{role}_{name}', deadline: '2026-10-15', note: '자유곡 영상 링크도 보내주세요' },
  }

  vi.mocked(fetch).mockImplementation(async (url) => {
    if (String(url) === '/api/forms/22382') return new Response(JSON.stringify(withSubmission), { status: 200 })

    if (String(url).endsWith('/preview')) return new Response(JSON.stringify({ pages: [], hotspots: [] }), { status: 200 })

    return new Response(new Blob(['hwp']), { status: 200, headers: { 'X-Document-Id': JOB } })
  })
  render(<ApplyApp vid="22382" />)
  fireEvent.change(await screen.findByLabelText('이름 *'), { target: { value: '홍길동' } })
  fireEvent.change(screen.getByLabelText('연락처 *'), { target: { value: '01012345678' } })
  fireEvent.click(screen.getByRole('button', { name: '여' }))
  fireEvent.click(screen.getByRole('button', { name: '곰역' }))

  fireEvent.click(screen.getByRole('button', { name: '지원서 만들기' }))

  const guide = within(await screen.findByRole('region', { name: '제출 안내' }))
  expect(guide.getByText('숲속공주_곰_홍길동')).toBeInTheDocument()
  expect(guide.getByText('10월 15일까지')).toBeInTheDocument()
  expect(guide.getByText('자유곡 영상 링크도 보내주세요')).toBeInTheDocument()
  expect(guide.getByRole('link', { name: '이 주소로 메일 쓰기' }))
    .toHaveAttribute('href', `mailto:audition@example.com?subject=${encodeURIComponent('숲속공주_곰_홍길동')}`)
})

test('on a standard-form notice, puts the PDF first and sends the PDF from the mail button', async () => {
  const share = vi.fn(async (_data: ShareData) => undefined)
  Object.defineProperty(navigator, 'share', { value: share, configurable: true })
  Object.defineProperty(navigator, 'canShare', { value: () => true, configurable: true })
  vi.mocked(fetch).mockImplementation(async (url) => {
    const address = String(url)

    if (address === '/api/forms/22382') return new Response(JSON.stringify({ ...form, pdfFirst: true }), { status: 200 })

    if (address.endsWith('/preview')) return new Response(JSON.stringify({ pages: [], hotspots: [] }), { status: 200 })

    if (address.endsWith('/completed.pdf')) return new Response(new Blob(['%PDF']), { status: 200 })

    return new Response(new Blob(['hwp']), { status: 200, headers: { 'X-Document-Id': JOB } })
  })
  render(<ApplyApp vid="22382" />)
  fireEvent.change(await screen.findByLabelText('이름 *'), { target: { value: '홍길동' } })
  fireEvent.change(screen.getByLabelText('연락처 *'), { target: { value: '01012345678' } })
  fireEvent.click(screen.getByRole('button', { name: '여' }))
  fireEvent.click(screen.getByRole('button', { name: '지원서 만들기' }))
  const mail = await screen.findByRole('button', { name: 'PDF 메일로 보내기' })
  const saves = screen.getAllByRole('button', { name: /로 저장$/ }).map((button) => button.textContent)
  expect(saves).toEqual(['PDF로 저장', '한글로 저장'])
  await waitFor(() => expect(vi.mocked(fetch).mock.calls.some(([url]) => String(url).endsWith('/completed.pdf'))).toBe(true))

  await waitFor(async () => {
    fireEvent.click(mail)
    await Promise.resolve()
    expect(share).toHaveBeenCalled()
  })

  const shared = share.mock.calls[0]?.[0].files?.[0]
  expect(shared?.type).toBe('application/pdf')
  expect(shared?.name.endsWith('.pdf')).toBe(true)
  Reflect.deleteProperty(navigator, 'share')
  Reflect.deleteProperty(navigator, 'canShare')
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
  fireEvent.change(await screen.findByLabelText('이름 *'), { target: { value: '홍길동' } })
  fireEvent.change(screen.getByLabelText('연락처 *'), { target: { value: '01012345678' } })
  fireEvent.click(screen.getByRole('button', { name: '남' }))

  fireEvent.click(screen.getByRole('button', { name: '지원서 만들기' }))

  await screen.findByText('지원서 파일이 만들어졌어요')
  await waitFor(() => expect(window.location.search).toContain(`doc=${JOB}`))
  expect(window.location.pathname).toBe('/apply/22382')
  expect(screen.getByRole('link', { name: '브라우저에서 이어하기' })).toHaveAttribute('href', expect.stringContaining(`/apply/22382?doc=${JOB}`))
  window.history.replaceState(null, '', '/')
  vi.restoreAllMocks()
})
