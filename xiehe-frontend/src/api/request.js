// ============================================================
// Axios 请求封装
// 1. 自动携带 JWT 令牌(请求头 Authorization)
// 2. 统一处理响应: 业务错误提示 / 401跳转登录
// 3. 文件下载(blob)特殊处理: 后端业务错误也是JSON格式,
//    需要从Blob中解析出真实错误信息,避免下载失败时毫无提示
// 4. 上传请求(FormData)自动使用更长超时,避免大文件(200MB)中途被默认30s超时中断
// ============================================================
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  // 默认同域(开发走 Vite 代理,生产走 Nginx 反向代理);
  // 前后端分域部署时通过 VITE_API_BASE 指定后端地址
  baseURL: import.meta.env.VITE_API_BASE || '',
  timeout: 30000            // 默认30秒
})

/** 上传类请求超时(毫秒): 200MB大文件预留足够时间 */
export const UPLOAD_TIMEOUT = 120000

/** 登录失效: 清理本地登录态并跳转登录页 */
function handleAuthFail(msg) {
  localStorage.removeItem('xiehe_token')
  localStorage.removeItem('xiehe_user')
  localStorage.removeItem('xiehe_role')
  router.push('/login')
  ElMessage.error(msg || '登录已失效')
}

// ---------- 请求拦截器: 自动加令牌 / 上传超时分级 ----------
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('xiehe_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // 上传请求(FormData)且未显式指定超时 -> 使用120秒,避免大文件上传被中断
  if (config.data instanceof FormData && !config.timeout) {
    config.timeout = UPLOAD_TIMEOUT
  }
  return config
})

// ---------- 响应拦截器: 统一处理 ----------
request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 二进制数据(文件下载): 需要区分"真实文件"与"错误JSON"
    if (response.config.responseType === 'blob') {
      const contentType = response.headers['content-type'] || ''
      if (contentType.includes('application/json')) {
        // 后端把业务错误(如 403 无权下载 / 404 已删除)包成了JSON,
        // 从Blob中解析出 msg 提示用户,而不是静默失败
        return new Promise((resolve, reject) => {
          const reader = new FileReader()
          reader.onload = () => {
            try {
              const err = JSON.parse(reader.result)
              const msg = err.msg || '操作失败'
              if (err.code === 401) {
                handleAuthFail(msg)
              } else {
                ElMessage.error(msg)
              }
              reject(new Error(msg))
            } catch (e) {
              ElMessage.error('下载失败,请稍后重试')
              reject(new Error('下载失败'))
            }
          }
          reader.onerror = () => {
            ElMessage.error('下载失败,请稍后重试')
            reject(new Error('下载失败'))
          }
          reader.readAsText(response.data)
        })
      }
      // 真实文件内容,原样返回给调用方
      return response
    }
    if (res.code === 200) {
      return res
    }
    // 未登录/登录失效 -> 清理并跳转登录
    if (res.code === 401) {
      handleAuthFail(res.msg)
      return Promise.reject(new Error(res.msg))
    }
    // 静默模式: 调用方自行处理错误提示(如分享页首次探测密码),避免打扰用户
    if (!response.config.silent) {
      ElMessage.error(res.msg || '请求失败')
    }
    return Promise.reject(new Error(res.msg))
  },
  (error) => {
    // HTTP层错误(500/404等)时后端返回的 JSON 也含业务提示,
    // 优先展示后端 msg,而不是原生英文 error.message
    let msg = '网络错误,请检查后端服务是否启动'
    if (error.response) {
      const data = error.response.data
      if (data && data.msg) {
        msg = data.msg
      } else if (error.code === 'ECONNABORTED') {
        msg = '请求超时,请稍后重试'
      } else {
        msg = `请求失败(${error.response.status})`
      }
    } else if (error.code === 'ECONNABORTED') {
      msg = '请求超时,请稍后重试'
    }
    // 静默模式请求不弹提示(调用方自行处理)
    if (!error.config || !error.config.silent) {
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

export default request
