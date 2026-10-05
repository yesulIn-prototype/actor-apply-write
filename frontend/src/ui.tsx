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
        <img className="brand" src="/logo.png" alt="예술in" width="56" height="32" />
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

export function Footer({ draftSaved = false }: { readonly draftSaved?: boolean }) {
  return (
    <footer className="footer">
      <p>서버의 사진·완성 파일은 30분 뒤 삭제돼요.{draftSaved && ' 입력 초안은 이 브라우저에 7일간 보관돼요.'}</p>
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
