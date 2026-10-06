import { track, withCampaign } from './analytics'
import type { Platform } from './platform'
import { externalBrowserUrl } from './platform'

export function InAppNotice({ platform, resumeUrl }: { platform: Platform; resumeUrl?: string }) {
  if (!platform.inApp) return null
  const external = externalBrowserUrl(platform, withCampaign(platform, resumeUrl ?? window.location.href))
  const opened = () => track('open_external_browser', { from: resumeUrl ? 'done' : 'start' })
  const menu = '오른쪽 위 ··· 에서 ‘외부 브라우저로 열기’를 눌러주세요'

  if (resumeUrl) {
    return (
      <div className="in-app-notice" role="note">
        <span>저장이나 메일 보내기가 안 되면 브라우저에서 이어서 할 수 있어요{platform.os === 'ios' ? `. 안 열리면 ${menu}` : ''}</span>
        {external && <a href={external} onClick={opened}>브라우저에서 이어하기</a>}
      </div>
    )
  }

  return (
    <div className="in-app-notice in-app-notice-start" role="note">
      <span>
        <strong>{platform.os === 'ios' ? 'Safari' : '인터넷 브라우저'}에서 열어주세요</strong>
        앱 안에서는 완성한 파일을 저장하거나 보내지 못할 수 있어요.{platform.os === 'ios' ? ` 버튼이 안 되면 ${menu}` : ''}
      </span>
      {external && <a href={external} onClick={opened}>{platform.os === 'ios' ? 'Safari로 열기' : '브라우저로 열기'}</a>}
    </div>
  )
}
