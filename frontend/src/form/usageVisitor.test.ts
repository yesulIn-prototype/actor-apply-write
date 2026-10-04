import { afterEach, expect, test, vi } from 'vitest'
import { usageVisitor } from './usageVisitor'

afterEach(() => vi.restoreAllMocks())

test('keeps a random browser identifier without using applicant information', () => {
  // given / when
  const first = usageVisitor()
  // then
  expect(first).toMatch(/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/)
  expect(usageVisitor()).toBe(first)
  expect(localStorage.getItem('yesulin.usageVisitor')).toBe(first)
})

test('can still build files when browser storage is unavailable', () => {
  // given
  vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new DOMException('disabled') })
  // when
  const visitor = usageVisitor()
  // then
  expect(visitor).toMatch(/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/)
})
