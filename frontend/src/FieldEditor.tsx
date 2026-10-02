import type { FieldCandidate } from './document'
import { FieldInput, PhotoTile } from './screens'

/** One found field of an uploaded form, inside the completed screen's edit sheet. */
export function FieldEditor({ field, values, photos, onValue, onPhoto }: {
  field: FieldCandidate
  values: Record<string, string>
  photos: Record<string, File | undefined>
  onValue: (id: string, value: string) => void
  onPhoto: (id: string, file?: File) => void
}) {
  if (field.kind === 'PHOTO') {
    return (
      <>
        <p className="sheet-title">{field.label}</p>
        <div className="sheet-photo">
          <PhotoTile field={field} file={photos[field.id]} onChange={onPhoto} />
        </div>
      </>
    )
  }
  return <FieldInput field={field} values={values} onChange={onValue} />
}
