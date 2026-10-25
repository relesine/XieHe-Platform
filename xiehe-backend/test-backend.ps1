# ============================================================
# 协合XieHe 后端接口自动化测试脚本 v2 (Invoke-RestMethod版)
# 覆盖: 登录/注册/文件/权限/好友/管理端 全部核心场景
# 运行方式: powershell -File test-backend.ps1
# ============================================================
$ErrorActionPreference = "Continue"
$BASE = "http://localhost:8080"
$PASS = 0
$FAIL = 0

function Assert($name, $cond, $extra = "") {
    if ($cond) { $script:PASS++; Write-Host "[PASS] $name" -ForegroundColor Green }
    else { $script:FAIL++; Write-Host "[FAIL] $name $extra" -ForegroundColor Red }
}

function PostJson($url, $obj) {
    $body = $obj | ConvertTo-Json -Compress
    $resp = Invoke-RestMethod -Uri "$BASE$url" -Method Post -ContentType "application/json" -Body $body
    return $resp
}

function GetAuth($url, $token) {
    $headers = @{ Authorization = "Bearer $token" }
    return Invoke-RestMethod -Uri "$BASE$url" -Method Get -Headers $headers
}

function PostAuth($url, $token, $obj) {
    $headers = @{ Authorization = "Bearer $token" }
    if ($null -eq $obj) {
        return Invoke-RestMethod -Uri "$BASE$url" -Method Post -Headers $headers
    }
    $body = $obj | ConvertTo-Json -Compress
    return Invoke-RestMethod -Uri "$BASE$url" -Method Post -Headers $headers -ContentType "application/json" -Body $body
}

function PutAuth($url, $token) {
    $headers = @{ Authorization = "Bearer $token" }
    return Invoke-RestMethod -Uri "$BASE$url" -Method Put -Headers $headers
}

function PutJson($url, $token, $obj) {
    $headers = @{ Authorization = "Bearer $token" }
    $body = $obj | ConvertTo-Json -Compress
    return Invoke-RestMethod -Uri "$BASE$url" -Method Put -Headers $headers -ContentType "application/json" -Body $body
}

function DeleteAuth($url, $token) {
    $headers = @{ Authorization = "Bearer $token" }
    return Invoke-RestMethod -Uri "$BASE$url" -Method Delete -Headers $headers
}

# 期望HTTP错误的请求(捕获4xx响应)
function GetExpectError($url, $token) {
    $headers = @{ Authorization = "Bearer $token" }
    try {
        Invoke-RestMethod -Uri "$BASE$url" -Method Get -Headers $headers | Out-Null
        return $null   # 未报错 -> 无期望错误
    } catch {
        # 从异常响应体解析后端Result.code
        try {
            $respBody = $_.ErrorDetails.Message
            $json = $respBody | ConvertFrom-Json
            return $json.code
        } catch {
            return $_.Exception.Response.StatusCode.value__
        }
    }
}

Write-Host "========== 1. 认证接口测试 ==========" -ForegroundColor Cyan

# 1.1 管理员登录
$r = PostJson "/api/auth/login" @{ username = "admin"; password = "admin123" }
$adminToken = $r.data.token
Assert "管理员登录 admin/admin123" ($null -ne $adminToken -and $adminToken.Length -gt 10)

# 1.2 普通用户登录 (张三 VIP)
$r = PostJson "/api/auth/login" @{ username = "zhangsan"; password = "123456" }
$zsToken = $r.data.token
Assert "张三登录 zhangsan/123456" ($null -ne $zsToken)

# 1.3 李四登录
$r = PostJson "/api/auth/login" @{ username = "lisi"; password = "123456" }
$lsToken = $r.data.token
Assert "李四登录 lisi/123456" ($null -ne $lsToken)

# 1.4 赵六登录
$r = PostJson "/api/auth/login" @{ username = "zhaoliu"; password = "123456" }
$zlToken = $r.data.token
Assert "赵六登录 zhaoliu/123456" ($null -ne $zlToken)

# 1.5 孙七登录
$r = PostJson "/api/auth/login" @{ username = "sunqi"; password = "123456" }
$sqToken = $r.data.token
Assert "孙七登录 sunqi/123456" ($null -ne $sqToken)

# 1.6 错误密码
$r = PostJson "/api/auth/login" @{ username = "zhangsan"; password = "wrong" }
Assert "错误密码被拒绝" ($r.code -eq 400)

# 1.7 未携带token访问受保护接口 -> 401
$r2 = GetAuth "/api/user/info" "none"
Assert "未登录访问被拦截(401)" ($r2.code -eq 401)

# 1.8 注册新用户
$r = PostJson "/api/auth/register" @{ username = "testuser1"; password = "123456"; nickname = "测试用户" }
Assert "注册新用户 testuser1" ($r.code -eq 200)

Write-Host "`n========== 2. 文件列表/详情 ==========" -ForegroundColor Cyan

