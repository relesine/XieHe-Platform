// ============================================================
// 通用工具函数
// ============================================================

/** 字节数格式化为人类可读: 1024 -> 1.0 KB */
export function formatSize(bytes) {
  if (bytes === null || bytes === undefined) return '-'
  if (bytes < 1024) return bytes + ' B'
  const units = ['KB', 'MB', 'GB', 'TB']
  let size = bytes
  let i = -1
  do {
    size /= 1024
    i++
  } while (size >= 1024 && i < units.length - 1)
  return size.toFixed(1) + ' ' + units[i]
}

/** 文件类型映射为 Element Plus 标签类型 */
export function fileTypeTag(type) {
  const map = {
    DOC: { label: '文档', cls: 'file-tag-doc' },
    IMAGE: { label: '图片', cls: 'file-tag-image' },
    ZIP: { label: '压缩包', cls: 'file-tag-zip' },
    VIDEO: { label: '视频', cls: 'file-tag-video' }
  }
  return map[type] || { label: type, cls: 'file-tag-doc' }
}

/** 文件类型映射为图标名 */
export function fileTypeIcon(type) {
  const map = {
    DOC: 'Document',
    IMAGE: 'Picture',
    ZIP: 'FolderOpened',
    VIDEO: 'VideoCamera'
  }
  return map[type] || 'Document'
}

/** 触发浏览器下载Blob文件 */
export function downloadBlob(blob, filename) {
  const url = window.URL.createObjectURL(new Blob([blob]))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  window.URL.revokeObjectURL(url)
}

/** 时间格式化为 2026-09-10 19:06 (去掉T) */
export function formatDateTime(str) {
  if (!str) return '-'
  return str.replace('T', ' ').substring(0, 16)
}

/**
 * 判断文件扩展名是否文本类(可内容预览)
 * 白名单必须与后端 FileTypeUtil.isTextFile 保持一致:
 * 不含 html/js/css/svg 等可执行内容文件(后端存储型XSS防护),含 yaml/c/cpp/h
 */
export function isTextExt(filename) {
  return /\.(txt|md|log|java|py|c|cpp|h|json|yml|yaml|xml|sql)$/i.test(filename || '')
}
