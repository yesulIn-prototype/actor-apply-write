import { PhotoTile } from '../screens'
import type { FormItem, Photos } from './types'
import { assertNever } from './types'

type Props = {
  readonly item: FormItem
  readonly values: readonly string[]
  readonly photos: Photos
  readonly onValues: (id: string, values: readonly string[]) => void
  readonly onPhoto: (id: string, file?: File) => void
}

/** One operator-defined item, drawn the way its type says: no guessing from the document. */
export function ItemInput({ item, values, photos, onValues, onPhoto }: Props) {
  switch (item.type) {
    case 'TEXT':
    case 'PHONE':
      return <TextItem item={item} value={values[0] ?? ''} onChange={(value) => onValues(item.id, [value])} />
    case 'SINGLE':
    case 'MULTI':
      return <ChoiceItem item={item} picked={values} onChange={(picked) => onValues(item.id, picked)} />
    case 'PHOTO':
      return (
        <div className="item-photo">
          <ItemLabel item={item} />
          <div className="photo-grid">
            <PhotoTile field={item} file={photos[item.id]} onChange={onPhoto} />
          </div>
        </div>
      )
    default:
      return assertNever(item.type)
  }
}

function ItemLabel({ item, htmlFor }: { item: FormItem; htmlFor?: string }) {
  const text = <>{item.label}{item.required && <span className="required" aria-label="필수"> *</span>}</>
  return htmlFor ? <label htmlFor={htmlFor}>{text}</label> : <p className="field-label">{text}</p>
}

function TextItem({ item, value, onChange }: { item: FormItem; value: string; onChange: (value: string) => void }) {
  const id = `item-${item.id}`
  const phone = item.type === 'PHONE'
  const common = {
    id,
    value,
    maxLength: phone ? 20 : item.maxLength,
    required: item.required,
    'aria-describedby': item.help ? `${id}-help` : undefined,
    onChange: (event: { target: { value: string } }) => onChange(event.target.value),
  }
  return (
    <div className="text-field">
      <ItemLabel item={item} htmlFor={id} />
      {item.multiline ? (
        <textarea {...common} rows={4} />
      ) : (
        <input
          {...common}
          type={phone ? 'tel' : 'text'}
          inputMode={phone ? 'tel' : undefined}
          autoComplete={phone ? 'tel' : 'off'}
          enterKeyHint="next"
          placeholder={phone ? '010-0000-0000' : undefined}
        />
      )}
      {item.help && <p className="field-help" id={`${id}-help`}>{item.help}</p>}
    </div>
  )
}

function ChoiceItem({ item, picked, onChange }: {
  item: FormItem
  picked: readonly string[]
  onChange: (picked: readonly string[]) => void
}) {
  const multi = item.type === 'MULTI'
  const full = multi && item.max > 0 && picked.length >= item.max
  const long = item.options.some((option) => option.label.length > 8)
  const limit = multi && item.max > 0 ? `${item.max}개까지 고를 수 있어요` : multi ? '여러 개 고를 수 있어요' : ''

  function toggle(id: string) {
    const selected = picked.includes(id)
    if (!multi) {
      // Tapping the picked chip again clears it, so an optional choice can be left empty.
      onChange(selected ? [] : [id])
      return
    }
    onChange(selected ? picked.filter((value) => value !== id) : [...picked, id])
  }

  return (
    <div className="choice" role="group" aria-labelledby={`item-${item.id}-label`}>
      <p className="field-label" id={`item-${item.id}-label`}>
        {item.label}{item.required && <span className="required" aria-label="필수"> *</span>}
      </p>
      <div className={long ? 'chips chips-stacked' : 'chips'}>
        {item.options.map((option) => {
          const selected = picked.includes(option.id)
          return (
            <button
              key={option.id}
              type="button"
              aria-pressed={selected}
              className={selected ? 'chip selected' : 'chip'}
              disabled={!selected && full}
              onClick={() => toggle(option.id)}
            >
              {option.label}
            </button>
          )
        })}
      </div>
      {(item.help || limit) && <p className="field-help">{[item.help, limit].filter(Boolean).join(' · ')}</p>}
    </div>
  )
}
