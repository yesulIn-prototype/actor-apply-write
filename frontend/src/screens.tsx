import { useEffect, useRef, useState } from 'react'
import type { AnalysisResponse, FieldCandidate } from './document'
import { inputHints, isChanged, isFilled, placeholder, templateBody } from './document'
import { parseTemplate } from './template'
import { TemplateField } from './TemplateField'
import { OutputNameField } from './OutputNameField'
import { AnimatedCount } from './AnimatedCount'
import { BottomCTA, Button, Footer, TopBar } from './ui'

export function UploadScreen({ busy, completedCount, onFile }: {
  busy: boolean
  completedCount?: number
  onFile: (file: File) => void
}) {
  const input = useRef<HTMLInputElement>(null)
  const pick = () => input.current?.click()
  return (
    <>
      <TopBar />
      <section className="content home">
        <h1 className="title">받은 지원서를<br />올려주세요</h1>
        {completedCount !== undefined && completedCount > 0 && (
          <p className="counter" aria-label={`지금까지 배우들이 예술in으로 지원서 ${completedCount.toLocaleString('ko-KR')}개를 완성했어요`}>
            지금까지 배우들이 예술in으로<br />지원서 <AnimatedCount target={completedCount} />를 완성했어요
          </p>
        )}
        <button type="button" className="drop-card" onClick={pick} disabled={busy}>
          <svg width="40" height="40" viewBox="0 0 40 40" aria-hidden="true">
            <rect x="8" y="4" width="24" height="32" rx="5" fill="var(--blue-50)" />
            <path d="M20 26V14m0 0l-5 5m5-5l5 5" fill="none" stroke="var(--blue-500)" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          <strong>{busy ? '파일을 읽고 있어요' : 'HWP 파일 선택'}</strong>
          <small>최대 20MB</small>
        </button>
        {/* No `accept`: iOS greys out .hwp because it has no registered file type. */}
        <input
          ref={input}
          className="visually-hidden"
          type="file"
          aria-label="지원서 파일"
          onChange={(event) => {
            const file = event.target.files?.[0]
            event.target.value = ''
            if (file) onFile(file)
          }}
        />
        <Footer />
      </section>
      <BottomCTA>
        <Button onClick={pick} loading={busy}>파일 선택하기</Button>
      </BottomCTA>
    </>
  )
}

type FillProps = {
  analysis: AnalysisResponse
  values: Record<string, string>
  photos: Record<string, File | undefined>
  outputName: string
  busy: boolean
  onValue: (id: string, value: string) => void
  onPhoto: (id: string, file?: File) => void
  onOutputName: (value: string) => void
  onBack: () => void
  onSubmit: () => void
}

export function FillScreen(props: FillProps) {
  const textFields = props.analysis.fields.filter((field) => field.kind === 'TEXT')
  const photoFields = props.analysis.fields.filter((field) => field.kind === 'PHOTO')
  // One "fill only the blanks" note is enough; repeating it under every template is noise.
  const helpId = textFields.find((field) => field.style === 'TEMPLATE' && !parseTemplate(templateBody(field)))?.id
  const hasInput = (textFields.some((field) => isChanged(field, props.values[field.id]))
    || photoFields.some((field) => props.photos[field.id])) && props.outputName.trim() !== ''

  return (
    <>
      <TopBar onBack={props.onBack} />
      <section className="content">
        <h1 className="title">지원서 내용을<br />채워주세요</h1>
        <p className="file-name">{props.analysis.fileName}</p>
        <OutputNameField value={props.outputName} onChange={props.onOutputName} />

        <div className="field-list">
          {blocks(textFields).map((block) => block.group ? (
            <TableGroup key={block.group} name={block.group} fields={block.fields} values={props.values} helpId={helpId} onChange={props.onValue} />
          ) : (
            <FieldInput key={block.fields[0].id} field={block.fields[0]} values={props.values} help={block.fields[0].id === helpId} onChange={props.onValue} />
          ))}
        </div>

        {photoFields.length > 0 && (
          <>
            <h2 className="section-title">사진</h2>
            <div className="photo-grid">
              {photoFields.map((field) => (
                <PhotoTile key={field.id} field={field} file={props.photos[field.id]} onChange={props.onPhoto} />
              ))}
            </div>
          </>
        )}
        <Footer />
      </section>
      <BottomCTA>
        <Button onClick={props.onSubmit} disabled={!hasInput} loading={props.busy}>완성하기</Button>
      </BottomCTA>
    </>
  )
}

