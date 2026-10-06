import { useState } from 'react'
import { saveFile } from '../delivery'
import { message } from '../shell'
import type { Restored } from './adminApi'
import { downloadBackup, restoreBackup } from './adminApi'

/**
 * Every notice's source, definition and publish state as one zip, and putting such a zip back. Restoring adds
 * only the notices this server lacks; one that already exists is never overwritten.
 */
export function BackupPanel({ onRestored }: { onRestored: () => void }) {
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<Restored>()
  const [error, setError] = useState('')

  async function download() {
    setBusy(true)
    setError('')

    try {
      const backup = await downloadBackup()
      saveFile(backup.url, backup.name)
      window.setTimeout(() => URL.revokeObjectURL(backup.url), 60_000)
    } catch (reason) {
      setError(message(reason))
    } finally {
      setBusy(false)
    }
  }

  async function restore(file: File) {
    setBusy(true)
    setError('')
    setResult(undefined)

    try {
      setResult(await restoreBackup(file))
      onRestored()
    } catch (reason) {
      setError(message(reason))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="admin-section">
      <h2>백업</h2>
      <p className="admin-help">
        모든 공고의 원본·양식 정의·공개 상태를 zip 하나로 받아요. 서버 데이터를 잃으면 이 파일을 올려 되살릴 수 있어요.
        이미 있는 공고는 덮어쓰지 않아요.
      </p>
      <div className="admin-row">
        <button type="button" onClick={() => void download()} disabled={busy}>백업 내려받기</button>
        <label className="admin-file">
          {busy ? '처리 중…' : '백업 파일로 복원'}
          <input
            type="file"
            accept=".zip,application/zip"
            disabled={busy}
            onChange={(event) => {
              const file = event.target.files?.[0]
              event.target.value = ''

              if (file) void restore(file)
            }}
          />
        </label>
      </div>
      {result && (
        <p className="admin-help" role="status">
          되살린 공고: {result.restored.length > 0 ? result.restored.join(', ') : '없음'}
          {result.skipped.length > 0 && ` · 이미 있어서 건너뜀: ${result.skipped.join(', ')}`}
        </p>
      )}
      {error && <p className="admin-error" role="alert">{error}</p>}
    </section>
  )
}
