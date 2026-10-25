<template>
  <div class="auth-page">
    <div class="auth-panel">
      <!-- 品牌信息区 -->
      <aside class="auth-brand">
        <div class="auth-logo">协</div>
        <h1>协合 XieHe</h1>
        <p class="auth-slogan">企业级协作文档与文件管理平台<br />安全存储 · 精细权限 · 高效协作</p>
        <ul class="auth-features">
          <li><el-icon><Lock /></el-icon>四级文件权限,访问范围清晰可控</li>
          <li><el-icon><Link /></el-icon>分享链接支持密码与有效期</li>
          <li><el-icon><Document /></el-icon>操作日志留痕,支持安全审计</li>
        </ul>
        <div class="auth-brand-footer">© 2026 协合 XieHe</div>
      </aside>

      <!-- 登录表单区 -->
      <main class="auth-form">
        <h2>登录工作台</h2>
        <p class="auth-sub">使用账号密码登录,继续处理你的文件与协作任务</p>

        <el-form :model="form" :rules="rules" ref="formRef" size="large" @submit.prevent>
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" :prefix-icon="'User'" autocomplete="username" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="'Lock'"
                      show-password autocomplete="current-password" @keyup.enter="handleLogin" />
          </el-form-item>
          <el-button type="primary" size="large" style="width: 100%;" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form>

        <div class="auth-switch">
          还没有账号?
          <router-link to="/register">立即注册</router-link>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiLogin } from '@/api'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  await formRef.value.validate()
  loading.value = true
  try {
    const res = await apiLogin(form)
    userStore.setLogin(res.data.token, res.data.user)
    ElMessage.success('登录成功')
    // 管理员进后台,普通用户进文件页
    router.push(res.data.user.role === 'ADMIN' ? '/admin' : '/')
  } catch (e) {
    // 错误提示已由拦截器处理
  } finally {
    loading.value = false
  }
}
</script>
