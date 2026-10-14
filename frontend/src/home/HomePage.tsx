import type { ReactNode } from 'react'
import { BottomCTA, Footer, TopBar } from '../ui'
import { HomeDemo } from './HomeDemo'
import './home.css'

/** The KakaoTalk open chat where new notices and their links are posted; the main yesulin.art site uses the same room. */
export const OPEN_CHAT_URL = 'https://open.kakao.com/o/pCRrDXOi'

type Step = { readonly title: string; readonly body: string; readonly icon: ReactNode }

const STEPS: readonly Step[] = [
  {
    title: '공고의 지원 링크 열기',
    body: '공고 알림에 함께 오는 ‘지원서 작성’ 링크를 눌러요. 앱 설치나 회원가입은 없어요.',
    icon: <path d="M10 14a4 4 0 0 0 5.66 0l3-3a4 4 0 0 0-5.66-5.66l-1 1M14 10a4 4 0 0 0-5.66 0l-3 3a4 4 0 0 0 5.66 5.66l1-1" />,
  },
  {
    title: '내용과 사진 채우기',
    body: '공고가 요구하는 항목만 물어봐요. 사진을 뺀 입력 내용은 이 브라우저에 7일 동안 저장돼 이어 쓸 수 있어요.',
    icon: <><rect x="4" y="3" width="16" height="18" rx="3" /><path d="M8 8h8M8 12h8M8 16h5" /></>,
  },
  {
    title: '미리 보고 바로 고치기',
    body: '완성된 지원서를 보면서 고칠 곳을 누르면 그 자리에서 바로 수정돼요.',
    icon: <><path d="M2.5 12S6 5 12 5s9.5 7 9.5 7-3.5 7-9.5 7-9.5-7-9.5-7Z" /><circle cx="12" cy="12" r="3" /></>,
  },
  {
    title: 'HWP·PDF로 저장하고 지원',
    body: '파일을 저장하거나 메일 앱으로 바로 보내요. 공고에 적힌 보낼 주소와 메일 제목도 알려 드려요.',
    icon: <><path d="M4 6l8 6 8-6" /><rect x="3" y="5" width="18" height="14" rx="3" /></>,
  },
]

/** The root address: what a notice link does, and the open chat where those links are shared. */
export function HomePage() {
  return (
    <main className="app">
      <TopBar />
      <section className="content home">
        <p className="home-eyebrow">연극·뮤지컬 배우를 위한 지원서 작성</p>
        <h1 className="home-title">오디션 지원서,{' '}<br />폰에서 바로 쓰고 보내요</h1>
        <p className="home-lead">공고마다 준비된 지원 링크에서 내용과 사진을 채우면, 공고에 맞는 지원서를 HWP·PDF 파일로 만들어 드려요. PC를 켜지 않아도 돼요.</p>
        <HomeDemo />

        <h2 className="home-heading">이렇게 써요</h2>
        <ol className="home-steps">
          {STEPS.map((step, index) => (
            <li key={step.title} className="home-step" style={{ animationDelay: `${index * 80}ms` }}>
              <span className="home-step-icon" aria-hidden="true">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                  {step.icon}
                </svg>
              </span>
              <div>
                <h3>{step.title}</h3>
                <p>{step.body}</p>
              </div>
            </li>
          ))}
        </ol>

        <h2 className="home-heading">공고는 오픈채팅방에서 받아요</h2>
        <p className="home-lead">OTR에 올라온 연극·뮤지컬 오디션 공고를 페이·마감일과 지원서 작성 링크까지 함께 올려 드려요.</p>
        <ChatPreview />
        <Footer />
      </section>
      <BottomCTA>
        <a className="button home-chat-button" href={OPEN_CHAT_URL} target="_blank" rel="noopener noreferrer">
          <ChatIcon />
          오픈채팅방에서 공고 받기
          <span className="visually-hidden">(새 창에서 열림)</span>
        </a>
      </BottomCTA>
    </main>
  )
}

/** What a posted notice looks like in the room. The notice is made up. */
function ChatPreview() {
  return (
    <figure className="home-chat" aria-label="오픈채팅방에 올라오는 공고 알림 예시">
      <div className="home-chat-bubble" aria-hidden="true">
        <strong>🔔 예술in 오디션 공고 알림 · 1건</strong>
        <span>[연극] 창작극 &lt;봄날의 극장&gt; 배우 모집</span>
        <span>💰 페이: 협의<br />📅 마감: 2026-11-30</span>
        <span>🔎 공고 자세히 보기<br /><u>yesulin.art/posts/…</u></span>
        <span>✍️ 지원서 편하게 작성하기<br /><u>apply.yesulin.art/apply/…</u></span>
      </div>
      <figcaption>예시 알림이에요. 실제 공고는 방에서 확인해 주세요.</figcaption>
    </figure>
  )
}

function ChatIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M12 3C6.48 3 2 6.58 2 11c0 2.83 1.84 5.31 4.6 6.73l-.93 3.4c-.08.3.26.54.52.37l4.03-2.66c.58.08 1.17.12 1.78.12 5.52 0 10-3.58 10-8S17.52 3 12 3Z" />
    </svg>
  )
}
