import { useCallback, useEffect, useRef, useState } from 'react'
import type { Completed } from './api'
import { analyzeDocument, fetchCompletedCount, generateDocument, preparePdf, resumeDocument } from './api'
import { openMailDraft, saveFile, shareFile } from './delivery'
import type { AnalysisResponse } from './document'
import { initialValue } from './document'
import { suggestedOutputName } from './fileName'
import { preparePhoto } from './photo'
import type { Platform } from './platform'
import { detectPlatform, externalBrowserUrl, opensExternallyOnLoad } from './platform'
import { DoneScreen } from './DoneScreen'
import { FillScreen, UploadScreen } from './screens'
import { Toast } from './ui'
import './App.css'

type Phase = 'upload' | 'analyzing' | 'fill' | 'generating' | 'done'

const DOCUMENT_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const TRIED_EXTERNAL = 'yesulin.triedExternalBrowser'

/** "?doc=<id>": a form finished in an in-app browser, handed over to the system browser. */
function resumeLink(documentId: string): string {
  return `${window.location.origin}/?doc=${documentId}`
}

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
  const [pdfBusy, setPdfBusy] = useState(false)
  const [toast, showToast] = useToast()
  const outputName = typedName ?? (analysis ? suggestedOutputName(analysis.fileName, analysis.fields, values) : '')

  useEffect(() => {
    if (phase !== 'upload') return
    let active = true
    fetchCompletedCount().then((count) => { if (active) setCompletedCount(count) })
    return () => { active = false }
  }, [phase])

  useEffect(() => {
    // In-app browsers (KakaoTalk, Threads on Android…) can't save files; hand the page to the system
    // browser right away. Only once per visit: if it bounces back, the page stays usable with the notice.
    if (!opensExternallyOnLoad(platform) || once(TRIED_EXTERNAL)) return
    const external = externalBrowserUrl(platform, window.location.href)
    if (external) window.location.href = external
  }, [platform])

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
    if (!platform.inApp) return
    const target = phase === 'done' && completed ? `/?doc=${completed.documentId}` : '/'
    if (phase !== 'analyzing' && window.location.pathname + window.location.search !== target) {
      window.history.replaceState(null, '', target)
    }
  }, [platform, phase, completed])

  async function analyze(file: File) {
    if (!/\.hwpx?$/i.test(file.name.trim())) {
      showToast('한글 파일(.hwp, .hwpx)만 올릴 수 있어요')
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
      showToast(message(reason))
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
      showToast(message(reason))
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

  async function mail() {
    if (!completed) return
    const result = await shareFile(completed.file)
    if (result !== 'unsupported') return
    saveFile(completed.downloadUrl, completed.file.name)
    showToast('파일을 저장했어요. 메일에 첨부해서 보내주세요')
    window.setTimeout(() => openMailDraft(completed.file.name.replace(/\.hwp$/i, '')), 800)
  }

  function save() {
    if (!completed) return
    saveFile(completed.downloadUrl, completed.file.name)
    showToast('한글 파일을 저장했어요')
  }

  async function savePdf() {
    if (!completed) return
    setPdfBusy(true)
    try {
      await preparePdf(completed)
      saveFile(completed.pdfUrl, completed.file.name.replace(/\.hwp$/i, '.pdf'))
      showToast('PDF를 저장했어요')
    } catch {
      showToast('PDF로 만들지 못했어요. 한글 파일로 저장해주세요')
    } finally {
      setPdfBusy(false)
    }
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
          analysis={analysis}
          completed={completed}
          values={values}
          photos={photos}
          onValue={(id, value) => setValues((current) => ({ ...current, [id]: value }))}
          onPhoto={pickPhoto}
          onRestore={(previousValues, previousPhotos) => { setValues(previousValues); setPhotos(previousPhotos) }}
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
          onMail={mail}
          onSave={save}
          onSavePdf={savePdf}
          pdfBusy={pdfBusy}
        />
      )}
      <Toast message={toast} />
    </main>
  )
}

function InAppNotice({ platform, resumeUrl }: { platform: Platform; resumeUrl?: string }) {
  if (!platform.inApp) return null
  const external = externalBrowserUrl(platform, resumeUrl ?? window.location.href)
  const menu = '오른쪽 위 ··· 에서 ‘외부 브라우저로 열기’를 눌러주세요'
  if (resumeUrl) {
    return (
      <div className="in-app-notice" role="note">
        <span>저장이나 메일 보내기가 안 되면 브라우저에서 이어서 할 수 있어요{platform.os === 'ios' ? `. 안 열리면 ${menu}` : ''}</span>
        {external && <a href={external}>브라우저에서 이어하기</a>}
      </div>
    )
  }
  return (
    <div className="in-app-notice in-app-notice-start" role="note">
      <span>
        <strong>{platform.os === 'ios' ? 'Safari' : '인터넷 브라우저'}에서 열어주세요</strong>
        앱 안에서는 완성한 파일을 저장하거나 보내지 못할 수 있어요.{platform.os === 'ios' ? ` 버튼이 안 되면 ${menu}` : ''}
      </span>
      {external && <a href={external}>{platform.os === 'ios' ? 'Safari로 열기' : '브라우저로 열기'}</a>}
    </div>
  )
}

/** True when this visit already did it; the first call records it. With storage blocked it reports true. */
function once(key: string): boolean {
  try {
    if (window.sessionStorage.getItem(key)) return true
    window.sessionStorage.setItem(key, '1')
  } catch {
    // Without storage the redirect could loop through the fallback page; skip it.
    return true
  }
  return false
}

function useToast(): [string, (text: string) => void] {
  const [text, setText] = useState('')
  const timer = useRef<number>(undefined)
  const show = useCallback((next: string) => {
    window.clearTimeout(timer.current)
    setText(next)
    timer.current = window.setTimeout(() => setText(''), 3000)
  }, [])
  return [text, show]
}

function message(reason: unknown): string {
  return reason instanceof Error ? reason.message : '처리하지 못했어요. 다시 시도해주세요'
}

export default App
