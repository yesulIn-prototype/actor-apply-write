import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { OPEN_CHAT_URL } from './home/HomePage'
import { Root } from './Root'

afterEach(() => { cleanup(); vi.unstubAllGlobals(); window.history.replaceState(null, '', '/') })

test.each(['/', '/?doc=7907f91f-de04-418f-a284-75836cbebce5', '/unknown'])('introduces the service at %s instead of offering upload', (path) => {
  window.history.replaceState(null, '', path)
  const fetcher = vi.fn()
  vi.stubGlobal('fetch', fetcher)
  render(<Root />)
  expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('오디션 지원서, 폰에서 바로 쓰고 보내요')
  expect(screen.queryByLabelText('지원서 파일')).not.toBeInTheDocument()
  expect(fetcher).not.toHaveBeenCalled()
})

test('sends visitors to the notice open chat in a new window', () => {
  render(<Root />)
  const join = screen.getByRole('link', { name: /오픈채팅방에서 공고 받기/ })
  expect(join).toHaveAttribute('href', OPEN_CHAT_URL)
  expect(join).toHaveAttribute('target', '_blank')
  expect(join).toHaveAttribute('rel', 'noopener noreferrer')
})
