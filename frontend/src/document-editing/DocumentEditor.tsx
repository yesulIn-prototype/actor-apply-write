import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'
import type { Completed, Hotspot, PreviewPage as Page } from '../api'
import { previewPageUrl } from '../api'
import { PreviewPage } from '../PreviewPage'
import { PreviewZoom } from '../PreviewZoom'
import type { Change, EditView, NumberedRegion } from './api'
import { editDocument, fetchEditing, numberRegions } from './api'
import { EditSheet } from './EditSheet'
import './editing.css'

export function DocumentEditor({ completed, onEdited, onLocked, fallback }: {
  readonly completed: Completed
  readonly onEdited: (file: Completed) => void
  readonly onLocked: (locked: boolean) => void
  readonly fallback?: ReactNode
}) {
  const [loaded, setLoaded] = useState<{ file: File; view: EditView }>()
  const [error, setError] = useState('')
  const [status, setStatus] = useState('')
  const [editing, setEditing] = useState<{ region: NumberedRegion; opener: HTMLButtonElement; revision: string }>()
  const [zoom, setZoom] = useState<{ page: Page; opener: HTMLButtonElement }>()
  const [busy, setBusy] = useState(false)
  const [refresh, setRefresh] = useState(0)
  const [selectedId, setSelectedId] = useState<string>()
  const view = loaded?.file === completed.file ? loaded.view : undefined
  const regions = numberRegions(view?.regions ?? [])
  const labels = new Map(regions.map((region) => [region.id, `${region.number} ${region.text.replace(/\s+/g, ' ').slice(0, 35) || '빈 영역'}`]))
  const hotspots = regions.flatMap((region) => region.boxes.map((box) => ({ ...box, fieldId: region.id, marker: region.number })))

  useEffect(() => {
    let active = true
    fetchEditing(completed).then((view) => { if (active) setLoaded({ file: completed.file, view }) })
      .catch((reason) => { if (active) setError(reason instanceof Error ? reason.message : '미리보기를 불러오지 못했어요') })

    return () => { active = false }
  }, [completed, refresh])
  useEffect(() => {
    onLocked(Boolean(editing || zoom || busy))

    return () => onLocked(false)
  }, [editing, zoom, busy, onLocked])

  function openRegion(region: NumberedRegion, opener: HTMLButtonElement) {
    if (view) { setEditing({ region, opener, revision: view.revision }); setSelectedId(region.id); setError('') }
  }

  function open(hotspot: Hotspot, opener: HTMLButtonElement) {
    const region = regions.find((candidate) => candidate.id === hotspot.fieldId)

    if (region) openRegion(region, opener)
  }

  async function apply(changes: readonly Change[]): Promise<boolean> {
    if (!editing) return false
    setBusy(true)

    try {
      onEdited(await editDocument(completed, editing.revision, changes))
      setSelectedId(changes[changes.length - 1]?.id)
      setStatus('수정한 지원서로 바뀌었어요. 아래 미리보기와 저장 파일에 반영됐어요.')

      return true
    } catch (reason) {
      setRefresh((count) => count + 1)
      throw reason
    } finally { setBusy(false) }
  }

  return <>
    <div className="document-editor">
      {view?.limitation && <p className="edit-help">{view.limitation}</p>}
      {status && <p className="edit-success" role="status">{status}</p>}
      {error && <p className="edit-error" role="alert">{error}</p>}
      {error && <button className="edit-small-button" type="button" onClick={() => { setError(''); setRefresh((count) => count + 1) }}>미리보기 다시 불러오기</button>}
      {!view && !error && <div className="preview-state" role="status">미리보기를 만들고 있어요</div>}
      {!view && error && fallback}
      <div className="preview">
        {view?.pages.map((page) => <PreviewPage key={page.number} page={page} labels={labels} selectedId={selectedId}
          hotspots={[]}
          imageUrl={previewPageUrl(completed.documentId, page.number, view.revision)}
          onEdit={open} onZoom={(opener) => setZoom({ page, opener })} />)}
      </div>
    </div>
    {zoom && view && createPortal(<PreviewZoom page={zoom.page} opener={zoom.opener} labels={labels}
      hotspots={hotspots.filter((spot) => spot.page === zoom.page.number)}
      imageUrl={previewPageUrl(completed.documentId, zoom.page.number, view.revision)}
      onClose={() => setZoom(undefined)} onEdit={(spot) => { setZoom(undefined); open(spot, zoom.opener) }} />, document.body)}
    {editing && createPortal(<EditSheet key={editing.region.id} region={editing.region} regions={regions}
      opener={editing.opener} onClose={() => {
        const opener = editing.opener
        setEditing(undefined); onLocked(false)
        requestAnimationFrame(() => { if (opener.isConnected) opener.focus() })
      }} onApply={apply} />, document.body)}
  </>
}
