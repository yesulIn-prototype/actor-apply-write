import { expect, test } from 'vitest'
import { movedChanges, numberRegions, parseEditView } from './api'

test('numbers unsorted boxes by page, vertical position, horizontal position and id', () => {
  // Given unsorted split regions, tied coordinates and a region with no boxes.
  const box = (page: number, y: number, x: number) => ({ page, x, y, width: 20, height: 20 })

  const input = [
    { id: 'split', text: '', boxes: [box(4, 1, 1), box(2, 10, 20)] },
    { id: 'b', text: '', boxes: [box(2, 10, 10)] },
    { id: 'a', text: '', boxes: [box(2, 10, 10)] },
    { id: 'top', text: '', boxes: [box(2, 5, 90)] },
    { id: 'next', text: '', boxes: [box(4, 1, 1)] },
    { id: 'empty', text: '', boxes: [] },
  ]

  // When visible regions are numbered.
  const result = numberRegions(input)

  // Then each page restarts at one and empty regions are omitted.
  expect(result.map(({ id, number }) => ({ id, number }))).toEqual([
    { id: 'top', number: '2-1' },
    { id: 'a', number: '2-2' },
    { id: 'b', number: '2-3' },
    { id: 'split', number: '2-4' },
    { id: 'next', number: '4-1' },
  ])
})

test('preserves frozen source regions and their box order', () => {
  // Given immutable boxes whose first entry is not their earliest location.
  const boxes = Object.freeze([
    { page: 3, x: 10, y: 20, width: 20, height: 20 },
    { page: 1, x: 10, y: 10, width: 20, height: 20 },
  ])

  const source = Object.freeze({ id: 'split', text: '본문', boxes })

  // When numbering a frozen input array.
  const result = numberRegions(Object.freeze([source]))

  // Then numbering does not change the source or replace its box array.
  expect(result[0]).toEqual({ ...source, number: '1-1' })
  expect(result[0]?.boxes).toBe(boxes)
  expect(boxes.map(({ page }) => page)).toEqual([3, 1])
  expect(numberRegions([])).toEqual([])
})

test('numbers by visual reading order and keeps a split region one target', () => {
  const regions = numberRegions([
    { id: 'c:0.1.1', text: '본문', boxes: [{ page: 2, x: 10, y: 40, width: 20, height: 20 }, { page: 3, x: 10, y: 20, width: 20, height: 20 }] },
    { id: 'p:0.0', text: '제목', boxes: [{ page: 1, x: 10, y: 10, width: 20, height: 20 }] },
  ])
  expect(regions.map((region) => region.number)).toEqual(['1-1', '2-1'])
  expect(regions[1]?.boxes).toHaveLength(2)
})

test('rejects ambiguous moves rather than deleting the first repeated occurrence silently', () => {
  const source = { id: 'c:0.0.0', number: '1-1', text: '같은 말\n같은 말', boxes: [] }
  const target = { id: 'c:0.1.0', number: '1-2', text: '', boxes: [] }
  expect(() => movedChanges(source, target, '같은 말')).toThrow('한 번만 나오는')
  expect(() => movedChanges(source, source, source.text)).toThrow('서로 다른')
})

test('rejects malformed editing metadata at the API boundary', () => {
  expect(() => parseEditView({ revision: 'r', pages: [], regions: [{ id: 'c', text: 't', boxes: [{ x: 'bad' }] }], limitation: '' })).toThrow('편집 위치')
})
