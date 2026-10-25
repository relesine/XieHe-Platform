// ============================================================
// 后端接口统一封装: 页面只需要调用这里的方法
// ============================================================
import request from './request'

// ---------- 认证 ----------
export const apiRegister = (data) => request.post('/api/auth/register', data)
export const apiLogin = (data) => request.post('/api/auth/login', data)
export const apiLogout = () => request.post('/api/auth/logout')

// ---------- 用户 ----------
export const apiGetInfo = () => request.get('/api/user/info')
export const apiUpdateProfile = (data) => request.put('/api/user/profile', data)
export const apiChangePassword = (data) => request.put('/api/user/password', data)
export const apiUploadAvatar = (formData) => request.post('/api/user/avatar', formData, {
  headers: { 'Content-Type': 'multipart/form-data' }
})
export const apiGetStorage = () => request.get('/api/user/storage')
export const apiUserStats = () => request.get('/api/user/stats')
export const apiSearchUsers = (keyword) => request.get('/api/user/search', { params: { keyword } })

// ---------- 好友 ----------
export const apiFriendList = () => request.get('/api/friend/list')
export const apiFriendAdd = (friendId) => request.post(`/api/friend/add/${friendId}`)
export const apiFriendRemove = (friendId) => request.delete(`/api/friend/${friendId}`)

// ---------- 文件 ----------
export const apiUpload = (formData, onProgress) => request.post('/api/file/upload', formData, {
  headers: { 'Content-Type': 'multipart/form-data' },
  onUploadProgress: (e) => { if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100)) }
})
export const apiMyFiles = (keyword, page, size) => request.get('/api/file/list/mine', { params: { keyword, page, size } })
export const apiPublicFiles = (keyword, page, size) => request.get('/api/file/list/public', { params: { keyword, page, size } })
export const apiSharedFiles = (page, size) => request.get('/api/file/list/shared', { params: { page, size } })
export const apiFavorites = (page, size) => request.get('/api/file/list/favorites', { params: { page, size } })
export const apiTrashFiles = (page, size) => request.get('/api/file/list/trash', { params: { page, size } })
export const apiFileDetail = (id) => request.get(`/api/file/${id}`)
export const apiFileDownload = (id) => request.get(`/api/file/${id}/download`, { responseType: 'blob' })
export const apiFilePreviewText = (id) => request.get(`/api/file/${id}/preview`)
export const apiFileRename = (id, filename) => request.put(`/api/file/${id}/rename`, null, { params: { filename } })
export const apiFileReplace = (id, formData, onProgress) => request.post(`/api/file/${id}/replace`, formData, {
  headers: { 'Content-Type': 'multipart/form-data' },
  onUploadProgress: (e) => { if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100)) }
})
export const apiFileSetPublic = (id, isPublic) => request.put(`/api/file/${id}/public`, null, { params: { isPublic } })
export const apiFileFavorite = (id, isFavorite) => request.put(`/api/file/${id}/favorite`, null, { params: { isFavorite } })
export const apiFileDelete = (id) => request.delete(`/api/file/${id}`)
export const apiFileBatchDelete = (ids) => request.post('/api/file/batch-delete', { ids })
export const apiFileRestore = (id) => request.put(`/api/file/${id}/restore`)
export const apiFileForever = (id) => request.delete(`/api/file/${id}/forever`)
// ---------- 授权 ----------
export const apiGrantPermission = (id, data) => request.post(`/api/file/${id}/permission`, data)
export const apiPermissionList = (id) => request.get(`/api/file/${id}/permission`)
export const apiRemovePermission = (id, userId) => request.delete(`/api/file/${id}/permission/${userId}`)
export const apiBatchGrant = (data) => request.post('/api/file/batch-permission', data)

// ---------- 分享链接 ----------
export const apiCreateShare = (id, expireDays, password) => request.post(`/api/file/${id}/share`, null, { params: { expireDays, password } })
export const apiListShare = (id) => request.get(`/api/file/${id}/share`)
export const apiDeleteShare = (id) => request.delete(`/api/file/${id}/share`)
export const apiBatchShare = (fileIds, expireDays, password) => request.post('/api/file/batch-share', fileIds, { params: { expireDays, password } })
export const apiShareInfo = (token, password, silent) => request.get(`/api/share/${token}`, { params: { password }, silent })
export const apiShareDownload = (token, password) => request.get(`/api/share/${token}/download`, { params: { password }, responseType: 'blob' })

// ---------- 管理后台 ----------
export const apiAdminStats = (refresh = false) => request.get('/api/admin/stats', { params: { refresh } })
export const apiAdminUsers = (params) => request.get('/api/admin/user/list', { params })
export const apiAdminUserStatus = (id, status) => request.put(`/api/admin/user/${id}/status`, null, { params: { status } })
export const apiAdminUserType = (id, userType) => request.put(`/api/admin/user/${id}/type`, null, { params: { userType } })
export const apiAdminFiles = (params) => request.get('/api/admin/file/list', { params })
export const apiAdminFileDelete = (id) => request.delete(`/api/admin/file/${id}`)
export const apiAdminLogs = (params) => request.get('/api/admin/log/list', { params })
