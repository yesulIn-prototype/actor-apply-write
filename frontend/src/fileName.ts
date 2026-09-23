import type { FieldCandidate } from './document'
import { defaultOutputFileName } from './document'

type Answer = { pattern: RegExp; value: (fields: FieldCandidate[], values: Record<string, string>) => string }

/** Placeholders a company writes into the form's own file name: "tf_(이름)_(성별)_(핸드폰뒷번호4자리).hwp". */
const ANSWERS: Answer[] = [
  { pattern: /[(（]?\s*(?:이름|성명|지원자\s*이름|지원자명)\s*[)）]?/, value: (fields, values) => answer(fields, values, /^(이름|성명)/) },
  { pattern: /[(（]?\s*성별\s*[)）]?/, value: (fields, values) => gender(answer(fields, values, /^성별/)) },
  {
    pattern: /[(（]?\s*(?:핸드폰|휴대폰|휴대전화|전화|연락처)\s*(?:번호)?\s*(?:뒷\s*번호|뒷자리|뒤)?\s*4\s*자리\s*[)）]?/,
    value: (fields, values) => (answer(fields, values, /연락처|휴대|핸드폰|전화|mobile|h\.?p/i).replace(/\D/g, '').match(/(\d{4})$/)?.[1] ?? ''),
  },
  { pattern: /[(（]?\s*(?:지원\s*)?배역\s*[)）]?/, value: (fields, values) => answer(fields, values, /배역/) },
]

/**
 * The name the finished file gets unless the applicant types their own: the company's naming pattern
 * filled with the answers so far ("tf_홍길동_남_5678.hwp"), or "<form name>_완성.hwp" when there is none.
 */
export function suggestedOutputName(original: string, fields: FieldCandidate[], values: Record<string, string>): string {
  const stem = original.replace(/\.hwpx?$/i, '')
  const parts = stem.split('_')
  let found = false
  const filled = parts.map((part) => {
    for (const { pattern, value } of ANSWERS) {
      if (!new RegExp(`^${pattern.source}$`).test(part.trim())) continue
      found = true
      return value(fields, values).replace(/[\\/:*?"<>|]/g, '').trim() || part
    }
    return part
  })
  return found ? `${filled.join('_')}.hwp` : defaultOutputFileName(original)
}

function answer(fields: FieldCandidate[], values: Record<string, string>, label: RegExp): string {
  const field = fields.find((candidate) => candidate.kind === 'TEXT' && label.test(candidate.label.replace(/\s+/g, '')))
  return field ? (values[field.id] ?? '').split('\n')[0].trim() : ''
}

/** "남", "여자", or a template with the pick marked: "■남 □여", "남( V ) 여( )", "남  /  여(V)". */
function gender(value: string): string {
  const picked = value.match(/■\s*(남|여)|(남|여)\s*[(（]\s*[Vv✓]\s*[)）]/)
  if (picked) return picked[1] ?? picked[2]
  const plain = value.match(/^(남|여)자?$/)
  return plain ? plain[1] : ''
}
