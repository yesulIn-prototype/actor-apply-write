export type ShareResult = 'shared' | 'cancelled' | 'unsupported'

/** Uses a real URL instead of a blob: URL so in-app browsers can hand it to their download manager. */
export function saveFile(downloadUrl: string, fileName: string) {
  const anchor = document.createElement('a')
  anchor.href = downloadUrl
  anchor.download = fileName
  anchor.rel = 'noopener'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
}

/**
 * Opens the OS share sheet with the HWP attached, so the actor sends it from their own mail app.
 * Must run synchronously inside a click handler — iOS drops the user gesture after any await.
 */
export async function shareFile(file: File): Promise<ShareResult> {
  const data: ShareData = { files: [file], title: file.name.replace(/\.hwp$/i, '') }
  if (!navigator.canShare?.(data)) return 'unsupported'
  try {
    await navigator.share(data)
    return 'shared'
  } catch (reason) {
    if (reason instanceof DOMException && reason.name === 'AbortError') return 'cancelled'
    return 'unsupported'
  }
}

export function openMailDraft(subject: string) {
  window.location.href = `mailto:?subject=${encodeURIComponent(subject)}`
}
