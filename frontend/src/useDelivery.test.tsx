import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import type { Completed } from './api'
import { useDelivery } from './useDelivery'
import { shareFile } from './delivery'

vi.mock('./delivery', () => ({ shareFile: vi.fn(async () => 'cancelled'), saveFile: vi.fn(), openMailDraft: vi.fn() }))
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks() })

test('never shares a prefetched old PDF when the same document id gets an edited HWP', async () => {
  // given: old PDF is ready; the edited file has the same job id but its PDF is still loading
  let finishNew: ((response: Response) => void) | undefined
  const nextPdf = new Promise<Response>((resolve) => { finishNew = resolve })
  vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(new Response('old pdf')).mockReturnValueOnce(nextPdf))
  const original: Completed = { documentId: 'same-job', file: new File(['old'], '지원서.hwp'), downloadUrl: '/hwp', pdfUrl: '/pdf' }
  const toast = vi.fn()
  const { result, rerender } = renderHook(({ file }) => useDelivery(file, toast, { pdfFirst: true }), { initialProps: { file: original } })
  await waitFor(() => expect(fetch).toHaveBeenCalledOnce())
  await waitFor(async () => {
    await act(async () => { await result.current.mail() })
    expect(shareFile).toHaveBeenCalledOnce()
  })
  // when
  const edited = { ...original, file: new File(['new'], '지원서.hwp'), directEdited: true }
  rerender({ file: edited })
  await act(async () => { await result.current.mail() })
  // then: old PDF not sent during preparation; the eventual file is from the new PDF
  expect(shareFile).toHaveBeenCalledOnce()
  expect(toast).toHaveBeenCalledWith('PDF를 준비하고 있어요. 잠시 후 다시 눌러주세요')
  await act(async () => { finishNew?.(new Response('new pdf')); await nextPdf })
  await act(async () => { await result.current.mail() })
  expect(shareFile).toHaveBeenCalledTimes(2)
  const latest = vi.mocked(shareFile).mock.calls[1]?.[0]
  expect(await latest?.text()).toBe('new pdf')
})
