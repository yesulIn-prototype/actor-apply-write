/**
 * Turns a form's fill-in text ("취미:      /특기:      / 결혼유무: 결혼(  ) 미혼 (  )") into
 * inputs and choices, and writes the answers back into the same text so the form keeps its layout.
 */

export type Slot = {
  kind: 'slot'
  key: string
  label: string
  /** Unit printed inside the blank, e.g. "cm" in "신장(    cm)". */
  unit: string
  start: number
  end: number
  shape: 'paren' | 'gap' | 'underline'
}

export type ChoiceOption = { label: string; start: number; end: number; shape: 'paren' | 'bare' | 'box' }

export type Choice = {
  kind: 'choice'
  key: string
  label: string
  options: ChoiceOption[]
}

export type Part = Slot | Choice

export type Template = {
  text: string
  /** Parts grouped by the line they came from, so "외국어 1" and "외국어 2" stay apart. */
  lines: Part[][]
}

export type Answers = Record<string, string>

const NEWLINE = String.fromCharCode(10)
const PAREN = /[(（]([^()（）]*)[)）]/g
const UNIT = /^\s*(cm|kg|㎝|㎏|세|살|년|개월)?\s*$/i
const GAP = /[:：]([ \t]{2,})/g
const UNDERLINE = /[_＿]{3,}/g
const SCALE = /상(\s+)중(\s+)하/g
const GENDER = /^(\s*)남(\s*[/,·]?\s*)여\s*$/
const NUMBERED = /^\s*\d+\s*[(（]\s*[)）]\s*(.+)$/
const SEPARATOR = /.*(?:[/,，·]|--|—)/s
const OPTION_MAX = 15
/** "□남  □여", "☐ 있음 ☐ 없음": a box printed before each option. */
const BOX = /[□☐]\s*([^□☐/,，·()（）\s](?:[^□☐/,，·()（）]*[^□☐/,，·()（）\s])?)/g

type Token = { start: number; end: number; type: 'paren' | 'gap' | 'underline'; unit: string }

export function parseTemplate(text: string): Template | null {
  const lines: Part[][] = []
  const rows = splitLines(text)
  const numbered = rows.filter((row) => NUMBERED.test(row.text))

  if (numbered.length >= 2) {
    // "1 (  ) 이번 공연만 참여합니다 / 2 (  ) 단원 지원합니다": pick one line.
    const options = numbered.map((row) => {
      const paren = new RegExp(PAREN.source).exec(row.text)!
      return {
        label: row.text.match(NUMBERED)![1].replace(/\s+/g, ' ').trim(),
        start: row.offset + paren.index,
        end: row.offset + paren.index + paren[0].length,
        shape: 'paren' as const,
      }
    })
    lines.push([{ kind: 'choice', key: 'numbered', label: '', options }])
  }

  rows.forEach((row, lineIndex) => {
    if (numbered.length >= 2 && numbered.includes(row)) return
    const parts = parseLine(row.text, row.offset, lineIndex)
    if (parts.length > 0) lines.push(parts)
  })
  return lines.length > 0 ? { text, lines } : null
}

function parseLine(line: string, offset: number, lineIndex: number): Part[] {
  const gender = GENDER.exec(line)
  if (gender) {
    const male = gender[1].length
    const female = male + 1 + gender[2].length
    return [{
      kind: 'choice',
      key: `${lineIndex}-gender`,
      label: '',
      options: [
        { label: '남', start: offset + male, end: offset + male + 1, shape: 'bare' },
        { label: '여', start: offset + female, end: offset + female + 1, shape: 'bare' },
      ],
    }]
  }

  const tokens = tokensOf(line)
  const boxes = [...line.matchAll(BOX)].filter((box) => box[1].length <= OPTION_MAX)
  const boxParts: { at: number; part: Part }[] = boxes.length < 2 ? [] : [{
    at: boxes[0].index,
    part: {
      kind: 'choice',
      key: `${lineIndex}-box`,
      label: labelBefore(line.slice(0, boxes[0].index)),
      options: boxes.map((box) => ({
        label: box[1], start: offset + box.index, end: offset + box.index + 1, shape: 'box' as const,
      })),
    },
  }]
  const scales = [...line.matchAll(SCALE)]
  const parts: { at: number; part: Part }[] = [...boxParts]
  let previousEnd = 0
  let index = 0
  while (index < tokens.length) {
    const token = tokens[index]
    const before = line.slice(previousEnd, token.start)
    // A run of "word( )" markers is a choice: "결혼( ) 미혼 ( )", "YES ( ) / NO ( )", "Soprano( ), Alto( )".
    const run = choiceRun(line, tokens, index, previousEnd)
    if (run.length >= 2) {
      const [groupLabel] = splitLabel(line.slice(previousEnd, tokens[index].start))
      parts.push({
        at: token.start,
        part: {
          kind: 'choice',
          key: `${lineIndex}-${index}`,
          label: groupLabel,
          options: run.map(({ token: marker, label }) => ({
            label, start: offset + marker.start, end: offset + marker.end, shape: 'paren' as const,
          })),
        },
      })
      previousEnd = run[run.length - 1].token.end
      index += run.length
      continue
    }
    let label = labelBefore(before)
    if (!label) label = labelAfter(line.slice(token.end), tokens[index + 1]?.start ?? line.length, token.end)
    parts.push({
      at: token.start,
      part: {
        kind: 'slot',
        key: `${lineIndex}-${index}`,
        label,
        unit: token.unit,
        start: offset + token.start,
        end: offset + token.end,
        shape: token.type,
      },
    })
    previousEnd = token.end
    index += 1
  }

  for (const scale of scales) {
    // "독해 -- 상  중  하": circle one.
    const start = scale.index
    const middle = start + 1 + scale[1].length
    const low = middle + 1 + scale[2].length
    const before = line.slice(0, start)
    const label = labelBefore(before.slice(Math.max(before.lastIndexOf('/') + 1, lastTokenEnd(tokens, start))))
    parts.push({
      at: start,
      part: {
        kind: 'choice',
        key: `${lineIndex}-scale-${start}`,
        label,
        options: [['상', start], ['중', middle], ['하', low]].map(([text, at]) => ({
          label: text as string, start: offset + (at as number), end: offset + (at as number) + 1, shape: 'bare' as const,
        })),
      },
    })
  }
  return parts.sort((left, right) => left.at - right.at).map(({ part }) => part)
}

