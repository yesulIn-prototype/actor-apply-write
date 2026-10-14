import { expect, test } from 'vitest'
import { arrangeItems } from './itemOrder'
import type { FormItem, ItemType } from './types'

function item(id: string, required = false, type: ItemType = 'TEXT'): FormItem {
  return { id, label: id, help: '', required, type, multiline: false, maxLength: 0, options: [], min: 0, max: 0, columns: [], maxRows: 0 }
}

const ids = (items: readonly FormItem[]) => items.map((each) => each.id)

test('puts required items first, optional next and every photo last, each in the basic item order', () => {
  // given: a standard form written in a slightly different order
  const items = [
    item('photoMain', true, 'PHOTO'), item('career', false, 'ROWS'), item('intro'), item('phone', true),
    item('name', true), item('height'), item('gender', true, 'SINGLE'), item('role', false, 'SINGLE'),
    item('photo2', false, 'PHOTO'), item('photo1', false, 'PHOTO'), item('birth', true), item('email'),
  ]

  // when
  const arranged = arrangeItems(items)
  // then
  expect(ids(arranged.required)).toEqual(['name', 'birth', 'phone', 'gender'])
  expect(ids(arranged.optional)).toEqual(['email', 'height', 'role', 'intro', 'career'])
  expect(ids(arranged.photos)).toEqual(['photoMain', 'photo1', 'photo2'])
})

test('keeps an item without a basic id next to the item it followed in the definition', () => {
  // given: a notice's own form with its own ids
  const items = [
    item('nameKo', true), item('nameEn'), item('gender', true, 'SINGLE'), item('birth', true), item('age'),
    item('phone', true), item('instagram'), item('unavailable'), item('longTerm', false, 'SINGLE'), item('intro'),
  ]

  // when
  const arranged = arrangeItems(items)
  // then
  expect(ids(arranged.required)).toEqual(['nameKo', 'birth', 'phone', 'gender'])
  expect(ids(arranged.optional)).toEqual(['nameEn', 'age', 'instagram', 'unavailable', 'longTerm', 'intro'])
  expect(arranged.photos).toEqual([])
})
