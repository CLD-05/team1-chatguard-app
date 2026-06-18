import api from './axios'

export const getStats = () =>
  api.get('/admin/stats').then((r) => r.data)

export const getLogs = ({ stage, verdict, page = 0, size = 50 } = {}) =>
  api.get('/admin/moderation-logs', { params: { stage, verdict, page, size } }).then((r) => r.data)

export const getBadWords = () =>
  api.get('/admin/bad-words').then((r) => r.data)

export const addBadWord = (word) =>
  api.post('/admin/bad-words', { word }).then((r) => r.data)

export const deleteBadWord = (id) =>
  api.delete(`/admin/bad-words/${id}`).then((r) => r.data)

export const uploadBadWords = (file) => {
  const form = new FormData()
  form.append('file', file)
  return api.post('/admin/bad-words/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }).then((r) => r.data)
}
