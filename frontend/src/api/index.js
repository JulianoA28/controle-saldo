import http from './http'

/** Spring pageable params: page is zero-based, size capped at 100 by the backend. */
function pageParams({ page = 1, itemsPerPage = 20 } = {}) {
  return { page: page - 1, size: itemsPerPage }
}

export const agenciesApi = {
  search: (search, size = 20) => http.get('/agencies', { params: { search, size } }).then((r) => r.data)
}

export const itemsApi = {
  list: (options, filters) =>
    http.get('/items', { params: { ...pageParams(options), ...filters } }).then((r) => r.data),
  get: (id) => http.get(`/items/${id}`).then((r) => r.data),
  create: (body) => http.post('/items', body).then((r) => r.data),
  update: (id, body) => http.put(`/items/${id}`, body).then((r) => r.data),
  adjust: (id, body) => http.post(`/items/${id}/adjustments`, body).then((r) => r.data),
  adjustments: (id, options) =>
    http.get(`/items/${id}/adjustments`, { params: pageParams(options) }).then((r) => r.data)
}

export const ordersApi = {
  list: (options, filters) =>
    http.get('/orders', { params: { ...pageParams(options), ...filters } }).then((r) => r.data),
  get: (id) => http.get(`/orders/${id}`).then((r) => r.data),
  /** Resolves with { order, created }: created=false means the server returned an existing order (resend). */
  place: (body) => http.post('/orders', body).then((r) => ({ order: r.data, created: r.status === 201 })),
  cancel: (id, reason) => http.post(`/orders/${id}/cancel`, { reason }).then((r) => r.data)
}

export const dataloadApi = {
  summary: () => http.get('/dataload/summary').then((r) => r.data),
  rejections: (options, filters) =>
    http.get('/dataload/rejections', { params: { ...pageParams(options), ...filters } }).then((r) => r.data),
  divergences: (options, filters) =>
    http.get('/dataload/divergences', { params: { ...pageParams(options), ...filters } }).then((r) => r.data)
}
