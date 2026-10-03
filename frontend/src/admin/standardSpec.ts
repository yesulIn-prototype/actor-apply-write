/**
 * A standard-form notice's settings ("base": "standard-v1", see design/form-definition-guide.md) as the operator's
 * screen edits them, and back. Whatever the screen has no field for (help text of other items, custom question
 * ids, the order of questions) is carried through, so settings an AI wrote survive being edited here.
 */
export type CustomType = 'text' | 'yesno' | 'single' | 'multi'

/** One added question: from the catalog, or written by the operator. Options are one per line. */
export type Extra =
  | { readonly kind: 'catalog'; readonly key: string; readonly required: boolean; readonly options: string }
  | {
    readonly kind: 'custom'
    readonly id: string
    readonly label: string
    readonly type: CustomType
    readonly required: boolean
    readonly options: string
  }

export type RoleMode = 'list' | 'text' | 'none'

export type StandardModel = {
  readonly title: string
  readonly fileName: string
  readonly roleMode: RoleMode
  /** One role per line. */
  readonly roles: string
  /** Most roles one applicant picks; 0 for no limit. */
  readonly pickRoles: number
  readonly askCurrent: boolean
  readonly askUnavailable: boolean
  readonly extras: readonly Extra[]
  /** By item id; the screen edits photoMain and career. */
  readonly help: Readonly<Record<string, string>>
  readonly email: string
  readonly subject: string
  readonly deadline: string
  readonly note: string
}

export const BASE = 'standard-v1'

export const EMPTY: StandardModel = {
  title: '',
  fileName: '{name}_지원서',
  roleMode: 'text',
  roles: '',
  pickRoles: 1,
  askCurrent: true,
  askUnavailable: true,
  extras: [],
  help: {},
  email: '',
  subject: '',
  deadline: '',
  note: '',
}

type Json = Record<string, unknown>

export function lines(text: string): string[] {
  return text.split('\n').map((line) => line.trim()).filter(Boolean)
}

/** The next free id for a written question: q1, q2, … */
export function nextCustomId(extras: readonly Extra[]): string {
  const taken = new Set(extras.flatMap((extra) => (extra.kind === 'custom' ? [extra.id] : [])))
  let number = 1
  while (taken.has(`q${number}`)) number += 1
  return `q${number}`
}

/** The settings as JSON for the server; empty values are left out. */
export function toSpec(model: StandardModel): string {
  const spec: Json = { base: BASE, title: model.title.trim() }
  if (model.fileName.trim()) spec.fileName = model.fileName.trim()
  if (model.roleMode === 'list') {
    spec.roles = lines(model.roles)
    if (model.pickRoles !== 1) spec.pickRoles = model.pickRoles
  }
  const drop = [
    ...(model.roleMode === 'none' ? ['role'] : []),
    ...(model.askCurrent ? [] : ['current']),
    ...(model.askUnavailable ? [] : ['unavailable']),
  ]
  if (drop.length > 0) spec.drop = drop
  if (model.extras.length > 0) spec.extras = model.extras.map(extraJson)
  const help = Object.fromEntries(Object.entries(model.help).map(([id, text]) => [id, text.trim()]).filter(([, text]) => text))
  if (Object.keys(help).length > 0) spec.help = help
  const submission = Object.fromEntries(
    (['email', 'subject', 'deadline', 'note'] as const).map((key) => [key, model[key].trim()]).filter(([, value]) => value),
  )
  if (Object.keys(submission).length > 0) spec.submission = submission
  return JSON.stringify(spec, null, 2)
}

function extraJson(extra: Extra): unknown {
  if (extra.kind === 'catalog') {
    const options = lines(extra.options)
    if (!extra.required && options.length === 0) return extra.key
    return {
      use: extra.key,
      ...(options.length > 0 ? { options } : {}),
      ...(extra.required ? { required: true } : {}),
    }
  }
  const custom: Json = { id: extra.id, label: extra.label.trim(), type: extra.type }
  if (extra.type === 'single' || extra.type === 'multi') custom.options = lines(extra.options)
  return { custom, ...(extra.required ? { required: true } : {}) }
}

/** Settings text back into the screen's fields; undefined when it is not standard-form settings. */
export function fromSpec(text: string): StandardModel | undefined {
  let spec: unknown
  try {
    spec = JSON.parse(text)
  } catch {
    return undefined
  }
  if (!isObject(spec) || spec.base === undefined) return undefined
  const roles = strings(spec.roles)
  const drop = strings(spec.drop)
  const submission = isObject(spec.submission) ? spec.submission : {}
  return {
    title: text_(spec.title),
    fileName: text_(spec.fileName),
    roleMode: roles.length > 0 ? 'list' : drop.includes('role') ? 'none' : 'text',
    roles: roles.join('\n'),
    pickRoles: typeof spec.pickRoles === 'number' ? spec.pickRoles : 1,
    askCurrent: !drop.includes('current'),
    askUnavailable: !drop.includes('unavailable'),
    extras: Array.isArray(spec.extras) ? spec.extras.flatMap(extraFrom) : [],
    help: isObject(spec.help) ? Object.fromEntries(Object.entries(spec.help).map(([id, value]) => [id, text_(value)])) : {},
    email: text_(submission.email),
    subject: text_(submission.subject),
    deadline: text_(submission.deadline),
    note: text_(submission.note),
  }
}

function extraFrom(entry: unknown): Extra[] {
  if (typeof entry === 'string') return [{ kind: 'catalog', key: entry, required: false, options: '' }]
  if (!isObject(entry)) return []
  const required = entry.required === true
  if (isObject(entry.custom)) {
    const type = entry.custom.type
    return [{
      kind: 'custom',
      id: text_(entry.custom.id),
      label: text_(entry.custom.label),
      type: type === 'yesno' || type === 'single' || type === 'multi' ? type : 'text',
      required,
      options: strings(entry.custom.options).join('\n'),
    }]
  }
  return [{ kind: 'catalog', key: text_(entry.use), required, options: strings(entry.options).join('\n') }]
}

function isObject(value: unknown): value is Json {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function strings(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}

function text_(value: unknown): string {
  return typeof value === 'string' ? value : ''
}