# 2.1 张三的文件
$r = GetAuth "/api/file/list/mine" $zsToken
$myCount = $r.data.records.Count
Assert "张三我的文件列表(3个预置)" ($myCount -ge 3)

# 2.2 公开文件
$r = GetAuth "/api/file/list/public" $zsToken
$pubCount = $r.data.records.Count
Assert "公开文件列表(3个预置)" ($pubCount -ge 3)

# 2.3 李四访问张三的私有文件3(好友EDIT授权)
$r = GetAuth "/api/file/3" $lsToken
Assert "李四对张三文件3的权限=EDIT(好友授权)" ($r.data.myPermission -eq "EDIT")

# 2.4 赵六访问文件3(手动VIEW授权,非好友)
$r = GetAuth "/api/file/3" $zlToken
Assert "赵六对文件3的权限=VIEW(手动授权)" ($r.data.myPermission -eq "VIEW")

# 2.5 孙七(无授权)访问私有文件3 -> 403
$r2 = GetAuth "/api/file/3" $sqToken
Assert "孙七访问私有文件3被拒绝(403)" ($r2.code -eq 403)

# 2.6 孙七访问公开文件1 -> 成功
$r = GetAuth "/api/file/1" $sqToken
Assert "孙七访问公开文件1成功" ($r.code -eq 200)

# 2.7 李四的"与我共享"包含文件3
$r = GetAuth "/api/file/list/shared" $lsToken
$shared = $r.data.records | Where-Object { $_.id -eq 3 }
Assert "李四'与我共享'包含文件3" ($null -ne $shared)

Write-Host "`n========== 3. 编辑权限规则 ==========" -ForegroundColor Cyan

# 3.1 李四(EDIT)重命名文件3 -> 成功
$r = PutAuth "/api/file/3/rename?filename=renamed_by_lisi.txt" $lsToken
Assert "李四(EDIT)重命名文件3成功" ($r.code -eq 200)
PutAuth "/api/file/3/rename?filename=张三的个人笔记.txt" $lsToken | Out-Null

# 3.2 赵六(VIEW)重命名文件3 -> 403
$r3 = PutAuth "/api/file/3/rename?filename=hack.txt" $zlToken
Assert "赵六(VIEW)重命名文件3被拒绝" ($r3.code -eq 403)

# 3.3 孙七(无权限)重命名公开文件1 -> 403 (公开不代表可编辑)
$r3 = PutAuth "/api/file/1/rename?filename=hack.txt" $sqToken
Assert "孙七编辑公开文件1被拒绝(公开≠可编辑)" ($r3.code -eq 403)

# 3.4 张三上传+删除自己的文件
$tmpFile = "$env:TEMP\xiehe_test.txt"
[System.IO.File]::WriteAllText($tmpFile, "临时测试文件内容", [System.Text.Encoding]::UTF8)
$r = curl.exe -s -X POST "$BASE/api/file/upload" -H "Authorization: Bearer $zsToken" -F "file=@$tmpFile"
$up = $r | ConvertFrom-Json
$newFileId = $up.data.id
Assert "张三上传文件成功" ($newFileId -gt 0)

# 3.5 李四删除张三的文件3 -> 403
$r3 = DeleteAuth "/api/file/3" $lsToken
Assert "李四删除他人文件被拒绝(403)" ($r3.code -eq 403)

Write-Host "`n========== 4. 下载与计数 ==========" -ForegroundColor Cyan

# 4.1 李四下载文件3 -> HTTP 200
$code = curl.exe -s -o NUL -w "%{http_code}" "$BASE/api/file/3/download" -H "Authorization: Bearer $lsToken"
Assert "李四下载文件3 HTTP 200" ($code -eq "200")

# 4.2 孙七下载文件3 -> 拒绝
$r2 = GetAuth "/api/file/3/download" $sqToken
Assert "孙七下载文件3被拒绝" ($r2.code -eq 403)

Write-Host "`n========== 5. 授权管理 ==========" -ForegroundColor Cyan

# 5.1 张三给孙七授权VIEW -> 授权后可访问 -> 撤销 -> 再次拒绝
$r = PostAuth "/api/file/3/permission" $zsToken @{ userId = 6; permission = "VIEW" }
Assert "张三手动授权孙七VIEW" ($r.code -eq 200)
$r = GetAuth "/api/file/3" $sqToken
Assert "孙七获得授权后访问成功" ($r.code -eq 200)
$r = DeleteAuth "/api/file/3/permission/6" $zsToken
Assert "撤销孙七授权" ($r.code -eq 200)
$r2 = GetAuth "/api/file/3" $sqToken
Assert "撤销后孙七访问被拒绝(403)" ($r2.code -eq 403)

# 5.2 非拥有者李四给文件3授权 -> 403
$r3 = PostAuth "/api/file/3/permission" $lsToken @{ userId = 6; permission = "VIEW" }
Assert "非拥有者授权被拒绝(403)" ($r3.code -eq 403)

# 5.3 授权列表
$r = GetAuth "/api/file/3/permission" $zsToken
Assert "查看授权列表成功" ($r.code -eq 200)

