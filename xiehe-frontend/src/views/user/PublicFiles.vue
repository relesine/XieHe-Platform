<template>
  <div class="page-container">
    <div class="page-title">
      <el-icon><View /></el-icon>公开文件
      <span class="page-desc">公开仅代表可浏览，编辑权限需单独授权</span>
    </div>
    <FileTable :files="files" view-type="public" :loading="loading"
               :total="total" :page="page" :size="size"
               @refresh="loadFiles" @page-change="onPageChange" @size-change="onSizeChange" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import FileTable from './components/FileTable.vue'
import { apiPublicFiles } from '@/api'

const files = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const keyword = ref('')

async function loadFiles(kw, goPage = 1) {
  loading.value = true
  // 仅"主动搜索"(传入字符串,含空串)时更新关键词;操作刷新(kw=undefined)时保留当前搜索词,避免上传/删除后搜索词丢失
  if (kw !== undefined) keyword.value = kw || ''
  page.value = goPage
  try {
    const res = await apiPublicFiles(keyword.value, page.value, size.value)
    files.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
  if (files.value.length === 0 && page.value > 1) {
    loadFiles(keyword.value, page.value - 1)
  }
}

function onPageChange(p) { loadFiles(keyword.value, p) }
function onSizeChange(s) { size.value = s; loadFiles(keyword.value, 1) }

onMounted(() => loadFiles())
</script>
