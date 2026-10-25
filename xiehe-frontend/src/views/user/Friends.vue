<template>
  <div class="page-container">
    <div class="page-head">
      <div>
        <h2 class="page-title">好友管理</h2>
        <p class="page-desc">好友是快捷授权对象，非好友也可通过手动授权访问文件</p>
      </div>
    </div>

    <!-- 添加好友 -->
    <section class="panel add-friend-panel">
      <div class="panel-header">
        <span class="panel-title">添加好友</span>
        <span class="panel-hint">支持按用户名或昵称搜索</span>
      </div>
      <div class="add-friend-body">
        <el-select
          v-model="searchKeyword"
          filterable
          remote
          :remote-method="searchUsers"
          placeholder="输入用户名或昵称搜索用户"
          class="friend-select"
          :loading="searching"
          clearable
        >
          <el-option v-for="u in userOptions" :key="u.id" :label="u.nickname + '（' + u.username + '）'" :value="u.id" />
        </el-select>
        <el-button type="primary" :disabled="!searchKeyword" @click="addFriend">添加好友</el-button>
      </div>
    </section>

    <!-- 好友列表 -->
    <section class="panel">
      <div class="panel-header">
        <span class="panel-title">好友列表</span>
        <span class="panel-hint">共 {{ friends.length }} 位好友</span>
      </div>
      <el-table :data="friends" v-loading="loading" empty-text="还没有好友">
        <el-table-column label="好友" min-width="220">
          <template #default="{ row }">
            <div class="friend-cell">
              <el-avatar :size="32" class="friend-avatar">{{ row.friend.nickname.charAt(0) }}</el-avatar>
              <div class="friend-meta">
                <span class="friend-name">{{ row.friend.nickname }}</span>
                <span class="friend-account">@{{ row.friend.username }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.friend.userType === 'VIP' ? 'warning' : 'info'" size="small" effect="plain">
              {{ row.friend.userType === 'VIP' ? '高级用户' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="成为好友时间" width="180">
          <template #default="{ row }"><span class="muted">{{ row.createTime }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="right">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="removeFriend(row)">删除好友</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiFriendList, apiFriendAdd, apiFriendRemove, apiSearchUsers } from '@/api'

const friends = ref([])
const loading = ref(false)
const searchKeyword = ref(null)
const userOptions = ref([])
const searching = ref(false)

async function loadFriends() {
  loading.value = true
  try {
    const res = await apiFriendList()
    friends.value = res.data
  } finally {
    loading.value = false
  }
}

async function searchUsers(keyword) {
  if (!keyword) { userOptions.value = []; return }
  searching.value = true
  try {
    const res = await apiSearchUsers(keyword)
    // 排除已是好友的
    const friendIds = new Set(friends.value.map(f => f.friend.id))
    userOptions.value = res.data.filter(u => !friendIds.has(u.id))
  } finally {
    searching.value = false
  }
}

async function addFriend() {
  await apiFriendAdd(searchKeyword.value)
  ElMessage.success('添加好友成功')
  searchKeyword.value = null
  userOptions.value = []
  loadFriends()
}

async function removeFriend(row) {
  await ElMessageBox.confirm(`确定删除好友「${row.friend.nickname}」吗？`, '删除好友', { type: 'warning' })
  await apiFriendRemove(row.friend.id)
  ElMessage.success('已删除好友')
  loadFriends()
}

onMounted(loadFriends)
</script>

<style scoped>
.page-head {
  margin-bottom: 16px;
}
.add-friend-panel {
  margin-bottom: 16px;
}
.add-friend-body {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.friend-select {
  width: 320px;
}
.friend-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.friend-avatar {
  background: #f2f3f5;
  color: #3370ff;
  font-size: 13px;
  font-weight: 500;
}
.friend-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}
.friend-name {
  font-size: 13px;
  color: #1d2129;
}
.friend-account {
  font-size: 12px;
  color: #86909c;
}
.muted {
  color: #86909c;
  font-size: 13px;
}
</style>
