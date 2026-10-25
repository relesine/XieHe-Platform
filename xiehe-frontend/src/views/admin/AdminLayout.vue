<template>
  <el-container class="app-shell">
    <el-aside width="220px" class="app-aside">
      <div class="app-brand">
        <span class="app-brand-logo">协</span>
        <span class="app-brand-name">协合</span>
        <span class="app-brand-tag">管理后台</span>
      </div>

      <div class="nav-label">平台管理</div>
      <el-menu :default-active="$route.path" router>
        <el-menu-item index="/admin/dashboard"><el-icon><DataLine /></el-icon><template #title>数据总览</template></el-menu-item>
        <el-menu-item index="/admin/users"><el-icon><User /></el-icon><template #title>用户管理</template></el-menu-item>
        <el-menu-item index="/admin/files"><el-icon><Folder /></el-icon><template #title>文件管理</template></el-menu-item>
        <el-menu-item index="/admin/logs"><el-icon><Document /></el-icon><template #title>操作日志</template></el-menu-item>
      </el-menu>

      <div class="nav-label">快捷入口</div>
      <el-menu>
        <el-menu-item index="/" @click="router.push('/')"><el-icon><Back /></el-icon><template #title>返回用户端</template></el-menu-item>
      </el-menu>

      <div class="app-storage">
        <div class="app-storage-head"><span>当前角色</span><b>超级管理员</b></div>
        <div class="app-storage-meta">操作均记录审计日志</div>
      </div>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <div class="app-breadcrumb">
          <span>管理后台</span>
          <span>/</span>
          <b>{{ $route.meta.title }}</b>
        </div>
        <el-dropdown trigger="click" @command="handleCommand">
          <span class="app-user">
            <el-avatar :size="26" class="app-avatar">{{ (userStore.nickname || '?').charAt(0) }}</el-avatar>
            <span>{{ userStore.nickname }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="user">返回用户端</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { apiLogout } from '@/api'

const router = useRouter()
const userStore = useUserStore()

async function handleCommand(command) {
  if (command === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗?', '退出登录', { type: 'warning', confirmButtonText: '退出', cancelButtonText: '取消' })
    try { await apiLogout() } catch (e) { /* 忽略 */ }
    userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } else if (command === 'user') {
    router.push('/')
  }
}
</script>

<style scoped>
.app-avatar {
  background: #f2f3f5;
  color: var(--xh-text-2);
  font-size: 12px;
  font-weight: 500;
}
</style>
