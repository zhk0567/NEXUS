# Push to Gitee Script
# UTF-8 encoding
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "准备推送到 Gitee..." -ForegroundColor Green
Write-Host ""

# Check if we're in the right directory
if (-not (Test-Path ".git")) {
    Write-Host "错误: 当前目录不是 Git 仓库" -ForegroundColor Red
    exit 1
}

# Check remote
$remote = git remote get-url origin 2>$null
if (-not $remote) {
    Write-Host "错误: 未配置远程仓库" -ForegroundColor Red
    exit 1
}

Write-Host "远程仓库: $remote" -ForegroundColor Yellow
Write-Host ""

# Try to push
Write-Host "正在推送到 Gitee..." -ForegroundColor Yellow
Write-Host "提示: 如果提示输入凭据，请输入:" -ForegroundColor Cyan
Write-Host "  - 用户名: zhk567" -ForegroundColor Cyan
Write-Host "  - 密码: 您的 Gitee 密码或访问令牌" -ForegroundColor Cyan
Write-Host ""

git push -u origin main

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "✅ 推送成功！" -ForegroundColor Green
    Write-Host "访问 https://gitee.com/zhk567/NEXUS 查看代码" -ForegroundColor Cyan
} else {
    Write-Host ""
    Write-Host "❌ 推送失败" -ForegroundColor Red
    Write-Host "请检查:" -ForegroundColor Yellow
    Write-Host "  1. Gitee 仓库是否存在" -ForegroundColor Yellow
    Write-Host "  2. 您是否有推送权限" -ForegroundColor Yellow
    Write-Host "  3. 用户名和密码/令牌是否正确" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "详细说明请查看 GIT_PUSH_GUIDE.md" -ForegroundColor Cyan
}

