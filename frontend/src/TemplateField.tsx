import type { FieldCandidate } from './document'
import { answerKey } from './document'
import type { Answers, Choice, Slot, Template } from './template'
import { composeTemplate } from './template'

type Props = {
  field: FieldCandidate
  label: string
  template: Template
  values: Record<string, string>
  onChange: (id: string, value: string) => void
}

/**
 * A form's fill-in text as real inputs: blanks become text boxes, "( ) 남 ( ) 여" becomes chips.
 * Every change recomposes the original text, which is what the file receives and the preview shows.
 */
export function TemplateField({ field, label, template, values, onChange }: Props) {
  const parts = template.lines.flat()
  const answers: Answers = Object.fromEntries(parts.map((part) => [part.key, values[answerKey(field, part.key)] ?? '']))
  const composed = composeTemplate(template, answers)
  const onlyChoice = parts.length === 1 && parts[0].kind === 'choice' && !parts[0].label

  function answer(partKey: string, value: string) {
    onChange(answerKey(field, partKey), value)
    onChange(field.id, composeTemplate(template, { ...answers, [partKey]: value }))
  }

  return (
    <div className="text-field template-field" role="group" aria-label={field.label}>
      <p className="field-label">{label}</p>
      {template.lines.map((line, index) => (
        <div className="template-line" key={index}>
          {line.map((part) => part.kind === 'slot' ? (
            <SlotInput key={part.key} field={field} slot={part} value={answers[part.key]} onAnswer={answer} />
          ) : (
            <ChoiceChips
              key={part.key}
              choice={part}
              name={onlyChoice ? field.label : part.label}
              showName={!onlyChoice && Boolean(part.label)}
              value={answers[part.key]}
              onAnswer={answer}
            />
          ))}
        </div>
      ))}
      {!onlyChoice && (
        <div className="template-preview" aria-live="polite">
          <span>지원서에는 이렇게 들어가요</span>
          <p>{composed.replace(/ {3,}/g, '  ').trim()}</p>
        </div>
      )}
    </div>
  )
}

function SlotInput({ field, slot, value, onAnswer }: {
  field: FieldCandidate
  slot: Slot
  value: string
  onAnswer: (key: string, value: string) => void
}) {
  const id = `${field.id}-${slot.key}`
  return (
    <div className="slot">
      <label htmlFor={id}>{slot.label || '입력'}</label>
      <div className="slot-input">
        <input
          id={id}
          type="text"
          enterKeyHint="next"
          inputMode={slot.unit ? 'decimal' : undefined}
          value={value}
          placeholder={slot.unit ? '0' : `${slot.label || field.label} 입력`}
          onChange={(event) => onAnswer(slot.key, event.target.value)}
        />
        {slot.unit && <span className="unit">{slot.unit}</span>}
      </div>
    </div>
  )
}

function ChoiceChips({ choice, name, showName, value, onAnswer }: {
  choice: Choice
  name: string
  showName: boolean
  value: string
  onAnswer: (key: string, value: string) => void
}) {
  const long = choice.options.some((option) => option.label.length > 8)
  return (
    <div className="choice">
      {showName && <span className="choice-name">{name}</span>}
      <div className={long ? 'chips chips-stacked' : 'chips'} role="group" aria-label={name}>
        {choice.options.map((option, index) => {
          const selected = value === String(index)
          return (
            <button
              key={option.start}
              type="button"
              aria-pressed={selected}
              className={selected ? 'chip selected' : 'chip'}
              // Tapping the picked chip again clears it: every choice stays optional.
              onClick={() => onAnswer(choice.key, selected ? '' : String(index))}
            >
              {option.label}
            </button>
          )
        })}
      </div>
    </div>
  )
}
