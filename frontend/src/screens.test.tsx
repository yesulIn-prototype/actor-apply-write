import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { afterEach, expect, test, vi } from 'vitest'
import type { AnalysisResponse, FieldCandidate } from './document'
import { initialValue, isFilled, outgoingValue } from './document'
import { FillScreen, UploadScreen } from './screens'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

test('counts the completed applications up to the server value', () => {
  const frames: FrameRequestCallback[] = []
  vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
    frames.push(callback)
    return frames.length
  })
  vi.stubGlobal('cancelAnimationFrame', vi.fn())
  vi.stubGlobal('matchMedia', () => ({ matches: false }))

  render(<UploadScreen busy={false} completedCount={12} onFile={() => undefined} />)
  expect(screen.getByText('0개')).toBeInTheDocument()

  act(() => { frames.shift()?.(0) })
  act(() => { frames.shift()?.(450) })
  const midway = Number.parseInt(screen.getByTestId('completed-count').textContent ?? '', 10)
  expect(midway).toBeGreaterThan(0)
  expect(midway).toBeLessThan(12)

  act(() => { frames.shift()?.(900) })
  expect(screen.getByText('12개')).toBeInTheDocument()
})

test('shows the final count without animation when reduced motion is preferred', () => {
  vi.stubGlobal('matchMedia', () => ({ matches: true }))
  render(<UploadScreen busy={false} completedCount={12} onFile={() => undefined} />)
  expect(screen.getByText('12개')).toBeInTheDocument()
})

function field(overrides: Partial<FieldCandidate>): FieldCandidate {
  return {
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
    ...overrides,
  }
}

const hobby = field({
  id: 'text-0-7-1',
  label: '취미/특기/결혼',
  style: 'TEMPLATE',
  currentText: '취미:             /특기:              / 결혼유무: 결혼(  ) 미혼 (  )',
})
const address = field({ id: 'text-0-3-1', label: '주소', style: 'GUIDE', currentText: '(동까지만 적어도 좋음)' })
const career = Array.from({ length: 5 }, (_, index) => ['공연명', '역할'].map((column, offset) => field({
  id: `text-0-${9 + index}-${offset + 1}`,
  label: `${column} ${index + 1}`,
  group: '공연경력',
  row: index + 1,
  rowName: String(index + 1),
  column,
}))).flat()

function Harness({ fields }: { fields: FieldCandidate[] }) {
  const analysis: AnalysisResponse = {
    documentId: 'd', fileName: '지원서.hwp', expiresAt: '', tableCount: 1, cellCount: 1, fields,
  }
  const [values, setValues] = useState<Record<string, string>>(Object.fromEntries(
    fields.filter((item) => item.style === 'TEMPLATE').map((item) => [item.id, initialValue(item)])))
  return (
    <FillScreen
      analysis={analysis}
      values={values}
      photos={{}}
      outputName="지원서_완성.hwp"
      busy={false}
      onValue={(id, value) => setValues((current) => ({ ...current, [id]: value }))}
      onPhoto={() => undefined}
      onOutputName={() => undefined}
      onBack={() => undefined}
      onSubmit={() => undefined}
    />
  )
}

test('turns a template into inputs and chips and previews the text written to the form', () => {
  render(<Harness fields={[hobby, address]} />)

  expect(screen.getByLabelText('주소')).toHaveAttribute('placeholder', '(동까지만 적어도 좋음)')
  expect(screen.getByRole('button', { name: '완성하기' })).toBeDisabled()

  fireEvent.change(screen.getByLabelText('취미'), { target: { value: '등산' } })
  fireEvent.click(screen.getByRole('button', { name: '미혼' }))

  expect(screen.getByRole('button', { name: '미혼' })).toHaveAttribute('aria-pressed', 'true')
  // Testing Library collapses whitespace in the page, so the query uses single spaces.
  expect(screen.getByText('취미: 등산 /특기: / 결혼유무: 결혼( ) 미혼 ( V )')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '완성하기' })).toBeEnabled()

  fireEvent.click(screen.getByRole('button', { name: '미혼' }))
  expect(screen.getByRole('button', { name: '미혼' })).toHaveAttribute('aria-pressed', 'false')
})

test('shows numbered table rows two at a time', () => {
  render(<Harness fields={career} />)

  expect(screen.getByRole('region', { name: '공연경력' })).toBeInTheDocument()
  expect(screen.getByLabelText('공연명 2')).toBeInTheDocument()
  expect(screen.queryByLabelText('공연명 3')).not.toBeInTheDocument()

  fireEvent.click(screen.getByRole('button', { name: '+ 줄 추가' }))

  expect(screen.getByLabelText('공연명 3')).toBeInTheDocument()
})

test('a template that names itself leaves its name out of the inputs and gets it back when saved', () => {
  const gender = field({ label: '성별', style: 'TEMPLATE', currentText: ['성별', ' 남(  ) 여(  )'].join(String.fromCharCode(10)) })

  expect(initialValue(gender)).toBe(' 남(  ) 여(  )')
  expect(outgoingValue(gender, '남( V ) 여(  )')).toBe(['성별', '남( V ) 여(  )'].join(String.fromCharCode(10)))
})

test('an untouched template is not sent', () => {
  expect(isFilled(hobby, initialValue(hobby))).toBe(false)
  expect(isFilled(hobby, `${initialValue(hobby)} `)).toBe(false)
  expect(isFilled(address, '서울 마포구')).toBe(true)
})
