import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { afterEach, expect, test } from 'vitest'
import { RowsInput } from './RowsInput'
import type { FormItem, PublicForm } from './types'
import { filledAnswers } from './types'

const school: FormItem = {
  id: 'school', label: '학력', help: '', required: false, type: 'ROWS', multiline: false, maxLength: 0,
  options: [], min: 0, max: 0, maxRows: 3, rowLabels: ['고등학교', '대학교', '대학원'],
  columns: [{ id: 'name', label: '학교명' }, { id: 'region', label: '지역' }],
}

function Harness({ onValues }: { readonly onValues: (values: readonly string[]) => void }) {
  const [values, setValues] = useState<readonly string[]>([])

  return <RowsInput item={school} values={values} onChange={(next) => { setValues(next); onValues(next) }} />
}

afterEach(cleanup)

test('줄 라벨 항목은 라벨마다 카드를 보이고 줄을 더하는 버튼은 없다', () => {
  render(<Harness onValues={() => {}} />)

  expect(screen.getByText('고등학교')).toBeTruthy()
  expect(screen.getByText('대학원')).toBeTruthy()
  expect(screen.queryByText(/줄 더하기/)).toBeNull()
})

test('쓴 줄은 제 라벨 자리에 남고, 없음을 누르면 그 줄을 비우고 접는다', () => {
  let last: readonly string[] = []
  render(<Harness onValues={(values) => { last = values }} />)

  fireEvent.change(screen.getByLabelText('학교명', { selector: '#item-school-1-name' }), { target: { value: '시험대' } })
  expect(last).toEqual(['', '', '시험대', '', '', ''])

  fireEvent.change(screen.getByLabelText('학교명', { selector: '#item-school-0-name' }), { target: { value: '시험고' } })
  fireEvent.click(screen.getByRole('button', { name: '고등학교 없음' }))
  expect(last).toEqual(['', '', '시험대', '', '', ''])
  expect(screen.getByText('고등학교 · 없음')).toBeTruthy()
  expect(document.querySelector('#item-school-0-name')).toBeNull()

  fireEvent.click(screen.getByRole('button', { name: '입력하기' }))
  expect(document.querySelector('#item-school-0-name')).not.toBeNull()
})

test('서버로 보낼 때 줄 라벨 항목은 빈 줄도 자리를 지키고, 모두 비면 보내지 않는다', () => {
  const form: PublicForm = {
    vid: '99001', version: 1, title: '합성 지원서', fileName: '', sourceName: 'sample.hwp', items: [school],
    submission: { email: '', subject: '', deadline: '', note: '' }, pdfFirst: false,
  }

  expect(filledAnswers(form, { school: ['', '', ' 시험대 ', '서울', '', ''] }))
    .toEqual({ school: ['', '', '시험대', '서울', '', ''] })
  expect(filledAnswers(form, { school: ['', ' ', '', '', '', ''] })).toEqual({})
})
