import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 8000 })

http.interceptors.request.use(config => {
  const token = sessionStorage.getItem('token')
  if (token) config.headers.authorization = token
  return config
})

http.interceptors.response.use(
  response => {
    const body = response.data
    if (body && typeof body.success === 'boolean') {
      if (!body.success) return Promise.reject(new Error(body.errorMsg || '请求失败'))
      return body.data
    }
    return body
  },
  error => {
    if (error.response?.status === 401) {
      sessionStorage.removeItem('token')
      if (location.pathname !== '/login') location.href = '/login'
      return Promise.reject(new Error('请先登录'))
    }
    return Promise.reject(new Error(error.response?.data?.errorMsg || '服务暂时不可用'))
  }
)

export default http
