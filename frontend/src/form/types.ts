/** A notice form as the operator defined it, minus everything about the document. */
export type ItemType = 'TEXT' | 'PHONE' | 'SINGLE' | 'MULTI' | 'PHOTO'

export type FormOption = { readonly id: string; readonly label: string }

export type FormItem = {
  readonly id: string
  readonly label: string
  readonly help: string
  readonly required: boolean
  readonly type: ItemType
  /** TEXT only. */
  readonly multiline: boolean
  /** TEXT only. */
  readonly maxLength: number
  /** SINGLE and MULTI only. */
  readonly options: readonly FormOption[]
  /** MULTI only: fewest picks once any is picked; 0 for none. */
  readonly min: number
  /** MULTI only: most picks, 0 for no limit. */
  readonly max: number
}

export type PublicForm = {
  readonly vid: string
  readonly version: number
  readonly title: string
  readonly items: readonly FormItem[]
}

/** By item id: one text, or the picked option ids. */
export type Answers = Readonly<Record<string, readonly string[]>>

export type Photos = Readonly<Record<string, File | undefined>>

export function assertNever(value: never): never {
  throw new Error(`unexpected ${String(value)}`)
}

/** Whether an item has an answer: text typed, an option picked, a photo chosen. */
export function answered(item: FormItem, answers: Answers, photos: Photos): boolean {
  switch (item.type) {
    case 'PHOTO':
      return photos[item.id] !== undefined
    case 'TEXT':
    case 'PHONE':
    case 'SINGLE':
    case 'MULTI':
      return (answers[item.id] ?? []).some((value) => value.trim() !== '')
    default:
      return assertNever(item.type)
  }
}

/** Required items still empty, in screen order. */
export function missing(form: PublicForm, answers: Answers, photos: Photos): readonly FormItem[] {
  return form.items.filter((item) => item.required && !answered(item, answers, photos))
}
