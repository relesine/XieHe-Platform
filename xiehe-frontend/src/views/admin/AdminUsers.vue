<template>
  <div class="page-container">
    <div class="page-head">
      <h2 class="page-title">用户管理</h2>
      <p class="page-desc">查看注册用户，调整账号状态与存储类型</p>
    </div>

    <div class="table-toolbar">
      <span class="toolbar-meta">共 {{ total }} 位用户</span>
      <div class="toolbar-group">
        <el-input v-model="keyword" placeholder="搜索用户名或昵称" clearable style="width: 220px;"
          @keyup.enter="load(1)" @clear="load(1)" />
        <el-button type="primary" @click="load(1)">搜索</el-button>
      </div>
    </div>

    <div class="table-panel">
      <el-table :data="users" v-loading="loading">
        <el-table-column label="ID" prop="id" width="64" />
        <el-table-column label="用户名" prop="username" width="130" />
        <el-table-column label="昵称" prop="nickname" min-width="120" />
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'primary'" size="small" effect="plain">
              {{ row.role === 'ADMIN' ? '管理员' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.userType === 'VIP' ? 'warning' : 'info'" size="small" effect="plain">
              {{ row.userType === 'VIP' ? '高级 20GB' : '普通 5GB' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="存储使用" width="150">
          <template #default="{ row }">
            <span class="muted">{{ formatSize(row.usedStorage) }} / {{ formatSize(row.storageLimit) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="plain">
              {{ row.status === 1 ? '正常' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="170">
          <template #default="{ row }"><span class="muted">{{ (row.createTime || '').replace('T', ' ') }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="right">
          <template #default="{ row }">
            <template v-if="row.role !== 'ADMIN'">
              <el-button link :type="row.status === 1 ? 'danger' : 'success'" size="small" @click="toggleStatus(row)">
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-button>
              <el-button link :type="row.userType === 'VIP' ? 'info' : 'warning'" size="small" @click="toggleType(row)">
                {{ row.userType === 'VIP' ? '降为普通' : '升为高级' }}
              </el-button>
            </template>
            <span v-else class="muted">不可操作</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination background layout="total, prev, pager, next" :total="total" :page-size="size"
          :current-page="page" @current-change="load" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiAdminUsers, apiAdminUserStatus, apiAdminUserType } from '@/api'
import { formatSize } from '@/utils/format'

const users = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const keyword = ref('')

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const res = await apiAdminUsers({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    users.value = res.data.records
    total.value = Number(res.data.total)
  } finally {
    loading.value = false
  }
}

async function toggleStatus(row) {
  const action = row.status === 1 ? '禁用' : '启用'
  await ElMessageBox.confirm(`确定${action}用户「${row.nickname}」吗？`, '账号状态', { type: 'warning' })
  await apiAdminUserStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success(`已${action}`)
  load()
}

async function toggleType(row) {
  const toType = row.userType === 'VIP' ? 'NORMAL' : 'VIP'
  await ElMessageBox.confirm(
    `确定将「${row.nickname}」${toType === 'VIP' ? '升级为高级用户（20GB）' : '降级为普通用户（5GB）'}吗？`,
    '存储类型',
    { type: 'warning' }
  )
  await apiAdminUserType(row.id, toType)
  ElMessage.success('操作成功')
  load()
}

onMounted(() => load(1))
</script>

<style scoped>
.table-panel {
  overflow: hidden;
}
.muted {
  color: #86909c;
}
</style>
