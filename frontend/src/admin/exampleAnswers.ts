import type { Answers, FormItem } from '../form/types'
import { assertNever } from '../form/types'

/** Operator-only examples. Explicit edits (including empty answers) take precedence in TestPanel. */
export function exampleAnswers(items: readonly FormItem[]): Answers {
  return Object.fromEntries(items.map((item, index) => {
    const example = `예시 입력 ${index + 1}`

    switch (item.type) {
      case 'TEXT':
        return [item.id, [example.slice(0, item.maxLength)]]
      case 'PHONE':
        return [item.id, ['010-0000-0000']]
      case 'SINGLE':
      case 'MULTI':
        return [item.id, item.options.slice(0, 1).map((option) => option.id)]
      case 'ROWS':
        return [item.id, item.columns.map((_, column) => `${example}-${column + 1}`)]
      case 'PHOTO':
        return [item.id, []]
      default:
        return assertNever(item.type)
    }
  }))
}
