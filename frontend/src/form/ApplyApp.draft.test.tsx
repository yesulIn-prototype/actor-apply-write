import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { IDBFactory } from 'fake-indexeddb'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { ApplyApp } from './ApplyApp'

const form = {
  vid: '99001', version: 1, title: '합성 지원서', sourceName: 'sample.hwp', fileName: '{name}',
  items: [{ id: 'name', label: '성명', type: 'TEXT', help: '', required: true, multiline: false, maxLength: 30,
    options: [], min: 0, max: 0, columns: [], maxRows: 0 }],
  submission: { email: '', subject: '', deadline: '', note: '' }, pdfFirst: false,
}

beforeEach(() => {
  vi.stubGlobal('indexedDB', new IDBFactory())
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(form))))
})

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

test('continues text and chosen output name when returning to a notice after unmounting', async () => {
  // given
  const first = render(<ApplyApp vid="99001" />)
  fireEvent.change(await screen.findByLabelText('성명 *'), { target: { value: '이어 쓰는 사람' } })
  expect(screen.getByRole('link', { name: '공고 보러 가기 ↗' })).toHaveAttribute('href', 'https://yesulin.art/posts/99001')
  expect(screen.getByText('아래 내용을 채우면 바로 이 공고에 지원할 수 있는 지원서 파일로 만들어 드려요.')).toBeVisible()
  expect(screen.getByRole('button', { name: '저장된 내용 지우기' }).closest('footer')).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('완성 파일 이름'), { target: { value: '이어서 쓰는 지원서' } })
  await screen.findByText('이 브라우저에 저장됐어요')
  // when
  first.unmount()
  render(<ApplyApp vid="99001" />)
  // then
  expect(await screen.findByLabelText('성명 *')).toHaveValue('이어 쓰는 사람')
  expect(screen.getByLabelText('완성 파일 이름')).toHaveValue('이어서 쓰는 지원서')
  expect(screen.getByText(/이전에 작성한 내용을 불러왔어요/)).toBeVisible()
})

test('explicitly clears persisted input and does not bring it back on the next visit', async () => {
  // given
  const view = render(<ApplyApp vid="99001" />)
  fireEvent.change(await screen.findByLabelText('성명 *'), { target: { value: '지울 내용' } })
  await screen.findByText('이 브라우저에 저장됐어요')
  // when
  fireEvent.click(screen.getByRole('button', { name: '저장된 내용 지우기' }))
  fireEvent.click(await screen.findByRole('button', { name: '확인하고 지우기' }))
  await waitFor(() => expect(screen.getByLabelText('성명 *')).toHaveValue(''))
  view.unmount()
  render(<ApplyApp vid="99001" />)
  // then
  expect(await screen.findByLabelText('성명 *')).toHaveValue('')
})

test('keeps writing available and warns when IndexedDB is unavailable', async () => {
  // given
  vi.stubGlobal('indexedDB', undefined)
  // when
  render(<ApplyApp vid="99001" />)
  fireEvent.change(await screen.findByLabelText('성명 *'), { target: { value: '메모리 작성' } })
  // then
  expect(screen.getByText(/자동 저장을 사용할 수 없어요/)).toBeVisible()
  expect(screen.getByLabelText('성명 *')).toHaveValue('메모리 작성')
  expect(screen.getByRole('button', { name: '지원서 만들기' })).toBeEnabled()
})

test('continues saving after back-forward cache lifecycle events', async () => {
  // given
  render(<ApplyApp vid="99001" />)
  fireEvent.change(await screen.findByLabelText('성명 *'), { target: { value: '처음 입력' } })
  await screen.findByText('이 브라우저에 저장됐어요')
  // when
  window.dispatchEvent(new PageTransitionEvent('pagehide', { persisted: true }))
  window.dispatchEvent(new PageTransitionEvent('pageshow', { persisted: true }))
  fireEvent.change(await screen.findByLabelText('성명 *'), { target: { value: '돌아와서 수정' } })
  await screen.findByText('이 브라우저에 저장됐어요')
  cleanup()
  render(<ApplyApp vid="99001" />)
  // then
  expect(await screen.findByLabelText('성명 *')).toHaveValue('돌아와서 수정')
})
