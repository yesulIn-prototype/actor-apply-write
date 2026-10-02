import { BottomCTA, Button, Footer, TopBar } from '../ui'
import { FormFields } from './FormFields'
import type { Answers, Photos, PublicForm } from './types'
import { missing } from './types'

type Props = {
  readonly form: PublicForm
  readonly answers: Answers
  readonly photos: Photos
  readonly busy: boolean
  readonly onValues: (id: string, values: readonly string[]) => void
  readonly onPhoto: (id: string, file?: File) => void
  readonly onSubmit: () => void
}

/** A notice link's form: the operator's questions, nothing to upload. */
export function NoticeFillScreen({ form, answers, photos, busy, onValues, onPhoto, onSubmit }: Props) {
  const left = missing(form, answers, photos)
  return (
    <>
      <TopBar />
      <section className="content notice-content">
        <h1 className="title">{form.title}</h1>
        <p className="notice-lead">아래 내용을 채우면 공고의 지원서 파일로 만들어 드려요</p>
        <FormFields form={form} answers={answers} photos={photos} onValues={onValues} onPhoto={onPhoto} />
        <Footer />
      </section>
      <BottomCTA>
        {left.length > 0 && (
          <p className="cta-note" aria-live="polite">
            필수 항목 {left.length}개가 남았어요{left.length <= 2 ? ` (${left.map((item) => item.label).join(', ')})` : ''}
          </p>
        )}
        <Button onClick={onSubmit} disabled={left.length > 0} loading={busy}>지원서 만들기</Button>
      </BottomCTA>
    </>
  )
}
