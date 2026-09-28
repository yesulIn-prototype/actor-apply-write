import type { FieldCandidate } from '../src/document'
import { initialValue, inputHints, templateBody } from '../src/document'
import { parseTemplate } from '../src/template'

/** What the applicant is shown for a cell: the kind of input on the phone. */
export type InputType = 'text' | 'multiline' | 'choice' | 'template' | 'photo' | 'phone' | 'email'

export type GoldField = {
  /** "table.row.cell" — the cell the answer is written into. */
  at: string
  /** Words the shown name must contain, separated by spaces ("경력사항 공연명 3"). */
  label: string
  type: InputType | InputType[]
  options?: string[]
  /** A completed form's earlier answer that must be offered for editing. */
  prefill?: boolean
}

export type GoldForm = { file: string; note: string; fields: GoldField[] }

export type Problem = { at: string; kind: 'missing' | 'extra' | 'label' | 'type' | 'options' | 'prefill'; expected: string; actual: string }

export type FormScore = {
  form: string
  gold: number
  predicted: number
  found: number
  labelOk: number
  typeOk: number
  problems: Problem[]
}

export function address(field: FieldCandidate): string {
  const { tableIndex, rowIndex, cellIndex } = field.address
  return `${tableIndex}.${rowIndex}.${cellIndex}`
}

/** The input the app renders for a field — the same decisions as FieldInput/TextField/TemplateField. */
export function shownType(field: FieldCandidate): { type: InputType; options: string[] } {
  if (field.kind === 'PHOTO') return { type: 'photo', options: [] }
  if (field.style === 'TEMPLATE') {
    const template = parseTemplate(templateBody(field))
    if (!template) return { type: 'multiline', options: [] }
    const parts = template.lines.flat()
    if (parts.length === 1 && parts[0].kind === 'choice') {
      return { type: 'choice', options: parts[0].options.map((option) => option.label) }
    }
    return { type: parts.length === 1 ? 'text' : 'template', options: [] }
  }
  if (field.multiline) return { type: 'multiline', options: [] }
  const hints = inputHints(field.label)
  if (hints.inputMode === 'tel') return { type: 'phone', options: [] }
  if (hints.inputMode === 'email') return { type: 'email', options: [] }
  return { type: 'text', options: [] }
}

/** The name the applicant reads next to the input: the table or group heading, then the field's own label. */
export function shownLabel(field: FieldCandidate): string {
  return [field.group, field.label].filter(Boolean).join(' ')
}

function compact(text: string): string {
  return text.toLowerCase().replace(/[\s()（）[\]<>:：/·.,\-_*※]+/g, '')
}

export function labelMatches(expected: string, shown: string): boolean {
  const haystack = compact(shown)
  return expected.split(/\s+/).filter(Boolean).every((word) => {
    const token = compact(word)
    // "1" must not be found inside "11".
    return /^\d+$/.test(token) ? new RegExp(`(?<!\\d)${token}(?!\\d)`).test(haystack) : haystack.includes(token)
  })
}

export function scoreForm(form: string, gold: GoldForm, predicted: FieldCandidate[]): FormScore {
  const byAddress = new Map(predicted.map((field) => [address(field), field]))
  const goldAddresses = new Set(gold.fields.map((field) => field.at))
  const problems: Problem[] = []
  let found = 0
  let labelOk = 0
  let typeOk = 0

  for (const expected of gold.fields) {
    const field = byAddress.get(expected.at)
    if (!field) {
      problems.push({ at: expected.at, kind: 'missing', expected: expected.label, actual: '' })
      continue
    }
    found++
    const label = shownLabel(field)
    if (labelMatches(expected.label, label)) labelOk++
    else problems.push({ at: expected.at, kind: 'label', expected: expected.label, actual: label })

    const shown = shownType(field)
    const allowed = Array.isArray(expected.type) ? expected.type : [expected.type]
    if (allowed.includes(shown.type)) typeOk++
    else problems.push({ at: expected.at, kind: 'type', expected: allowed.join('|'), actual: `${shown.type} (${label})` })

    if (expected.options && shown.type === 'choice') {
      const want = expected.options.map(compact).sort().join('|')
      const got = shown.options.map(compact).sort().join('|')
      if (want !== got) problems.push({ at: expected.at, kind: 'options', expected: expected.options.join(' / '), actual: shown.options.join(' / ') })
    }
    if (expected.prefill && !initialValue(field).trim()) {
      problems.push({ at: expected.at, kind: 'prefill', expected: 'earlier answer prefilled', actual: `empty (${field.style})` })
    }
  }
  for (const field of predicted) {
    if (!goldAddresses.has(address(field))) {
      problems.push({ at: address(field), kind: 'extra', expected: '', actual: `${shownLabel(field)} [${field.currentText.slice(0, 30)}]` })
    }
  }
  return { form, gold: gold.fields.length, predicted: predicted.length, found, labelOk, typeOk, problems }
}
