import type { FormItem } from './types'
import { rowsOf } from './types'

type Props = {
  readonly item: FormItem
  readonly values: readonly string[]
  readonly onChange: (values: readonly string[]) => void
}

/**
 * A ROWS item (출연 경력): one card per row with a line for each column, and a button to add rows up to the
 * operator's limit. An empty answer still shows one row to start typing in.
 */
export function RowsInput({ item, values, onChange }: Props) {
  const written = rowsOf(item, values)
  const rows = written.length > 0 ? written : [item.columns.map(() => '')]
  const full = rows.length >= item.maxRows

  function change(rowIndex: number, column: number, value: string) {
    onChange(rows.map((row, at) => (at === rowIndex ? row.map((old, k) => (k === column ? value : old)) : row)).flat())
  }

  function remove(rowIndex: number) {
    onChange(rows.filter((_, at) => at !== rowIndex).flat())
  }

  return (
    <div className="rows-field" role="group" aria-labelledby={`item-${item.id}-label`}>
      <p className="field-label" id={`item-${item.id}-label`}>
        {item.label}{item.required && <span className="required" aria-label="필수"> *</span>}
      </p>
      {item.help && <p className="field-help">{item.help}</p>}
      {rows.map((row, rowIndex) => (
        <fieldset className="rows-entry" key={rowIndex}>
          <legend className="rows-entry-head">
            <span>{rowIndex + 1}</span>
            {rows.length > 1 && (
              <button type="button" className="rows-remove" onClick={() => remove(rowIndex)}
                aria-label={`${item.label} ${rowIndex + 1}번째 줄 지우기`}>
                지우기
              </button>
            )}
          </legend>
          {item.columns.map((column, k) => {
            const id = `item-${item.id}-${rowIndex}-${column.id}`
            return (
              <div className="rows-cell" key={column.id}>
                <label htmlFor={id}>{column.label}</label>
                <input id={id} type="text" value={row[k] ?? ''} maxLength={100} autoComplete="off" enterKeyHint="next"
                  onChange={(event) => change(rowIndex, k, event.target.value)} />
              </div>
            )
          })}
        </fieldset>
      ))}
      <button type="button" className="rows-add" disabled={full}
        onClick={() => onChange([...rows.flat(), ...item.columns.map(() => '')])}>
        {full ? `${item.maxRows}줄까지 쓸 수 있어요` : `+ 줄 더하기 (${rows.length}/${item.maxRows})`}
      </button>
    </div>
  )
}
