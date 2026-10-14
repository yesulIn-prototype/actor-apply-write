import { useState } from 'react'
import { ItemInput } from './ItemInput'
import type { Answers, FormItem, Photos } from './types'

type Props = {
  readonly items: readonly FormItem[]
  readonly answers: Answers
  readonly photos: Photos
  readonly onValues: (id: string, values: readonly string[]) => void
  readonly onPhoto: (id: string, file?: File) => void
}

/**
 * Optional items as a short list of chips; tapping one opens just that item underneath, so the page stays
 * short however many there are. A chip marks an item already written, so a restored draft never looks lost.
 */
export function OptionalItems({ items, answers, photos, onValues, onPhoto }: Props) {
  const [openId, setOpenId] = useState<string>()
  const open = items.find((item) => item.id === openId)

  return (
    <section className="item-group" aria-labelledby="group-optional">
      <h2 id="group-optional" className="item-group-title">선택 항목</h2>
      <p className="item-group-help">필요한 것만 눌러서 써요. 비워 둬도 지원서를 만들 수 있어요.</p>
      <div className="optional-chips">
        {items.map((item) => {
          const done = written(answers[item.id])
          const isOpen = item.id === openId

          return (
            <button
              key={item.id}
              type="button"
              className={`optional-chip${done ? ' done' : ''}${isOpen ? ' open' : ''}`}
              aria-label={done ? `${item.label} 작성함` : item.label}
              aria-expanded={isOpen}
              aria-controls={isOpen ? 'optional-editor' : undefined}
              onClick={() => setOpenId(isOpen ? undefined : item.id)}
            >
              <span aria-hidden="true">{done ? '✓' : '+'}</span>
              {item.label}
            </button>
          )
        })}
      </div>
      {open && (
        <div className="optional-editor" id="optional-editor">
          <ItemInput item={open} values={answers[open.id] ?? []} photos={photos} onValues={onValues} onPhoto={onPhoto} />
          <button type="button" className="optional-close" onClick={() => setOpenId(undefined)}>다 썼어요</button>
        </div>
      )}
    </section>
  )
}

function written(values: readonly string[] | undefined): boolean {
  return values?.some((value) => value.trim() !== '') ?? false
}
