import { useEffect, useRef, useState } from 'react'
import type { Screen } from './analytics'
import { campaign, track, trackScreen } from './analytics'
import type { Completed } from './api'
import { analyzeDocument, fetchCompletedCount, generateDocument, resumeDocument } from './api'
import type { AnalysisResponse } from './document'
import { initialValue } from './document'
import { suggestedOutputName } from './fileName'
import { preparePhoto } from './photo'
import { detectPlatform } from './platform'
import { DoneScreen } from './DoneScreen'
import { FieldEditor } from './FieldEditor'
import { FillScreen, UploadScreen } from './screens'
import { InAppNotice } from './InAppNotice'
import { message, resumeLink, useLeaveInAppBrowser, useToast } from './shell'
import { useDelivery } from './useDelivery'
import { Toast } from './ui'
import './App.css'

type Phase = 'upload' | 'analyzing' | 'fill' | 'generating' | 'done'

const SCREENS: Partial<Record<Phase, Screen>> = { upload: 'upload', fill: 'fill', generating: 'fill', done: 'done' }

const DOCUMENT_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function App() {
  const [platform] = useState(() => detectPlatform(navigator.userAgent))
  const [resumeId] = useState(() => {
    const id = new URLSearchParams(window.location.search).get('doc')
    return id && DOCUMENT_ID.test(id) ? id : undefined
  })
  const [phase, setPhase] = useState<Phase>(() => (resumeId ? 'analyzing' : 'upload'))
  const [analysis, setAnalysis] = useState<AnalysisResponse>()
  const [values, setValues] = useState<Record<string, string>>({})
  const [photos, setPhotos] = useState<Record<string, File | undefined>>({})
  // Until the applicant types a name, the file follows the company's naming pattern as answers come in.
  const [typedName, setTypedName] = useState<string>()
  const [completed, setCompleted] = useState<Completed>()
  const [completedCount, setCompletedCount] = useState<number>()
  const [toast, showToast] = useToast()
  const delivery = useDelivery(completed, showToast)
  const editSnapshot = useRef<{ values: Record<string, string>; photos: Record<string, File | undefined> }>(undefined)
  const outputName = typedName ?? (analysis ? suggestedOutputName(analysis.fileName, analysis.fields, values) : '')

  useEffect(() => {
    if (phase !== 'upload') return
    let active = true
    fetchCompletedCount().then((count) => { if (active) setCompletedCount(count) })
    return () => { active = false }
  }, [phase])

  useLeaveInAppBrowser(platform)

  useEffect(() => {
    // A resumed form has no answers; it was completed in the in-app browser it came from.
    const resumed = phase === 'done' && analysis?.fields.length === 0
    const screen = resumed ? 'resume' : SCREENS[phase]
    if (screen) trackScreen(screen)
  }, [phase, analysis])

  useEffect(() => {
    if (!resumeId) return
    const documentId = resumeId
    let active = true
    resumeDocument(documentId)
      .then((resumed) => {
        if (!active) return
        setAnalysis({
          documentId, fileName: resumed.file.name, expiresAt: '', tableCount: 0, cellCount: 0, fields: [],
        })
        setCompleted(resumed)
        setPhase('done')
      })
      .catch((reason) => {
        if (!active) return
        window.history.replaceState(null, '', '/')
        showToast(message(reason))
        setPhase('upload')
      })
    return () => { active = false }
  }, [resumeId, showToast])

  useEffect(() => {
    // Inside an in-app browser the finished form's address carries its id, so "open in browser"
    // (the app's own menu or our link) continues right here instead of starting over.
    // The source tags stay too, so the reopened page still knows where the visit came from.
    if (!platform.inApp) return
    const query = new URLSearchParams(phase === 'done' && completed ? { doc: completed.documentId } : {})
    campaign(platform).forEach((value, key) => query.set(key, value))
    const target = query.toString() ? `/?${query}` : '/'
    if (phase !== 'analyzing' && window.location.pathname + window.location.search !== target) {
      window.history.replaceState(null, '', target)
    }
  }, [platform, phase, completed])

  async function analyze(file: File) {
    if (!/\.hwpx?$/i.test(file.name.trim())) {
      fail('analyze_error', '한글 파일(.hwp, .hwpx)만 올릴 수 있어요')
      return
    }
    setPhase('analyzing')
    try {
      const result = await analyzeDocument(file)
      setAnalysis(result)
      setValues(Object.fromEntries(result.fields
        .filter((field) => field.style === 'TEMPLATE' || field.style === 'FILLED')
        .map((field) => [field.id, initialValue(field)])))
      setPhotos({})
      setTypedName(undefined)
      setPhase('fill')
      window.scrollTo(0, 0)
    } catch (reason) {
      fail('analyze_error', message(reason))
      setPhase('upload')
    }
  }

  async function pickPhoto(id: string, file?: File) {
    if (!file) {
      setPhotos((current) => ({ ...current, [id]: undefined }))
      return
    }
    try {
      const prepared = await preparePhoto(file)
      setPhotos((current) => ({ ...current, [id]: prepared }))
    } catch (reason) {
      showToast(message(reason))
    }
  }

  async function generate() {
    if (!analysis) return
    setPhase('generating')
    try {
      setCompleted(await generateDocument(analysis, values, photos, outputName))
      setPhase('done')
      window.scrollTo(0, 0)
    } catch (reason) {
      fail('generate_error', message(reason))
      setPhase('fill')
    }
  }

  /** Rebuilds the file after an edit on the preview; the applicant stays on the result screen. */
  async function apply(): Promise<boolean> {
    if (!analysis) return false
    try {
      setCompleted(await generateDocument(analysis, values, photos, outputName))
      showToast('수정했어요')
      return true
    } catch (reason) {
      showToast(message(reason))
      return false
    }
  }

  function fail(event: string, text: string) {
    showToast(text)
    track(event, { reason: text })
  }


  return (
    <main className="app">
      <InAppNotice
        platform={platform}
        resumeUrl={phase === 'done' && completed ? resumeLink(completed.documentId) : undefined}
      />
      {(phase === 'upload' || phase === 'analyzing') && (
        <UploadScreen busy={phase === 'analyzing'} completedCount={completedCount} onFile={analyze} />
      )}
      {(phase === 'fill' || phase === 'generating') && analysis && (
        <FillScreen
          analysis={analysis}
          values={values}
          photos={photos}
          outputName={outputName}
          busy={phase === 'generating'}
          onValue={(id, value) => setValues((current) => ({ ...current, [id]: value }))}
          onPhoto={pickPhoto}
          onOutputName={setTypedName}
          onBack={() => { setAnalysis(undefined); setPhase('upload') }}
          onSubmit={generate}
        />
      )}
      {phase === 'done' && completed && analysis && (
        <DoneScreen
          completed={completed}
          labels={new Map(analysis.fields.map((field) => [field.id, field.label]))}
          renderEditor={(id) => {
            const field = analysis.fields.find((candidate) => candidate.id === id)
            return field && (
              <FieldEditor
                field={field}
                values={values}
                photos={photos}
                onValue={(fieldId, value) => setValues((current) => ({ ...current, [fieldId]: value }))}
                onPhoto={pickPhoto}
              />
            )
          }}
          onEditOpen={() => { editSnapshot.current = { values, photos } }}
          onEditCancel={() => {
            if (!editSnapshot.current) return
            setValues(editSnapshot.current.values)
            setPhotos(editSnapshot.current.photos)
          }}
          onApply={apply}
          onBack={() => {
            if (analysis.fields.length > 0) {
              setPhase('fill')
              return
            }
            // A resumed form has no answers to go back to; start a new one.
            window.history.replaceState(null, '', '/')
            setAnalysis(undefined)
            setCompleted(undefined)
            setPhase('upload')
          }}
          onMail={delivery.mail}
          onSave={delivery.save}
          onSavePdf={delivery.savePdf}
          pdfBusy={delivery.pdfBusy}
        />
      )}
      <Toast message={toast} />
    </main>
  )
}

export default App
