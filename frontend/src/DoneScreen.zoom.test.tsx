import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { DoneScreen } from './DoneScreen'
import { ItemInput } from './form/ItemInput'
import type { FormItem } from './form/types'

const item: FormItem = {
  id: 'name', label: '이름', type: 'TEXT', help: '', required: false, multiline: false,
  maxLength: 30, options: [], min: 0, max: 0, columns: [], maxRows: 0,
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

test('opens an enlarged page and edits its field from the zoomed preview', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
    pages: [{ number: 1, width: 800, height: 1100 }],
    hotspots: [{ fieldId: 'name', page: 1, x: 100, y: 100, width: 80, height: 30 }],
  }), { status: 200 })))
  render(<DoneScreen
    completed={{ documentId: 'b3bcb48a-72b8-4dd9-9277-8cdce606233c', file: new File(['hwp'], '지원서_완성.hwp'), downloadUrl: '', pdfUrl: '' }}
    labels={new Map([[item.id, item.label]])}
    renderEditor={() => <ItemInput
      item={item}
      values={['검증배우']}
      photos={{}}
      onValues={() => undefined}
      onPhoto={() => undefined}
    />}
    pdfBusy={false}
    onEditOpen={() => undefined}
    onEditCancel={() => undefined}
    onApply={async () => true}
    onBack={() => undefined}
    onMail={() => undefined}
    onSave={() => undefined}
    onSavePdf={() => undefined}
  />)

  const zoomButton = await screen.findByRole('button', { name: '1쪽 미리보기 확대' })
  fireEvent.click(zoomButton)
  const zoom = screen.getByRole('dialog', { name: '1쪽 미리보기 확대' })
  expect(zoom).toBeInTheDocument()
  expect(within(zoom).getByRole('button', { name: '확대 닫기' })).toHaveFocus()
  expect(screen.getByText('150%')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: '확대하기' }))
  expect(screen.getByText('200%')).toBeInTheDocument()

  fireEvent.keyDown(zoom, { key: 'Escape' })
  expect(screen.queryByRole('dialog', { name: '1쪽 미리보기 확대' })).not.toBeInTheDocument()
  expect(zoomButton).toHaveFocus()
  fireEvent.click(zoomButton)

  fireEvent.click(within(screen.getByRole('dialog', { name: '1쪽 미리보기 확대' })).getByRole('button', { name: '이름 수정' }))
  expect(screen.getByRole('dialog', { name: '이름 수정' })).toBeInTheDocument()
  expect(screen.queryByRole('dialog', { name: '1쪽 미리보기 확대' })).not.toBeInTheDocument()
})
