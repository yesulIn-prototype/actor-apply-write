import { useEffect, useState } from 'react'
import { track } from './analytics'
import type { Completed } from './api'
import { preparePdf, request } from './api'
import { openMailDraft, saveFile, shareFile } from './delivery'

export type DeliveryOptions = {
  /** Send and save the PDF first (a standard-form notice: applicants send a profile). */
  readonly pdfFirst?: boolean
  /** The notice's address and mail subject, for the mail draft opened when the file can't be shared. */
  readonly mailTo?: string
  readonly mailSubject?: string
}

type PdfFile = { readonly source: File; readonly file?: File; readonly failed?: boolean }

/** Sending and saving the finished file, the same on every screen that finishes one. */
export function useDelivery(completed: Completed | undefined, showToast: (text: string) => void, options: DeliveryOptions = {}) {
  const [pdfBusy, setPdfBusy] = useState(false)
  const [pdf, setPdf] = useState<PdfFile>()
  const pdfFirst = options.pdfFirst === true

  // The share sheet only opens straight from a tap (iOS drops the gesture after an await), so the PDF to
  // send is fetched as soon as the file is done, not when the button is pressed.
  useEffect(() => {
    if (!pdfFirst || !completed) return
    let active = true
    loadPdf(completed)
      .then((file) => { if (active) setPdf({ source: completed.file, file }) })
      .catch(() => { if (active) setPdf({ source: completed.file, failed: true }) })
    return () => { active = false }
  }, [pdfFirst, completed])

  async function mail() {
    if (!completed) return
    const ready = pdf?.source === completed.file ? pdf : undefined
    if (pdfFirst && !ready) {
      showToast('PDF를 준비하고 있어요. 잠시 후 다시 눌러주세요')
      return
    }
    const file = ready?.file ?? completed.file
    const result = await shareFile(file)
    if (result === 'shared') track('send_mail', { method: 'share' })
    if (result === 'cancelled') track('send_mail_cancel')
    if (result !== 'unsupported') return
    track('send_mail', { method: 'download' })
    saveFile(ready?.file ? completed.pdfUrl : completed.downloadUrl, file.name)
    showToast('파일을 저장했어요. 메일에 첨부해서 보내주세요')
    const subject = options.mailSubject || file.name.replace(/\.(hwp|pdf)$/i, '')
    window.setTimeout(() => openMailDraft(subject, options.mailTo ?? ''), 800)
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
      saveFile(completed.pdfUrl, pdfName(completed))
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

function pdfName(completed: Completed): string {
  return completed.file.name.replace(/\.hwp$/i, '.pdf')
}

async function loadPdf(completed: Completed): Promise<File> {
  const response = await request(completed.pdfUrl, { method: 'GET' })
  return new File([await response.blob()], pdfName(completed), { type: 'application/pdf' })
}
