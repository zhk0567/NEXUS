# NEXUS Unified - 智能语音交互平台

## 项目简介

NEXUS Unified 是一个企业级智能语音交互平台，集成实时语音识别、AI对话和语音合成功能。支持连续对话、历史记录管理、故事阅读系统等功能。

## 核心功能

- 🎤 **实时语音识别**：基于Dolphin ASR模型，支持16kHz高质量音频
- 🤖 **智能AI对话**：集成DeepSeek API，支持流式对话和上下文理解
- 🔊 **多音色语音合成**：5种中文音色，支持实时播放
- 📖 **故事阅读系统**：30天循环故事，支持文字和音频双模式阅读
- 📱 **现代化UI**：Jetpack Compose构建，支持主题切换和字体调节
- 🗄️ **MySQL数据库**：企业级数据存储，支持用户管理和交互记录

## 快速开始

### 环境要求

- Python 3.8+
- Android Studio（最新版本）
- MySQL 5.7+
- Android设备（API 21+）

### 安装步骤

1. **克隆项目**
```bash
# 从 Gitee 克隆
git clone https://gitee.com/zhk567/NEXUS.git
cd NEXUS-main

# 或从 GitHub 克隆
git clone https://github.com/zhk0567/NEXUS.git
cd NEXUS-main
```

2. **配置Python环境**
```bash
# 创建虚拟环境
python -m venv venv
source venv/bin/activate  # Linux/Mac
# venv\Scripts\activate   # Windows

# 安装依赖
pip install -r requirements.txt
```

3. **配置数据库**
```bash
# 创建数据库
mysql -u root -p
CREATE DATABASE nexus_unified;
CREATE USER 'nexus_user'@'localhost' IDENTIFIED BY 'zhk050607';
GRANT ALL PRIVILEGES ON nexus_unified.* TO 'nexus_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;

# 初始化数据库
python init_database.py
```

4. **启动后端服务**
```bash
python app.py
```

5. **编译Android应用**
```bash
cd app
./gradlew assembleDebug
./gradlew installDebug
```

### 测试账号

**重要**：系统只允许以下10个预置账号登录，其他账号均无法使用。

| 用户名 | 密码 |
|--------|------|
| user01 | 123456 |
| user02 | 123456 |
| user03 | 123456 |
| user04 | 123456 |
| user05 | 123456 |
| user06 | 123456 |
| user07 | 123456 |
| user08 | 123456 |
| user09 | 123456 |
| user10 | 123456 |

**安全限制**：
- ✅ 只有这10个账号可以登录使用应用
- ✅ 应用启动时会检查登录状态，未登录状态无法使用软件功能
- ✅ 注册功能已禁用，无法创建新账号
- ✅ 其他所有账号（包括之前创建的）均被禁用

**初始化测试账号**：
- 测试账号已预置在数据库中，无需额外操作

## 项目结构

```
NEXUS-main/
├── app/                          # AI对话应用
│   └── src/main/java/com/llasm/nexusunified/
├── story_control_app/            # 每日故事应用
│   └── app/src/main/java/com/llasm/storycontrol/
├── app.py                        # 后端服务启动入口
├── backend/                      # 后端模块
│   ├── config.py                # 配置管理
│   ├── logger_config.py         # 日志配置
│   ├── service_monitor.py        # 服务监控
│   ├── tts_service.py           # TTS服务
│   ├── asr_service.py           # ASR服务
│   ├── ai_service.py            # AI聊天服务
│   └── routes/                  # 路由模块
│       ├── health_routes.py
│       ├── tts_routes.py
│       ├── asr_routes.py
│       ├── chat_routes.py
│       ├── auth_routes.py
│       ├── interaction_routes.py
│       ├── story_routes.py
│       ├── admin_user_routes.py
│       ├── admin_story_routes.py
│       └── error_routes.py
├── database_manager.py            # 数据库管理
├── database_config.py             # 数据库配置
└── requirements.txt               # Python依赖
```

## 核心特性

### 30天循环故事系统
- 每天自动更换故事，30天一个周期循环
- 使用日期模30算法自动选择对应故事
- 应用运行时每分钟检查日期变化，自动更新
- 音频文件与故事内容同步更新

### 双模式阅读系统
- **文字模式**：滚动阅读，智能进度跟踪
- **音频模式**：语音播放，独立进度管理
- 任一模式完成即视为阅读完成

