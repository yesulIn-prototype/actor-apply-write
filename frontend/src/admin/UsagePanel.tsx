import type { UsageSnapshot } from './adminApi'

/** Totals plus what each number counts, the same three columns as the notice list. */
export function UsagePanel({ snapshot }: { readonly snapshot: UsageSnapshot }) {
  const { counts } = snapshot

  return (
    <div className="admin-usage" aria-label="전체 공고 이용량">
      <div className="admin-usage-totals">
        <div><strong>페이지 조회 {counts.views.toLocaleString()}회</strong><p>지원 링크를 열어 양식을 불러온 횟수</p></div>
        <div><strong>HWP 생성 {counts.hwp.toLocaleString()}개</strong><p>‘지원서 만들기’까지 마친 지원서 수</p></div>
        <div><strong>PDF 변환 {counts.pdf.toLocaleString()}개</strong><p>완성 화면에서 PDF까지 만든 지원서 수</p></div>
      </div>
      <p className="admin-help">{new Date(snapshot.startedAt).toLocaleString('ko-KR')}부터 집계 · 삭제된 공고 포함 · 페이지 조회는 조회수 기능을 배포한 뒤부터 세요.</p>
      <ul className="admin-help admin-usage-rules">
        <li><b>페이지 조회</b>: 공개된 <code>/apply/{'{vid}'}</code> 링크를 열 때마다 1회. 새로고침·같은 사람의 재방문도 세고, 카톡 등의 링크 미리보기는 세지 않아요.</li>
        <li><b>HWP 생성</b>: 배우가 입력을 마치고 ‘지원서 만들기’를 눌러 서버가 HWP를 처음 만든 지원서마다 1개. 고쳐서 다시 만들기와 운영자 테스트는 세지 않아요. 파일을 저장했거나 메일로 보냈는지는 알 수 없어요.</li>
        <li><b>PDF 변환</b>: 완성된 지원서의 PDF가 처음 만들어질 때 1개. ‘PDF 저장’을 누르거나, PDF를 먼저 보내는 공고에서 완성 화면이 열리면 만들어져요.</li>
      </ul>
    </div>
  )
}
