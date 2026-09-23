import type { ReactNode } from 'react'

export function TopBar({ onBack, children }: { onBack?: () => void; children?: ReactNode }) {
  return (
    <header className="top-bar">
      {onBack ? (
        <button type="button" className="icon-button" aria-label="뒤로" onClick={onBack}>
          <svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M15 5l-7 7 7 7" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
      ) : (
        <span className="brand">예술<b>in</b></span>
      )}
      {children}
    </header>
  )
}

type ButtonProps = {
  children: ReactNode
  onClick?: () => void
  disabled?: boolean
  loading?: boolean
  variant?: 'primary' | 'secondary'
}

export function Button({ children, onClick, disabled, loading, variant = 'primary' }: ButtonProps) {
  return (
    <button
      type="button"
      className={`button button-${variant}`}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      onClick={onClick}
    >
      <span className={loading ? 'button-label hidden' : 'button-label'}>{children}</span>
      {loading && <span className="dots" aria-hidden="true"><i /><i /><i /></span>}
    </button>
  )
}

export const INQUIRY_URL = 'https://pf.kakao.com/_pbTBX'

export function Footer() {
  return (
    <footer className="footer">
      <p>입력한 정보는 지원서 작성에만 쓰이고 30분 뒤 자동 삭제돼요</p>
      <a href={INQUIRY_URL} target="_blank" rel="noopener noreferrer">
        카카오톡 문의
        <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M9 5l7 7-7 7" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </a>
    </footer>
  )
}

export function BottomCTA({ children }: { children: ReactNode }) {
  return <div className="bottom-cta">{children}</div>
}

export function Toast({ message }: { message: string }) {
  return (
    <div className="toast-region" aria-live="polite">
      {message && <div className="toast" role="status">{message}</div>}
    </div>
  )
}