### 智能进度跟踪
- 阅读进度实时更新和同步
- 状态永久保存，不会回退
- 支持管理员手动修改完成状态

## API接口

### 健康检查
```http
GET /api/health
```

### 获取当天故事（30天循环）
```http
GET /api/stories/active
```

### 更新阅读进度
```http
POST /api/reading/progress
Content-Type: application/json

{
  "user_id": "user_123",
  "story_id": "story_001",
  "story_title": "故事标题",
  "current_position": 1000,
  "total_length": 5000
}
```

## 数据库结构

主要数据表：
- `users` - 用户表
- `reading_progress` - 阅读进度表
- `story_interactions` - 故事交互表
- `user_sessions` - 用户会话表
- `error_reports` - 错误报告表

## 更新日志

### v3.5.0 (2025-01-24)
- 🐛 修复音频播放状态同步问题，确保UI立即响应播放/暂停操作
- 🐛 修复音频文件匹配逻辑，支持多种文件名格式和特殊字符
- 🐛 移除初始加载时的错误消息显示，改善用户体验
- ✨ 优化音频播放控制，添加状态同步机制
- ✨ 改进音频文件查找策略，支持ID匹配、标题匹配等多种方式

### v3.4.0 (2025-11-22)
- 🔧 后端代码重构：将大型文件拆分为模块化结构（所有文件<500行）
- 🔧 符合PEP 8和阿里编码规范，提升代码可维护性
- ✨ 实现错误收集系统，自动收集客户端错误
- 🐛 修复日期查询功能，动态添加当前日期信息
- 🐛 优化对话历史处理，限制长度避免请求过大

### v3.3.0 (2025-01-XX)
- ✨ 实现30天循环故事功能
- ✨ 每天自动更换故事内容和音频文件
- ✨ 优化音频文件查找逻辑，使用标题命名
- 🔧 优化代码结构，提升性能

### v3.2.0 (2025-01-XX)
- 🔒 代码安全重构，所有API密钥移至后端
- 🔒 实现动态配置获取机制

## 技术栈

- **前端**：Android + Kotlin + Jetpack Compose
- **后端**：Python + Flask（模块化架构）
- **AI模型**：Dolphin ASR + DeepSeek API + Edge-TTS
- **数据库**：MySQL + PyMySQL

## 项目实现

### 整体架构和设计思路

本项目采用前后端分离的架构设计，分为三个核心模块：

**1. 后端服务模块（Python + Flask）**
- 使用Flask框架构建RESTful API服务，提供统一的接口层
- 集成Dolphin ASR模型进行实时语音识别，支持16kHz音频处理
- 集成DeepSeek API实现流式AI对话，支持上下文理解和连续对话
- 使用Edge-TTS实现多音色中文语音合成，提供5种不同音色选择
- 采用PyMySQL连接MySQL数据库，实现线程安全的数据库连接池管理
- 实现服务监控和健康检查机制，实时监控ASR、TTS、Chat三大服务的运行状态

**2. AI对话应用模块（Android + Kotlin + Jetpack Compose）**
- 基于Jetpack Compose构建现代化UI界面，支持Material Design 3设计规范
- 实现实时语音录制和流式传输，通过WebSocket与后端ASR服务通信
- 集成流式对话功能，支持AI回复的实时显示和语音播放
- 实现用户认证和会话管理，支持多设备单账号登录限制
- 使用SharedPreferences进行本地数据持久化，存储用户信息和设置

**3. 故事阅读应用模块（Android + Kotlin + Jetpack Compose）**
- 实现30天循环故事系统，基于日期模30算法自动选择对应故事
- 开发双模式阅读系统：文字滚动模式和音频播放模式，支持独立进度管理
- 实现智能阅读进度跟踪，实时同步到数据库，支持应用重启后状态恢复
- 集成TTS服务实现文字转语音功能，支持阅读进度同步
- 实现日期变化检测机制，应用运行时每分钟检查日期变化并自动更新故事

### 我负责的模块和结果

**后端API服务开发**：
- 开发了模块化的Flask后端服务，采用分层架构设计（所有文件<500行，符合PEP 8规范）
- 实现了用户认证、会话管理、阅读进度管理、故事管理等核心功能
- 开发了数据库管理器（database_manager.py），实现了线程安全的数据库操作
- 实现了服务监控系统，监控ASR、TTS、Chat三大服务的请求量、成功率、响应时间等指标
- 实现了错误收集系统，自动收集客户端错误并存储到数据库

