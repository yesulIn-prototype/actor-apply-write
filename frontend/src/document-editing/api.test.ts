import { expect, test } from 'vitest'
import { movedChanges, numberRegions, parseEditView } from './api'

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
