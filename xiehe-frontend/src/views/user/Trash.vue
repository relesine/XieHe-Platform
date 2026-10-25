<template>
  <div class="page-container">
    <div class="page-title">
      <el-icon><DeleteFilled /></el-icon>回收站
      <span class="page-desc">已删除的文件在这里可恢复或彻底删除，彻底删除后释放存储空间</span>
    </div>
    <FileTable :files="files" view-type="trash" :loading="loading"
               :total="total" :page="page" :size="size"
               @refresh="loadTrash" @page-change="onPageChange" @size-change="onSizeChange" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import FileTable from './components/FileTable.vue'
import { apiTrashFiles } from '@/api'

const files = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

async function loadTrash(goPage = 1) {
  loading.value = true
  page.value = goPage
  try {
    const res = await apiTrashFiles(page.value, size.value)
    files.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
  if (files.value.length === 0 && page.value > 1) {
    loadTrash(page.value - 1)
  }
}

function onPageChange(p) { loadTrash(p) }
function onSizeChange(s) { size.value = s; loadTrash(1) }

onMounted(() => loadTrash())
</script>
