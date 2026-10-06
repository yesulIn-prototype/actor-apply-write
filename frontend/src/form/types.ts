/** A notice form as the operator defined it, minus everything about the document. */
export type ItemType = 'TEXT' | 'PHONE' | 'SINGLE' | 'MULTI' | 'PHOTO' | 'ROWS'

/** `output`: what the document (and the file name) gets instead of the label, when the operator set one. */
export type FormOption = { readonly id: string; readonly label: string; readonly output?: string }

/** One column of a ROWS item: the applicant writes one line of it per row. */
export type FormColumn = { readonly id: string; readonly label: string }

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
  /** ROWS only: what each row asks, in order. */
  readonly columns: readonly FormColumn[]
  /** ROWS only: most rows the applicant may write. */
  readonly maxRows: number
}

/** How the notice takes applications; empty strings for what the operator did not give. */
export type Submission = {
  readonly email: string
  /** Template like the file name's: "꼬마박사장영실_{role}_{name}". */
  readonly subject: string
  /** "2026-10-07". */
  readonly deadline: string
  readonly note: string
}

export type PublicForm = {
  readonly vid: string
  readonly version: number
  readonly title: string
  /** The operator's file name template ("{name}_지원서"), "" for none. */
  readonly fileName: string
  /** The form's own file name, for the default name. */
  readonly sourceName: string
  readonly items: readonly FormItem[]
  readonly submission: Submission
  /** A standard-form notice: applicants send a profile, so the PDF is sent and saved first. */
  readonly pdfFirst: boolean
}

/**
 * By item id: one text, or the picked option ids. A ROWS item's values go row by row, one per column
 * (작품, 역할, 작품, 역할, …).
 */
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
    case 'ROWS':
      return (answers[item.id] ?? []).some((value) => value.trim() !== '')
    default:
      return assertNever(item.type)
  }
}

/** A ROWS answer split into its rows, each with a value for every column. */
export function rowsOf(item: FormItem, values: readonly string[]): readonly (readonly string[])[] {
  const width = item.columns.length
  const rows: string[][] = []

  for (let start = 0; start < values.length; start += width) {
    rows.push(Array.from({ length: width }, (_, column) => values[start + column] ?? ''))
  }

  return rows
}

/** What the server gets: trimmed values, blank ones (and a ROWS item's blank rows) left out. */
export function filledAnswers(form: PublicForm, answers: Answers) {
  const filled: Record<string, readonly string[]> = {}

  for (const item of form.items) {
    const values = answers[item.id] ?? []

    const kept = item.type === 'ROWS'
      ? rowsOf(item, values).map((row) => row.map((value) => value.trim())).filter((row) => row.some((value) => value !== '')).flat()
      : values.map((value) => value.trim()).filter((value) => value !== '')

    if (kept.length > 0) filled[item.id] = kept
  }

  return filled
}

/** Required items still empty, in screen order. */
export function missing(form: PublicForm, answers: Answers, photos: Photos): readonly FormItem[] {
  return form.items.filter((item) => item.required && !answered(item, answers, photos))
}
