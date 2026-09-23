import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import type { AnalysisResponse } from './document'
import { DoneScreen } from './DoneScreen'

const analysis: AnalysisResponse = {
  documentId: 'b3bcb48a-72b8-4dd9-9277-8cdce606233c',
  fileName: '지원서.hwp',
  expiresAt: '',
  tableCount: 1,
  cellCount: 2,
  fields: [{
    id: 'text-0-0-1',
    label: '이름',
    kind: 'TEXT',
    address: { tableIndex: 0, rowIndex: 0, cellIndex: 1 },
    currentText: '',
    confidence: 1,
    warning: '',
    multiline: false,
    style: 'BLANK',
    hint: '',
    group: '',
    row: 0,
    rowName: '',
    column: '',
  }],
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

test('opens an enlarged page and edits its field from the zoomed preview', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
    pages: [{ number: 1, width: 800, height: 1100 }],
    hotspots: [{ fieldId: 'text-0-0-1', page: 1, x: 100, y: 100, width: 80, height: 30 }],
  }), { status: 200 })))
  render(<DoneScreen
    analysis={analysis}
    completed={{ documentId: analysis.documentId, file: new File(['hwp'], '지원서_완성.hwp'), downloadUrl: '', pdfUrl: '' }}
    values={{ 'text-0-0-1': '검증배우' }}
    photos={{}}
    pdfBusy={false}
    onValue={() => undefined}
    onPhoto={() => undefined}
    onRestore={() => undefined}
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
