import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'
import type { Completed, Preview, PreviewPage as Page } from './api'
import { fetchPreview, previewPageUrl } from './api'
import { DocumentEditor } from './document-editing/DocumentEditor'
import { PreviewPage } from './PreviewPage'
import { PreviewZoom } from './PreviewZoom'
import { BottomCTA, Button, TopBar } from './ui'

type Props = {
  readonly completed: Completed
  readonly guide?: ReactNode
  readonly pdfBusy: boolean
  readonly pdfFirst?: boolean
  readonly onDocumentEdited?: (completed: Completed) => void
  readonly onBack: () => void
  readonly onMail: () => void
  readonly onSave: () => void
  readonly onSavePdf: () => void
}

/** Inspect the actual completed file. Direct editing never regenerates it from form answers. */
export function DoneScreen(props: Props) {
  const [locked, setLocked] = useState(false)
  useEffect(() => {
    if (!locked) return
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    return () => { document.body.style.overflow = previous }
  }, [locked])
  const editable = Boolean(props.completed.editToken && props.onDocumentEdited)

  return <div inert={locked}>
    <TopBar onBack={props.onBack} />
    <section className="content done-content">
      <h1 className="title">지원서 파일이 만들어졌어요</h1>
      <p className="file-name">{props.completed.file.name}</p>
      <p className="done-note">{editable
        ? '제출 전 내용을 확인해주세요. 직접 수정에서 빈칸·제목·안내문을 고치고, 글을 다른 칸으로 옮길 수 있어요. 사진·입력 항목을 다시 바꾸려면 뒤로 가세요.'
        : '앱에서 만든 지원서를 이어서 열었어요. 저장하거나 메일로 보내주세요. 고칠 곳이 있으면 새로 작성해주세요.'}</p>
      {props.guide}
      {props.completed.editToken && props.onDocumentEdited
        ? <DocumentEditor completed={props.completed} onEdited={props.onDocumentEdited} onLocked={setLocked}
            fallback={<ReadOnlyPreview completed={props.completed} onLocked={setLocked} />} />
        : <ReadOnlyPreview completed={props.completed} onLocked={setLocked} />}
    </section>
    <BottomCTA>
      <Button onClick={props.onMail}>{props.pdfFirst ? 'PDF 메일로 보내기' : '메일로 보내기'}</Button>
      <div className="button-row">
        {props.pdfFirst ? <>
          <Button variant="secondary" onClick={props.onSavePdf} loading={props.pdfBusy}>PDF로 저장</Button>
          <Button variant="secondary" onClick={props.onSave}>한글로 저장</Button>
        </> : <>
          <Button variant="secondary" onClick={props.onSave}>한글로 저장</Button>
          <Button variant="secondary" onClick={props.onSavePdf} loading={props.pdfBusy}>PDF로 저장</Button>
        </>}
      </div>
    </BottomCTA>
  </div>
}

function ReadOnlyPreview({ completed, onLocked }: {
  readonly completed: Completed
  readonly onLocked: (locked: boolean) => void
}) {
  const [preview, setPreview] = useState<Preview>()
  const [failed, setFailed] = useState(false)
  const [zoom, setZoom] = useState<{ page: Page; opener: HTMLButtonElement }>()
  useEffect(() => {
    onLocked(Boolean(zoom))

    return () => onLocked(false)
  }, [zoom, onLocked])
  useEffect(() => {
    let active = true
    fetchPreview(completed.documentId).then((result) => { if (active) setPreview(result) })
      .catch(() => { if (active) setFailed(true) })

    return () => { active = false }
  }, [completed])

  return <>
    <div className="preview" inert={Boolean(zoom)}>
      {!preview && <div className="preview-state" role="status">{failed ? '미리보기를 불러오지 못했어요' : '미리보기를 만들고 있어요'}</div>}
      {preview?.pages.map((page) => <PreviewPage key={page.number} page={page} hotspots={[]} labels={new Map()}
        imageUrl={previewPageUrl(completed.documentId, page.number, 0)} onEdit={() => undefined}
        onZoom={(opener) => setZoom({ page, opener })} />)}
    </div>
    {zoom && createPortal(<PreviewZoom page={zoom.page} opener={zoom.opener} hotspots={[]} labels={new Map()}
      imageUrl={previewPageUrl(completed.documentId, zoom.page.number, 0)} onClose={() => setZoom(undefined)} onEdit={() => undefined} />, document.body)}
  </>
}
