import { expect, test } from 'vitest'
import { EMPTY, fromSpec, nextCustomId, toSpec } from './standardSpec'

const WRITTEN = {
  base: 'standard-v1',
  title: '어린이 과학뮤지컬 <꼬마박사 장영실> 배우 지원서',
  fileName: '꼬마박사장영실_{role}_{name}',
  roles: ['장영실 (남)', '아리 (여)'],
  drop: ['current'],
  extras: ['video', { custom: { id: 'science', label: '과학 실험 공연 경험', type: 'yesno' }, required: true },
    { use: 'auditionDates', options: ['10월 9일', '10월 10일'] }],
  help: { photoMain: '최근 6개월 이내 사진', area: '현재 거주지역' },
  submission: { email: 'audition@example.com', subject: '꼬마박사장영실_{role}_{name}', deadline: '2026-10-15' },
}

test('settings written by hand (or by an AI) come back unchanged after opening them on the screen', () => {
  const model = fromSpec(JSON.stringify(WRITTEN))

  expect(model).toBeDefined()
  expect(JSON.parse(toSpec(model!))).toEqual(WRITTEN)
})

test('reads the screen fields from the settings', () => {
  const model = fromSpec(JSON.stringify(WRITTEN))!

  expect(model.roleMode).toBe('list')
  expect(model.roles).toBe('장영실 (남)\n아리 (여)')
  expect(model.askCurrent).toBe(false)
  expect(model.askUnavailable).toBe(true)
  expect(model.extras.map((extra) => (extra.kind === 'catalog' ? extra.key : extra.id))).toEqual(['video', 'science', 'auditionDates'])
  expect(model.email).toBe('audition@example.com')
})

test('leaves out what the operator left empty and drops rows the notice does not ask', () => {
  const spec = JSON.parse(toSpec({ ...EMPTY, title: ' 별빛 정원 ', roleMode: 'none', askUnavailable: false, email: ' a@example.com ' }))

  expect(spec).toEqual({
    base: 'standard-v1',
    title: '별빛 정원',
    fileName: '{name}_지원서',
    drop: ['role', 'unavailable'],
    submission: { email: 'a@example.com' },
  })
})

test('is not standard-form settings without a base, or when the text is not JSON', () => {
  expect(fromSpec('{"title": "x", "items": [], "outputs": []}')).toBeUndefined()
  expect(fromSpec('{ not json')).toBeUndefined()
})

test('numbers written questions after the ones already there', () => {
  expect(nextCustomId([])).toBe('q1')
  expect(nextCustomId([{ kind: 'custom', id: 'q1', label: '', type: 'text', required: false, options: '' }])).toBe('q2')
})
