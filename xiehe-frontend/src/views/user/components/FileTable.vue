<template>
  <div>
    <!-- ============ 工具栏 ============ -->
    <div class="table-toolbar">
      <div class="toolbar-group">
        <!-- 上传(仅我的文件), accept 与后端扩展名白名单保持一致 -->
        <el-upload v-if="canUpload" :show-file-list="false" :http-request="handleUpload"
          accept=".doc,.docx,.txt,.pdf,.md,.xls,.xlsx,.ppt,.pptx,.log,.png,.jpg,.jpeg,.gif,.bmp,.webp,.ico,.zip,.rar,.7z,.tar,.gz,.mp4,.avi,.mov,.mkv,.flv,.wmv,.webm,.java,.py,.c,.cpp,.h,.sql,.json,.yml,.yaml,.xml">
          <el-button type="primary" :icon="'Upload'">上传文件</el-button>
        </el-upload>
        <el-button :icon="'Refresh'" @click="emit('refresh')">刷新</el-button>
        <!-- 批量删除(我的文件) -->
        <el-button v-if="canUpload && selection.length > 0" type="danger" plain :icon="'Delete'"
          @click="handleBatchDelete">
          批量删除({{ selection.length }})
        </el-button>
        <!-- 批量分享 / 批量授权(我的文件,仅自己拥有的文件) -->
        <el-button v-if="canUpload && !isTrash && selection.length > 0" plain :icon="'Share'"
          @click="openBatchShare">批量分享({{ selection.length }})</el-button>
        <el-button v-if="canUpload && !isTrash && selection.length > 0" plain :icon="'User'"
          @click="openBatchGrant">批量授权({{ selection.length }})</el-button>
        <!-- 批量恢复 / 彻底删除(回收站) -->
        <template v-if="isTrash">
          <el-button v-if="selection.length > 0" plain :icon="'RefreshLeft'" @click="handleBatchRestore">批量恢复({{
            selection.length }})</el-button>
          <el-button v-if="selection.length > 0" type="danger" plain :icon="'Delete'"
            @click="handleBatchForever">批量彻底删除({{ selection.length }})</el-button>
        </template>
      </div>

      <div class="toolbar-group">
        <!-- 类型筛选 -->
        <el-select v-model="typeFilter" style="width: 116px;">
          <el-option label="全部类型" value="" />
          <el-option label="文档" value="DOC" />
          <el-option label="图片" value="IMAGE" />
          <el-option label="压缩包" value="ZIP" />
          <el-option label="视频" value="VIDEO" />
        </el-select>
        <!-- 搜索: 回车或清空即触发 -->
        <el-input v-model="keyword" placeholder="搜索文件名" clearable style="width: 200px;" :prefix-icon="'Search'"
          @keyup.enter="doSearch" @clear="doSearch" />
        <!-- 视图切换: 表格 / 卡片 -->
        <el-radio-group v-model="viewMode">
          <el-radio-button value="table" title="表格视图"><el-icon>
              <List />
            </el-icon></el-radio-button>
          <el-radio-button value="card" title="卡片视图"><el-icon>
              <Grid />
            </el-icon></el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <!-- 上传/替换进度条 -->
    <el-progress v-if="uploading" :percentage="uploadPercent" :stroke-width="6" style="margin-bottom: 12px;"
      :status="uploadPercent >= 100 ? 'success' : undefined">
      <span style="font-size: 12px; color: #909399;">{{ replaceMode ? '正在替换文件内容...' : '正在上传...' }}</span>
    </el-progress>

    <!-- 隐藏的文件选择器: 用于"替换内容" -->
    <input ref="replaceInputRef" type="file" class="replace-file-input" style="display: none;"
      @change="onReplaceFileSelect" />

    <!-- 首次加载骨架屏(优于全屏转圈) -->
    <div v-if="loading && filteredFiles.length === 0" class="table-skeleton">
      <el-skeleton :rows="6" animated />
    </div>

    <!-- 空状态 -->
    <el-empty v-else-if="!loading && filteredFiles.length === 0" :description="emptyText" />

    <!-- ============ 表格视图 ============ -->
    <div v-if="viewMode === 'table' && filteredFiles.length > 0" class="table-panel">
      <el-table :data="filteredFiles" v-loading="loading" @selection-change="selection = $event"
        :row-class-name="({ row }) => row.isFavorite === 1 ? 'row-favorite' : ''">
        <el-table-column v-if="isTrash || canUpload" type="selection" width="46" />
        <el-table-column label="文件名" min-width="260">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 8px; cursor: pointer;" @click="openPreview(row)">
              <el-icon :size="20" :color="typeColor(row.fileType)">
                <component :is="typeIcon(row.fileType)" />
              </el-icon>
              <span>{{ row.filename }}</span>
              <el-icon v-if="row.isFavorite === 1" :size="15" color="#e6a23c">
                <StarFilled />
              </el-icon>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :class="typeTag(row.fileType).cls" size="small">{{ typeTag(row.fileType).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column v-if="!isTrash" label="公开" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isPublic === 1 ? 'success' : 'info'" size="small">{{ row.isPublic === 1 ? '公开' : '私有'
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="!isOwnerOnlyView" label="拥有者" width="100">
          <template #default="{ row }">{{ row.ownerName }}</template>
        </el-table-column>
        <el-table-column v-if="!isTrash" label="我的权限" width="100">
          <template #default="{ row }">
            <el-tag :type="permType(row.myPermission)" size="small">{{ permLabel(row.myPermission) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下载量" width="80" prop="downloadCount" />
        <el-table-column label="上传时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" :width="isTrash ? 150 : 170" fixed="right">
          <template #default="{ row }">
            <!-- 回收站: 恢复 + 彻底删除 -->
            <template v-if="isTrash">
              <el-button link type="primary" size="small" @click="handleRestore(row)">恢复</el-button>
              <el-button link type="danger" size="small" @click="handleForever(row)">彻底删除</el-button>
            </template>
            <!-- 正常文件: 高频操作平铺,低频操作收纳到"更多" -->
            <template v-else>
              <el-button link type="primary" size="small" @click="openPreview(row)">预览</el-button>
              <el-button link size="small" @click="handleDownload(row)">下载</el-button>
              <el-dropdown v-if="canEdit(row) || isOwner(row)" trigger="click" @command="(cmd) => handleMore(cmd, row)">
                <el-button link size="small" class="more-btn">
                  更多<el-icon>
                    <ArrowDown />
                  </el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="canEdit(row)" command="rename">重命名</el-dropdown-item>
                    <el-dropdown-item v-if="canEdit(row)" command="replace">替换内容</el-dropdown-item>
                    <el-dropdown-item v-if="isOwner(row) && canUpload" command="favorite">
                      {{ row.isFavorite === 1 ? '取消收藏' : '收藏' }}
                    </el-dropdown-item>
                    <el-dropdown-item v-if="isOwner(row) && canUpload" command="share">分享链接</el-dropdown-item>
                    <el-dropdown-item v-if="isOwner(row)" command="permission">授权管理</el-dropdown-item>
                    <el-dropdown-item v-if="isOwner(row) && canUpload" divided command="delete">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- ============ 卡片视图 ============ -->
    <div v-if="viewMode === 'card' && filteredFiles.length > 0" class="file-card-grid" v-loading="loading">
      <div v-for="row in filteredFiles" :key="row.id" class="file-card" @click="openPreview(row)">
        <el-icon v-if="isOwner(row) && canUpload && !isTrash" class="fc-star"
          :style="row.isFavorite === 1 ? 'color:#e6a23c' : 'color:#c0c4cc'" @click.stop="toggleStar(row)">
          <component :is="row.isFavorite === 1 ? 'StarFilled' : 'Star'" />
        </el-icon>
        <div class="fc-icon" :style="{ background: typeBg(row.fileType) }">
          <el-icon :size="26" :color="typeColor(row.fileType)">
            <component :is="typeIcon(row.fileType)" />
          </el-icon>
        </div>
        <div class="fc-name">{{ row.filename }}</div>
        <div class="fc-meta">
          <el-tag :class="typeTag(row.fileType).cls" size="small">{{ typeTag(row.fileType).label }}</el-tag>
          <span>{{ formatSize(row.size) }}</span>
        </div>
        <div class="fc-meta">
          <span v-if="isTrash">已删除</span>
          <span v-else>{{ row.isPublic === 1 ? '公开' : '私有' }}</span>
          <span>下载 {{ row.downloadCount }}</span>
        </div>
        <div class="fc-actions" @click.stop>
          <el-button link type="success" size="small" @click="handleDownload(row)">下载</el-button>
          <template v-if="isTrash">
            <el-button link type="success" size="small" @click="handleRestore(row)">恢复</el-button>
            <el-button link type="danger" size="small" @click="handleForever(row)">彻底删除</el-button>
          </template>
          <template v-else>
            <el-button v-if="canEdit(row)" link type="warning" size="small" @click="openRename(row)">重命名</el-button>
            <el-button v-if="canEdit(row)" link type="warning" plain size="small"
              @click="startReplace(row)">替换内容</el-button>
            <el-button v-if="isOwner(row) && canUpload" link type="info" size="small"
              @click="openShare(row)">分享</el-button>
            <el-button v-if="isOwner(row)" link type="info" size="small" @click="openPerm(row)">授权</el-button>
            <el-button v-if="isOwner(row) && canUpload" link type="danger" size="small"
              @click="handleDelete(row)">删除</el-button>
          </template>
        </div>
      </div>
    </div>

    <!-- ============ 分页(服务端分页) ============ -->
    <div v-if="total > size" class="pagination-bar">
      <el-pagination background layout="total, sizes, prev, pager, next" :total="total" :current-page="page"
        :page-size="size" :page-sizes="[10, 20, 50]" @current-change="(p) => emit('page-change', p)"
        @size-change="(s) => emit('size-change', s)" />
    </div>

    <!-- ============ 预览对话框 ============ -->
    <el-dialog v-model="previewVisible" :title="current?.filename || '文件预览'" width="720px">
      <div v-if="current">
        <div
          style="text-align: center; padding: 12px; background: #f7f8fa; border: 1px solid #f2f3f5; border-radius: 4px;">
          <img v-if="current.fileType === 'IMAGE'" :src="current.fileUrl"
            style="max-width: 100%; max-height: 340px; border-radius: 4px;" />
          <video v-else-if="current.fileType === 'VIDEO'" :src="current.fileUrl" controls
            style="max-width: 100%; max-height: 340px; border-radius: 4px;" />
          <div v-else-if="previewText">
            <pre
              style="text-align: left; max-height: 340px; overflow: auto; background: #fff; border: 1px solid #e5e6eb; border-radius: 4px; padding: 12px; font-size: 13px; line-height: 1.7; white-space: pre-wrap; word-break: break-all;">
          {{ previewText }}</pre>
            <div v-if="previewText.trim().endsWith('…') || previewText.length >= 50000"
              style="margin-top: 6px; font-size: 12px; color: #909399;">(已显示前50KB,完整内容请下载查看)</div>
          </div>
          <div v-else style="padding: 30px 0;">
            <el-icon :size="60" :color="typeColor(current.fileType)">
              <component :is="typeIcon(current.fileType)" />
            </el-icon>
          </div>
        </div>
        <el-descriptions :column="2" border style="margin-top: 16px;">
          <el-descriptions-item label="文件名称">{{ current.filename }}</el-descriptions-item>
          <el-descriptions-item label="文件类型">{{ typeTag(current.fileType).label }}</el-descriptions-item>
          <el-descriptions-item label="文件大小">{{ formatSize(current.size) }}</el-descriptions-item>
          <el-descriptions-item label="拥有者">{{ current.ownerName }}</el-descriptions-item>
          <el-descriptions-item label="公开状态">{{ current.isPublic === 1 ? '公开' : '私有' }}</el-descriptions-item>
          <el-descriptions-item label="我的权限">{{ permLabel(current.myPermission) }}</el-descriptions-item>
          <el-descriptions-item label="上传时间" :span="2">{{ formatDateTime(current.createTime) }}</el-descriptions-item>
        </el-descriptions>
        <div style="text-align: center; margin-top: 18px;">
          <el-button v-if="isTextExt(current.filename)" :loading="previewLoading" :icon="'Document'"
            @click="loadPreviewText">{{ previewText ? '刷新文本内容' : '查看文本内容' }}</el-button>
          <el-button type="primary" :icon="'Download'" @click="handleDownload(current)">下载文件</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- ============ 重命名对话框 ============ -->
    <el-dialog v-model="renameVisible" title="重命名文件" width="420px">
      <el-input v-model="renameName" placeholder="请输入新的文件名" maxlength="255" @keyup.enter="confirmRename" />
      <template #footer>
        <el-button @click="renameVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmRename">确定</el-button>
      </template>
    </el-dialog>

    <!-- ============ 授权管理对话框 ============ -->
    <el-dialog v-model="permVisible" :title="'授权管理 - ' + (current?.filename || '')" width="600px">
      <div style="display: flex; gap: 10px; margin-bottom: 16px;">
        <el-select v-model="permTargetId" filterable remote :remote-method="searchUser" placeholder="输入用户名搜索用户"
          style="flex: 1;" :loading="searching">
          <el-option v-for="u in userOptions" :key="u.id" :label="u.nickname + '(' + u.username + ')'" :value="u.id" />
        </el-select>
        <el-select v-model="permValue" style="width: 120px;">
          <el-option label="可查看" value="VIEW" />
          <el-option label="可编辑" value="EDIT" />
        </el-select>
        <el-button type="primary" @click="confirmGrant">授权</el-button>
      </div>
      <el-table :data="permList" stripe empty-text="暂无授权记录">
        <el-table-column label="用户" min-width="120">
          <template #default="{ row }">{{ row.nickname }}({{ row.username }})</template>
        </el-table-column>
        <el-table-column label="权限" width="90">
          <template #default="{ row }">
            <el-tag :type="row.permission === 'EDIT' ? 'warning' : 'success'" size="small">
              {{ row.permission === 'EDIT' ? '可编辑' : '可查看' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.source === 'FRIEND'" type="primary" size="small">好友授权</el-tag>
            <el-tag v-else size="small">手动授权</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="授权时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="danger" size="small" @click="confirmRemovePerm(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- ============ 分享对话框 ============ -->
    <el-dialog v-model="shareVisible" :title="'分享文件 - ' + (current?.filename || '')" width="560px">
      <div v-if="shareUrl" class="share-result">
        <div class="share-result-tip">
          任何人凭此链接可查看/下载该文件(无需登录)。文件移入回收站或彻底删除后链接自动失效。
        </div>
        <div class="share-result-meta">
          有效期: {{ shareExpiresAt ? '至 ' + formatDateTime(shareExpiresAt) : '永久有效' }}
          <span v-if="shareHasPassword"> · 访问密码: 已设置(访问时需输入)</span>
        </div>
        <div style="display: flex; gap: 8px;">
          <el-input v-model="shareUrl" readonly />
          <el-button type="primary" :icon="'Link'" @click="copyShareUrl">复制链接</el-button>
        </div>
        <div style="margin-top: 10px; text-align: center;">
          <el-button type="danger" plain :icon="'CircleClose'" @click="cancelShare">取消分享</el-button>
        </div>
      </div>
      <div v-else style="padding: 6px 0;">
        <el-form label-width="90px">
          <el-form-item label="有效期">
            <el-select v-model="shareExpireDays" style="width: 100%;">
              <el-option label="永久有效" :value="0" />
              <el-option label="1天" :value="1" />
              <el-option label="7天" :value="7" />
            </el-select>
          </el-form-item>
          <el-form-item label="访问密码">
            <el-input v-model="sharePassword" placeholder="选填,设置后需输入密码才能访问(4-16位)" show-password maxlength="16" />
          </el-form-item>
        </el-form>
        <div style="text-align: center; color: #909399; font-size: 13px;">
          尚未生成分享链接,点击下方按钮生成
        </div>
      </div>
      <template #footer>
        <el-button @click="shareVisible = false">关闭</el-button>
        <el-button v-if="!shareUrl" type="primary" :icon="'Link'" :loading="shareLoading"
          @click="createShare">生成分享链接</el-button>
      </template>
    </el-dialog>

    <!-- ============ 批量授权对话框 ============ -->
    <el-dialog v-model="batchGrantVisible" :title="'批量授权(' + selection.length + ' 个文件)'" width="480px">
      <div style="font-size: 13px; color: #909399; margin-bottom: 12px;">
        将选中的 {{ selection.length }} 个文件同时授权给同一位用户(仅能授权自己拥有的文件)。
      </div>
      <el-form label-width="80px">
        <el-form-item label="授权用户">
          <el-select v-model="batchGrantUserId" filterable remote :remote-method="searchGrantUser"
            placeholder="输入用户名或昵称搜索" style="width: 100%;">
            <el-option v-for="u in grantUserOptions" :key="u.id" :label="u.nickname + ' (@' + u.username + ')'"
              :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="权限">
          <el-radio-group v-model="batchGrantPerm">
            <el-radio value="VIEW">查看</el-radio>
            <el-radio value="EDIT">编辑</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchGrantVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchGrantLoading" @click="submitBatchGrant">确认批量授权</el-button>
      </template>
    </el-dialog>

    <!-- ============ 批量分享对话框 ============ -->
    <el-dialog v-model="batchShareVisible" :title="'批量分享(' + selection.length + ' 个文件)'" width="480px">
      <div style="font-size: 13px; color: #909399; margin-bottom: 12px;">
        为选中的 {{ selection.length }} 个文件生成分享链接(仅能分享自己拥有的文件)。
      </div>
      <el-form label-width="80px">
        <el-form-item label="有效期">
          <el-select v-model="batchShareExpireDays" style="width: 100%;">
            <el-option label="永久有效" :value="0" />
            <el-option label="1天" :value="1" />
            <el-option label="7天" :value="7" />
          </el-select>
        </el-form-item>
        <el-form-item label="访问密码">
          <el-input v-model="batchSharePassword" placeholder="选填,设置后需输入密码才能访问(4-16位)" show-password maxlength="16" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchShareVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchShareLoading" @click="submitBatchShare">确认批量分享</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatSize, fileTypeTag, fileTypeIcon, downloadBlob, formatDateTime, isTextExt } from '@/utils/format'
import {
  apiUpload, apiFileRename, apiFileReplace, apiFileDelete, apiFileDownload, apiFilePreviewText,
  apiSearchUsers, apiGrantPermission, apiPermissionList, apiRemovePermission, apiBatchGrant,
  apiFileFavorite, apiFileBatchDelete, apiFileRestore, apiFileForever,
  apiCreateShare, apiListShare, apiDeleteShare, apiBatchShare
} from '@/api'

// ============ props: 视图类型决定可执行的操作 ============
const props = defineProps({
  files: { type: Array, default: () => [] },
  viewType: { type: String, default: 'mine' },   // mine / public / shared / favorites / trash
  loading: { type: Boolean, default: false },
  // 分页(服务端分页,由父页面管理页码状态)
  total: { type: Number, default: 0 },
  page: { type: Number, default: 1 },
  size: { type: Number, default: 10 }
})
const emit = defineEmits(['refresh', 'page-change', 'size-change'])

const canUpload = props.viewType === 'mine'
const isTrash = props.viewType === 'trash'
const isOwnerOnlyView = props.viewType === 'mine' || props.viewType === 'favorites' || props.viewType === 'trash'
const isOwner = (row) => row.myPermission === 'OWNER'
const canEdit = (row) => row.myPermission === 'OWNER' || row.myPermission === 'EDIT'
const emptyText = computed(() => {
  if (isTrash) return '回收站是空的,删除的文件会在这里'
  if (props.viewType === 'favorites') return '还没有收藏任何文件,点击星标即可收藏'
  if (props.viewType === 'public') return '暂无公开文件'
  if (props.viewType === 'shared') return '还没有人向你分享文件'
  return '暂无文件,点击右上角"上传文件"开始'
})

// ============ 工具 ============
const typeTag = fileTypeTag
const typeIcon = (t) => fileTypeIcon(t)
const typeColor = (t) => ({ DOC: '#245bdb', IMAGE: '#b35b00', ZIP: '#00892b', VIDEO: '#b8323f' }[t] || '#86909c')
const typeBg = (t) => ({ DOC: '#eef3ff', IMAGE: '#fff7e8', ZIP: '#eaf8ea', VIDEO: '#ffeceb' }[t] || '#f2f3f5')
const permLabel = (p) => ({ OWNER: '拥有者', EDIT: '可编辑', VIEW: '可查看', NONE: '无权限' }[p] || p)
const permType = (p) => ({ OWNER: 'primary', EDIT: 'warning', VIEW: 'success', NONE: 'info' }[p] || 'info')

// ============ 视图模式 / 筛选 / 搜索 ============
const viewMode = ref('table')
const typeFilter = ref('')
const keyword = ref('')
const filteredFiles = computed(() => {
  let list = props.files
  if (typeFilter.value) {
    list = list.filter(f => f.fileType === typeFilter.value)
  }
  return list
})
function doSearch() { emit('refresh', keyword.value) }

/** "更多"菜单: 保留全部原有操作,仅做入口收纳 */
function handleMore(command, row) {
  if (command === 'rename') {
    openRename(row)
  } else if (command === 'replace') {
    startReplace(row)
  } else if (command === 'favorite') {
    toggleStar(row)
  } else if (command === 'share') {
    openShare(row)
  } else if (command === 'permission') {
    openPerm(row)
  } else if (command === 'delete') {
    handleDelete(row)
  }
}

// ============ 批量授权 / 批量分享 ============
const batchGrantVisible = ref(false)
const batchGrantUserId = ref(null)
const batchGrantPerm = ref('VIEW')
const grantUserOptions = ref([])
const batchGrantLoading = ref(false)
function openBatchGrant() {
  batchGrantUserId.value = null
  batchGrantPerm.value = 'VIEW'
  grantUserOptions.value = []
  batchGrantVisible.value = true
}
async function searchGrantUser(kw) {
  if (!kw) { grantUserOptions.value = []; return }
  try {
    const res = await apiSearchUsers(kw)
    grantUserOptions.value = res.data || []
  } catch (e) { /* 忽略 */ }
}
async function submitBatchGrant() {
  if (!batchGrantUserId.value) {
    ElMessage.warning('请先选择要授权的用户')
    return
  }
  batchGrantLoading.value = true
  try {
    const res = await apiBatchGrant({
      fileIds: selection.value.map(f => f.id),
      userId: batchGrantUserId.value,
      permission: batchGrantPerm.value
    })
    ElMessage.success(res.msg || '批量授权成功')
    batchGrantVisible.value = false
    selection.value = []
    emit('refresh')
  } catch (e) { /* 拦截器已提示 */ } finally {
    batchGrantLoading.value = false
  }
}

const batchShareVisible = ref(false)
const batchShareExpireDays = ref(0)
const batchSharePassword = ref('')
const batchShareLoading = ref(false)
function openBatchShare() {
  batchShareExpireDays.value = 0
  batchSharePassword.value = ''
  batchShareVisible.value = true
}
async function submitBatchShare() {
  // 密码长度校验(与后端强校验一致,4-16位)
  if (batchSharePassword.value && (batchSharePassword.value.length < 4 || batchSharePassword.value.length > 16)) {
    ElMessage.warning('分享访问密码长度须为4-16位')
    return
  }
  batchShareLoading.value = true
  try {
    const res = await apiBatchShare(
      selection.value.map(f => f.id),
      batchShareExpireDays.value,
      batchSharePassword.value
    )
    ElMessage.success(`已生成 ${res.data.length} 个分享链接`)
    batchShareVisible.value = false
    selection.value = []
    emit('refresh')
  } catch (e) { /* 拦截器已提示 */ } finally {
    batchShareLoading.value = false
  }
}

// ============ 批量选择 ============
const selection = ref([])

// ============ 存储空间联动 ============
/** 通知侧边栏(UserLayout)刷新存储空间展示(上传/删除/恢复会改变已用空间) */
function notifyStorageChange() {
  window.dispatchEvent(new CustomEvent('xiehe:storage-refresh'))
}

// ============ 上传(带进度) ============
const uploading = ref(false)
const uploadPercent = ref(0)
const replaceMode = ref(false)
async function handleUpload({ file }) {
  const formData = new FormData()
  formData.append('file', file)
  replaceMode.value = false
  uploading.value = true
  uploadPercent.value = 0
  try {
    const res = await apiUpload(formData, (p) => { uploadPercent.value = p })
    ElMessage.success(`上传成功: ${res.data.filename}`)
    notifyStorageChange()
    emit('refresh')
  } catch (e) { /* 拦截器已提示 */ } finally {
    uploading.value = false
  }
}

// ============ 替换文件内容(拥有者或EDIT权限) ============
const replaceInputRef = ref(null)
/** 前端扩展名白名单(与后端一致,提前拦截不合规文件) */
const ALLOWED_EXTENSIONS = [
  'doc', 'docx', 'txt', 'pdf', 'md', 'xls', 'xlsx', 'ppt', 'pptx', 'log',
  'png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'ico',
  'zip', 'rar', '7z', 'tar', 'gz',
  'mp4', 'avi', 'mov', 'mkv', 'flv', 'wmv', 'webm',
  'java', 'py', 'c', 'cpp', 'h', 'sql', 'json', 'yml', 'yaml', 'xml'
]
function startReplace(row) {
  current.value = row
  if (replaceInputRef.value) {
    replaceInputRef.value.value = ''   // 清空旧选择,保证相同文件也能触发change
    replaceInputRef.value.click()
  }
}
async function onReplaceFileSelect(e) {
  const file = e.target.files?.[0]
  if (!file) return
  // 前端提前校验扩展名,减少无效请求
  const name = file.name.toLowerCase()
  const dot = name.lastIndexOf('.')
  const ext = dot >= 0 ? name.substring(dot + 1) : ''
  if (!ALLOWED_EXTENSIONS.includes(ext)) {
    ElMessage.error(`不支持的文件类型: .${ext || '(无扩展名)'}`)
    return
  }
  const formData = new FormData()
  formData.append('file', file)
  replaceMode.value = true
  uploading.value = true
  uploadPercent.value = 0
  try {
    const res = await apiFileReplace(current.value.id, formData, (p) => { uploadPercent.value = p })
    ElMessage.success(`「${current.value.filename}」内容已替换,新大小 ${formatSize(res.data.size)}`)
    notifyStorageChange()
    emit('refresh')
  } catch (err) { /* 拦截器已提示 */ } finally {
    uploading.value = false
    if (replaceInputRef.value) replaceInputRef.value.value = ''
  }
}

// ============ 预览(图片/视频/文本) ============
const previewVisible = ref(false)
const current = ref(null)
const previewText = ref('')
const previewLoading = ref(false)
function openPreview(row) {
  current.value = row
  previewText.value = ''
  previewVisible.value = true
}
async function loadPreviewText() {
  previewLoading.value = true
  try {
    const res = await apiFilePreviewText(current.value.id)
    previewText.value = res.data
  } catch (e) { /* 拦截器已提示 */ } finally {
    previewLoading.value = false
  }
}

// ============ 下载 ============
async function handleDownload(row) {
  try {
    const res = await apiFileDownload(row.id)
    downloadBlob(res.data, row.filename)
    ElMessage.success('下载成功')
    emit('refresh')   // 刷新下载量
  } catch (e) { /* 拦截器已提示 */ }
}

// ============ 收藏(星标) ============
async function toggleStar(row) {
  const target = row.isFavorite === 1 ? 0 : 1
  await apiFileFavorite(row.id, target)
  ElMessage.success(target === 1 ? '已收藏' : '已取消收藏')
  emit('refresh')
}

// ============ 重命名 ============
const renameVisible = ref(false)
const renameName = ref('')
function openRename(row) {
  current.value = row
  renameName.value = row.filename
  renameVisible.value = true
}
async function confirmRename() {
  if (!renameName.value.trim()) { ElMessage.warning('文件名不能为空'); return }
  await apiFileRename(current.value.id, renameName.value.trim())
  ElMessage.success('重命名成功')
  renameVisible.value = false
  emit('refresh')
}

// ============ 删除 / 回收站 ============
async function handleDelete(row) {
  await ElMessageBox.confirm(`确定将「${row.filename}」移入回收站吗?可在回收站中恢复。`, '删除确认', { type: 'warning' })
  await apiFileDelete(row.id)
  ElMessage.success('已移入回收站')
  notifyStorageChange()
  emit('refresh')
}
async function handleBatchDelete() {
  await ElMessageBox.confirm(`确定将这 ${selection.value.length} 个文件移入回收站吗?可在回收站中恢复。`, '批量删除确认', { type: 'warning' })
  await apiFileBatchDelete(selection.value.map(f => f.id))
  ElMessage.success('已批量移入回收站')
  selection.value = []
  notifyStorageChange()
  emit('refresh')
}
async function handleRestore(row) {
  await apiFileRestore(row.id)
  ElMessage.success('已恢复')
  notifyStorageChange()
  emit('refresh')
}
async function handleBatchRestore() {
  for (const f of selection.value) { await apiFileRestore(f.id) }
  ElMessage.success('批量恢复成功')
  selection.value = []
  notifyStorageChange()
  emit('refresh')
}
async function handleForever(row) {
  await ElMessageBox.confirm(`彻底删除「${row.filename}」将无法恢复,确定吗?`, '彻底删除确认', { type: 'error' })
  await apiFileForever(row.id)
  ElMessage.success('已彻底删除')
  notifyStorageChange()
  emit('refresh')
}
async function handleBatchForever() {
  await ElMessageBox.confirm(`彻底删除这 ${selection.value.length} 个文件将无法恢复,确定吗?`, '批量彻底删除', { type: 'error' })
  for (const f of selection.value) { await apiFileForever(f.id) }
  ElMessage.success('批量彻底删除成功')
  selection.value = []
  notifyStorageChange()
  emit('refresh')
}

// ============ 授权管理 ============
const permVisible = ref(false)
const permList = ref([])
const permTargetId = ref(null)
const permValue = ref('VIEW')
const userOptions = ref([])
const searching = ref(false)

async function openPerm(row) {
  current.value = row
  permTargetId.value = null
  permValue.value = 'VIEW'
  await loadPermList()
  permVisible.value = true
}
async function loadPermList() {
  const res = await apiPermissionList(current.value.id)
  permList.value = res.data
}
async function searchUser(keyword) {
  if (!keyword) { userOptions.value = []; return }
  searching.value = true
  try {
    const res = await apiSearchUsers(keyword)
    const me = JSON.parse(localStorage.getItem('xiehe_user') || 'null')
    userOptions.value = res.data.filter(u => u.id !== me?.id)
  } finally {
    searching.value = false
  }
}
async function confirmGrant() {
  if (!permTargetId.value) { ElMessage.warning('请先选择要授权的用户'); return }
  await apiGrantPermission(current.value.id, { userId: permTargetId.value, permission: permValue.value })
  ElMessage.success('授权成功')
  await loadPermList()
}
async function confirmRemovePerm(row) {
  await ElMessageBox.confirm(`确定取消「${row.nickname}」的访问权限吗?`, '提示', { type: 'warning' })
  await apiRemovePermission(current.value.id, row.userId)
  ElMessage.success('已取消授权')
  await loadPermList()
}

// ============ 分享链接 ============
const shareVisible = ref(false)
const shareUrl = ref('')
const shareLoading = ref(false)
const shareExpireDays = ref(0)       // 0=永久 1=1天 7=7天
const sharePassword = ref('')        // 选填访问密码
const shareExpiresAt = ref(null)     // 已生成分享的过期时间
const shareHasPassword = ref(false)  // 已生成分享是否设了密码
async function openShare(row) {
  current.value = row
  shareVisible.value = true
  shareUrl.value = ''
  shareExpireDays.value = 0
  sharePassword.value = ''
  shareExpiresAt.value = null
  shareHasPassword.value = false
  try {
    const res = await apiListShare(row.id)
    if (res.data && res.data.length > 0) {
      const s = res.data[0]
      shareUrl.value = s.url
      shareExpiresAt.value = s.expiresAt
      shareHasPassword.value = !!s.hasPassword
    }
  } catch (e) { /* 忽略 */ }
}
async function createShare() {
  // 密码长度校验(与后端强校验一致,4-16位)
  if (sharePassword.value && (sharePassword.value.length < 4 || sharePassword.value.length > 16)) {
    ElMessage.warning('分享访问密码长度须为4-16位')
    return
  }
  shareLoading.value = true
  try {
    const res = await apiCreateShare(current.value.id, shareExpireDays.value, sharePassword.value)
    shareUrl.value = res.data.url
    shareExpiresAt.value = res.data.expiresAt
    shareHasPassword.value = !!res.data.hasPassword
    ElMessage.success('分享链接已生成')
  } catch (e) { /* 拦截器已提示 */ } finally {
    shareLoading.value = false
  }
}
async function copyShareUrl() {
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    ElMessage.success('链接已复制,可发送给任何人')
  } catch (e) {
    ElMessage.warning('复制失败,请手动选择复制')
  }
}
async function cancelShare() {
  await ElMessageBox.confirm('取消分享后,原链接将立即失效,确定吗?', '提示', { type: 'warning' })
  await apiDeleteShare(current.value.id)
  ElMessage.success('已取消分享')
  shareUrl.value = ''
}
</script>

<style>
/* 表格视图容器 */
.table-panel {
  margin-bottom: 0;
}

.table-skeleton {
  margin-bottom: 0;
}

/* 分页条 */
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

/* "更多"操作入口: 弱化为次要文字操作 */
.more-btn {
  margin-left: 10px;
  color: var(--xh-text-2);
}

.more-btn:hover {
  color: var(--xh-primary);
}

.more-btn .el-icon {
  margin-left: 2px;
  font-size: 12px;
}

/* 分享结果: 中性提示块,不使用高饱和绿色 */
.share-result {
  padding: 16px;
  background: #f7f8fa;
  border: 1px solid var(--xh-border);
  border-radius: var(--xh-radius-md);
}

.share-result-tip {
  margin-bottom: 8px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--xh-text-2);
}

.share-result-meta {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--xh-text-3);
}

.share-result-actions {
  margin-top: 10px;
  text-align: center;
}
</style>
