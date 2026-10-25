<template>
  <div class="page-container">
    <div class="page-title">
      <el-icon><Star /></el-icon>我的收藏
      <span class="page-desc">星标标记的重要文件，点击星标可取消收藏</span>
    </div>
    <FileTable :files="files" view-type="favorites" :loading="loading"
               :total="total" :page="page" :size="size"
               @refresh="loadFavorites" @page-change="onPageChange" @size-change="onSizeChange" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import FileTable from './components/FileTable.vue'
import { apiFavorites } from '@/api'

const files = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)

async function loadFavorites(goPage = 1) {
  loading.value = true
  page.value = goPage
  try {
    const res = await apiFavorites(page.value, size.value)
    files.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
  if (files.value.length === 0 && page.value > 1) {
    loadFavorites(page.value - 1)
  }
}

function onPageChange(p) { loadFavorites(p) }
function onSizeChange(s) { size.value = s; loadFavorites(1) }

onMounted(loadFavorites)
</script>
