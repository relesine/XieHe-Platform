// ============================================================
// Pinia 用户状态管理: 保存登录信息,所有页面共享
// ============================================================
import { defineStore } from 'pinia'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('xiehe_token') || '',
    user: JSON.parse(localStorage.getItem('xiehe_user') || 'null')
  }),
  getters: {
    isLogin: (state) => !!state.token,
    isAdmin: (state) => state.user?.role === 'ADMIN',
    nickname: (state) => state.user?.nickname || ''
  },
  actions: {
    /** 登录成功后保存信息 */
    setLogin(token, user) {
      this.token = token
      this.user = user
      localStorage.setItem('xiehe_token', token)
      localStorage.setItem('xiehe_user', JSON.stringify(user))
      localStorage.setItem('xiehe_role', user.role)
    },
    /** 更新用户信息 */
    setUser(user) {
      this.user = user
      localStorage.setItem('xiehe_user', JSON.stringify(user))
    },
    /** 退出登录,清除本地信息 */
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('xiehe_token')
      localStorage.removeItem('xiehe_user')
      localStorage.removeItem('xiehe_role')
    }
  }
})
