import type { FormItem } from './types'

/** The basic item ids of design/form-definition-guide.md, in the order every notice shows them. */
const BASIC_ORDER = [
  'name', 'birth', 'phone', 'email', 'gender', 'area', 'height', 'weight', 'school', 'skill',
  'role', 'current', 'unavailable', 'more', 'intro', 'career', 'photoMain', 'photo1', 'photo2',
]

export type ArrangedItems = {
  readonly required: readonly FormItem[]
  readonly optional: readonly FormItem[]
  readonly photos: readonly FormItem[]
}

/**
 * The screen order only; the document and file name never depend on it. Required items come first,
 * optional ones next and photos last, so every notice reads the same way. Inside each group basic ids
 * follow BASIC_ORDER, and any other id stays right after the item it followed in the definition.
 */
export function arrangeItems(items: readonly FormItem[]): ArrangedItems {
  const ranks = new Map<string, number>()
  let previous = -1
  items.forEach((each) => {
    const basic = BASIC_ORDER.indexOf(each.id)

    if (basic >= 0) previous = basic
    ranks.set(each.id, previous)
  })
  const sorted = [...items].sort((a, b) => (ranks.get(a.id) ?? 0) - (ranks.get(b.id) ?? 0))
  const fields = sorted.filter((each) => each.type !== 'PHOTO')

  return {
    required: fields.filter((each) => each.required),
    optional: fields.filter((each) => !each.required),
    photos: sorted.filter((each) => each.type === 'PHOTO'),
  }
}
