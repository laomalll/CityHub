export const formatPrice = value => (Number(value || 0) / 100).toFixed(2)

export const imageUrl = value => {
  if (!value) return '/imgs/icons/default-icon.png'
  return value.split(',')[0]
}

export const toTimestamp = value => {
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value
    return new Date(year, month - 1, day, hour, minute, second).getTime()
  }
  return new Date(value).getTime()
}

export const formatDate = value => {
  if (!value) return ''
  const date = new Date(toTimestamp(value))
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}
