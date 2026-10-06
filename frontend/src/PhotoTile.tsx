import { useEffect, useRef } from 'react'

/** A photo slot on an operator-defined notice form. */
export function PhotoTile({ field, file, onChange }: {
  field: { readonly id: string; readonly label: string }
  file?: File
  onChange: (id: string, file?: File) => void
}) {
  return (
    <div className="photo-tile">
      <label className={file ? 'photo-slot filled' : 'photo-slot'}>
        {file ? (
          <Preview file={file} alt={`${field.label} 미리보기`} />
        ) : (
          <svg width="28" height="28" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 5v14M5 12h14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
          </svg>
        )}
        <input
          className="visually-hidden"
          type="file"
          accept="image/*"
          aria-label={`${field.label} 사진 선택`}
          onChange={(event) => {
            const picked = event.target.files?.[0]
            event.target.value = ''

            if (picked) onChange(field.id, picked)
          }}
        />
      </label>
      {file && (
        <button type="button" className="photo-remove" aria-label={`${field.label} 사진 지우기`} onClick={() => onChange(field.id)}>
          <svg width="14" height="14" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M6 6l12 12M18 6L6 18" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" />
          </svg>
        </button>
      )}
      <span className="photo-label">{field.label}</span>
    </div>
  )
}

function Preview({ file, alt }: { file: File; alt: string }) {
  const image = useRef<HTMLImageElement>(null)
  useEffect(() => {
    const url = URL.createObjectURL(file)

    if (image.current) image.current.src = url

    return () => URL.revokeObjectURL(url)
  }, [file])

  return <img ref={image} alt={alt} />
}