type Block = { group: string; fields: FieldCandidate[] }

/** Keeps document order; a table's fields stay together as one block where the table starts. */
function blocks(fields: FieldCandidate[]): Block[] {
  const result: Block[] = []
  for (const field of fields) {
    const last = result[result.length - 1]
    if (field.group && last?.group === field.group) last.fields.push(field)
    else result.push({ group: field.group, fields: [field] })
  }
  return result
}

/** A table shown row by row. Numbered rows (공연경력 1…11) open two at a time; named rows (초등학교…) all show. */
function TableGroup({ name, fields, values, helpId, onChange }: {
  name: string
  fields: FieldCandidate[]
  values: Record<string, string>
  helpId?: string
  onChange: (id: string, value: string) => void
}) {
  const rows = [...new Set(fields.map((field) => field.row))]
  const numbered = fields.every((field) => /^\d+$/.test(field.rowName))
  const filledRows = rows.map((row, index) =>
    fields.some((field) => field.row === row && isFilled(field, values[field.id])) ? index : -1)
  const lastFilled = Math.max(-1, ...filledRows)
  const [opened, setOpened] = useState(Math.min(2, rows.length))
  const visible = numbered ? rows.slice(0, Math.max(opened, lastFilled + 1)) : rows

  return (
    <section className="table-group" aria-label={name}>
      <h2 className="table-title">{name}</h2>
      {visible.map((row) => {
        const cells = fields.filter((field) => field.row === row)
        return (
          <div className="table-row" key={row}>
            {/* A labelled group ("희망 근무 타임": 1순위 · 2순위 · 3순위) is one row with no title of its own. */}
            {(numbered || cells[0].rowName) && (
              <p className="table-row-title">{numbered ? `${row}` : cells[0].rowName}</p>
            )}
            {cells.map((field) => (
              <FieldInput key={field.id} field={field} label={field.column} values={values} help={field.id === helpId} onChange={onChange} />
            ))}
          </div>
        )
      })}
      {visible.length < rows.length && (
        <button type="button" className="add-row" onClick={() => setOpened(visible.length + 1)}>
          + 줄 추가
        </button>
      )}
    </section>
  )
}

/** A template the parser understands gets chips and inputs; everything else is a text box. */
export function FieldInput({ field, label, values, help, onChange }: {
  field: FieldCandidate
  label?: string
  values: Record<string, string>
  help?: boolean
  onChange: (id: string, value: string) => void
}) {
  const template = field.style === 'TEMPLATE' ? parseTemplate(templateBody(field)) : null
  if (template) {
    return <TemplateField field={field} label={label || field.label} template={template} values={values} onChange={onChange} />
  }
  return <TextField field={field} label={label} value={values[field.id] ?? ''} help={help} onChange={onChange} />
}

function TextField({ field, label, value, help, onChange }: {
  field: FieldCandidate
  label?: string
  value: string
  help?: boolean
  onChange: (id: string, value: string) => void
}) {
  const id = `value-${field.id}`
  const hints = inputHints(field.label)
  // Templates always wrap: "취미:  /특기:  일반사회 소속단체:  / 결혼유무: …" is too long for one line on a phone.
  const multiline = field.multiline || field.style === 'TEMPLATE'
  const common = {
    id,
    value,
    placeholder: placeholder(field),
    'aria-label': label && label !== field.label ? field.label : undefined,
    onChange: (event: { target: { value: string } }) => onChange(field.id, event.target.value),
  }
  return (
    <div className="text-field">
      <label htmlFor={id}>{label || field.label}</label>
      {multiline ? (
        <textarea {...common} rows={field.style === 'TEMPLATE' ? 2 : 4} />
      ) : (
        <input
          {...common}
          type="text"
          enterKeyHint="next"
          inputMode={hints.inputMode}
          autoComplete={hints.autoComplete ?? 'off'}
        />
      )}
      {help && <p className="field-help">빈칸이나 괄호 안에만 채워주세요</p>}
    </div>
  )
}

export function PhotoTile({ field, file, onChange }: {
  field: FieldCandidate
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
