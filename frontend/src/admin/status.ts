import type { Status } from './adminApi'

export const STATUS_LABEL: Readonly<Record<Status, string>> = {
  DRAFT: '준비 중',
  PUBLISHED: '공개 중',
  CLOSED: '공개 종료',
  DELETED: '삭제됨',
}
