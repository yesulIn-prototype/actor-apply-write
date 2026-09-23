export function OutputNameField({ value, onChange }: {
  value: string
  onChange: (value: string) => void
}) {
  return (
    <div className="text-field output-name">
      <label htmlFor="output-file-name">완성 파일 이름</label>
      <input
        id="output-file-name"
        type="text"
        value={value}
        maxLength={104}
        autoComplete="off"
        onChange={(event) => onChange(event.target.value)}
      />
      <p className="field-help">한글·PDF 저장에 함께 적용돼요</p>
    </div>
  )
}