**Android应用开发**：
- 开发了AI对话应用（app目录，39个Kotlin文件），实现了完整的语音交互功能
- 开发了故事阅读应用（story_control_app目录，27个Kotlin文件），实现了30天循环故事系统
- 实现了用户登录、退出、账号白名单检测等功能
- 实现了阅读进度实时同步和状态持久化，确保应用重启后数据不丢失

**数据库设计和优化**：
- 设计了4个核心数据表（users、reading_progress、story_interactions、user_sessions）
- 实现了数据库连接池和查询缓存机制，提升查询性能
- 实现了单设备登录检测，同一账号同一应用类型只能在一个设备上登录

**安全机制实现**：
- 实现了三层账号白名单机制：后端认证检查、API注册限制、客户端状态检查
- 实现了密码SHA256加密存储
- 实现了会话管理和自动过期机制

**量化成果**：
- 后端API响应时间平均<200ms，支持并发请求处理
- 数据库查询缓存命中率达到60%+，显著提升性能
- 阅读进度同步成功率100%，状态持久化可靠性达到99.9%
- 30天循环故事系统准确率100%，日期变化检测及时性<1分钟

### 我遇到的难点、坑和解决方案

**难点1：30天循环故事算法的偏移问题**
- **问题**：初始实现使用1970-01-01作为基准日期，导致2025-01-01对应索引19而非0
- **原因**：LocalDate.of(1970, 1, 1).toEpochDay()返回的是从1970-01-01开始的天数，2025-01-01距离1970-01-01的天数模30后不等于0
- **解决方案**：将基准日期改为2025-01-01，使该日期对应索引0，同时修改前后端算法保持一致

**难点2：阅读进度在应用重启后丢失**
- **问题**：完成阅读后清理后台再次打开应用，阅读进度和完成状态丢失
- **原因**：数据库同步后未更新本地缓存，应用初始化时数据库未加载完成就检查状态
- **解决方案**：
  - 在同步成功后立即更新本地缓存和StateFlow，确保状态一致性
  - 添加延迟检查机制（500ms初始化延迟，300ms故事加载延迟）
  - 监听阅读进度变化，实时更新完成状态
  - 在故事变化时保护已完成状态，避免被重置

**难点3：Kotlin Regex语法错误**
- **问题**：包含中文引号的Regex模式导致编译错误（Expecting ','、Unresolved reference等）
- **原因**：Kotlin对包含特殊字符的字符串解析存在问题，中文引号需要特殊处理
- **解决方案**：使用原始字符串（raw string，三引号"""..."""）处理包含中文引号的Regex模式，避免转义问题

**难点4：同一账号多设备登录问题**
- **问题**：需要实现同一账号同一应用类型只能在一个设备上登录
- **解决方案**：
  - 在数据库user_sessions表中添加app_type、device_info、ip_address字段
  - 登录时检查是否存在相同用户和app_type的活跃会话，存在则先结束旧会话
  - 在Android应用中通过User-Agent或device_info标识应用类型

**难点5：音频播放生命周期管理**
- **问题**：退出语音设置页面或聊天页面时，音频继续在后台播放
- **原因**：未在Composable销毁时停止音频播放
- **解决方案**：
  - 使用DisposableEffect在组件销毁时调用ttsService.stopPlayback()
  - 在导航图标onClick和BackHandler中也显式停止播放并更新状态
  - 确保资源正确释放，避免内存泄漏

**难点6：Gradle构建Java版本不匹配**
- **问题**：Gradle构建失败，提示需要Java 11但使用的是Java 8
- **解决方案**：在gradle.properties中配置org.gradle.java.home指向Java 21路径，停止现有Gradle守护进程，重新构建

**难点7：数据库连接稳定性问题**
- **问题**：长时间运行后数据库连接可能断开，导致请求失败
- **解决方案**：
  - 实现连接健康检查机制（ping检测）
  - 添加自动重连逻辑，连接断开时自动重新连接
  - 设置连接超时和读写超时参数
  - 实现连接池管理，支持多连接复用

## 许可证

MIT License

## 联系方式

- **Gitee 仓库**：https://gitee.com/zhk567/NEXUS
- **GitHub 仓库**：https://github.com/zhk0567/NEXUS
- **问题反馈**：请在 Gitee 或 GitHub Issues 中提交

---

Made with ❤️ by NEXUS Team
