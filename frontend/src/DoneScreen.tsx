import { useEffect, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import type { Completed, Hotspot, Preview, PreviewPage as Page } from './api'
import { fetchPreview, previewPageUrl } from './api'
import type { AnalysisResponse, FieldCandidate } from './document'
import { PreviewPage } from './PreviewPage'
import { PreviewZoom } from './PreviewZoom'
import { FieldInput, PhotoTile } from './screens'
import { BottomCTA, Button, TopBar } from './ui'

type Props = {
  analysis: AnalysisResponse
  completed: Completed
  values: Record<string, string>
  photos: Record<string, File | undefined>
  pdfBusy: boolean
  onValue: (id: string, value: string) => void
  onPhoto: (id: string, file?: File) => void
  /** Puts back what the sheet changed when the applicant closes it without applying. */
  onRestore: (values: Record<string, string>, photos: Record<string, File | undefined>) => void
  /** Rebuilds the file from the current answers; resolves false when that failed. */
  onApply: () => Promise<boolean>
  onBack: () => void
  onMail: () => void
  onSave: () => void
  onSavePdf: () => void
}

type Loaded = { key: string; preview?: Preview; failed?: boolean }

/** The finished form as it will be sent; tapping a filled-in spot opens that field right here. */
export function DoneScreen(props: Props) {
  const [version, setVersion] = useState(0)
  const [loaded, setLoaded] = useState<Loaded>()
  const [editing, setEditing] = useState<{
    field: FieldCandidate
    values: Props['values']
    photos: Props['photos']
    opener: HTMLButtonElement
  }>()
  const [applying, setApplying] = useState(false)
  const [zoom, setZoom] = useState<{ page: Page; opener: HTMLButtonElement }>()
  const key = `${props.completed.documentId}:${version}`

  useEffect(() => {
    let active = true
    fetchPreview(props.completed.documentId)
      .then((preview) => { if (active) setLoaded({ key, preview }) })
      .catch(() => { if (active) setLoaded({ key, failed: true }) })
    return () => { active = false }
  }, [key, props.completed.documentId])

  const current = loaded?.key === key ? loaded : undefined
  const preview = current?.preview
  const fields = new Map(props.analysis.fields.map((field) => [field.id, field]))
  // A form resumed in another browser brings its file but not the answers, so it can't be edited here.
  const editable = props.analysis.fields.length > 0
  const hotspots = editable ? preview?.hotspots ?? [] : []

  function open(hotspot: Hotspot, opener: HTMLButtonElement) {
    const field = fields.get(hotspot.fieldId)
    if (field) setEditing({ field, values: props.values, photos: props.photos, opener })
  }

  function cancel() {
    if (editing) props.onRestore(editing.values, editing.photos)
    setEditing(undefined)
  }

  async function apply() {
    setApplying(true)
    const ok = await props.onApply()
    setApplying(false)
    if (ok) {
      setEditing(undefined)
      setVersion((value) => value + 1)
    }
  }

  return (
    <>
      <div inert={Boolean(editing || zoom)}>
      <TopBar onBack={props.onBack} />
      <section className="content done">
        <span className="done-check" aria-hidden="true">
          <svg width="16" height="16" viewBox="0 0 24 24">
            <path d="M5 12.5l4.5 4.5L19 7.5" fill="none" stroke="#fff" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </span>
        <h1 className="title">지원서 파일이 만들어졌어요</h1>
        <p className="file-name">{props.completed.file.name}</p>
        <p className="done-note">
          {editable
            ? '제출하기 전에 빈칸·사진·공고 필수 항목을 확인해주세요. 오른쪽 위 확대 버튼으로 크게 보고, 칸을 누르면 수정할 수 있어요.'
            : '앱에서 만든 지원서를 이어서 열었어요. 저장하거나 메일로 보내주세요. 고칠 곳이 있으면 새로 작성해주세요.'}
        </p>

        <div className="preview">
          {!current && (
            <div className="preview-state" role="status">
              <span className="dots" aria-hidden="true"><i /><i /><i /></span>
              미리보기를 만들고 있어요
            </div>
          )}
          {current?.failed && <div className="preview-state">미리보기를 불러오지 못했어요</div>}
          {preview?.pages.map((page) => (
            <PreviewPage
              key={page.number}
              page={page}
              hotspots={hotspots.filter((hotspot) => hotspot.page === page.number)}
              fields={fields}
              imageUrl={previewPageUrl(props.completed.documentId, page.number, version)}
              onEdit={open}
              onZoom={(opener) => setZoom({ page, opener })}
            />
          ))}
        </div>
        {preview && editable && (
          <details className="edit-field-list">
            <summary>수정할 칸 목록</summary>
            <div className="edit-field-items">
              {[...new Map(preview.hotspots.map((hotspot) => [hotspot.fieldId, hotspot])).values()]
                .map((hotspot) => {
                  const field = fields.get(hotspot.fieldId)
                  return field && (
                    <button key={field.id} type="button" onClick={(event) => open(hotspot, event.currentTarget)}>
                      {field.label} 항목 수정
                    </button>
                  )
                })}
            </div>
          </details>
        )}
      </section>
      <BottomCTA>
        <Button onClick={props.onMail}>메일로 보내기</Button>
        <div className="button-row">
          <Button variant="secondary" onClick={props.onSave}>한글로 저장</Button>
          <Button variant="secondary" onClick={props.onSavePdf} loading={props.pdfBusy}>PDF로 저장</Button>
        </div>
      </BottomCTA>
      </div>

      {zoom && preview && (
        <PreviewZoom
          page={zoom.page}
          hotspots={hotspots.filter((hotspot) => hotspot.page === zoom.page.number)}
          fields={fields}
          imageUrl={previewPageUrl(props.completed.documentId, zoom.page.number, version)}
          opener={zoom.opener}
          onClose={() => setZoom(undefined)}
          onEdit={(hotspot) => {
            setZoom(undefined)
            open(hotspot, zoom.opener)
          }}
        />
      )}

      {editing && (
        <EditSheet
          field={editing.field}
          opener={editing.opener}
          values={props.values}
          photos={props.photos}
          applying={applying}
          onValue={props.onValue}
          onPhoto={props.onPhoto}
          onCancel={cancel}
          onApply={apply}
        />
      )}
    </>
  )
}

function EditSheet({ field, opener, values, photos, applying, onValue, onPhoto, onCancel, onApply }: {
  field: FieldCandidate
  opener: HTMLButtonElement
  values: Record<string, string>
  photos: Record<string, File | undefined>
  applying: boolean
  onValue: (id: string, value: string) => void
  onPhoto: (id: string, file?: File) => void
  onCancel: () => void
  onApply: () => void
}) {
  const sheet = useRef<HTMLDivElement>(null)

  useEffect(() => {
    sheet.current?.querySelector<HTMLElement>('input, textarea, button')?.focus()
    return () => {
      if (opener.isConnected) opener.focus()
      else document.querySelector<HTMLElement>('.top-bar button')?.focus()
    }
  }, [opener])

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      onCancel()
    }
    if (event.key !== 'Tab') return
    const focusable = [...(sheet.current?.querySelectorAll<HTMLElement>(
      'input:not(:disabled), textarea:not(:disabled), button:not(:disabled)',
    ) ?? [])]
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last?.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first?.focus()
    }
  }

  return (
    <div className="sheet-layer">
      {/* Tapping outside closes, as the 닫기 button does; the scrim itself is not a control. */}
      <div className="sheet-scrim" aria-hidden="true" onClick={onCancel} />
      <div ref={sheet} className="sheet" role="dialog" aria-modal="true" aria-label={`${field.label} 수정`} onKeyDown={handleKeyDown}>
        <span className="sheet-handle" aria-hidden="true" />
        <div className="sheet-body">
          {field.kind === 'PHOTO' ? (
            <>
              <p className="sheet-title">{field.label}</p>
              <div className="sheet-photo">
                <PhotoTile field={field} file={photos[field.id]} onChange={onPhoto} />
              </div>
            </>
          ) : (
            <FieldInput field={field} values={values} onChange={onValue} />
          )}
        </div>
        <div className="sheet-actions">
          <Button variant="secondary" onClick={onCancel}>닫기</Button>
          <Button onClick={onApply} loading={applying}>수정하기</Button>
        </div>
      </div>
    </div>
  )
}
