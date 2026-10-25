<template>
  <div class="page-container">
    <div class="page-title">
      <el-icon><Share /></el-icon>与我共享
      <span class="page-desc">其他用户授权给你的文件</span>
    </div>
    <FileTable :files="files" view-type="shared" :loading="loading"
               :total="total" :page="page" :size="size"
               @refresh="loadFiles" @page-change="onPageChange" @size-change="onSizeChange" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import FileTable from './components/FileTable.vue'
import { apiSharedFiles } from '@/api'

const files = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

async function loadFiles(goPage = 1) {
  loading.value = true
  page.value = goPage
  try {
    const res = await apiSharedFiles(page.value, size.value)
    files.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
  if (files.value.length === 0 && page.value > 1) {
    loadFiles(page.value - 1)
  }
}

function onPageChange(p) { loadFiles(p) }
function onSizeChange(s) { size.value = s; loadFiles(1) }

onMounted(() => loadFiles())
</script>
