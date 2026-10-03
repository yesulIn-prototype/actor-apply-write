import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { Root } from './Root'

afterEach(() => { cleanup(); vi.unstubAllGlobals(); window.history.replaceState(null, '', '/') })

test.each(['/', '/?doc=7907f91f-de04-418f-a284-75836cbebce5', '/unknown'])('requires a notice link at %s instead of offering upload', (path) => {
  window.history.replaceState(null, '', path)
  const fetcher = vi.fn()
  vi.stubGlobal('fetch', fetcher)
  render(<Root />)
  expect(screen.getByRole('heading', { name: '공고별 지원 링크를 열어주세요' })).toBeInTheDocument()
  expect(screen.queryByLabelText('지원서 파일')).not.toBeInTheDocument()
  expect(fetcher).not.toHaveBeenCalled()
})
