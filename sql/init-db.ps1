# ============================================================
# 协合XieHe 一键初始化脚本
# 功能: 1. 重建数据库(6张表+预置数据)  2. 同步清理Redis缓存  3. 创建项目专用账号  4. 确保上传目录种子文件存在
# 运行: powershell -ExecutionPolicy Bypass -File sql/init-db.ps1
# 注意: 会清空现有数据重新初始化,如需保留数据请勿运行
# 说明: 当前机器数据库为 phpstudy 自带 MySQL 8.0.12,root 密码 123456;
#       若换用其他 MySQL 实例,请修改下方 $mysqlBin 与 root 密码参数
# ============================================================
$mysqlBin = "D:\phpstudy_pro\Extensions\MySQL8.0.12\bin\mysql.exe"
$sqlFile  = "F:\AI_full-stack_project\XieHe\sql\xiehe_init.sql"
$seedDir  = "F:\AI_full-stack_project\XieHe\data\uploads\seed"
# phpstudy MySQL root 密码 123456;若你的 root 有不同密码,请修改
$rootArgs = @("-uroot", "-p123456", "--default-character-set=utf8mb4")

Write-Host "========== 第1步: 重建数据库 ==========" -ForegroundColor Cyan
# 2>$null 屏蔽MySQL"命令行密码不安全"警告(stderr),仅保留真实错误
& $mysqlBin @rootArgs -e "source $sqlFile" 2>$null
if ($LASTEXITCODE -ne 0) { Write-Host "数据库初始化失败!" -ForegroundColor Red; exit 1 }
Write-Host "数据库初始化完成" -ForegroundColor Green

# v1.3: 同步清理 Redis 中本应用的缓存(仅 xiehe:* 前缀,不误删其他应用数据),
# 避免旧令牌/登录失败计数/状态缓存残留,导致"重建库后跑回归测试"被污染(曾出现改密旧令牌误判有效)
$redisCli = "F:\Redis\redis-cli.exe"
if (Test-Path $redisCli) {
  $oldKeys = & $redisCli --scan --pattern "xiehe:*"
  if ($oldKeys) { & $redisCli DEL @oldKeys *> $null }
  Write-Host "已清理 Redis 缓存(xiehe:* 前缀)" -ForegroundColor Green
} else {
  Write-Host "未找到 redis-cli(F:\Redis\redis-cli.exe),跳过 Redis 清理;若回归异常请手动执行 FLUSHDB" -ForegroundColor DarkYellow
}

Write-Host "`n========== 第2步: 创建项目专用数据库账号 ==========" -ForegroundColor Cyan
# 项目连接使用专用账号 xiehe/123456(与 application.yml 保持一致),不使用 root
& $mysqlBin @rootArgs -e "CREATE USER IF NOT EXISTS 'xiehe'@'localhost' IDENTIFIED BY '123456'; GRANT ALL PRIVILEGES ON xiehe.* TO 'xiehe'@'localhost'; FLUSH PRIVILEGES;" 2>$null
if ($LASTEXITCODE -ne 0) { Write-Host "创建数据库账号失败!" -ForegroundColor Red; exit 1 }
Write-Host "已创建专用账号 xiehe / 123456 (仅授权 xiehe 库)" -ForegroundColor Green

Write-Host "`n========== 第3步: 补齐种子文件 ==========" -ForegroundColor Cyan
if (-not (Test-Path $seedDir)) { New-Item -ItemType Directory -Path $seedDir -Force | Out-Null }

# 各占位文件(仅用于演示,保证下载/预览功能可用)
$files = @{
  "需求说明书.txt" = "协合XieHe 协作文档云协作平台`r`n`r`n项目需求说明书`r`n`r`n1. 项目背景: 团队协作中文件共享与权限管理混乱`r`n2. 项目目标: 打造一个支持多人协作、权限精细控制的云文档平台`r`n3. 核心功能: 文件上传/下载/预览、好友协作、权限管理、公开分享`r`n4. 技术栈: Spring Boot + Vue3 + MySQL + Redis"
  "张三的个人笔记.txt" = "张三的个人笔记(私有文件)`r`n只有被授权的用户才能查看或编辑本文件。`r`n`r`n今日待办:`r`n- 完成协合平台第一轮开发`r`n- 设计数据库权限模型`r`n- 编写接口测试脚本`r`n`r`n心得: 权限控制一定要在后端强制校验,不能只靠前端隐藏按钮。"
  "示例压缩包.zip" = [System.Text.Encoding]::ASCII.GetBytes("ZIP-DEMO-FILE")
  "示例视频.mp4" = [System.Text.Encoding]::ASCII.GetBytes("MP4-DEMO")
}

foreach ($name in $files.Keys) {
  $path = Join-Path $seedDir $name
  $value = $files[$name]
  if (Test-Path $path) {
    Write-Host "已存在: $name (跳过)" -ForegroundColor DarkGray
  } else {
    if ($value -is [byte[]]) { [System.IO.File]::WriteAllBytes($path, $value) }
    else { [System.IO.File]::WriteAllText($path, $value, (New-Object System.Text.UTF8Encoding($false))) }
    Write-Host "已创建: $name" -ForegroundColor Green
  }
}

# v1.3: 用代码生成真实 PNG 占位图片(原文本占位"PNG-DEMO"会导致图片预览裂图)
$pngPath = Join-Path $seedDir "团队合影.png"
if (Test-Path $pngPath) {
  Write-Host "已存在: 团队合影.png (跳过)" -ForegroundColor DarkGray
} else {
  Add-Type -AssemblyName System.Drawing
  $bmp = New-Object System.Drawing.Bitmap 240,180
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $r = New-Object System.Drawing.Rectangle 0,0,240,180
  $b = New-Object System.Drawing.Drawing2D.LinearGradientBrush $r, ([System.Drawing.Color]::FromArgb(59,130,246)), ([System.Drawing.Color]::FromArgb(29,78,216)), 45
  $g.FillRectangle($b, $r); $g.Dispose(); $b.Dispose()
  $bmp.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
  Write-Host "已创建: 团队合影.png (真实图片)" -ForegroundColor Green
}

Write-Host "`n========== 初始化完成! 可用测试账号 ==========" -ForegroundColor Cyan
Write-Host "管理员: admin / admin123"
Write-Host "普通用户: zhangsan / 123456 (VIP 20GB), lisi / 123456, wangwu / 123456"
