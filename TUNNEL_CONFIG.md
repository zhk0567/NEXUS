# NEXUS 内网穿透配置文档

## 概述

本文档记录了NEXUS项目的内网穿透配置方式，用于将本地服务器暴露到公网，使Android应用可以在任何地方访问。

## 当前配置状态

- **当前使用**: 本地连接 (`http://192.168.50.205:5000`)
- **公网隧道**: fgnwct (`http://nexus.free.svipss.top`)
- **隧道状态**: 已配置但未启用

## 内网穿透方案

### 1. fgnwct 隧道服务

#### 服务信息
- **官网**: https://www.fgnwct.com/tunnels.html
- **隧道地址**: `nexus.free.svipss.top`
- **隧道ID**: `b2fc8a013c`
- **协议**: HTTP(S)
- **节点**: 免费#1 1Mbps
- **有效期**: 3天 (2025-10-01 至 2025-10-04)

#### 配置文件 (frpc.ini)
```ini
[common]
server_addr = fgnwct.com
server_port = 7000
tls_enable = true
auth_token = b2fc8a013c

[nexus_server]
type = http
local_ip = 127.0.0.1
local_port = 5000
custom_domains = nexus.free.svipss.top
```

#### 启动方式
```bash
# 启动完整服务（后端+隧道）
python start_nexus.py

# 或分别启动
python nexus_backend.py  # 启动后端
.\frpc.exe -c frpc.ini   # 启动隧道
```

#### 测试连接
```bash
# 测试公网连接
curl http://nexus.free.svipss.top/api/health

# 或使用PowerShell
Invoke-WebRequest -Uri "http://nexus.free.svipss.top/api/health" -UseBasicParsing
```

## Android配置切换

### 切换到本地连接
```kotlin
// 在 ServerConfig.kt 中
const val CURRENT_SERVER = LOCAL_SERVER
const val CURRENT_WEBSOCKET = LOCAL_WEBSOCKET
```

### 切换到公网连接
```kotlin
// 在 ServerConfig.kt 中
const val CURRENT_SERVER = FGNWCT_SERVER
const val CURRENT_WEBSOCKET = FGNWCT_WEBSOCKET
```

## 文件结构

```
NEXUS - Final/
├── nexus_backend.py          # 后端服务器
├── start_nexus.py            # 完整启动脚本
├── frpc.ini                  # fgnwct隧道配置
├── frpc.exe                  # fgnwct客户端（需下载）
├── TUNNEL_CONFIG.md          # 本文档
└── app/src/main/java/com/llasm/nexusunified/config/
    └── ServerConfig.kt       # Android配置
```

## 使用说明

### 本地开发模式
1. 确保Android设备和PC在同一WiFi网络
2. 启动服务器: `python nexus_backend.py`
3. Android应用连接: `http://192.168.50.205:5000`

### 公网访问模式
1. 下载fgnwct客户端: `frpc.exe`
2. 启动完整服务: `python start_nexus.py`
3. Android应用连接: `http://nexus.free.svipss.top`

## 注意事项

1. **隧道有效期**: fgnwct免费隧道有3天有效期，到期需要重新创建
2. **网络要求**: 公网模式无需同一网络，本地模式需要同一WiFi
3. **防火墙**: 确保端口5000在防火墙中开放
4. **重新编译**: 切换配置后需要重新编译Android应用

## 故障排除

### 连接失败
1. 检查服务器是否启动: `http://localhost:5000/api/health`
2. 检查隧道状态: 访问fgnwct控制面板
3. 检查防火墙设置
4. 检查网络连接

### 隧道离线
1. 重新启动frpc客户端
2. 检查frpc.ini配置
3. 检查认证令牌是否有效
4. 重新创建隧道

## 更新记录

- **2025-10-01**: 初始配置fgnwct隧道
- **2025-10-01**: 创建配置文档
- **2025-10-01**: 切换到本地连接模式
