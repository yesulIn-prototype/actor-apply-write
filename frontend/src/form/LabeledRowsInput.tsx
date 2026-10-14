import { useState } from 'react'
import type { FormItem } from './types'
import { rowsOf } from './types'

type Props = {
  readonly item: FormItem
  readonly values: readonly string[]
  readonly onChange: (values: readonly string[]) => void
}

/**
 * A ROWS item whose rows the form fixes (학력: 초등학교 … 대학원): one card per label, always in the form's order.
 * "없음" clears that card and folds it, so its row stays as the form printed it; rows cannot be added.
 */
export function LabeledRowsInput({ item, values, onChange }: Props) {
  const labels = item.rowLabels ?? []
  const written = rowsOf(item, values)
  const rows = labels.map((_, at) => written[at] ?? item.columns.map(() => ''))
  const [skipped, setSkipped] = useState<ReadonlySet<number>>(() => new Set())

  function change(rowIndex: number, column: number, value: string) {
    onChange(rows.map((row, at) => (at === rowIndex ? row.map((old, k) => (k === column ? value : old)) : row)).flat())
  }

  function skip(rowIndex: number) {
    onChange(rows.map((row, at) => (at === rowIndex ? row.map(() => '') : row)).flat())
    setSkipped(new Set([...skipped, rowIndex]))
  }

  function unskip(rowIndex: number) {
    setSkipped(new Set([...skipped].filter((at) => at !== rowIndex)))
  }

  return (
    <div className="rows-field" role="group" aria-labelledby={`item-${item.id}-label`}>
      <p className="field-label" id={`item-${item.id}-label`}>
        {item.label}{item.required && <span className="required" aria-label="필수"> *</span>}
      </p>
      {item.help && <p className="field-help">{item.help}</p>}
      {labels.map((label, rowIndex) => skipped.has(rowIndex) ? (
        <div className="rows-entry rows-skipped" key={label}>
          <span className="rows-entry-head">
            <span>{label} · 없음</span>
            <button type="button" className="rows-remove" onClick={() => unskip(rowIndex)}>입력하기</button>
          </span>
        </div>
      ) : (
        <fieldset className="rows-entry" key={label}>
          <legend className="rows-entry-head">
            <span>{label}</span>
            <button type="button" className="rows-remove" onClick={() => skip(rowIndex)} aria-label={`${label} 없음`}>
              없음
            </button>
          </legend>
          {item.columns.map((column, k) => {
            const id = `item-${item.id}-${rowIndex}-${column.id}`

            return (
              <div className="rows-cell" key={column.id}>
                <label htmlFor={id}>{column.label}</label>
                <input id={id} type="text" value={rows[rowIndex][k] ?? ''} maxLength={100} autoComplete="off"
                  enterKeyHint="next" onChange={(event) => change(rowIndex, k, event.target.value)} />
              </div>
            )
          })}
        </fieldset>
      ))}
    </div>
  )
}
