import api from './axios'

export const getStats = () =>
  api.get('/admin/stats').then((r) => r.data)

export const getLogs = ({ stage, verdict, before, limit = 50 } = {}) =>
  api.get('/admin/moderation-logs', { params: { stage, verdict, before, limit } }).then((r) => r.data)

export const getBadWords = () =>
  api.get('/admin/keywords').then((r) => r.data)

export const addBadWord = (word) =>
  api.post('/admin/keywords', { word }).then((r) => r.data)

export const deleteBadWord = (id) =>
  api.delete(`/admin/keywords/${id}`).then((r) => r.data)

