# ============================================================
# 协合XieHe 后端接口自动化测试 v2 (新功能: 回收站/收藏/分享/预览/批量删除/统计)
# 运行方式: powershell -ExecutionPolicy Bypass -File test-backend-v2.ps1
# 说明: 业务错误统一为 HTTP 200 + body.code!=200, 断言看 body.code
# ============================================================
$ErrorActionPreference = "Stop"
$base = "http://localhost:8080"
$pass = 0; $fail = 0

# ---------- 断言工具 ----------
function Assert($name, $cond, $extra = "") {
  if ($cond) {
    $script:pass++
    Write-Host "[PASS] $name" -ForegroundColor Green
  } else {
    $script:fail++
    Write-Host "[FAIL] $name  $extra" -ForegroundColor Red
  }
}

function Req($method, $url, $token, $body) {
  $args = @("-s", "-m", "20", "-X", $method, "$base$url")
  if ($token) { $args += @("-H", "Authorization: Bearer $token") }
  if ($body) {
    $tmp = "$env:TEMP\req_body.json"
    Set-Content -Path $tmp -Value $body -Encoding UTF8
    $args += @("-H", "Content-Type: application/json", "-d", "@$tmp")
  }
  $r = & curl.exe @args
  return ($r | ConvertFrom-Json)
}

