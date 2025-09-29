@echo off
chcp 65001 >nul
title NEXUS服务器启动器

echo.
echo ============================================================
echo 🚀 NEXUS服务器启动器
echo ============================================================
echo.

REM 检查Python是否安装
python --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Python未安装或未添加到PATH
    echo 请先安装Python并添加到系统PATH
    pause
    exit /b 1
)

REM 检查ngrok是否安装
ngrok version >nul 2>&1
if errorlevel 1 (
    echo ⚠️ ngrok未安装
    echo 将使用本地模式启动（无外网访问）
    echo.
)

REM 检查必要文件
if not exist "nexus_backend.py" (
    echo ❌ 找不到nexus_backend.py
    echo 请确保在正确的目录中运行此脚本
    pause
    exit /b 1
)

if not exist "requirements.txt" (
    echo ❌ 找不到requirements.txt
    echo 请确保在正确的目录中运行此脚本
    pause
    exit /b 1
)

echo ✅ 环境检查通过
echo.

REM 选择启动模式
echo 请选择启动模式:
echo 1. 完整启动（包含数据库初始化、ngrok隧道等）
echo 2. 快速启动（仅启动后端服务器和ngrok）
echo 3. 仅启动后端服务器（无外网访问）
echo.
set /p choice="请输入选择 (1-3): "

if "%choice%"=="1" (
    echo.
    echo 🚀 启动完整模式...
    python start_nexus_server.py
) else if "%choice%"=="2" (
    echo.
    echo 🚀 启动快速模式...
    python quick_start.py
) else if "%choice%"=="3" (
    echo.
    echo 🚀 启动后端服务器...
    python nexus_backend.py
) else (
    echo ❌ 无效选择
    pause
    exit /b 1
)

echo.
echo ⏹️ 服务器已停止
pause
