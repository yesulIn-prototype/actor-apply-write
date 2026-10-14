import { useState } from 'react'
import { arrangeItems } from './itemOrder'
import { ItemInput } from './ItemInput'
import type { Answers, FormItem, Photos, PublicForm } from './types'

type Props = {
  readonly form: PublicForm
  readonly answers: Answers
  readonly photos: Photos
  readonly onValues: (id: string, values: readonly string[]) => void
  readonly onPhoto: (id: string, file?: File) => void
}

/**
 * A notice form's items in the same order on every notice: required ones, then optional ones folded away
 * until asked for, then photos. Folded items keep their answers; they are only not drawn.
 */
export function FormFields({ form, answers, photos, onValues, onPhoto }: Props) {
  const { required, optional, photos: photoItems } = arrangeItems(form.items)
  // A restored draft that already answered an optional item opens them, so nothing written looks lost.
  const [showOptional, setShowOptional] = useState(() => optional.some((item) => written(answers[item.id])))

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
      {optional.length > 0 && (
        <section className="item-group" aria-labelledby="group-optional">
          <h2 id="group-optional" className="item-group-title">선택 항목</h2>
          {showOptional ? draw(optional) : (
            <button type="button" className="item-group-more" aria-expanded="false" onClick={() => setShowOptional(true)}>
              선택 항목 {optional.length}개 더 쓰기
              <span>비워 둬도 지원서를 만들 수 있어요</span>
            </button>
          )}
        </section>
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

function written(values: readonly string[] | undefined): boolean {
  return values?.some((value) => value.trim() !== '') ?? false
}
