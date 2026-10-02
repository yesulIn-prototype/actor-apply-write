import { useState } from 'react'
import { track } from './analytics'
import type { Completed } from './api'
import { preparePdf } from './api'
import { openMailDraft, saveFile, shareFile } from './delivery'

/** Sending and saving the finished file, the same on every screen that finishes one. */
export function useDelivery(completed: Completed | undefined, showToast: (text: string) => void) {
  const [pdfBusy, setPdfBusy] = useState(false)

  async function mail() {
    if (!completed) return
    const result = await shareFile(completed.file)
    if (result === 'shared') track('send_mail', { method: 'share' })
    if (result === 'cancelled') track('send_mail_cancel')
    if (result !== 'unsupported') return
    track('send_mail', { method: 'download' })
    saveFile(completed.downloadUrl, completed.file.name)
    showToast('파일을 저장했어요. 메일에 첨부해서 보내주세요')
    window.setTimeout(() => openMailDraft(completed.file.name.replace(/\.hwp$/i, '')), 800)
  }

  function save() {
    if (!completed) return
    saveFile(completed.downloadUrl, completed.file.name)
    track('save_hwp')
    showToast('한글 파일을 저장했어요')
  }

  async function savePdf() {
    if (!completed) return
    setPdfBusy(true)
    try {
      await preparePdf(completed)
      saveFile(completed.pdfUrl, completed.file.name.replace(/\.hwp$/i, '.pdf'))
      track('save_pdf')
      showToast('PDF를 저장했어요')
    } catch {
      const text = 'PDF로 만들지 못했어요. 한글 파일로 저장해주세요'
      showToast(text)
      track('pdf_error', { reason: text })
    } finally {
      setPdfBusy(false)
    }
  }

  return { mail, save, savePdf, pdfBusy }
}
