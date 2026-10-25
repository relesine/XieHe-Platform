<template>
  <div class="auth-page">
    <div class="auth-panel">
      <aside class="auth-brand">
        <div class="auth-logo">协</div>
        <h1>协合 XieHe</h1>
        <p class="auth-slogan">注册即可获得 5GB 免费存储空间<br />支持文档、图片、压缩包与视频文件</p>
        <ul class="auth-features">
          <li><el-icon><Folder /></el-icon>我的文件、收藏、回收站一目了然</li>
          <li><el-icon><Share /></el-icon>一键分享给同事或生成外链</li>
          <li><el-icon><User /></el-icon>好友协作,按需授予查看/编辑权限</li>
        </ul>
        <div class="auth-brand-footer">© 2026 协合 XieHe</div>
      </aside>

      <main class="auth-form">
        <h2>创建账号</h2>
        <p class="auth-sub">填写以下信息完成注册,注册后即可登录使用</p>

        <el-form :model="form" :rules="rules" ref="formRef" size="large" @submit.prevent>
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名(3-20位字母/数字/下划线)" :prefix-icon="'User'" autocomplete="username" />
          </el-form-item>
          <el-form-item prop="nickname">
            <el-input v-model="form.nickname" placeholder="昵称(显示名称)" :prefix-icon="'Postcard'" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码(6-20位)" :prefix-icon="'Lock'"
                      show-password autocomplete="new-password" />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" placeholder="确认密码" :prefix-icon="'Lock'"
                      show-password autocomplete="new-password" @keyup.enter="handleRegister" />
          </el-form-item>
          <el-button type="primary" size="large" style="width: 100%;" :loading="loading" @click="handleRegister">
            注 册
          </el-button>
        </el-form>

        <div class="auth-switch">
          已有账号?
          <router-link to="/login">返回登录</router-link>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiRegister } from '@/api'

const router = useRouter()
const formRef = ref()
const loading = ref(false)

const form = reactive({ username: '', nickname: '', password: '', confirmPassword: '' })

const validateConfirm = (rule, value, callback) => {
  if (value !== form.password) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{3,20}$/, message: '3-20位字母/数字/下划线', trigger: 'blur' }
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度6-20位', trigger: 'blur' }
  ],
  confirmPassword: [{ required: true, validator: validateConfirm, trigger: 'blur' }]
}

async function handleRegister() {
  await formRef.value.validate()
  loading.value = true
  try {
    await apiRegister({ username: form.username, nickname: form.nickname, password: form.password })
    ElMessage.success('注册成功,请登录')
    router.push('/login')
  } catch (e) {
    // 错误提示已由拦截器处理
  } finally {
    loading.value = false
  }
}
</script>
