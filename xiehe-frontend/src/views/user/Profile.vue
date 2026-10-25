<template>
  <div class="page-container">
    <div class="page-title">
      个人中心
      <span class="page-desc">管理个人资料、存储空间与登录密码</span>
    </div>

    <!-- 数据统计 -->
    <div class="metric-grid">
      <div class="metric-item" v-for="card in statCards" :key="card.label">
        <div class="metric-icon"><el-icon>
            <component :is="card.icon" />
          </el-icon></div>
        <div>
          <div class="metric-value">{{ card.value }}</div>
          <div class="metric-label">{{ card.label }}</div>
        </div>
      </div>
    </div>

    <el-row :gutter="16">
      <!-- 个人信息 -->
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>个人信息</template>
          <div class="profile-identity">
            <el-avatar :size="64" :src="user?.avatar || undefined"
              style="background: #eef3ff; color: #245bdb; font-size: 24px;">
              {{ (user?.nickname || '?').charAt(0) }}
            </el-avatar>
            <div class="profile-name">{{ user?.nickname }}</div>
            <div class="profile-account">@{{ user?.username }}</div>
            <div class="profile-tags">
              <el-tag :type="user?.role === 'ADMIN' ? 'danger' : 'primary'" size="small">{{ user?.role === 'ADMIN' ?
                '超级管理员' :
                '普通用户' }}</el-tag>
              <el-tag type="info" size="small">
                {{ user?.userType === 'VIP' ? '高级版 20GB' : '标准版 5GB' }}
              </el-tag>
            </div>
          </div>
          <el-form label-width="64px" :model="profileForm">
            <el-form-item label="昵称">
              <el-input v-model="profileForm.nickname" />
            </el-form-item>
            <el-form-item label="头像">
              <div style="display: flex; gap: 10px; align-items: center; width: 100%;">
                <el-upload :show-file-list="false" :http-request="handleAvatarUpload"
                  accept=".png,.jpg,.jpeg,.gif,.webp,.bmp,.ico">
                  <el-button :icon="'Upload'" :loading="avatarUploading">上传本地头像</el-button>
                </el-upload>
                <span style="font-size: 12px; color: #909399;">支持 png/jpg/gif/webp,不超过2MB</span>
              </div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveProfile">保存资料</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 存储空间 + 修改密码 -->
      <el-col :span="14">
        <el-card shadow="never" style="margin-bottom: 16px;">
          <template #header>存储空间</template>
          <div class="storage-box">
            <el-progress :percentage="storagePercent" :stroke-width="8" :format="storageFormat" :color="storageColor" />
            <div class="storage-text">
              已用 <b>{{ formatSize(user?.usedStorage) }}</b> / 共 {{ formatSize(user?.storageLimit) }}
              <el-tag v-if="user?.userType === 'NORMAL'" size="small" style="margin-left: 8px;" type="info">
                升级高级版可获得 20GB 空间
              </el-tag>
            </div>
          </div>
        </el-card>

        <el-card shadow="never">
          <template #header>修改密码</template>
          <el-form label-width="64px" :model="pwdForm" style="max-width: 420px;">
            <el-form-item label="原密码">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-20位" />
            </el-form-item>
            <el-form-item>
              <el-button type="warning" @click="changePwd">修改密码</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { apiGetInfo, apiUpdateProfile, apiChangePassword, apiUserStats, apiUploadAvatar } from '@/api'
import { formatSize } from '@/utils/format'

const userStore = useUserStore()
const user = ref(null)
const profileForm = ref({ nickname: '', avatar: '' })
const pwdForm = ref({ oldPassword: '', newPassword: '' })
const avatarUploading = ref(false)
const stats = ref({ fileCount: 0, publicCount: 0, favoriteCount: 0, totalDownloads: 0, sharedCount: 0 })

// 统计卡片数据
const statCards = computed(() => [
  { label: '我的文件', value: stats.value.fileCount, icon: 'Folder' },
  { label: '公开文件', value: stats.value.publicCount, icon: 'View' },
  { label: '我的收藏', value: stats.value.favoriteCount, icon: 'Star' },
  { label: '总下载量', value: stats.value.totalDownloads, icon: 'Download' },
  { label: '收到共享', value: stats.value.sharedCount, icon: 'Share' }
])

const storagePercent = computed(() => {
  if (!user.value || !user.value.storageLimit) return 0
  return Math.min(100, Math.round((user.value.usedStorage / user.value.storageLimit) * 100))
})
const storageFormat = () => storagePercent.value + '%'
const storageColor = computed(() => (storagePercent.value > 90 ? '#f53f3f' : storagePercent.value > 70 ? '#ff7d00' : '#3370ff'))

async function loadInfo() {
  const res = await apiGetInfo()
  user.value = res.data
  userStore.setUser(res.data)
  profileForm.value = { nickname: res.data.nickname, avatar: res.data.avatar || '' }
  // 加载统计
  try {
    const s = await apiUserStats()
    stats.value = s.data
  } catch (e) { /* 忽略 */ }
}

async function saveProfile() {
  const res = await apiUpdateProfile(profileForm.value)
  userStore.setUser(res.data)
  ElMessage.success('资料已保存')
  loadInfo()
}

/** 上传本地头像: multipart 直传后端,成功后刷新资料与全局头像 */
async function handleAvatarUpload({ file }) {
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('头像图片不能超过2MB')
    return
  }
  avatarUploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await apiUploadAvatar(formData)
    userStore.setUser(res.data)
    ElMessage.success('头像已更新')
    loadInfo()
  } catch (e) { /* 拦截器已提示 */ } finally {
    avatarUploading.value = false
  }
}

async function changePwd() {
  if (!pwdForm.value.oldPassword || !pwdForm.value.newPassword) {
    ElMessage.warning('请填写完整')
    return
  }
  if (pwdForm.value.newPassword.length < 6) {
    ElMessage.warning('新密码至少6位')
    return
  }
  await apiChangePassword(pwdForm.value)
  ElMessage.success('密码修改成功,请重新登录')
  userStore.logout()
  window.location.href = '/login'
}

onMounted(loadInfo)
</script>
