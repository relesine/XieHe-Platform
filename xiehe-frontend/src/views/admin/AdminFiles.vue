<template>
  <div class="page-container">
    <div class="page-head">
      <h2 class="page-title">文件管理</h2>
      <p class="page-desc">查看全平台文件，可对违规内容执行删除</p>
    </div>

    <div class="table-toolbar">
      <span class="toolbar-meta">共 {{ total }} 份文件</span>
      <div class="toolbar-group">
        <el-input v-model="keyword" placeholder="搜索文件名" clearable style="width: 220px;" @keyup.enter="load(1)"
          @clear="load(1)" />
        <el-button type="primary" @click="load(1)">搜索</el-button>
      </div>
    </div>

    <div class="table-panel">
      <el-table :data="files" v-loading="loading">
        <el-table-column label="ID" prop="id" width="64" />
        <el-table-column label="文件名" prop="filename" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :class="fileTypeTag(row.fileType).cls" size="small">{{ fileTypeTag(row.fileType).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="拥有者 ID" prop="userId" width="100" />
        <el-table-column label="大小" width="100">
          <template #default="{ row }"><span class="muted">{{ formatSize(row.size) }}</span></template>
        </el-table-column>
        <el-table-column label="可见性" width="90">
          <template #default="{ row }">
            <el-tag :type="row.isPublic === 1 ? 'success' : 'info'" size="small" effect="plain">
              {{ row.isPublic === 1 ? '公开' : '私有' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下载量" prop="downloadCount" width="90" />
        <el-table-column label="上传时间" width="170">
          <template #default="{ row }"><span class="muted">{{ (row.createTime || '').replace('T', ' ')
              }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right" align="right">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="removeFile(row)">删除</el-button>
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
import { apiAdminFiles, apiAdminFileDelete } from '@/api'
import { formatSize, fileTypeTag } from '@/utils/format'

const files = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const keyword = ref('')

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const res = await apiAdminFiles({ page: page.value, size: size.value, keyword: keyword.value || undefined })
    files.value = res.data.records
    total.value = Number(res.data.total)
  } finally {
    loading.value = false
  }
}

async function removeFile(row) {
  await ElMessageBox.confirm(`确定删除文件「${row.filename}」吗？删除后不可恢复。`, '删除文件', { type: 'warning' })
  await apiAdminFileDelete(row.id)
  ElMessage.success('删除成功')
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
