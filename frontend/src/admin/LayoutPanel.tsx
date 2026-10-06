import { useEffect, useState } from 'react'
import { message } from '../shell'
import type { Cell, Layout } from './adminApi'
import { layoutPageUrl, loadLayout } from './adminApi'

type Loaded = { readonly layout: Layout; readonly urls: readonly string[] }

/**
 * The blank form with every cell's address drawn on it ("0.2.1" = table 0, row 2, cell 1), which is how
 * outputs name cells. Nothing is guessed: the operator reads the address and writes it down.
 */
export function LayoutPanel({ vid, cells, revision }: { vid: string; cells: readonly Cell[]; revision: number }) {
  const [loaded, setLoaded] = useState<Loaded>()
  const [error, setError] = useState('')
  const [picked, setPicked] = useState<string>()
  const [copyStatus, setCopyStatus] = useState('')

  async function copyCells() {
    const rows = cells.map((cell) => {
      const span = cell.rowSpan > 1 || cell.columnSpan > 1 ? ` (${cell.rowSpan}×${cell.columnSpan})` : ''

      const text = (cell.text || '(빈 칸)').replace(/&/g, '&amp;').replace(/</g, '&lt;')
        .replace(/>/g, '&gt;').replace(/\|/g, '&#124;').replace(/\r\n|\r|\n/g, '<br>')

      return `| ${cell.address} | ${cell.row}·${cell.column}${span} | ${text} |`
    })

    try {
      await navigator.clipboard.writeText(['| 칸 | 행·열 (병합) | 원문 |', '| --- | --- | --- |', ...rows].join('\n'))
      setCopyStatus('전체 칸 목록을 복사했어요.')
    } catch { // no-excuse-ok: catch - clipboard permission failures are shown to the operator
      setCopyStatus('복사하지 못했어요. 칸 목록을 펼쳐 직접 선택해 복사해주세요.')
    }
  }

  useEffect(() => {
    let active = true
    let urls: string[] = []
    loadLayout(vid)
      .then(async (layout) => {
        urls = await Promise.all(layout.pages.map((page) => layoutPageUrl(vid, page.number)))

        if (active) setLoaded({ layout, urls })
      })
      .catch((reason) => { if (active) setError(message(reason)) })

    return () => {
      active = false
      urls.forEach((url) => URL.revokeObjectURL(url))
    }
  }, [vid, revision])

  const pickedCell = cells.find((cell) => cell.address === picked)

  return (
    <section className="admin-section">
      <h2>2. 칸 위치 확인</h2>
      <p className="admin-help">칸을 누르면 주소가 복사돼요. 정의의 <code>"cell"</code>에 그대로 쓰면 돼요.</p>
      {error && <p className="admin-error" role="alert">{error}</p>}
      {!loaded && !error && <p className="admin-help">원본을 그리는 중…</p>}
      {pickedCell && (
        <p className="admin-picked" aria-live="polite">
          <code>{pickedCell.address}</code> 원문: {pickedCell.text ? `“${pickedCell.text}”` : '(빈 칸)'}
        </p>
      )}
      <div className="admin-pages">
        {loaded?.layout.pages.map((page, index) => (
          <div key={page.number} className="admin-page" style={{ aspectRatio: `${page.width} / ${page.height}` }}>
            <img src={loaded.urls[index]} alt={`${page.number}쪽 원본`} />
            {loaded.layout.cells.filter((box) => box.page === page.number).map((box, boxIndex) => (
              <button
                key={`${box.address}-${boxIndex}`}
                type="button"
                className={box.address === picked ? 'admin-cell picked' : 'admin-cell'}
                title={cells.find((cell) => cell.address === box.address)?.text || '(빈 칸)'}
                style={{
                  left: `${(box.x / page.width) * 100}%`,
                  top: `${(box.y / page.height) * 100}%`,
                  width: `${(box.width / page.width) * 100}%`,
                  height: `${(box.height / page.height) * 100}%`,
                }}
                onClick={() => {
                  setPicked(box.address)
                  void navigator.clipboard?.writeText(box.address).catch(() => undefined)
                }}
              >
                <span>{box.address}</span>
              </button>
            ))}
          </div>
        ))}
      </div>
      <details className="admin-cells">
        <summary>
          칸 목록 ({cells.length}개){' '}
          <button type="button" disabled={cells.length === 0} onClick={(event) => {
            event.preventDefault()
            event.stopPropagation()
            void copyCells()
          }}>전체 복사</button>
        </summary>
        <table className="admin-table">
          <thead><tr><th>칸</th><th>행·열 (병합)</th><th>원문</th></tr></thead>
          <tbody>
            {cells.map((cell) => (
              <tr key={cell.address} className={cell.address === picked ? 'picked' : undefined}>
                <td><code>{cell.address}</code></td>
                <td>{cell.row}·{cell.column}{cell.rowSpan > 1 || cell.columnSpan > 1 ? ` (${cell.rowSpan}×${cell.columnSpan})` : ''}</td>
                <td className="admin-cell-text">{cell.text || <span className="admin-muted">(빈 칸)</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
      <p className="admin-help" role="status">{copyStatus}</p>
    </section>
  )
}
