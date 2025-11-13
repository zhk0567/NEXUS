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
git clone https://gitee.com/zhk567/NEXUS.git
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
python nexus_backend.py
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
- 通过API创建：`python create_test_users.py`（需要后端服务运行）
- 通过SQL脚本：`mysql -u root -p nexus_unified < create_test_users.sql`

**禁用其他账号**（如果数据库中有其他账号）：
- 运行SQL脚本：`mysql -u root -p nexus_unified < disable_other_users.sql`

**更新数据库表结构**（添加单设备登录检测支持）：
- 运行SQL脚本：`mysql -u root -p nexus_unified < update_sessions_table.sql`

## 项目结构

```
NEXUS-main/
├── app/                          # AI对话应用
│   └── src/main/java/com/llasm/nexusunified/
├── story_control_app/            # 每日故事应用
│   └── app/src/main/java/com/llasm/storycontrol/
├── nexus_backend.py              # 后端服务
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
- `sessions` - 会话表

## 更新日志

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
- **后端**：Python + Flask + PyTorch
- **AI模型**：Dolphin ASR + DeepSeek API + Edge-TTS
- **数据库**：MySQL + PyMySQL

## 许可证

MIT License

## 联系方式

- **项目地址**：https://gitee.com/zhk567/NEXUS
- **问题反馈**：请在Gitee Issues中提交

---

Made with ❤️ by NEXUS Team
