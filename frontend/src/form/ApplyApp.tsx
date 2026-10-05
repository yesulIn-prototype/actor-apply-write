import { useEffect, useState } from 'react'
import { campaign, track, trackScreen } from '../analytics'
import type { Completed } from '../api'
import { DoneScreen } from '../DoneScreen'
import { detectPlatform } from '../platform'
import { InAppNotice } from '../InAppNotice'
import { message, resumeLink, useLeaveInAppBrowser, useToast } from '../shell'
import { Button, Footer, Toast, TopBar } from '../ui'
import { useDelivery } from '../useDelivery'
import { buildForm, fetchForm } from './api'
import { filledTemplate, suggestedFileName } from './fileName'
import { SubmissionGuide } from './SubmissionGuide'
import { RegenerationConfirm } from '../document-editing/RegenerationConfirm'
import { NoticeFillScreen } from './NoticeFillScreen'
import type { PublicForm } from './types'
import { useAnswers } from './useAnswers'
import { ResumeNotice } from './ResumeNotice'
import { useApplicationDraft } from './useApplicationDraft'
import { DraftPanel } from './DraftPanel'
import { DraftClearConfirm } from './DraftClearConfirm'
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
  const [resumeId] = useState(() => {
    const id = new URLSearchParams(window.location.search).get('doc')
    return id && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id) ? id : undefined
  })
  return resumeId ? <ResumeNotice vid={vid} documentId={resumeId} /> : <NoticeApplication key={vid} vid={vid} />
}

function NoticeApplication({ vid }: { vid: string }) {
  const [platform] = useState(() => detectPlatform(navigator.userAgent))
  const [phase, setPhase] = useState<Phase>({ kind: 'loading' })
  const [completed, setCompleted] = useState<Completed>()
  const [confirmRegeneration, setConfirmRegeneration] = useState(false)
  const [confirmClear, setConfirmClear] = useState(false)
  const [toast, showToast] = useToast()
  const answers = useAnswers(showToast)
  const draft = useApplicationDraft(phase.kind === 'loading' || phase.kind === 'unavailable' ? undefined : phase.form)
  const shown = phase.kind === 'done' ? phase.form : undefined
  const delivery = useDelivery(completed, showToast, {
    pdfFirst: shown?.pdfFirst,
    mailTo: shown?.submission.email,
    mailSubject: shown ? filledTemplate(shown, draft.answers, shown.submission.subject).trim() : undefined,
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
    // with that file on the same notice link.
    if (!platform.inApp || phase.kind !== 'done' || !completed) return
    const query = new URLSearchParams({ doc: completed.documentId })
    campaign(platform).forEach((value, key) => query.set(key, value))
    window.history.replaceState(null, '', `/apply/${vid}?${query}`)
  }, [platform, phase.kind, completed, vid])

  async function build(form: PublicForm, documentId?: string): Promise<Completed> {
    return buildForm({
      url: `/api/forms/${vid}/generate`,
      form,
      answers: draft.answers,
      photos: answers.photos,
      documentId,
      editToken: completed?.editToken,
      fileName: draft.outputName ?? suggestedFileName(form, draft.answers),
    })
  }

  async function submit(form: PublicForm, confirmed = false) {
    if (completed?.directEdited && !confirmed) { setConfirmRegeneration(true); return }
    setConfirmRegeneration(false)
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

  return (
    <main className="app" inert={confirmRegeneration || confirmClear}>
      <InAppNotice
        platform={platform}
        resumeUrl={phase.kind === 'done' && completed ? resumeLink(completed.documentId) : undefined}
      />
      {(phase.kind === 'loading' || (phase.kind === 'fill' && !draft.ready)) && <Waiting />}
      {phase.kind === 'unavailable' && <Unavailable reason={phase.reason} />}
      {(phase.kind === 'fill' || phase.kind === 'generating') && draft.ready && (
        <NoticeFillScreen
          form={phase.form}
          answers={draft.answers}
          photos={answers.photos}
          busy={phase.kind === 'generating'}
          outputName={draft.outputName ?? suggestedFileName(phase.form, draft.answers)}
          onOutputName={draft.setOutputName}
          onValues={draft.setValues}
          onPhoto={answers.pickPhoto}
          onSubmit={() => submit(phase.form)}
          draftPanel={<DraftPanel status={draft.status} restored={draft.restored} changed={draft.changed}
            hasPhotos={phase.form.items.some((item) => item.type === 'PHOTO')} onClear={() => setConfirmClear(true)} />}
        />
      )}
      {phase.kind === 'fill' && completed?.directEdited && <section className="content">
        <p className="done-note">다시 만들기 전까지 직접 수정한 지원서는 그대로 있어요.</p>
        <Button variant="secondary" onClick={() => setPhase({ kind: 'done', form: phase.form })}>수정한 지원서로 돌아가기</Button>
      </section>}
      {phase.kind === 'done' && completed && (
        <DoneScreen
          completed={completed}
          onDocumentEdited={setCompleted}
          guide={<SubmissionGuide form={phase.form} answers={draft.answers} onCopied={showToast} />}
          pdfBusy={delivery.pdfBusy}
          pdfFirst={phase.form.pdfFirst}
          onBack={() => setPhase({ kind: 'fill', form: phase.form })}
          onMail={delivery.mail}
          onSave={delivery.save}
          onSavePdf={delivery.savePdf}
        />
      )}
      <Toast message={toast} />
      {confirmClear && <DraftClearConfirm onCancel={() => setConfirmClear(false)}
        onConfirm={async () => { await draft.clear(); setConfirmClear(false) }} />}
      {confirmRegeneration && phase.kind === 'fill' && <RegenerationConfirm onCancel={() => setConfirmRegeneration(false)}
        onConfirm={() => submit(phase.form, true)} />}
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
