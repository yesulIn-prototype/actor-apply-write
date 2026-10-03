import { useEffect, useState } from 'react'
import { campaign, track, trackScreen } from '../analytics'
import type { Completed } from '../api'
import { DoneScreen } from '../DoneScreen'
import { detectPlatform } from '../platform'
import { InAppNotice } from '../InAppNotice'
import { message, resumeLink, useLeaveInAppBrowser, useToast } from '../shell'
import { Footer, Toast, TopBar } from '../ui'
import { useDelivery } from '../useDelivery'
import { buildForm, fetchForm } from './api'
import { filledTemplate, suggestedFileName } from './fileName'
import { SubmissionGuide } from './SubmissionGuide'
import { ItemInput } from './ItemInput'
import { NoticeFillScreen } from './NoticeFillScreen'
import type { PublicForm } from './types'
import { useAnswers } from './useAnswers'
import '../App.css'
import './form.css'

type Phase =
  | { readonly kind: 'loading' }
  | { readonly kind: 'unavailable'; readonly reason: string }
  | { readonly kind: 'fill' | 'generating' | 'done'; readonly form: PublicForm }

/**
 * A notice link (/apply/22382): the form the operator published for that notice. Every visitor builds
 * their own file; the link only names the notice.
 */
export function ApplyApp({ vid }: { vid: string }) {
  const [platform] = useState(() => detectPlatform(navigator.userAgent))
  const [phase, setPhase] = useState<Phase>({ kind: 'loading' })
  const [completed, setCompleted] = useState<Completed>()
  // Until the applicant types a name, the file follows the operator's template as answers come in.
  const [typedName, setTypedName] = useState<string>()
  const [toast, showToast] = useToast()
  const answers = useAnswers(showToast)
  const shown = phase.kind === 'done' ? phase.form : undefined
  const delivery = useDelivery(completed, showToast, {
    pdfFirst: shown?.pdfFirst,
    mailTo: shown?.submission.email,
    mailSubject: shown ? filledTemplate(shown, answers.answers, shown.submission.subject).trim() : undefined,
  })
  useLeaveInAppBrowser(platform)

  useEffect(() => {
    let active = true
    fetchForm(vid)
      .then((form) => { if (active) setPhase({ kind: 'fill', form }) })
      .catch((reason) => { if (active) setPhase({ kind: 'unavailable', reason: message(reason) }) })
    return () => { active = false }
  }, [vid])

  useEffect(() => {
    if (phase.kind === 'fill') trackScreen('apply_fill')
    if (phase.kind === 'done') trackScreen('apply_done')
  }, [phase.kind])

  useEffect(() => {
    // Inside an in-app browser the finished file's address carries its id, so "open in browser" continues
    // with that file (the same hand-over as an uploaded form), not with a blank notice form.
    if (!platform.inApp || phase.kind !== 'done' || !completed) return
    const query = new URLSearchParams({ doc: completed.documentId })
    campaign(platform).forEach((value, key) => query.set(key, value))
    window.history.replaceState(null, '', `/?${query}`)
  }, [platform, phase.kind, completed])

  async function build(form: PublicForm, documentId?: string): Promise<Completed> {
    return buildForm({
      url: `/api/forms/${vid}/generate`,
      form,
      answers: answers.answers,
      photos: answers.photos,
      documentId,
      fileName: typedName ?? suggestedFileName(form, answers.answers),
    })
  }

  async function submit(form: PublicForm) {
    setPhase({ kind: 'generating', form })
    try {
      setCompleted(await build(form, completed?.documentId))
      setPhase({ kind: 'done', form })
      window.scrollTo(0, 0)
    } catch (reason) {
      showToast(message(reason))
      track('generate_error', { reason: message(reason) })
      setPhase({ kind: 'fill', form })
    }
  }

  async function apply(form: PublicForm): Promise<boolean> {
    try {
      setCompleted(await build(form, completed?.documentId))
      showToast('수정했어요')
      return true
    } catch (reason) {
      showToast(message(reason))
      return false
    }
  }

  return (
    <main className="app">
      <InAppNotice
        platform={platform}
        resumeUrl={phase.kind === 'done' && completed ? resumeLink(completed.documentId) : undefined}
      />
      {phase.kind === 'loading' && <Waiting />}
      {phase.kind === 'unavailable' && <Unavailable reason={phase.reason} />}
      {(phase.kind === 'fill' || phase.kind === 'generating') && (
        <NoticeFillScreen
          form={phase.form}
          answers={answers.answers}
          photos={answers.photos}
          busy={phase.kind === 'generating'}
          outputName={typedName ?? suggestedFileName(phase.form, answers.answers)}
          onOutputName={setTypedName}
          onValues={answers.setValues}
          onPhoto={answers.pickPhoto}
          onSubmit={() => submit(phase.form)}
        />
      )}
      {phase.kind === 'done' && completed && (
        <DoneScreen
          completed={completed}
          labels={new Map(phase.form.items.map((item) => [item.id, item.label]))}
          guide={<SubmissionGuide form={phase.form} answers={answers.answers} onCopied={showToast} />}
          renderEditor={(id) => {
            const item = phase.form.items.find((candidate) => candidate.id === id)
            return item && (
              <ItemInput
                item={item}
                values={answers.answers[item.id] ?? []}
                photos={answers.photos}
                onValues={answers.setValues}
                onPhoto={answers.pickPhoto}
              />
            )
          }}
          pdfBusy={delivery.pdfBusy}
          pdfFirst={phase.form.pdfFirst}
          onEditOpen={answers.remember}
          onEditCancel={answers.restore}
          onApply={() => apply(phase.form)}
          onBack={() => setPhase({ kind: 'fill', form: phase.form })}
          onMail={delivery.mail}
          onSave={delivery.save}
          onSavePdf={delivery.savePdf}
        />
      )}
      <Toast message={toast} />
    </main>
  )
}

function Waiting() {
  return (
    <>
      <TopBar />
      <section className="content">
        <div className="preview-state" role="status">
          <span className="dots" aria-hidden="true"><i /><i /><i /></span>
          지원서 양식을 불러오고 있어요
        </div>
      </section>
    </>
  )
}

function Unavailable({ reason }: { reason: string }) {
  return (
    <>
      <TopBar />
      <section className="content">
        <h1 className="title">지원서를 열 수 없어요</h1>
        <p className="notice-lead">{reason}</p>
        <Footer />
      </section>
    </>
  )
}
