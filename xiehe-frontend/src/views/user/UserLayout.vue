<template>
  <el-container class="app-shell">
    <!-- 左侧导航: 浅色工作台风格,窄屏折叠为图标栏 -->
    <el-aside :width="isMobile ? '64px' : '232px'" class="app-aside" :class="{ 'is-collapsed': isMobile }">
      <div class="app-brand">
        <span class="app-brand-logo">协</span>
        <span v-show="!isMobile" class="app-brand-name">协合 XieHe</span>
      </div>

      <div v-show="!isMobile" class="nav-label">文件空间</div>
      <el-menu :default-active="$route.path" router :collapse="isMobile" :collapse-transition="false">
        <el-menu-item index="/files/mine"><el-icon><Folder /></el-icon><template #title>我的文件</template></el-menu-item>
        <el-menu-item index="/files/favorites"><el-icon><Star /></el-icon><template #title>我的收藏</template></el-menu-item>
        <el-menu-item index="/files/public"><el-icon><View /></el-icon><template #title>公开文件</template></el-menu-item>
        <el-menu-item index="/files/shared"><el-icon><Share /></el-icon><template #title>与我共享</template></el-menu-item>
        <el-menu-item index="/files/trash"><el-icon><Delete /></el-icon><template #title>回收站</template></el-menu-item>
      </el-menu>

      <div v-show="!isMobile" class="nav-label">协作与设置</div>
      <el-menu :default-active="$route.path" router :collapse="isMobile" :collapse-transition="false">
        <el-menu-item index="/friends"><el-icon><User /></el-icon><template #title>好友管理</template></el-menu-item>
        <el-menu-item index="/profile"><el-icon><Setting /></el-icon><template #title>个人中心</template></el-menu-item>
        <el-menu-item v-if="userStore.isAdmin" index="/admin"><el-icon><Monitor /></el-icon><template #title>管理后台</template></el-menu-item>
      </el-menu>

      <!-- 底部存储空间 -->
      <div class="app-storage">
        <div v-show="!isMobile" class="app-storage-head">
          <span>存储空间</span>
          <b>{{ storagePercent }}%</b>
        </div>
        <el-progress :percentage="storagePercent" :stroke-width="6" :show-text="false" :color="storageColor" />
        <div v-show="!isMobile" class="app-storage-meta">
          已用 {{ formatSize(storage?.usedStorage) }} / {{ formatSize(storage?.storageLimit) }}
        </div>
      </div>
    </el-aside>

    <el-container>
      <!-- 顶部栏 -->
      <el-header class="app-header">
        <div class="app-breadcrumb">
          <span>协合作业台</span>
          <span>/</span>
          <b>{{ $route.meta.title }}</b>
        </div>
        <el-dropdown trigger="click" @command="handleCommand">
          <span class="app-user">
            <el-avatar :size="26" :src="userStore.user?.avatar || undefined" class="app-avatar">
              {{ (userStore.nickname || '?').charAt(0) }}
            </el-avatar>
            <span>{{ userStore.nickname }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item v-if="userStore.isAdmin" command="admin">管理后台</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <!-- 内容区 -->
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { apiLogout, apiGetStorage } from '@/api'
import { formatSize } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()
const storage = ref(null)

const storagePercent = computed(() => {
  if (!storage.value || !storage.value.storageLimit) return 0
  return Math.min(100, Math.round((storage.value.usedStorage / storage.value.storageLimit) * 100))
})
const storageColor = computed(() => (storagePercent.value > 90 ? '#f53f3f' : storagePercent.value > 70 ? '#ff7d00' : '#3370ff'))

async function loadStorage() {
  try {
    const res = await apiGetStorage()
    storage.value = res.data
  } catch (e) { /* 存储空间展示失败不影响主流程 */ }
}

// 文件上传/删除/恢复/彻底删除后, FileTable 会广播该事件,实时刷新存储空间展示
function onStorageRefresh() {
  loadStorage()
}

async function handleCommand(command) {
  if (command === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗?', '退出登录', { type: 'warning', confirmButtonText: '退出', cancelButtonText: '取消' })
    try { await apiLogout() } catch (e) { /* 忽略登出接口异常 */ }
    userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } else if (command === 'admin') {
    router.push('/admin')
  } else if (command === 'profile') {
    router.push('/profile')
  }
}

onMounted(() => {
  loadStorage()
  window.addEventListener('xiehe:storage-refresh', onStorageRefresh)
  window.addEventListener('resize', onResize)
  onResize()
})
onUnmounted(() => {
  window.removeEventListener('xiehe:storage-refresh', onStorageRefresh)
  window.removeEventListener('resize', onResize)
})

// ============ 窄屏适配: 窗口宽度 <768px 时折叠侧边栏 ============
const isMobile = ref(false)
function onResize() {
  isMobile.value = window.innerWidth < 768
}
</script>

<style scoped>
.app-avatar {
  background: var(--xh-primary-light);
  color: var(--xh-primary);
  font-size: 12px;
  font-weight: 500;
}
</style>
