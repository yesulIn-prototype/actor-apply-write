import { expect, test } from 'vitest'
import type { FieldCandidate } from './document'
import { suggestedOutputName } from './fileName'

function field(id: string, label: string): FieldCandidate {
  return {
    id, label, kind: 'TEXT', address: { tableIndex: 0, rowIndex: 0, cellIndex: 0 }, currentText: '', confidence: 1,
    warning: '', multiline: false, style: 'BLANK', hint: '', group: '', row: 0, rowName: '', column: '',
  }
}

const fields = [field('name', '성명'), field('gender', '성별'), field('phone', '연락처'), field('role', '지원 배역')]

test('fills the naming pattern written in the form file name', () => {
  const values = { name: '홍길동', gender: '남( V ) 여( )', phone: '010-1234-5678', role: '햄릿' }

  expect(suggestedOutputName('tf_(이름)_(성별)_(핸드폰뒷번호4자리).hwp', fields, values)).toBe('tf_홍길동_남_5678.hwp')
  expect(suggestedOutputName('2026_이름_성별.hwp', fields, { ...values, gender: '□남   ■여' })).toBe('2026_홍길동_여.hwp')
  expect(suggestedOutputName('하츄핑_지원배역_지원자이름_휴대폰 뒤 4자리.hwp', fields, values)).toBe('하츄핑_햄릿_홍길동_5678.hwp')
})

test('keeps a placeholder until it is answered', () => {
  expect(suggestedOutputName('2026_이름_성별.hwp', fields, { name: '홍길동' })).toBe('2026_홍길동_성별.hwp')
})

test('falls back to the form name when it has no pattern', () => {
  expect(suggestedOutputName('한강대_오디션 지원서.hwp', fields, { name: '홍길동' })).toBe('한강대_오디션 지원서_완성.hwp')
})
