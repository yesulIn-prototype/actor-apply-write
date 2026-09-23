import type { Hotspot, PreviewPage as Page } from './api'
import type { FieldCandidate } from './document'

type Props = {
  readonly page: Page
  readonly hotspots: readonly Hotspot[]
  readonly fields: ReadonlyMap<string, FieldCandidate>
  readonly imageUrl: string
  readonly enlarged?: boolean
  readonly onEdit: (hotspot: Hotspot, opener: HTMLButtonElement) => void
  readonly onZoom?: (opener: HTMLButtonElement) => void
}

export function PreviewPage({ page, hotspots, fields, imageUrl, enlarged = false, onEdit, onZoom }: Props) {
  return (
    <div className="preview-page" style={{ aspectRatio: `${page.width} / ${page.height}` }}>
      <img src={imageUrl} alt={`${page.number}쪽 ${enlarged ? '확대 ' : ''}미리보기`} />
      {onZoom && (
        <button
          type="button"
          className="preview-page-open"
          aria-label={`${page.number}쪽 미리보기 확대`}
          onClick={(event) => onZoom(event.currentTarget)}
        >
          <span aria-hidden="true">↗ 확대</span>
        </button>
      )}
      {hotspots.map((hotspot, index) => (
        <button
          key={`${hotspot.fieldId}-${index}`}
          type="button"
          className="hotspot"
          aria-label={`${fields.get(hotspot.fieldId)?.label ?? '칸'} 수정`}
          style={{
            left: `${(hotspot.x / page.width) * 100}%`,
            top: `${(hotspot.y / page.height) * 100}%`,
            width: `${(hotspot.width / page.width) * 100}%`,
            height: `${(hotspot.height / page.height) * 100}%`,
          }}
          onClick={(event) => onEdit(hotspot, event.currentTarget)}
        />
      ))}
    </div>
  )
}
