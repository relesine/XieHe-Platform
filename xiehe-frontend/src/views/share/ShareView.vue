<template>
  <div class="share-page">
    <div class="share-brand">
      <span class="app-brand-logo">协</span>
      协合 XieHe
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="share-card">
      <el-skeleton :rows="5" animated />
    </div>

    <!-- 访问密码输入（带密码的分享） -->
    <div v-else-if="needsPassword" class="share-card share-card-center">
      <div class="share-file-icon">
        <el-icon :size="24" color="#ff7d00">
          <Lock />
        </el-icon>
      </div>
      <h3 class="share-title">此分享设置了访问密码</h3>
      <p class="share-sub">请输入分享者提供的密码后查看</p>
      <el-input v-model="pwd" type="password" show-password placeholder="请输入访问密码"
        style="margin-top: 16px; max-width: 280px;" @keyup.enter="submitPwd" />
      <div style="margin-top: 16px;">
        <el-button type="primary" :loading="loading" @click="submitPwd">确认密码</el-button>
      </div>
    </div>

    <!-- 分享文件信息 -->
    <div v-else-if="file" class="share-card">
      <div class="share-file-head">
        <div class="share-file-icon">
          <el-icon :size="24" :color="typeColor(file.fileType)">
            <component :is="typeIcon(file.fileType)" />
          </el-icon>
        </div>
        <h3 class="share-title">{{ file.filename }}</h3>
        <p class="share-sub">来自「{{ file.ownerName }}」的分享</p>
      </div>

      <el-descriptions :column="2" border style="margin-bottom: 20px;">
        <el-descriptions-item label="文件类型">{{ typeTag(file.fileType).label }}</el-descriptions-item>
        <el-descriptions-item label="文件大小">{{ formatSize(file.size) }}</el-descriptions-item>
        <el-descriptions-item label="下载次数">{{ file.downloadCount }}</el-descriptions-item>
        <el-descriptions-item label="分享时间">{{ formatDateTime(file.createTime) }}</el-descriptions-item>
      </el-descriptions>

      <div class="share-actions">
        <el-button type="primary" :icon="'Download'" @click="handleDownload">下载文件</el-button>
        <el-button :icon="'Back'" @click="$router.push('/login')">去登录</el-button>
      </div>
      <p class="share-hint">本链接由协合 XieHe 用户生成，无需登录即可下载</p>
    </div>

    <!-- 失效/过期提示 -->
    <div v-else class="share-card share-card-center">
      <div class="share-file-icon">
        <el-icon :size="24" color="#c9cdd4">
          <CircleClose />
        </el-icon>
      </div>
      <h3 class="share-title">{{ expired ? '分享链接已过期' : '分享不存在或已失效' }}</h3>
      <p class="share-sub">
        {{ expired ? '请联系分享者重新生成分享链接' : '链接可能已被分享者取消，或文件已被删除' }}
      </p>
      <el-button type="primary" style="margin-top: 20px;" @click="$router.push('/login')">返回登录</el-button>
    </div>

    <p class="share-footer">协合 XieHe · 协作文档云平台</p>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { formatSize, fileTypeTag, fileTypeIcon, downloadBlob, formatDateTime } from '@/utils/format'
import { apiShareInfo, apiShareDownload } from '@/api'

const route = useRoute()
const file = ref(null)
const loading = ref(true)
const needsPassword = ref(false)
const expired = ref(false)
const pwd = ref('')

const typeTag = fileTypeTag
const typeIcon = (t) => fileTypeIcon(t)
const typeColor = (t) => ({ DOC: '#245bdb', IMAGE: '#b35b00', ZIP: '#00892b', VIDEO: '#b8323f' }[t] || '#86909c')

/** 按(可选)密码加载分享文件;根据错误类型区分"需密码/已过期/已失效" */
async function loadShare(password, silent = false) {
  loading.value = true
  needsPassword.value = false
  expired.value = false
  file.value = null
  try {
    const res = await apiShareInfo(route.params.token, password, silent)
    file.value = res.data
  } catch (e) {
    const msg = (e && e.message) || ''
    if (msg.includes('密码')) {
      needsPassword.value = true
    } else if (msg.includes('过期')) {
      expired.value = true
    }
    // 其余情况 file 保持 null -> 显示"不存在或已失效"
  } finally {
    loading.value = false
  }
}

function submitPwd() {
  if (!pwd.value.trim()) {
    ElMessage.warning('请输入访问密码')
    return
  }
  loadShare(pwd.value)
}

async function handleDownload() {
  try {
    const res = await apiShareDownload(route.params.token, pwd.value || undefined)
    downloadBlob(res.data, file.value.filename)
    ElMessage.success('下载成功')
    loadShare(pwd.value)   // 刷新下载次数
  } catch (e) { /* 拦截器已提示 */ }
}

onMounted(() => loadShare('', true))
</script>

<style scoped>
.share-card-center {
  text-align: center;
}

.share-file-head {
  margin-bottom: 20px;
  text-align: center;
}

.share-title {
  font-size: 17px;
  font-weight: 600;
  color: #1f2329;
  line-height: 1.4;
  word-break: break-all;
}

.share-sub {
  margin-top: 6px;
  font-size: 13px;
  color: #86909c;
}

.share-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.share-hint {
  margin-top: 14px;
  text-align: center;
  font-size: 12px;
  color: #86909c;
}

@media (max-width: 560px) {
  .share-card {
    padding: 20px;
  }
}
</style>
