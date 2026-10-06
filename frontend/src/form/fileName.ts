import type { Answers, FormItem, PublicForm } from './types'

const PLACEHOLDER = /\{([A-Za-z][A-Za-z0-9_]{0,39})\}/g

const FORBIDDEN = /[\\/:*?"<>|]/g

/**
 * The name offered for the finished file until the applicant types their own: the operator's template
 * filled with the answers so far ("홍길동_33_30대 여_010-1234-5678.hwp"), the same way the server fills it,
 * or "<form name>_완성.hwp" when there is no template.
 */
export function suggestedFileName(form: PublicForm, answers: Answers): string {
  const filled = filledTemplate(form, answers, form.fileName).replace(FORBIDDEN, '').trim()

  if (filled && filled !== form.fileName.replace(PLACEHOLDER, '').trim()) return `${filled}.hwp`
  const stem = form.sourceName.replace(/\.hwpx?$/i, '') || form.title || '지원서'

  return `${stem.endsWith('_완성') ? stem : `${stem}_완성`}.hwp`
}

/** An operator's template ("{name}_{role}") with the answers so far; several picks join with "_". */
export function filledTemplate(form: PublicForm, answers: Answers, template: string): string {
  const byId = new Map(form.items.map((item) => [item.id, item]))

  return template.replace(PLACEHOLDER, (whole, id: string) => {
    const item = byId.get(id)

    return item ? written(item, answers[id] ?? []) : whole
  })
}

/** An answer as the document gets it: picked options by their document text, phone numbers formatted. */
function written(item: FormItem, values: readonly string[]): string {
  const given = values.flatMap((value) => {
    const trimmed = value.trim()

    return trimmed ? [trimmed] : []
  })

  switch (item.type) {
    case 'SINGLE':
    case 'MULTI':
      return given
        .map((id) => item.options.find((option) => option.id === id))
        .map((option) => option?.output || option?.label || '')
        .join('_')
    case 'PHONE':
      return given[0] ? phone(given[0]) : ''
    case 'TEXT':
    case 'PHOTO':
      return given[0]?.replace(/\s*\n\s*/g, ' ') ?? ''
    case 'ROWS':
      // The server refuses a rows item in the file name template.
      return ''
  }
}

function phone(raw: string): string {
  const digits = raw.replace(/\D/g, '')

  if (!digits.startsWith('0') || digits.length < 9 || digits.length > 11) return raw
  const area = digits.startsWith('02') ? 2 : 3
  const last = digits.length - 4

  return `${digits.slice(0, area)}-${digits.slice(area, last)}-${digits.slice(last)}`
}
