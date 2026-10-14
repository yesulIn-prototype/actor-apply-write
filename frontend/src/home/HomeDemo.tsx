import './demo.css'

/**
 * A looping sketch of one notice link: fill two answers and a photo, tap the button, get the files.
 * Drawn in CSS so it stays sharp and needs no screenshot of real data. Without motion it rests on the filled form.
 */
export function HomeDemo() {
  return (
    <div
      className="home-demo"
      role="img"
      aria-label="지원 링크에서 이름·연락처와 사진을 채우고 지원서 만들기를 누르면 HWP·PDF 파일이 만들어지는 모습"
    >
      <div className="demo-phone" aria-hidden="true">
        <div className="demo-screen">
          <p className="demo-title">연극 &lt;봄날의 극장&gt; 지원서</p>
          <div className="demo-row">
            <div className="demo-fields">
              <span className="demo-label">이름</span>
              <span className="demo-input"><span className="demo-typed demo-typed-name">김예술</span></span>
              <span className="demo-label">연락처</span>
              <span className="demo-input"><span className="demo-typed demo-typed-phone">010-1234-5678</span></span>
            </div>
            <span className="demo-photo">
              <svg viewBox="0 0 60 76" width="60" height="76">
                <rect width="60" height="76" fill="#dbe8fb" />
                <circle cx="30" cy="30" r="13" fill="#9fbde8" />
                <path d="M6 76c2-16 12-24 24-24s22 8 24 24Z" fill="#9fbde8" />
              </svg>
            </span>
          </div>
          <span className="demo-label">출연 경력</span>
          <span className="demo-lines"><i /><i /><i /></span>
          <span className="demo-button">지원서 만들기</span>
          <div className="demo-result">
            <span className="demo-check">✓</span>
            <p>지원서 파일이 만들어졌어요</p>
            <span className="demo-files">
              <span className="demo-file demo-file-hwp">HWP</span>
              <span className="demo-file demo-file-pdf">PDF</span>
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}
