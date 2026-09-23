const MAX_BYTES = 8 * 1024 * 1024
const MAX_EDGE = 2400

/**
 * The server only accepts JPEG/PNG up to 12MB and sizes the photo by its pixel dimensions.
 * Phones hand us HEIC, WebP, 20MB originals and JPEGs rotated only by an EXIF tag, so everything
 * except a small PNG is redrawn upright and re-encoded to JPEG in the browser.
 */
export async function preparePhoto(file: File): Promise<File> {
  if (file.type === 'image/png' && file.size <= MAX_BYTES) return file

  const image = await decode(file)
  const scale = Math.min(1, MAX_EDGE / Math.max(image.width, image.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(image.width * scale)
  canvas.height = Math.round(image.height * scale)
  const context = canvas.getContext('2d')
  if (!context) throw new Error('사진을 불러오지 못했어요')
  context.fillStyle = '#fff'
  context.fillRect(0, 0, canvas.width, canvas.height)
  context.drawImage(image, 0, 0, canvas.width, canvas.height)
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.9))
  if (!blob) throw new Error('사진을 불러오지 못했어요')
  const name = file.name.replace(/\.[^.]+$/, '') || 'photo'
  return new File([blob], `${name}.jpg`, { type: 'image/jpeg' })
}

function decode(file: File): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const image = new Image()
    image.onload = () => {
      URL.revokeObjectURL(url)
      resolve(image)
    }
    image.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('이 사진 형식은 쓸 수 없어요. JPG나 PNG로 골라주세요'))
    }
    image.src = url
  })
}
