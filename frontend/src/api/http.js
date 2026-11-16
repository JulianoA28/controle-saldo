import axios from 'axios'

/**
 * Axios instance for the backend. Every request goes through /api
 * (Vite proxy in dev, nginx in Docker), so there is no CORS to configure.
 */
const http = axios.create({ baseURL: '/api' })

/**
 * Normalizes failures into the backend error contract { code, message, details }
 * plus the HTTP status, so views never need to dig into axios internals.
 */
http.interceptors.response.use(
  (response) => response,
  (error) => {
    const response = error.response
    const body = response && response.data
    const isApiError = body && typeof body === 'object' && 'code' in body
    return Promise.reject({
      status: response ? response.status : 0,
      code: isApiError ? body.code : 'NETWORK_ERROR',
      message: isApiError ? body.message : 'Could not reach the server.',
      details: isApiError ? body.details : []
    })
  }
)

export default http
