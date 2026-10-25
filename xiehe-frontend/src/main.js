// ============================================================
// 前端入口文件: 创建 Vue 应用,注册路由/状态管理/UI库
// ============================================================
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './styles/global.css'

const app = createApp(App)

// 全局异常兜底: 避免单个组件异常导致整页白屏且无任何线索
app.config.errorHandler = (err, instance, info) => {
  console.error('[XieHe] 未捕获的组件异常:', err, info)
}

// 路由懒加载分包失败(发布后旧 chunk 被清理)时自动刷新一次
router.onError((err, to) => {
  const message = String(err && err.message)
  if (/Failed to fetch dynamically imported module|Importing a module script failed/i.test(message)) {
    window.location.href = to.fullPath
  }
})

// 全局注册 Element Plus 图标组件
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
