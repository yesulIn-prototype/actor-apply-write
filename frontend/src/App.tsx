import { useEffect, useRef, useState } from 'react'
import type { Completed } from './api'
import { analyzeDocument, fetchCompletedCount, generateDocument, preparePdf } from './api'
import { openMailDraft, saveFile, shareFile } from './delivery'
import type { AnalysisResponse } from './document'
import { initialValue } from './document'
import { suggestedOutputName } from './fileName'
import { preparePhoto } from './photo'
import type { Platform } from './platform'
import { detectPlatform, externalBrowserUrl } from './platform'
import { DoneScreen } from './DoneScreen'
import { FillScreen, UploadScreen } from './screens'
import { Toast } from './ui'
import './App.css'

type Phase = 'upload' | 'analyzing' | 'fill' | 'generating' | 'done'

function App() {
  const [platform] = useState(() => detectPlatform(navigator.userAgent))
  const [phase, setPhase] = useState<Phase>('upload')
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
    // KakaoTalk's webview can't save files, but it can hand the page to the system browser.
    const external = externalBrowserUrl(platform, window.location.href)
    if (platform.inApp === 'kakaotalk' && external) window.location.href = external
  }, [platform])

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
      <InAppNotice platform={platform} />
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
          onBack={() => setPhase('fill')}
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

function InAppNotice({ platform }: { platform: Platform }) {
  if (!platform.inApp) return null
  const external = externalBrowserUrl(platform, window.location.href)
  return (
    <div className="in-app-notice" role="note">
      {external ? (
        <>
          <span>파일 저장은 브라우저에서 할 수 있어요</span>
          <a href={external}>브라우저로 열기</a>
        </>
      ) : (
        <span>파일을 저장하려면 오른쪽 위 ··· 에서 ‘외부 브라우저로 열기’를 눌러주세요</span>
      )}
    </div>
  )
}

function useToast(): [string, (text: string) => void] {
  const [text, setText] = useState('')
  const timer = useRef<number>(undefined)
  return [text, (next: string) => {
    window.clearTimeout(timer.current)
    setText(next)
    timer.current = window.setTimeout(() => setText(''), 3000)
  }]
}

function message(reason: unknown): string {
  return reason instanceof Error ? reason.message : '처리하지 못했어요. 다시 시도해주세요'
}

export default App
