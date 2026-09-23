import { parseTemplate } from './template'

export type CellAddress = {
  tableIndex: number
  rowIndex: number
  cellIndex: number
}

export type InputStyle = 'BLANK' | 'TEMPLATE' | 'GUIDE' | 'APPEND' | 'FILLED'

export type FieldCandidate = {
  id: string
  label: string
  kind: 'TEXT' | 'PHOTO'
  address: CellAddress
  currentText: string
  confidence: number
  warning: string
  multiline: boolean
  /** BLANK: empty cell · TEMPLATE: edit the cell's text in place · GUIDE: text is a placeholder · APPEND: written under the label
   *  · FILLED: an answer already in a completed form, edited in place */
  style: InputStyle
  hint: string
  /** Table the field belongs to ("학력"), "" for a plain field. */
  group: string
  row: number
  rowName: string
  column: string
}

const NEWLINE = String.fromCharCode(10)

/** "성별⏎ 남( ) 여( )": the first line is the field's own name, kept in the file but not shown for editing. */
function ownHeading(field: FieldCandidate): string {
  const [first, ...rest] = field.currentText.split(NEWLINE)
  return rest.length > 0 && first.trim() === field.label ? first : ''
}

/** The part of a template the applicant fills: its text without the field's own heading line. */
export function templateBody(field: FieldCandidate): string {
  const heading = ownHeading(field)
  return heading ? field.currentText.slice(heading.length + 1) : field.currentText
}

/**
 * Templates start as the form's own text. A template the parser understands is filled through inputs,
 * so its text stays exact; one it does not is edited by hand, with alignment spaces shortened to fit a phone.
 */
export function initialValue(field: FieldCandidate): string {
  if (field.style === 'FILLED') return field.currentText.trim()
  if (field.style !== 'TEMPLATE') return ''
  const body = templateBody(field)
  return parseTemplate(body) ? body : body.replace(/ {3,}/g, '  ').replace(/^ +/gm, '')
}

/** Where the answer to one part of a template is kept, next to the field's composed value. */
export function answerKey(field: FieldCandidate, partKey: string): string {
  return `${field.id}::${partKey}`
}

/** What is written back to the cell: a template's own heading line is put back in front. */
export function outgoingValue(field: FieldCandidate, value: string): string {
  const heading = field.style === 'TEMPLATE' ? ownHeading(field) : ''
  return heading ? heading + NEWLINE + value : value
}

/** Filled means the applicant wrote something — an untouched template does not count. */
export function isFilled(field: FieldCandidate, value: string | undefined): boolean {
  const text = (value ?? '').trim()
  return text !== '' && text !== initialValue(field).trim()
}

/** What goes into the new file: what was written, plus an earlier answer that was edited or cleared. */
export function isChanged(field: FieldCandidate, value: string | undefined): boolean {
  if (field.style === 'FILLED') return value !== undefined && value.trim() !== initialValue(field)
  return isFilled(field, value)
}

export function placeholder(field: FieldCandidate): string {
  if (field.style === 'GUIDE' && field.currentText.trim()) return field.currentText.replace(/ +/g, ' ').trim()
  return field.hint || `${field.column || field.label} 입력`
}

export function defaultOutputFileName(original: string): string {
  const stem = original.replace(/\.hwp$/i, '')
  return `${stem.endsWith('_완성') ? stem : `${stem}_완성`}.hwp`
}

export type AnalysisResponse = {
  documentId: string
  fileName: string
  expiresAt: string
  tableCount: number
  cellCount: number
  fields: FieldCandidate[]
}

export type ApiError = {
  code: string
  message: string
}

type InputHints = {
  inputMode?: 'tel' | 'email'
  autoComplete?: string
}

export function inputHints(label: string): InputHints {
  if (/연락처|전화|핸드폰|휴대폰|H\.?P/i.test(label)) return { inputMode: 'tel', autoComplete: 'tel' }
  if (/메일|e-?mail/i.test(label)) return { inputMode: 'email', autoComplete: 'email' }
  if (/^(이름|성명)/.test(label)) return { autoComplete: 'name' }
  return {}
}
