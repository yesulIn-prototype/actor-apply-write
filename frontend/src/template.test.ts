import { expect, test } from 'vitest'
import type { Choice, Part, Slot } from './template'
import { composeTemplate, parseTemplate } from './template'

const NL = String.fromCharCode(10)

// Template texts copied from real forms (ESTC, 하츄핑, 생활연기, 공부의신).
const HOBBY = '취미:             /특기:              일반사회 소속단체:                  / 결혼유무: 결혼(  ) 미혼 (  )'
const LANGUAGE = [
  '외국어 1: (                      )  독해 -- 상  중  하   /   회화 -- 상  중  하   ',
  '외국어 2: (                      )  독해 -- 상  중  하   /   회화 -- 상  중  하',
].join(NL)
const PARTICIPATION = ['1 (     )  이 번 공연만 참여합니다', '2 (   ) 지속적 Shakespeare 명작공연을 위한 ESTC 단원 지원합니다'].join(NL)

function describe(parts: Part[]) {
  return parts.map((part) => part.kind === 'slot'
    ? `slot:${part.label}${part.unit ? `[${part.unit}]` : ''}`
    : `choice:${part.label}=${part.options.map((option) => option.label).join('|')}`)
}

function parse(text: string) {
  const template = parseTemplate(text)
  expect(template).not.toBeNull()
  return template!
}

test('splits 취미/특기/결혼 into three inputs and a choice', () => {
  const template = parse(HOBBY)

  expect(describe(template.lines.flat())).toEqual(['slot:취미', 'slot:특기', 'slot:일반사회 소속단체', 'choice:결혼유무=결혼|미혼'])

  const [hobby, skill, , married] = template.lines.flat() as [Slot, Slot, Slot, Choice]
  expect(composeTemplate(template, { [hobby.key]: '드럼', [skill.key]: '빠른 움직임', [married.key]: '1' }))
    .toBe('취미: 드럼  /특기: 빠른 움직임  일반사회 소속단체:                  / 결혼유무: 결혼(  ) 미혼 ( V )')
})

test('reads 외국어 lines as a language input plus two 상/중/하 scales each', () => {
  const template = parse(LANGUAGE)

  expect(template.lines.map(describe)).toEqual([
    ['slot:외국어 1', 'choice:독해=상|중|하', 'choice:회화=상|중|하'],
    ['slot:외국어 2', 'choice:독해=상|중|하', 'choice:회화=상|중|하'],
  ])
  const [language, reading] = template.lines[0] as [Slot, Choice]
  expect(composeTemplate(template, { [language.key]: '영어', [reading.key]: '0' }).split(NL)[0])
    .toBe('외국어 1: ( 영어 )  독해 -- 상(V)  중  하   /   회화 -- 상  중  하   ')
})

test('numbered lines become one pick-one choice', () => {
  const template = parse(PARTICIPATION)

  expect(describe(template.lines.flat())).toEqual(['choice:=이 번 공연만 참여합니다|지속적 Shakespeare 명작공연을 위한 ESTC 단원 지원합니다'])
  expect(composeTemplate(template, { [template.lines[0][0].key]: '0' }).split(NL)[0]).toBe('1 ( V )  이 번 공연만 참여합니다')
})

test.each([
  [' 남(  ) 여(  )', ['choice:=남|여']],
  ['남  /  여', ['choice:=남|여']],
  ['YES (     )   /   NO (      )', ['choice:=YES|NO']],
  ['Soprano(   ), Mezzo Soprano(   ), Alto(   ), Tenor(   ), Baritone(   ), Bass(   )', ['choice:=Soprano|Mezzo Soprano|Alto|Tenor|Baritone|Bass']],
  ['신장(    cm)   체중(   kg)', ['slot:신장[cm]', 'slot:체중[kg]']],
  ['(                            ) 초등학교', ['slot:초등학교']],
  [['병역  (        )', '/혈액형 (          )'].join(NL), ['slot:병역', 'slot:혈액형']],
])('parses %j', (text, expected) => {
  expect(describe(parse(text).lines.flat())).toEqual(expected)
})

test('fills units and bare choices in place', () => {
  const body = parse('신장(    cm)   체중(   kg)')
  const [height, weight] = body.lines[0]
  expect(composeTemplate(body, { [height.key]: '178', [weight.key]: '68' })).toBe('신장( 178cm )   체중( 68kg )')

  const gender = parse('남  /  여')
  expect(composeTemplate(gender, { [gender.lines[0][0].key]: '1' })).toBe('남  /  여(V)')
})

test('returns the text unchanged when nothing is answered', () => {
  expect(composeTemplate(parse(HOBBY), {})).toBe(HOBBY)
})

test('gives up on text without blanks', () => {
  expect(parseTemplate('자유롭게 작성해 주세요')).toBeNull()
})

test('fills a printed box in front of the picked option', () => {
  // 더스테이지컴퍼니 2026: "□남   □여".
  const gender = parse('□남   □여')
  expect(describe(gender.lines.flat())).toEqual(['choice:=남|여'])
  expect(composeTemplate(gender, { [gender.lines[0][0].key]: '1' })).toBe('□남   ■여')
  expect(describe(parse('병역 ☐ 군필 ☐ 미필 ☐ 면제').lines.flat())).toEqual(['choice:병역=군필|미필|면제'])
})
