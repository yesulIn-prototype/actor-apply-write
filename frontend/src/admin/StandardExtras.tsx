import type { CatalogItem } from './adminApi'
import type { CustomType, Extra } from './standardSpec'
import { nextCustomId } from './standardSpec'

const TYPES: readonly { readonly value: CustomType; readonly label: string }[] = [
  { value: 'text', label: '글로 받기' },
  { value: 'yesno', label: '예·아니오' },
  { value: 'single', label: '하나 고르기' },
  { value: 'multi', label: '여러 개 고르기' },
]

type Props = {
  readonly catalog: readonly CatalogItem[]
  readonly extras: readonly Extra[]
  readonly onChange: (extras: readonly Extra[]) => void
}

/**
 * The questions added under 지원 정보: ticked from the catalog, or written for this notice. They appear in the
 * order they were added, on the applicant's screen and as rows of the form.
 */
export function StandardExtras({ catalog, extras, onChange }: Props) {
  const picked = (key: string) => extras.find((extra) => extra.kind === 'catalog' && extra.key === key)

  function toggle(item: CatalogItem) {
    onChange(picked(item.key)
      ? extras.filter((extra) => !(extra.kind === 'catalog' && extra.key === item.key))
      : [...extras, { kind: 'catalog', key: item.key, required: false, options: '' }])
  }

  function update(index: number, next: Extra) {
    onChange(extras.map((extra, at) => (at === index ? next : extra)))
  }

  return (
    <fieldset className="admin-fieldset">
      <legend>추가 질문</legend>
      <p className="admin-help">공고 본문이 따로 묻는 것을 고르세요. 지원 정보 아래에 한 줄씩 들어가요.</p>
      <div className="admin-checks">
        {catalog.map((item) => {
          const extra = picked(item.key)
          const index = extra ? extras.indexOf(extra) : -1
          return (
            <div className="admin-check" key={item.key}>
              <label>
                <input type="checkbox" checked={Boolean(extra)} onChange={() => toggle(item)} />
                {item.label}
              </label>
              {extra && (
                <label className="admin-required">
                  <input type="checkbox" checked={extra.required} onChange={(event) => update(index, { ...extra, required: event.target.checked })} />
                  필수
                </label>
              )}
              {extra?.kind === 'catalog' && item.operatorOptions && (
                <textarea
                  aria-label={`${item.label} 선택지`}
                  placeholder={'한 줄에 하나씩\n10월 9일 (금)\n10월 10일 (토)'}
                  value={extra.options}
                  onChange={(event) => update(index, { ...extra, options: event.target.value })}
                />
              )}
            </div>
          )
        })}
      </div>

      <h3>직접 적는 질문</h3>
      {extras.map((extra, index) => extra.kind === 'custom' && (
        <div className="admin-custom" key={extra.id}>
          <input aria-label="질문" placeholder="예: 인형극 경험" value={extra.label}
            onChange={(event) => update(index, { ...extra, label: event.target.value })} />
          <select aria-label="답 모양" value={extra.type}
            onChange={(event) => update(index, {
              ...extra,
              type: TYPES.find((type) => type.value === event.target.value)?.value ?? 'text',
            })}>
            {TYPES.map((type) => <option key={type.value} value={type.value}>{type.label}</option>)}
          </select>
          <label className="admin-required">
            <input type="checkbox" checked={extra.required} onChange={(event) => update(index, { ...extra, required: event.target.checked })} />
            필수
          </label>
          <button type="button" onClick={() => onChange(extras.filter((_, at) => at !== index))}>지우기</button>
          {(extra.type === 'single' || extra.type === 'multi') && (
            <textarea aria-label={`${extra.label || '질문'} 선택지`} placeholder="선택지를 한 줄에 하나씩" value={extra.options}
              onChange={(event) => update(index, { ...extra, options: event.target.value })} />
          )}
        </div>
      ))}
      <button type="button"
        onClick={() => onChange([...extras, { kind: 'custom', id: nextCustomId(extras), label: '', type: 'text', required: false, options: '' }])}>
        + 질문 직접 적기
      </button>
    </fieldset>
  )
}
