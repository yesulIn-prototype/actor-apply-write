import { expect, test } from 'vitest'
import { labelMatches } from './score'

test('matches a shown name by its words, numbers as whole numbers', () => {
  expect(labelMatches('경력사항 공연명 3', '경력사항 공연명 3')).toBe(true)
  expect(labelMatches('희망 근무 타임 1순위', '희망 근무 타임 희망 근무 타임 1순위')).toBe(true)
  expect(labelMatches('SNS', 'SNS 주소')).toBe(true)
  expect(labelMatches('경력사항 공연명 1', '경력사항 공연명 11')).toBe(false)
  expect(labelMatches('연락처 핸드폰', '연락처 1')).toBe(false)
})
