// ============================================================
// 路由配置: 包含 登录/注册/用户端/管理端 四部分
// ============================================================
import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  // ---------- 登录 / 注册 / 公开分享页 ----------
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  { path: '/register', name: 'Register', component: () => import('@/views/Register.vue'), meta: { public: true } },
  { path: '/share/:token', name: 'ShareView', component: () => import('@/views/share/ShareView.vue'), meta: { public: true, title: '文件分享' } },

  // ---------- 用户端(需要登录) ----------
  {
    path: '/',
    component: () => import('@/views/user/UserLayout.vue'),
    redirect: '/files/mine',
    children: [
      { path: 'files/mine', name: 'MyFiles', component: () => import('@/views/user/MyFiles.vue'), meta: { title: '我的文件' } },
      { path: 'files/favorites', name: 'Favorites', component: () => import('@/views/user/Favorites.vue'), meta: { title: '我的收藏' } },
      { path: 'files/public', name: 'PublicFiles', component: () => import('@/views/user/PublicFiles.vue'), meta: { title: '公开文件' } },
      { path: 'files/shared', name: 'SharedFiles', component: () => import('@/views/user/SharedFiles.vue'), meta: { title: '与我共享' } },
      { path: 'files/trash', name: 'Trash', component: () => import('@/views/user/Trash.vue'), meta: { title: '回收站' } },
      { path: 'friends', name: 'Friends', component: () => import('@/views/user/Friends.vue'), meta: { title: '好友管理' } },
      { path: 'profile', name: 'Profile', component: () => import('@/views/user/Profile.vue'), meta: { title: '个人中心' } }
    ]
  },

  // ---------- 管理后台(需要登录且角色为ADMIN) ----------
  {
    path: '/admin',
    component: () => import('@/views/admin/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    meta: { admin: true },
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/admin/Dashboard.vue'), meta: { title: '数据总览' } },
      { path: 'users', name: 'AdminUsers', component: () => import('@/views/admin/AdminUsers.vue'), meta: { title: '用户管理' } },
      { path: 'files', name: 'AdminFiles', component: () => import('@/views/admin/AdminFiles.vue'), meta: { title: '文件管理' } },
      { path: 'logs', name: 'AdminLogs', component: () => import('@/views/admin/AdminLogs.vue'), meta: { title: '操作日志' } }
    ]
  },

  // 兜底: 未匹配路由跳转登录
  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// ---------- 全局路由守卫: 未登录跳转登录页 ----------
router.beforeEach((to) => {
  const token = localStorage.getItem('xiehe_token')
  // 非公开页面需要登录
  if (!to.meta.public && !token) {
    return { path: '/login' }
  }
  // 已登录访问登录页 -> 跳回首页
  if (to.path === '/login' && token) {
    return { path: '/' }
  }
  // 管理后台需要 ADMIN 角色
  if (to.meta.admin) {
    const role = localStorage.getItem('xiehe_role')
    if (role !== 'ADMIN') {
      return { path: '/' }
    }
  }
  return true
})

export default router
