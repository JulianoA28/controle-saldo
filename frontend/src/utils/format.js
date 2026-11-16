const dateTime = new Intl.DateTimeFormat('en-GB', { dateStyle: 'short', timeStyle: 'short' })
const number = new Intl.NumberFormat('en-US')

export const formatDateTime = (value) => (value ? dateTime.format(new Date(value)) : '')
export const formatNumber = (value) => (value === null || value === undefined ? '' : number.format(value))

/** One readable line per error detail, whatever its shape. */
export function describeDetail(detail) {
  if (detail === null || typeof detail !== 'object') return String(detail)
  if ('field' in detail) return `${detail.field}: ${detail.message}`
  if ('itemCode' in detail && 'available' in detail) {
    const wanted = 'requested' in detail ? `requested ${detail.requested}` : `delta ${detail.delta}`
    return `${detail.itemCode}: ${wanted}, available ${detail.available}`
  }
  return JSON.stringify(detail)
}