Write-Host "`n========== 6. 好友接口 ==========" -ForegroundColor Cyan

# 6.1 张三的好友列表
$r = GetAuth "/api/friend/list" $zsToken
$friendCount = $r.data.Count
Assert "张三好友列表(2人)" ($friendCount -ge 2)

# 6.2 添加好友 + 重复添加
$r = PostAuth "/api/friend/add/5" $zsToken $null
Assert "张三添加赵六为好友" ($r.code -eq 200)
$r3 = PostAuth "/api/friend/add/5" $zsToken $null
Assert "重复添加好友被拒绝" ($r3.code -eq 400)
# 清理: 删除测试添加的好友关系
& "D:\phpstudy_pro\Extensions\MySQL8.0.12\bin\mysql.exe" -uroot -p123456 -e "USE xiehe; DELETE FROM t_friend_relation WHERE (user_id=2 AND friend_id=5) OR (user_id=5 AND friend_id=2);" 2>&1 | Out-Null

Write-Host "`n========== 7. 管理后台接口 ==========" -ForegroundColor Cyan

# 7.1 普通用户访问管理接口 -> 403
$r3 = GetAuth "/api/admin/stats" $zsToken
Assert "普通用户访问管理接口被拒绝(403)" ($r3.code -eq 403)

# 7.2 管理员统计 (refresh=true 强制刷新,避免命中旧缓存)
$r = GetAuth "/api/admin/stats?refresh=true" $adminToken
Assert "管理员统计-用户数>=7" ($r.data.userCount -ge 7)
Assert "管理员统计-文件数>=10" ($r.data.fileCount -ge 10)

# 7.3 管理员用户列表
$r = GetAuth "/api/admin/user/list?page=1&size=10" $adminToken
Assert "管理员用户分页列表" ($r.data.records.Count -ge 7)

# 7.4 管理员文件列表
$r = GetAuth "/api/admin/file/list?page=1&size=10" $adminToken
Assert "管理员文件分页列表" ($r.data.records.Count -ge 10)

# 7.5 管理员禁用用户(赵六=id5)
$r = PutAuth "/api/admin/user/5/status?status=0" $adminToken
Assert "管理员禁用赵六" ($r.code -eq 200)
$r = PostJson "/api/auth/login" @{ username = "zhaoliu"; password = "123456" }
Assert "被禁用用户无法登录" ($r.code -eq 403)
PutAuth "/api/admin/user/5/status?status=1" $adminToken | Out-Null

# 7.6 管理员升级用户为VIP
$r = PutAuth "/api/admin/user/4/type?userType=VIP" $adminToken
Assert "管理员升级王五为VIP" ($r.code -eq 200)
PutAuth "/api/admin/user/4/type?userType=NORMAL" $adminToken | Out-Null

# 7.7 管理员操作日志
$r = GetAuth "/api/admin/log/list?page=1&size=10" $adminToken
Assert "管理员操作日志列表" ($r.data.records.Count -ge 1)

# 7.8 管理员删除文件
$r = DeleteAuth "/api/admin/file/$newFileId" $adminToken
Assert "管理员删除文件成功" ($r.code -eq 200)

Write-Host "`n========== 8. 用户接口 ==========" -ForegroundColor Cyan

# 8.1 修改资料(PUT)
$r = PutJson "/api/user/profile" $zsToken @{ nickname = "张三"; avatar = "" }
Assert "修改个人资料" ($r.code -eq 200)

# 8.2 错误原密码修改(PUT)
$r3 = PutJson "/api/user/password" $zsToken @{ oldPassword = "bad"; newPassword = "654321" }
Assert "错误原密码修改失败" ($r3.code -eq 400)

# 8.3 存储空间
$r = GetAuth "/api/user/storage" $zsToken
Assert "张三存储上限=20GB(VIP)" ($r.data.storageLimit -eq 21474836480)

# 8.4 用户搜索
$r = GetAuth "/api/user/search?keyword=%E6%9D%8E" $zsToken
Assert "搜索用户'李'" ($r.code -eq 200)

# 8.5 登出后token失效
$r = PostAuth "/api/auth/logout" $zsToken $null
Assert "登出成功" ($r.code -eq 200)
$r2 = GetAuth "/api/user/info" $zsToken
Assert "登出后token失效(401)" ($r2.code -eq 401)

# 清理测试用户
& "D:\phpstudy_pro\Extensions\MySQL8.0.12\bin\mysql.exe" -uroot -p123456 -e "USE xiehe; DELETE FROM t_user WHERE username='testuser1'; DELETE FROM t_operation_log;" 2>&1 | Out-Null

Write-Host "`n======================================" -ForegroundColor Cyan
Write-Host "测试结果: 通过 $PASS 项, 失败 $FAIL 项" -ForegroundColor $(if($FAIL -eq 0){"Green"}else{"Red"})
Write-Host "======================================" -ForegroundColor Cyan
exit $(if($FAIL -eq 0){0}else{1})
