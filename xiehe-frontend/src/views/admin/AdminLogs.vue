<template>
  <div class="page-container">
    <div class="page-head">
      <h2 class="page-title">操作日志</h2>
      <p class="page-desc">记录管理员对用户与文件的关键操作，用于审计追溯</p>
    </div>

    <div class="table-toolbar">
      <span class="toolbar-meta">共 {{ total }} 条记录</span>
    </div>

    <div class="table-panel">
      <el-table :data="logs" v-loading="loading">
        <el-table-column label="ID" prop="id" width="70" />
        <el-table-column label="操作人" prop="username" width="130" />
        <el-table-column label="操作动作" width="140">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作详情" prop="detail" min-width="320" show-overflow-tooltip />
        <el-table-column label="操作时间" width="170">
          <template #default="{ row }"><span class="muted">{{ (row.createTime || '').replace('T', ' ') }}</span></template>
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
import { apiAdminLogs } from '@/api'

const logs = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const res = await apiAdminLogs({ page: page.value, size: size.value })
    logs.value = res.data.records
    total.value = Number(res.data.total)
  } finally {
    loading.value = false
  }
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