function tokensOf(line: string): Token[] {
  const tokens: Token[] = []
  for (const match of line.matchAll(PAREN)) {
    const unit = UNIT.exec(match[1])
    if (unit) tokens.push({ start: match.index, end: match.index + match[0].length, type: 'paren', unit: unit[1] ?? '' })
  }
  for (const match of line.matchAll(GAP)) {
    const start = match.index + 1
    const end = start + match[1].length
    // "외국어 1:      (      )": the parenthesised blank is the input, not the gap before it.
    if (!/^[(（]/.test(line.slice(end))) tokens.push({ start, end, type: 'gap', unit: '' })
  }
  // "지원 배역(중복 가능) :" with nothing after the colon: the answer follows it.
  const trailing = /[:：](\s*)$/.exec(line)
  if (trailing && !tokens.some((token) => token.end > trailing.index)) {
    tokens.push({ start: trailing.index + 1, end: line.length, type: 'gap', unit: '' })
  }
  for (const match of line.matchAll(UNDERLINE)) {
    tokens.push({ start: match.index, end: match.index + match[0].length, type: 'underline', unit: '' })
  }
  return tokens.sort((left, right) => left.start - right.start)
}

function choiceRun(line: string, tokens: Token[], from: number, previousEnd: number) {
  const run: { token: Token; label: string }[] = []
  let cursor = previousEnd
  for (let index = from; index < tokens.length; index++) {
    const token = tokens[index]
    if (token.type !== 'paren' || token.unit) break
    const [, label] = splitLabel(line.slice(cursor, token.start))
    if (!label || label.length > OPTION_MAX) break
    run.push({ token, label })
    cursor = token.end
  }
  return run
}

/** " / 결혼유무: 결혼" → ["결혼유무", "결혼"]; "YES " → ["", "YES"]. */
function splitLabel(raw: string): [string, string] {
  const segment = raw.replace(SEPARATOR, '').trim()
  const colon = segment.search(/[:：]/)
  if (colon < 0) return ['', segment]
  return [segment.slice(0, colon).trim(), segment.slice(colon + 1).trim()]
}

/** "외국어 1: " → "외국어 1", "  / 회화 -- " → "회화": drop the trailing marks first, then anything before a separator. */
function labelBefore(raw: string): string {
  return raw.replace(/[:：\-–—\s]+$/, '').replace(SEPARATOR, '').replace(/\s+/g, ' ').trim()
}

/** "(          ) 초등학교": the blank is named by what follows it. */
function labelAfter(rest: string, nextStart: number, tokenEnd: number): string {
  return rest.slice(0, nextStart - tokenEnd).split(/[/,，·]|--/)[0].replace(/\s+/g, ' ').trim()
}

function lastTokenEnd(tokens: Token[], before: number): number {
  return tokens.filter((token) => token.end <= before).reduce((end, token) => Math.max(end, token.end), 0)
}

function splitLines(text: string) {
  const rows: { text: string; offset: number }[] = []
  let offset = 0
  for (const line of text.split(NEWLINE)) {
    rows.push({ text: line, offset })
    offset += line.length + 1
  }
  return rows
}

/** Writes answers into the original text: "( V )" for a picked box, "■남" for a printed box, "상(V)" for a picked word, values into blanks. */
export function composeTemplate(template: Template, answers: Answers): string {
  const edits: { start: number; end: number; text: string }[] = []
  for (const part of template.lines.flat()) {
    const answer = (answers[part.key] ?? '').trim()
    if (!answer) continue
    if (part.kind === 'slot') {
      const value = answer + part.unit
      const text = part.shape === 'paren' ? `( ${value} )` : part.shape === 'gap' ? ` ${value}  ` : value
      edits.push({ start: part.start, end: part.end, text })
      continue
    }
    const option = part.options[Number(answer)]
    if (!option) continue
    edits.push(option.shape === 'paren'
      ? { start: option.start, end: option.end, text: '( V )' }
      : option.shape === 'box'
        ? { start: option.start, end: option.end, text: '■' }
        : { start: option.end, end: option.end, text: '(V)' })
  }
  let text = template.text
  for (const edit of edits.sort((left, right) => right.start - left.start)) {
    text = text.slice(0, edit.start) + edit.text + text.slice(edit.end)
  }
  return text
}