function Login($username, $password) {
  $body = "{`"username`":`"$username`",`"password`":`"$password`"}"
  $r = Req "POST" "/api/auth/login" $null $body
  return $r
}

# ============================================================
Write-Host "========== 协合XieHe 后端 v2 测试开始 ==========" -ForegroundColor Cyan

# ---------- 登录 ----------
$r = Login "zhangsan" "123456"
Assert "zhangsan登录成功" ($r.code -eq 200)
$token = $r.data.token
$r = Login "lisi" "123456"
Assert "lisi登录成功" ($r.code -eq 200)
$token2 = $r.data.token
$r = Login "admin" "admin123"
Assert "admin登录成功" ($r.code -eq 200)
$tokenAdmin = $r.data.token

# ---------- 用户统计 ----------
$r = Req "GET" "/api/user/stats" $token
Assert "用户统计接口" ($r.code -eq 200)
Assert "统计-文件数=3" ($r.data.fileCount -eq 3) "实际=$($r.data.fileCount)"
Assert "统计-公开数=2" ($r.data.publicCount -eq 2) "实际=$($r.data.publicCount)"
Assert "统计-下载量=45" ($r.data.totalDownloads -eq 45) "实际=$($r.data.totalDownloads)"
Assert "统计-收到共享=1" ($r.data.sharedCount -eq 1) "实际=$($r.data.sharedCount)"

# ---------- 收藏(星标) ----------
$r = Req "PUT" "/api/file/1/favorite?isFavorite=1" $token
Assert "收藏文件1" ($r.code -eq 200 -and $r.data.isFavorite -eq 1)
$r = Req "GET" "/api/file/list/favorites" $token
Assert "收藏列表含文件1" ($r.data.records.Count -eq 1 -and $r.data.records[0].id -eq 1)
# 非拥有者不能收藏
$r = Req "PUT" "/api/file/1/favorite?isFavorite=1" $token2
Assert "非拥有者收藏被拒" ($r.code -eq 403) "code=$($r.code)"
# 取消收藏
$r = Req "PUT" "/api/file/1/favorite?isFavorite=0" $token
Assert "取消收藏文件1" ($r.code -eq 200 -and $r.data.isFavorite -eq 0)

# ---------- 分享链接 ----------
$r = Req "POST" "/api/file/1/share" $token
Assert "创建分享链接" ($r.code -eq 200 -and $r.data.token.Length -gt 0)
$shareUrl = $r.data.url
Assert "分享链接格式正确" ($shareUrl -match "^http://localhost:5173/share/[0-9a-f]{32}$") $shareUrl
$tokenShare = $r.data.token
# 免登录访问分享
$r = Req "GET" "/api/share/$tokenShare" $null
Assert "免登录查看分享" ($r.code -eq 200 -and $r.data.filename -eq "项目需求说明书.docx")
Assert "免登录权限为VIEW" ($r.data.myPermission -eq "VIEW")
# 非拥有者不能创建分享
$r = Req "POST" "/api/file/1/share" $token2
Assert "非拥有者创建分享被拒" ($r.code -eq 403) "code=$($r.code)"
# 分享下载(免登录)
$r = & curl.exe -s -m 20 -o NUL -w "%{http_code}" "$base/api/share/$tokenShare/download"
Assert "免登录分享下载200" ($r -eq 200) "http=$r"
# 重复创建返回同一链接
$r2 = Req "POST" "/api/file/1/share" $token
Assert "重复创建返回原链接" ($r2.data.token -eq $tokenShare)
# 取消分享后失效
$r = Req "DELETE" "/api/file/1/share" $token
Assert "取消分享" ($r.code -eq 200)
$r = Req "GET" "/api/share/$tokenShare" $null
Assert "取消后链接404" ($r.code -eq 404) "code=$($r.code)"

# ---------- 分享增强: 有效期 + 访问密码 ----------
# 带密码+7天有效期的分享(文件2,zhangsan拥有)
$r = Req "POST" "/api/file/2/share?expireDays=7&password=8888" $token
Assert "创建带密码分享" ($r.code -eq 200 -and $r.data.hasPassword -eq $true -and $r.data.expired -eq $false)
Assert "带密码分享有有效期" ($r.data.expiresAt -ne $null)
$tokenSharePwd = $r.data.token
# 无密码访问被拒
$r = Req "GET" "/api/share/$tokenSharePwd" $null
Assert "无密码访问被拒" ($r.code -eq 403) "code=$($r.code)"
# 错误密码被拒(注意: 变量后紧跟?查询串,PowerShell会误解析变量名,必须用 $() 包裹)
$r = Req "GET" "/api/share/$($tokenSharePwd)?password=1234" $null
Assert "错误密码访问被拒" ($r.code -eq 403) "code=$($r.code)"
# 正确密码访问成功
$r = Req "GET" "/api/share/$($tokenSharePwd)?password=8888" $null
Assert "正确密码访问成功" ($r.code -eq 200 -and $r.data.filename -eq "团队合影.jpg")
# 永久分享(expireDays=0)无过期时间: 先取消已有分享再创建,确保走新建逻辑
$r = Req "DELETE" "/api/file/2/share" $token
Assert "取消密码分享(为永久分享让路)" ($r.code -eq 200)
$r = Req "POST" "/api/file/2/share?expireDays=0" $token
Assert "永久分享无过期时间" ($r.code -eq 200 -and $null -eq $r.data.expiresAt)
# 取消密码分享,清理现场
$r = Req "DELETE" "/api/file/2/share" $token
Assert "取消密码分享" ($r.code -eq 200)

# ---------- 列表分页(新增) ----------
$r = Req "GET" "/api/file/list/mine?page=1&size=2" $token
Assert "我的文件分页total=3" ($r.code -eq 200 -and $r.data.total -eq 3) "total=$($r.data.total)"
Assert "我的文件第1页2条" ($r.data.records.Count -eq 2) "count=$($r.data.records.Count)"
$r = Req "GET" "/api/file/list/mine?page=2&size=2" $token
Assert "我的文件第2页1条" ($r.code -eq 200 -and $r.data.records.Count -eq 1)

# ---------- 批量授权/批量分享(F4) ----------
# 批量授权文件1/2给王五(仅拥有者可操作)
$r = Req "POST" "/api/file/batch-permission" $token '{"fileIds":[1,2],"userId":4,"permission":"VIEW"}'
Assert "批量授权2个文件给王五" ($r.code -eq 200 -and $r.data -eq 2) "code=$($r.code)"
$r = Req "GET" "/api/file/1/permission" $token
$hasWw = $r.data | Where-Object { $_.userId -eq 4 }
Assert "文件1授权列表含王五" ($null -ne $hasWw)
# 非拥有者李四批量授权 -> 403
$r = Req "POST" "/api/file/batch-permission" $token2 '{"fileIds":[1,2],"userId":4,"permission":"VIEW"}'
Assert "非拥有者批量授权被拒" ($r.code -eq 403) "code=$($r.code)"
# 批量分享文件1/2(永久+无密码)
$r = Req "POST" "/api/file/batch-share?expireDays=0" $token '[1,2]'
Assert "批量分享2个文件" ($r.code -eq 200 -and $r.data.Count -eq 2) "count=$($r.data.Count)"
# 清理: 取消两文件分享 + 撤销王五授权
$r = Req "DELETE" "/api/file/1/share" $token
Assert "取消文件1批量分享" ($r.code -eq 200)
$r = Req "DELETE" "/api/file/2/share" $token
Assert "取消文件2批量分享" ($r.code -eq 200)
$r = Req "DELETE" "/api/file/1/permission/4" $token
Assert "撤销文件1王五授权" ($r.code -eq 200)
$r = Req "DELETE" "/api/file/2/permission/4" $token
Assert "撤销文件2王五授权" ($r.code -eq 200)

# ---------- 文本预览 ----------
$r = Req "GET" "/api/file/3/preview" $token
Assert "文本预览成功" ($r.code -eq 200 -and $r.data.Contains("张三的个人笔记")) "code=$($r.code)"
# 无权限用户预览被拒 (zhangsan对lisi的私有文件5无授权)
$r = Req "GET" "/api/file/5/preview" $token
Assert "无权限预览被拒" ($r.code -eq 403) "code=$($r.code)"

# ---------- 回收站流转 ----------
$r = Req "DELETE" "/api/file/1" $token
Assert "删除文件1入回收站" ($r.code -eq 200)
$r = Req "GET" "/api/file/list/trash" $token
Assert "回收站列表含文件1" ($r.data.records.Count -eq 1 -and $r.data.records[0].id -eq 1)
# 回收站文件不能被预览(正常业务)
$r = Req "GET" "/api/file/1/preview" $token
Assert "回收站文件不可访问" ($r.code -ne 200) "code=$($r.code)"
# 恢复
$r = Req "PUT" "/api/file/1/restore" $token
Assert "恢复文件1" ($r.code -eq 200)
$r = Req "GET" "/api/file/list/trash" $token
Assert "恢复后回收站为空" ($r.data.records.Count -eq 0)
# 他人不能删除我的文件
$r = Req "DELETE" "/api/file/1" $token2
Assert "他人删除被拒" ($r.code -eq 403) "code=$($r.code)"

# ---------- 批量删除 ----------
$r = Req "POST" "/api/file/batch-delete" $token '{"ids":[2,3]}'
Assert "批量删除文件2/3" ($r.code -eq 200)
$r = Req "GET" "/api/file/list/trash" $token
Assert "回收站2个文件" ($r.data.records.Count -eq 2) "count=$($r.data.records.Count)"
# 混合拥有权: 张三批量删除包含李四的文件4 -> 只删自己的
$r = Req "POST" "/api/file/batch-delete" $token '{"ids":[3,4]}'
Assert "批量删除混合拥有权" ($r.code -eq 200)
$r = Req "GET" "/api/file/list/trash" $token
Assert "回收站仍2个(未删李四文件)" ($r.data.records.Count -eq 2) "count=$($r.data.records.Count)"

# ---------- 彻底删除 + 空间回收 ----------
$r = Req "GET" "/api/user/storage" $token
$usedBefore = $r.data.usedStorage
Assert "空间快照读取成功" ($r.code -eq 200)
# 文件3大小是 44 字节(小文本), 文件2是图片
$r = Req "DELETE" "/api/file/3/forever" $token
Assert "彻底删除文件3" ($r.code -eq 200)
$r = Req "GET" "/api/user/storage" $token
Assert "空间已回收" ($r.data.usedStorage -lt $usedBefore) "before=$usedBefore after=$($r.data.usedStorage)"
# 恢复文件2, 保留两个回收站文件最终恢复
$r = Req "PUT" "/api/file/2/restore" $token
Assert "恢复文件2" ($r.code -eq 200)

# ---------- 权限回归 ----------
# 张三公开文件2, 李四可下载不可重命名
$r = Req "PUT" "/api/file/2/public?isPublic=1" $token
Assert "张三公开文件2" ($r.code -eq 200)
$r = Req "PUT" "/api/file/2/rename?filename=hack.txt" $token2
Assert "李四重命名公开文件被拒" ($r.code -eq 403) "code=$($r.code)"
$r = Req "GET" "/api/file/2" $token2
Assert "李四可查看公开文件" ($r.code -eq 200)

# ---------- 管理端统计分布 (refresh=true 强制刷新缓存) ----------
$r = Req "GET" "/api/admin/stats?refresh=true" $tokenAdmin
Assert "管理端统计含类型分布" ($r.code -eq 200 -and $r.data.fileTypeDist.DOC -ge 1)
Assert "管理端统计含用户分布" ($r.code -eq 200 -and $r.data.userTypeDist.VIP -eq 2)
# 管理端文件列表不含回收站
$r = Req "GET" "/api/admin/file/list?page=1&size=50" $tokenAdmin
$adminFiles = $r.data.records | Where-Object { $_.status -ne 1 }
Assert "管理端列表不含回收站文件" ($adminFiles.Count -eq 0)

# ============================================================
Write-Host "`n========== 测试结果: 通过 $pass / 失败 $fail ==========" -ForegroundColor Cyan

# ---------- 测试善后: 恢复被测试"彻底删除"的种子文件 ----------
# 本套件会把种子文件"张三的个人笔记.txt"(文件3)彻底删除,
# 若不做善后,下次运行时文件3的文本预览会因物理文件缺失而失败。
$seedFile = "F:\AI_full-stack_project\XieHe\data\uploads\seed\张三的个人笔记.txt"
if (-not (Test-Path $seedFile)) {
  $content = "张三的个人笔记(私有文件)`r`n只有被授权的用户才能查看或编辑本文件。`r`n`r`n今日待办:`r`n- 完成协合平台第一轮开发`r`n- 设计数据库权限模型`r`n- 编写接口测试脚本`r`n`r`n心得: 权限控制一定要在后端强制校验,不能只靠前端隐藏按钮。"
  [System.IO.File]::WriteAllText($seedFile, $content, (New-Object System.Text.UTF8Encoding($false)))
  Write-Host "[善后] 已恢复被删除的种子文件: 张三的个人笔记.txt" -ForegroundColor DarkGray
}

if ($fail -gt 0) { exit 1 } else { Write-Host "全部通过!" -ForegroundColor Green; exit 0 }
