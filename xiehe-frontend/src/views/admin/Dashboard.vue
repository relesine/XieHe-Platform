<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">平台数据总览</h2>
        <p class="page-desc">用户、文件与存储的实时统计，数据来自平台运行状态</p>
      </div>
      <el-button :icon="'Refresh'" :loading="refreshing" @click="refresh">刷新数据</el-button>
    </div>

    <!-- 统计指标 -->
    <div class="metric-grid">
      <div class="metric-item" v-for="card in cards" :key="card.label">
        <div class="metric-icon"><el-icon>
            <component :is="card.icon" />
          </el-icon></div>
        <div>
          <div class="metric-value">{{ card.value }}</div>
          <div class="metric-label">{{ card.label }}</div>
        </div>
      </div>
    </div>

    <!-- 存储使用情况 -->
    <section class="panel" style="margin-bottom: 16px;">
      <div class="panel-header">
        <span class="panel-title">平台存储使用情况</span>
        <span class="panel-hint">所有用户配额汇总</span>
      </div>
      <div class="panel-body">
        <el-progress :percentage="storagePercent" :stroke-width="8" :format="storageFormat" :color="storageColor" />
        <div class="storage-text">
          已使用 <b>{{ formatSize(stats?.totalStorageUsed) }}</b> / 总配额 {{ formatSize(stats?.totalStorageLimit) }}
        </div>
      </div>
    </section>

    <!-- 内容分布 -->
    <el-row :gutter="16">
      <el-col :span="12">
        <section class="panel">
          <div class="panel-header">
            <span class="panel-title">文件类型分布</span>
          </div>
          <div class="panel-body">
            <div v-if="typeDist">
              <div class="dist-bar" v-for="t in typeDist" :key="t.key">
                <span class="dist-label">{{ t.label }}</span>
                <div class="dist-track">
                  <div class="dist-fill" :style="{ width: t.percent + '%', background: t.color }">{{ t.value }}</div>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂无数据" :image-size="56" />
          </div>
        </section>
      </el-col>
      <el-col :span="12">
        <section class="panel">
          <div class="panel-header">
            <span class="panel-title">用户类型分布</span>
          </div>
          <div class="panel-body">
            <div v-if="userDist">
              <div class="dist-bar" v-for="t in userDist" :key="t.key">
                <span class="dist-label">{{ t.label }}</span>
                <div class="dist-track">
                  <div class="dist-fill" :style="{ width: t.percent + '%', background: t.color }">{{ t.value }}</div>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂无数据" :image-size="56" />
          </div>
        </section>
      </el-col>
    </el-row>

    <!-- 平台说明 -->
    <section class="panel" style="margin-top: 16px;">
      <div class="panel-header">
        <span class="panel-title">权限规则说明</span>
      </div>
      <div class="panel-body">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="公开文件">任何登录用户可浏览、下载，编辑权限需单独授予</el-descriptions-item>
          <el-descriptions-item label="私有文件">仅拥有者及被授权用户可访问</el-descriptions-item>
          <el-descriptions-item label="好友关系">好友是快捷授权对象，非好友也可手动授予编辑权限</el-descriptions-item>
          <el-descriptions-item label="编辑权限">拥有者或被授予 EDIT 权限的用户可重命名、替换文件</el-descriptions-item>
        </el-descriptions>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { apiAdminStats } from '@/api'
import { formatSize } from '@/utils/format'

const stats = ref(null)
const refreshing = ref(false)

// 强制刷新: 请求后端 ?refresh=true 绕过 Redis 缓存重新统计
async function refresh() {
  refreshing.value = true
  try {
    const res = await apiAdminStats(true)
    stats.value = res.data
    ElMessage.success('数据已刷新')
  } catch (e) { /* 拦截器已提示 */ } finally {
    refreshing.value = false
  }
}

async function load() {
  const res = await apiAdminStats()
  stats.value = res.data
}

onMounted(load)

const cards = computed(() => [
  { label: '用户总数', value: stats.value?.userCount ?? '-', icon: 'User' },
  { label: '高级用户', value: stats.value?.vipCount ?? '-', icon: 'Medal' },
  { label: '文件总数', value: stats.value?.fileCount ?? '-', icon: 'Folder' },
  { label: '总下载次数', value: stats.value?.totalDownloads ?? '-', icon: 'Download' }
])

const storagePercent = computed(() => {
  if (!stats.value?.totalStorageLimit) return 0
  return Math.min(100, Math.round((stats.value.totalStorageUsed / stats.value.totalStorageLimit) * 100))
})
const storageFormat = () => storagePercent.value + '%'
const storageColor = computed(() => (storagePercent.value > 90 ? '#f53f3f' : '#3370ff'))

// 文件类型分布条形图数据
const typeDist = computed(() => {
  const dist = stats.value?.fileTypeDist
  if (!dist) return null
  const meta = {
    DOC: { label: '文档', color: '#3370ff' },
    IMAGE: { label: '图片', color: '#ff9a2e' },
    ZIP: { label: '压缩包', color: '#00b42a' },
    VIDEO: { label: '视频', color: '#f53f3f' }
  }
  const total = Object.values(dist).reduce((a, b) => a + b, 0) || 1
  return Object.entries(dist).map(([key, value]) => ({
    key, value, ...(meta[key] || { label: key, color: '#86909c' }),
    percent: Math.max(4, Math.round((value / total) * 100))
  }))
})

// 用户类型分布条形图数据
const userDist = computed(() => {
  const dist = stats.value?.userTypeDist
  if (!dist) return null
  const meta = {
    NORMAL: { label: '普通用户', color: '#3370ff' },
    VIP: { label: '高级用户', color: '#ff7d00' }
  }
  const total = Object.values(dist).reduce((a, b) => a + b, 0) || 1
  return Object.entries(dist).map(([key, value]) => ({
    key, value, ...(meta[key] || { label: key, color: '#86909c' }),
    percent: Math.max(4, Math.round((value / total) * 100))
  }))
})
</script>

<style scoped>
.page-header {
  align-items: flex-start;
}
.page-desc {
  margin-top: 4px;
}
</style>
