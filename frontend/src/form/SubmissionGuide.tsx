import { filledTemplate } from './fileName'
import type { Answers, PublicForm } from './types'

type Props = {
  readonly form: PublicForm
  readonly answers: Answers
  readonly onCopied: (text: string) => void
}

/**
 * Where and how to send the finished file, as the notice asks: the address, the mail subject filled with the
 * applicant's answers, the deadline. The applicant's own mail app sends it; nothing is sent from here.
 */
export function SubmissionGuide({ form, answers, onCopied }: Props) {
  const { email, deadline, note } = form.submission
  const subject = filledTemplate(form, answers, form.submission.subject).trim()
  if (!email && !subject) return null

  async function copy(text: string, done: string) {
    try {
      await navigator.clipboard.writeText(text)
      onCopied(done)
    } catch {
      onCopied('복사하지 못했어요. 길게 눌러 복사해주세요')
    }
  }

  const mailto = `mailto:${email}${subject ? `?subject=${encodeURIComponent(subject)}` : ''}`
  return (
    <section className="submission" aria-labelledby="submission-title">
      <h2 id="submission-title">제출 안내</h2>
      <dl>
        {email && (
          <div>
            <dt>받는 곳</dt>
            <dd>
              <span>{email}</span>
              <button type="button" onClick={() => void copy(email, '이메일 주소를 복사했어요')}>복사</button>
            </dd>
          </div>
        )}
        {subject && (
          <div>
            <dt>메일 제목</dt>
            <dd>
              <span>{subject}</span>
              <button type="button" onClick={() => void copy(subject, '메일 제목을 복사했어요')}>복사</button>
            </dd>
          </div>
        )}
        {deadline && (
          <div>
            <dt>마감</dt>
            <dd><span>{deadlineText(deadline)}</span></dd>
          </div>
        )}
      </dl>
      {note && <p className="submission-note">{note}</p>}
      {email && <a className="submission-mail" href={mailto}>이 주소로 메일 쓰기</a>}
      <p className="submission-help">지원서 파일을 저장한 뒤 메일에 첨부해주세요</p>
    </section>
  )
}

/** "2026-10-07" → "10월 7일까지". */
function deadlineText(date: string): string {
  const match = /^\d{4}-(\d{2})-(\d{2})$/.exec(date)
  return match ? `${Number(match[1])}월 ${Number(match[2])}일까지` : date
}
