# ============================================================
# 协合XieHe 后端接口自动化测试 v3 (v1.2 安全加固与逻辑修复回归)
# 覆盖点:
#   1. 加盐密码登录(新种子数据) / 错误密码拒绝
#   2. 防暴力破解(IP维度): 连续失败5次锁定,正确密码也被拒
#   3. 批量授权/批量分享: 对"非本人文件"自动跳过,不中断
#   4. 上传: 超长文件名(>255)被拒 / .html危险扩展名被拒
#   5. 头像协议白名单: javascript: 协议被拒
#   6. 修改密码: 旧密码失效+旧令牌全部踢出(加盐新格式)
#   7. 管理端统计SQL聚合仍正常 / 操作日志含IP字段
#   8. 共享列表SQL分页正常
# 运行前提: 已执行 sql/init-db.ps1 重置数据库 + 后端已启动(新代码)
# 运行方式: powershell -ExecutionPolicy Bypass -File test-backend-v3.ps1
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
    $tmp = "$env:TEMP\v3_req_body.json"
    Set-Content -Path $tmp -Value $body -Encoding UTF8
    $args += @("-H", "Content-Type: application/json", "-d", "@$tmp")
  }
  $r = & curl.exe @args
  return ($r | ConvertFrom-Json)
}

function Login($username, $password) {
  $body = "{`"username`":`"$username`",`"password`":`"$password`"}"
  return (Req "POST" "/api/auth/login" $null $body)
}

function Upload($url, $token, $filePath) {
  $args = @("-s", "-m", "30", "-X", "POST", "$base$url", "-F", "file=@$filePath")
  if ($token) { $args += @("-H", "Authorization: Bearer $token") }
  $r = & curl.exe @args
  return ($r | ConvertFrom-Json)
}

# 上传并指定"显示文件名"(multipart filename=字段),用于构造超长文件名等特殊用例,
# 避免在本地创建超长路径文件触发Windows 260字符路径限制
function UploadNamed($url, $token, $filePath, $displayName) {
  $args = @("-s", "-m", "30", "-X", "POST", "$base$url", "-F", "file=@$filePath;filename=$displayName")
  if ($token) { $args += @("-H", "Authorization: Bearer $token") }
  $r = & curl.exe @args
  return ($r | ConvertFrom-Json)
}

# ============================================================
Write-Host "========== 协合XieHe 后端 v1.2 安全回归测试开始 ==========" -ForegroundColor Cyan

# ---------- 0. 基础登录(加盐密码) ----------
$r = Login "zhangsan" "123456"
Assert "加盐密码zhangsan登录成功" ($r.code -eq 200) "code=$($r.code)"
$token = $r.data.token
$r = Login "lisi" "123456"
Assert "加盐密码lisi登录成功" ($r.code -eq 200)
$token2 = $r.data.token
$r = Login "admin" "admin123"
Assert "加盐密码admin登录成功" ($r.code -eq 200)
$tokenAdmin = $r.data.token
$r = Login "zhangsan" "wrongpass"
Assert "错误密码登录被拒" ($r.code -ne 200)

# ---------- 1. 防暴力破解(独立测试账号,避免影响其他用户) ----------
$r = Req "POST" "/api/auth/register" $null '{"username":"v3lock","password":"123456","nickname":"锁测试"}'
Assert "注册防暴测试账号" ($r.code -eq 200) "code=$($r.code)"
# 连续失败5次(第6次即使密码正确也被锁)
$locked = $false
for ($i = 1; $i -le 5; $i++) {
  $r = Login "v3lock" "badpass$i"
}
$r = Login "v3lock" "123456"
Assert "连续失败5次后正确密码也被锁定" ($r.code -ne 200 -and $r.msg.Contains("尝试次数过多")) "code=$($r.code) msg=$($r.msg)"
# 其他用户不受影响(IP维度key隔离)
$r = Login "zhangsan" "123456"
Assert "其他用户不受锁定影响" ($r.code -eq 200)

# ---------- 2. 批量授权跳过非本人文件 ----------
# 张三批量授权 [3(自己的), 4(李四的)] 给王五 -> 只授权成功1个,不抛403
$r = Req "POST" "/api/file/batch-permission" $token '{"fileIds":[3,4],"userId":4,"permission":"VIEW"}'
Assert "批量授权跳过他人文件(成功1个)" ($r.code -eq 200 -and $r.data -eq 1) "code=$($r.code) count=$($r.data)"
# 验证文件3确实授权了王五
$r = Req "GET" "/api/file/3/permission" $token
$hasWw = $r.data | Where-Object { $_.userId -eq 4 }
Assert "文件3授权列表含王五" ($null -ne $hasWw)

# ---------- 3. 批量分享跳过非本人文件 ----------
# 张三批量分享 [3(自己的), 4(李四的)] -> 只生成1个链接
$r = Req "POST" "/api/file/batch-share?expireDays=0" $token '[3,4]'
Assert "批量分享跳过他人文件(生成1个)" ($r.code -eq 200 -and $r.data.Count -eq 1) "code=$($r.code) count=$($r.data.Count)"
# 清理: 取消文件3的分享
$r = Req "DELETE" "/api/file/3/share" $token
Assert "清理文件3分享" ($r.code -eq 200)

# ---------- 4. 上传安全校验 ----------
# 4.1 .html 危险扩展名被拒
$htmlFile = "$env:TEMP\v3_payload.html"
Set-Content -Path $htmlFile -Value "<script>alert(1)</script>" -Encoding UTF8
$r = Upload "/api/file/upload" $token $htmlFile
Assert "上传.html被拒" ($r.code -ne 200 -and $r.msg.Contains("不支持的文件类型")) "code=$($r.code) msg=$($r.msg)"
Remove-Item $htmlFile -Force
# 4.2 超长文件名(256字符)被拒(本地文件用短名,通过curl filename=构造超长显示名)
$longName = "v3_" + ("a" * 252) + ".txt"
$longFile = "$env:TEMP\v3_short.txt"
Set-Content -Path $longFile -Value "hello" -Encoding UTF8
$r = UploadNamed "/api/file/upload" $token $longFile $longName
Assert "超长文件名上传被拒" ($r.code -ne 200 -and $r.msg.Contains("255")) "code=$($r.code) msg=$($r.msg)"
Remove-Item $longFile -Force
# 4.3 正常文件上传成功
$okFile = "$env:TEMP\v3_normal.txt"
Set-Content -Path $okFile -Value "v1.2 upgrade test content" -Encoding UTF8
$r = Upload "/api/file/upload" $token $okFile
Assert "正常txt上传成功" ($r.code -eq 200 -and $r.data.filename -eq "v3_normal.txt") "code=$($r.code)"
# 清理现场: 删除刚上传的文件
if ($r.code -eq 200) {
  $newFileId = $r.data.id
  $r = Req "DELETE" "/api/file/$newFileId" $token
  Assert "清理上传测试文件" ($r.code -eq 200)
  $r = Req "DELETE" "/api/file/$newFileId/forever" $token
  Assert "彻底清理上传测试文件" ($r.code -eq 200)
}
Remove-Item $okFile -Force

# ---------- 5. 头像协议白名单 ----------
$r = Req "PUT" "/api/user/profile" $token '{"nickname":"张三","avatar":"javascript:alert(1)"}'
Assert "javascript协议头像被拒" ($r.code -ne 200 -and $r.msg.Contains("头像地址")) "code=$($r.code) msg=$($r.msg)"
$r = Req "PUT" "/api/user/profile" $token '{"nickname":"张三","avatar":"https://example.com/a.png"}'
Assert "https外链头像通过" ($r.code -eq 200) "code=$($r.code)"

# ---------- 6. 修改密码(加盐新格式 + 旧令牌踢出) ----------
$r = Req "POST" "/api/auth/register" $null '{"username":"v3pw","password":"123456","nickname":"改密测试"}'
Assert "注册改密测试账号" ($r.code -eq 200)
$r = Login "v3pw" "123456"
$pwToken = $r.data.token
# 错误原密码被拒
$r = Req "PUT" "/api/user/password" $pwToken '{"oldPassword":"wrong","newPassword":"newpass888"}'
Assert "错误原密码被拒" ($r.code -ne 200)
# 正确改密
$r = Req "PUT" "/api/user/password" $pwToken '{"oldPassword":"123456","newPassword":"newpass888"}'
Assert "修改密码成功" ($r.code -eq 200)
# 旧密码登录失败 + 新密码登录成功
$r = Login "v3pw" "123456"
Assert "旧密码已失效" ($r.code -ne 200)
$r = Login "v3pw" "newpass888"
Assert "新密码登录成功" ($r.code -eq 200)
# 改密后旧令牌被踢出(改密前拿的令牌应失效)
$r = Req "GET" "/api/user/stats" $pwToken
Assert "改密后旧令牌失效" ($r.code -ne 200) "code=$($r.code)"

# ---------- 7. 管理端统计SQL聚合 + 日志IP ----------
$r = Req "GET" "/api/admin/stats?refresh=true" $tokenAdmin
Assert "管理端统计正常(DOC>=1)" ($r.code -eq 200 -and $r.data.fileTypeDist.DOC -ge 1) "code=$($r.code)"
$r = Req "GET" "/api/admin/log/list?page=1&size=50" $tokenAdmin
Assert "操作日志列表正常" ($r.code -eq 200 -and $r.data.records.Count -ge 1) "code=$($r.code)"
$hasIp = $r.data.records | Where-Object { $_.ip -ne $null -and $_.ip -ne "" }
Assert "操作日志含IP字段" ($null -ne $hasIp) "count=$($r.data.records.Count)"
# 普通用户访问管理端仍被拒(回源DB校验)
$r = Req "GET" "/api/admin/stats?refresh=true" $token
Assert "普通用户访问管理端被拒" ($r.code -eq 403) "code=$($r.code)"

# ---------- 8. 共享列表SQL分页 ----------
$r = Req "GET" "/api/file/list/shared?page=1&size=10" $token2
Assert "李四共享列表total=1" ($r.code -eq 200 -and $r.data.total -eq 1) "code=$($r.code) total=$($r.data.total)"
Assert "共享列表文件名正确" ($r.data.records[0].filename -eq "张三的个人笔记.txt") "name=$($r.data.records[0].filename)"
# 越界页返回空列表而非报错
$r = Req "GET" "/api/file/list/shared?page=99&size=10" $token2
Assert "共享列表越界页空列表" ($r.code -eq 200 -and $r.data.records.Count -eq 0) "code=$($r.code)"

# ============================================================
Write-Host "`n========== 测试结果: 通过 $pass / 失败 $fail ==========" -ForegroundColor Cyan
if ($fail -gt 0) { exit 1 } else { Write-Host "全部通过!" -ForegroundColor Green; exit 0 }
