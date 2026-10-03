import { useEffect, useState } from 'react'
import { trackScreen } from '../analytics'
import type { Completed } from '../api'
import { resumeDocument } from '../api'
import { DoneScreen } from '../DoneScreen'
import { InAppNotice } from '../InAppNotice'
import { detectPlatform } from '../platform'
import { message, resumeLink, useLeaveInAppBrowser, useToast } from '../shell'
import { Toast, TopBar } from '../ui'
import { useDelivery } from '../useDelivery'

/** Resumes only the completed file; applicant answers stay in the browser that made it. */
export function ResumeNotice({ vid, documentId }: { readonly vid: string; readonly documentId: string }) {
  const [platform] = useState(() => detectPlatform(navigator.userAgent))
  const [completed, setCompleted] = useState<Completed>()
  const [error, setError] = useState('')
  const [toast, showToast] = useToast()
  const delivery = useDelivery(completed, showToast)
  useLeaveInAppBrowser(platform)
  useEffect(() => {
    let active = true
    resumeDocument(documentId).then((result) => {
      if (active) { setCompleted(result); trackScreen('apply_resume') }
    }).catch((reason) => { if (active) setError(message(reason)) })
    return () => { active = false }
  }, [documentId])
  return (
    <main className="app">
      <InAppNotice platform={platform} resumeUrl={resumeLink(documentId)} />
      {completed ? (
        <DoneScreen completed={completed} labels={new Map()} renderEditor={() => undefined}
          pdfBusy={delivery.pdfBusy} onEditOpen={() => undefined} onEditCancel={() => undefined}
          onApply={async () => false} onBack={() => { window.location.href = `/apply/${vid}` }}
          onMail={delivery.mail} onSave={delivery.save} onSavePdf={delivery.savePdf} />
      ) : (
        <><TopBar /><section className="content">
          <h1 className="title">{error ? '지원서를 열 수 없어요' : '완성한 지원서를 불러오고 있어요'}</h1>
          {error && <p role="alert" className="notice-lead">{error}</p>}
        </section></>
      )}
      <Toast message={toast} />
    </main>
  )
}
