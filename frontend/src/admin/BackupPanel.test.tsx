import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { BackupPanel } from './BackupPanel'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

function choose(file: File) {
  fireEvent.change(screen.getByLabelText('백업 파일로 복원'), { target: { files: [file] } })
}

test('restores a backup and says which notices came back and which were already there', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ restored: ['22369'], skipped: ['22370'] }), { status: 200 })))
  const onRestored = vi.fn()
  render(<BackupPanel onRestored={onRestored} />)

  choose(new File(['zip'], 'yesulin-forms.zip', { type: 'application/zip' }))

  expect(await screen.findByRole('status')).toHaveTextContent('되살린 공고: 22369 · 이미 있어서 건너뜀: 22370')
  expect(onRestored).toHaveBeenCalled()
  const [url, init] = vi.mocked(fetch).mock.calls[0] ?? []
  expect(url).toBe('/api/admin/backup')
  expect(init?.method).toBe('POST')
})

test('shows the server reason when the file is not a backup', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(
    JSON.stringify({ code: 'INVALID_BACKUP', message: '예술in 공고 양식 백업 파일이 아닙니다.' }), { status: 400 })))
  render(<BackupPanel onRestored={vi.fn()} />)

  choose(new File(['x'], 'other.zip', { type: 'application/zip' }))

  expect(await screen.findByRole('alert')).toHaveTextContent('예술in 공고 양식 백업 파일이 아닙니다.')
})
