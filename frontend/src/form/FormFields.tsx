import { ItemInput } from './ItemInput'
import type { Answers, Photos, PublicForm } from './types'

type Props = {
  readonly form: PublicForm
  readonly answers: Answers
  readonly photos: Photos
  readonly onValues: (id: string, values: readonly string[]) => void
  readonly onPhoto: (id: string, file?: File) => void
}

/** Every item of a notice form, in the order the operator listed them. */
export function FormFields({ form, answers, photos, onValues, onPhoto }: Props) {
  return (
    <div className="field-list">
      {form.items.map((item) => (
        <ItemInput
          key={item.id}
          item={item}
          values={answers[item.id] ?? []}
          photos={photos}
          onValues={onValues}
          onPhoto={onPhoto}
        />
      ))}
    </div>
  )
}
