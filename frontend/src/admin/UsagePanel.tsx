import type { UsageSnapshot } from './adminApi'

export function UsagePanel({ snapshot }: { readonly snapshot: UsageSnapshot }) {
  const { counts } = snapshot

  return (
    <div className="admin-usage" aria-label="전체 공고 이용량">
      <div className="admin-usage-totals">
        <div><strong>이용 브라우저 {counts.visitors.toLocaleString()}개</strong><p>지원서를 만든 익명 브라우저 수</p></div>
        <div><strong>생성 파일 {(counts.hwp + counts.pdf).toLocaleString()}개</strong><p>HWP {counts.hwp.toLocaleString()}개 · PDF {counts.pdf.toLocaleString()}개</p></div>
      </div>
      <p className="admin-help">{new Date(snapshot.startedAt).toLocaleString('ko-KR')}부터 집계 · 삭제된 공고 포함</p>
      <p className="admin-help">작업마다 HWP·PDF를 각각 처음 만들 때 1개씩 세요. 재생성·반복 다운로드·운영자 테스트는 제외해요.</p>
      <p className="admin-help">기기·브라우저가 다르거나 저장소를 지우면 같은 사람도 다시 셀 수 있어요. 실제 사람 수와는 달라요.</p>
      {counts.unidentifiedJobs > 0 && <p className="admin-help">브라우저 식별이 없는 작업 {counts.unidentifiedJobs}건은 파일 수에만 포함돼요.</p>}
    </div>
  )
}
