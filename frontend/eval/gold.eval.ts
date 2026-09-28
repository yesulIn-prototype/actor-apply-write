// Scores field recognition against the answer key in eval/gold: `npm run eval` with the backend running
// (EVAL_API, default http://localhost:8080). Writes eval/report.json and prints a summary.
import { readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { expect, test } from 'vitest'
import type { AnalysisResponse } from '../src/document'
import type { FormScore, GoldForm } from './score'
import { scoreForm } from './score'

const API = process.env.EVAL_API ?? 'http://localhost:8080'
const GOLD = join(__dirname, 'gold')
const FIXTURES = join(__dirname, '..', '..', 'backend', 'src', 'test', 'resources', 'fixtures')

async function analyze(file: string): Promise<AnalysisResponse> {
  const body = new FormData()
  body.append('document', new Blob([readFileSync(join(FIXTURES, file))]), file)
  const response = await fetch(`${API}/api/documents/analyze`, { method: 'POST', body })
  if (!response.ok) throw new Error(`${file}: ${response.status} ${await response.text()}`)
  return response.json() as Promise<AnalysisResponse>
}

function percent(part: number, whole: number): string {
  return whole === 0 ? '-' : `${Math.round((part / whole) * 100)}%`
}

test('field recognition against the answer key', async () => {
  const scores: FormScore[] = []
  for (const name of readdirSync(GOLD).filter((file) => file.endsWith('.json')).sort()) {
    const gold = JSON.parse(readFileSync(join(GOLD, name), 'utf8')) as GoldForm
    const analysis = await analyze(gold.file)
    scores.push(scoreForm(name.replace(/\.json$/, ''), gold, analysis.fields))
  }

  const total = scores.reduce((sum, score) => ({
    gold: sum.gold + score.gold,
    predicted: sum.predicted + score.predicted,
    found: sum.found + score.found,
    labelOk: sum.labelOk + score.labelOk,
    typeOk: sum.typeOk + score.typeOk,
    extra: sum.extra + score.problems.filter((problem) => problem.kind === 'extra').length,
    perfect: sum.perfect + (score.problems.length === 0 ? 1 : 0),
  }), { gold: 0, predicted: 0, found: 0, labelOk: 0, typeOk: 0, extra: 0, perfect: 0 })

  const lines = scores.map((score) => {
    const extra = score.problems.filter((problem) => problem.kind === 'extra').length
    return `${score.form.padEnd(14)} 칸 ${String(score.gold).padStart(3)}  찾음 ${percent(score.found, score.gold).padStart(4)}`
      + `  엉뚱한 칸 ${String(extra).padStart(2)}  이름 ${percent(score.labelOk, score.found).padStart(4)}`
      + `  입력 종류 ${percent(score.typeOk, score.found).padStart(4)}  문제 ${score.problems.length}`
  })
  console.log([
    ...lines,
    '-'.repeat(86),
    `${'전체'.padEnd(13)} 칸 ${total.gold}  찾음 ${percent(total.found, total.gold)}  엉뚱한 칸 ${total.extra}`
      + `  이름 ${percent(total.labelOk, total.found)}  입력 종류 ${percent(total.typeOk, total.found)}`
      + `  완벽한 양식 ${total.perfect}/${scores.length}`,
  ].join('\n'))
  for (const score of scores) {
    for (const problem of score.problems) {
      console.log(`  ${score.form} ${problem.at} ${problem.kind}: expected [${problem.expected}] got [${problem.actual}]`)
    }
  }
  writeFileSync(join(__dirname, 'report.json'), JSON.stringify({ total, scores }, null, 1))
  expect(scores.length).toBeGreaterThan(0)
}, 120_000)
