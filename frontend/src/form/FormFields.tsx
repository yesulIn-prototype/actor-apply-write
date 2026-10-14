import { arrangeItems } from './itemOrder'
import { ItemInput } from './ItemInput'
import { OptionalItems } from './OptionalItems'
import type { Answers, FormItem, Photos, PublicForm } from './types'

type Props = {
  readonly form: PublicForm
  readonly answers: Answers
  readonly photos: Photos
  readonly onValues: (id: string, values: readonly string[]) => void
  readonly onPhoto: (id: string, file?: File) => void
  /** The operator's test panel edits every example at once, so optional items are drawn open. */
  readonly allOpen?: boolean
}

/**
 * A notice form's items in the same order on every notice: required ones, then optional ones as chips
 * opened one at a time, then photos. Closed items keep their answers; they are only not drawn.
 */
export function FormFields({ form, answers, photos, onValues, onPhoto, allOpen = false }: Props) {
  const { required, optional, photos: photoItems } = arrangeItems(form.items)

  const draw = (items: readonly FormItem[]) => (
    <div className="field-list">
      {items.map((item) => (
        <ItemInput key={item.id} item={item} values={answers[item.id] ?? []} photos={photos} onValues={onValues} onPhoto={onPhoto} />
      ))}
    </div>
  )

  return (
    <>
      {required.length > 0 && (
        <section className="item-group" aria-labelledby="group-required">
          <h2 id="group-required" className="item-group-title">필수 항목</h2>
          {draw(required)}
        </section>
      )}
      {optional.length > 0 && allOpen && (
        <section className="item-group" aria-labelledby="group-optional">
          <h2 id="group-optional" className="item-group-title">선택 항목</h2>
          {draw(optional)}
        </section>
      )}
      {optional.length > 0 && !allOpen && (
        <OptionalItems items={optional} answers={answers} photos={photos} onValues={onValues} onPhoto={onPhoto} />
      )}
      {photoItems.length > 0 && (
        <section className="item-group" aria-labelledby="group-photos">
          <h2 id="group-photos" className="item-group-title">사진</h2>
          {draw(photoItems)}
        </section>
      )}
    </>
  )
}
