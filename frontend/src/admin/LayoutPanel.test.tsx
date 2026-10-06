import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { LayoutPanel } from './LayoutPanel'
import type { Cell } from './adminApi'

beforeEach(() => { vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ pages: [], cells: [] })))) })

afterEach(() => { cleanup(); vi.unstubAllGlobals() })

const cells: readonly Cell[] = [
  { address: '0.0.0', row: 0, column: 0, rowSpan: 1, columnSpan: 1, text: '이름' },
  { address: '0.0.1', row: 0, column: 1, rowSpan: 2, columnSpan: 3, text: '' },
  { address: '1.2.0', row: 2, column: 0, rowSpan: 1, columnSpan: 1, text: '첫 줄\r\n둘째 | <내용> & 끝' },
]

test('copies every cell as a Markdown table without toggling the list', async () => {
  const writeText = vi.fn(async () => undefined)
  vi.stubGlobal('navigator', { clipboard: { writeText } })
  const { container } = render(<LayoutPanel vid="15" cells={cells} revision={1} />)
  const details = container.querySelector('details')
  fireEvent.click(screen.getByRole('button', { name: '전체 복사' }))
  await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('전체 칸 목록을 복사했어요.'))
  expect(writeText).toHaveBeenCalledWith([
    '| 칸 | 행·열 (병합) | 원문 |', '| --- | --- | --- |',
    '| 0.0.0 | 0·0 | 이름 |', '| 0.0.1 | 0·1 (2×3) | (빈 칸) |',
    '| 1.2.0 | 2·0 | 첫 줄<br>둘째 &#124; &lt;내용&gt; &amp; 끝 |',
  ].join('\n'))
  expect(details).not.toHaveAttribute('open')
  fireEvent.click(screen.getByText('칸 목록 (3개)'))
  expect(details).toHaveAttribute('open')
  fireEvent.click(screen.getByRole('button', { name: '전체 복사' }))
  expect(details).toHaveAttribute('open')
})

test('shows a recoverable message when clipboard permission is denied', async () => {
  vi.stubGlobal('navigator', { clipboard: { writeText: vi.fn().mockRejectedValue(new DOMException('Denied', 'NotAllowedError')) } })
  render(<LayoutPanel vid="15" cells={cells} revision={1} />)
  fireEvent.click(screen.getByRole('button', { name: '전체 복사' }))
  await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('복사하지 못했어요.'))
  expect(screen.getByRole('status')).not.toHaveTextContent('복사했어요')
})

test('disables copying an empty list', () => {
  render(<LayoutPanel vid="15" cells={[]} revision={1} />)
  expect(screen.getByRole('button', { name: '전체 복사' })).toBeDisabled()
})
