# NEXUS - 智能AI语音助手

<div align="center">

![NEXUS Logo](https://img.shields.io/badge/NEXUS-AI%20Assistant-blue?style=for-the-badge&logo=android)
![Version](https://img.shields.io/badge/version-2.1.0-green?style=for-the-badge)
![License](https://img.shields.io/badge/license-MIT-blue?style=for-the-badge)
![Platform](https://img.shields.io/badge/platform-Android%20%7C%20Python-orange?style=for-the-badge)

**一个集成了语音识别、AI对话和语音合成的智能助手应用**

[功能特性](#-核心功能) • [快速开始](#-快速开始) • [技术架构](#️-技术架构) • [API文档](#-api接口) • [贡献指南](#-贡献指南)

</div>

---

## 📖 项目简介

NEXUS是一个基于AI的智能语音助手系统，集成了先进的语音识别、自然语言处理和语音合成技术。系统采用现代化的Android应用界面和Python后端服务，为用户提供流畅、智能的语音交互体验。

### 🎯 项目亮点

- 🎤 **完整语音交互** - 语音输入 → AI处理 → 语音输出
- 🤖 **智能对话系统** - 基于DeepSeek API的上下文理解
- 📱 **现代UI设计** - Material Design 3 + 优雅的渐变效果
- 📊 **历史记录管理** - 智能的对话历史保存和检索
- ⚡ **高性能架构** - 支持并发访问和实时响应
- 🔄 **实时刷新** - 支持AI回答的实时刷新功能

---

## ✨ 核心功能

### 🎤 语音交互系统
- **智能语音识别** - 基于Vosk的中文语音识别引擎
- **自然语音合成** - 支持多种TTS引擎的高质量语音输出
- **实时对话处理** - 低延迟的语音交互体验
- **录音质量控制** - 3秒最短录音时长，确保录音质量
- **动态UI反馈** - 录音时显示音波动画，提供直观的交互反馈

### 🤖 AI对话引擎
- **智能问答系统** - 基于DeepSeek API的先进对话能力
- **上下文记忆** - 支持多轮对话和上下文理解
- **中文优化** - 专门针对中文对话优化的体验
- **实时刷新** - 支持AI回答的实时刷新和重新生成
- **新话题管理** - 一键清空对话历史，开始全新话题

### 📱 现代移动界面
- **Material Design 3** - 遵循Google最新设计规范
- **优雅配色方案** - 黑白灰配色，简洁而专业
- **渐变文字效果** - NEXUS标题采用四色渐变设计
- **圆形头像系统** - 完美的圆形用户和AI头像
- **智能按钮状态** - 根据输入内容动态启用/禁用功能
- **历史记录界面** - 完整的对话历史管理和检索

### 📊 数据分析系统
- **用户行为分析** - 交互模式和使用习惯统计
- **性能监控** - 系统响应时间和资源使用监控
- **业务洞察** - 热门查询和用户需求分析
- **可视化报告** - 交互式图表和HTML仪表板

---

## 🏗️ 技术架构

### 后端技术栈
```
Python 3.8+          # 核心开发语言
├── Flask            # Web框架和API服务
├── MySQL            # 数据存储和会话管理
├── Vosk             # 语音识别引擎
├── DeepSeek API     # AI对话服务
├── Edge TTS         # 语音合成服务
└── 连接池管理        # 高性能数据库连接
```

### 前端技术栈
```
Android (Kotlin)     # 移动端应用
├── Jetpack Compose  # 现代UI框架
├── Material Design 3 # 设计规范
├── StateFlow        # 响应式状态管理
├── Canvas绘制       # 自定义圆形头像
└── BackHandler      # 返回键处理
```

### 系统架构图
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Android App   │    │  Python Backend │    │   AI Services   │
│                 │    │                 │    │                 │
│ • 语音输入       │◄──►│ • Flask API     │◄──►│ • DeepSeek API  │
│ • UI界面        │    │ • 语音识别       │    │ • Edge TTS      │
│ • 历史记录       │    │ • 会话管理       │    │ • Vosk ASR      │
│ • 状态管理       │    │ • 数据存储       │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

---

## 🚀 快速开始

### 环境要求
- **Python 3.8+** - 后端服务
- **MySQL 8.0+** - 数据存储
- **Android Studio** - 移动端开发
- **Conda** - 环境管理

### 安装步骤

#### 1. 克隆项目
```bash
git clone https://github.com/your-username/NEXUS.git
cd NEXUS
```

#### 2. 设置Python环境
```bash
# 创建Conda环境
conda create -n nexus python=3.8
conda activate nexus

# 安装依赖
pip install -r requirements.txt
```

#### 3. 配置数据库
```bash
# 初始化数据库
python mysql_config.py
```

#### 4. 启动后端服务
```bash
# 一键启动
python start_nexus.py

# 或直接启动
python high_performance_backend.py
```

#### 5. 构建Android应用
```bash
cd android
./gradlew assembleDebug
```

---

## 📁 项目结构

```
NEXUS/
├── 🚀 启动脚本
│   ├── start_nexus.py                    # 一键启动脚本
│   └── high_performance_backend.py       # 核心后端服务
│
├── 🔧 配置文件
│   ├── mysql_config.py                   # 数据库配置
│   ├── requirements.txt                  # Python依赖
│   └── data_analysis.py                  # 数据分析工具
│
├── 📊 分析报告
│   └── nexus_comprehensive_dashboard.html # 综合仪表板
│
├── 🤖 AI模型
│   └── models/vosk/
│       └── vosk-model-small-cn-0.22/     # 中文语音识别模型
│
├── 📱 Android应用
│   └── android/
│       ├── app/src/main/java/com/llasm/voiceassistant/
│       │   ├── ui/                       # UI组件
│       │   │   ├── ChatScreen.kt         # 主聊天界面
│       │   │   ├── HistoryScreen.kt      # 历史记录界面
│       │   │   └── components/           # UI组件
│       │   ├── viewmodel/                # 视图模型
│       │   ├── data/                     # 数据管理
│       │   ├── service/                  # 服务层
│       │   └── network/                  # 网络层
│       └── app/src/main/res/             # 应用资源
│
└── 📄 文档
    ├── README.md                         # 项目文档
    └── LICENSE                           # 开源许可证
```

---

## 📡 API接口

### 核心接口

| 方法 | 端点 | 功能 | 说明 |
|------|------|------|------|
| `POST` | `/api/chat` | AI对话 | 文本对话接口 |
| `POST` | `/api/transcribe` | 语音识别 | 音频转文字 |
| `POST` | `/api/tts` | 语音合成 | 文字转语音 |
| `POST` | `/api/voice_chat` | 语音对话 | 完整语音交互 |
| `POST` | `/api/start_session` | 开始会话 | 创建新会话 |
| `POST` | `/api/end_session` | 结束会话 | 结束当前会话 |
| `GET` | `/health` | 健康检查 | 服务状态检查 |
| `GET` | `/api/stats` | 系统统计 | 获取系统统计信息 |

### 请求示例

#### AI对话
```bash
curl -X POST http://localhost:5000/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "你好，请介绍一下自己",
    "session_id": "user_session_123"
  }'
```

#### 语音识别
```bash
curl -X POST http://localhost:5000/api/transcribe \
  -F "audio=@audio.wav" \
  -F "session_id=user_session_123"
```

#### 语音合成
```bash
curl -X POST http://localhost:5000/api/tts \
  -H "Content-Type: application/json" \
  -d '{
    "text": "你好，我是NEXUS智能助手",
    "session_id": "user_session_123"
  }'
```

---

## 📊 数据分析

### 分析功能
- **用户行为分析** - 交互模式、使用习惯、活跃度统计
- **性能监控** - 响应时间、资源使用、系统负载分析
- **业务洞察** - 热门查询、用户需求、趋势预测
- **可视化报告** - 交互式图表、静态图片、HTML报告

### 快速使用
```bash
# 运行数据分析
python data_analysis.py

# 查看综合仪表板
open nexus_comprehensive_dashboard.html
```

### 生成报告
- **综合仪表板** - 所有图表整合在一个页面中
- **每日统计** - 交互次数、用户数、响应时间趋势
- **使用模式** - 小时使用模式、热门查询分析
- **性能指标** - 使用时间统计、会话统计、性能指标

---

## ⚡ 性能特性

### 高并发支持
- **连接池管理** - MySQL连接池优化
- **异步处理** - 非阻塞I/O操作
- **多级缓存** - 内存和数据库缓存
- **负载均衡** - 支持水平扩展

### 性能指标
- **响应时间** - 平均API响应时间 < 3秒
- **ASR处理时间** - 语音识别平均 < 0.2秒
- **TTS处理时间** - 语音合成平均 < 1秒
- **并发支持** - 支持1000+并发用户

---

## 🔧 配置说明

### 数据库配置
```python
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': 'your_password',
    'database': 'llasm_usage_data',
    'charset': 'utf8mb4',
    'pool_size': 10,
    'pool_recycle': 3600
}
```

### API配置
```python
DEEPSEEK_API_KEY = "your_deepseek_api_key"
DEEPSEEK_BASE_URL = "https://api.deepseek.com"
DEEPSEEK_MODEL = "deepseek-chat"
```

### 服务配置
```python
HOST = "0.0.0.0"
PORT = 5000
DEBUG = False
MAX_CONTENT_LENGTH = 16 * 1024 * 1024  # 16MB
```

---

## 🛠️ 开发指南

### 后端开发
```bash
# 1. 环境设置
conda activate nexus
pip install -r requirements.txt

# 2. 数据库初始化
python mysql_config.py

# 3. 启动开发服务器
python high_performance_backend.py
```

### Android开发
```bash
# 1. 打开项目
cd android
# 使用Android Studio打开项目

# 2. 构建应用
./gradlew assembleDebug

# 3. 安装到设备
./gradlew installDebug
```

### 代码规范
- **Python** - 遵循PEP 8规范
- **Kotlin** - 遵循Kotlin编码规范
- **文档** - 完善的代码注释和文档

---

## 📈 监控与维护

### 系统监控
- **健康检查** - `GET /health`
- **性能统计** - `GET /api/stats`
- **数据分析** - 运行 `python data_analysis.py`

### 日志管理
- **应用日志** - 系统运行日志
- **错误日志** - 异常和错误记录
- **性能日志** - 性能指标记录

### 数据备份
- **数据库备份** - 定期备份MySQL数据
- **模型备份** - 备份AI模型文件
- **配置备份** - 备份系统配置文件

---

## 🔒 安全考虑

### 数据安全
- **数据加密** - 敏感数据加密存储
- **访问控制** - API访问权限控制
- **隐私保护** - 用户数据隐私保护

### 系统安全
- **输入验证** - 严格的输入数据验证
- **错误处理** - 完善的异常处理机制
- **安全日志** - 安全事件记录

---

## 🤝 贡献指南

### 开发流程
1. Fork项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交代码 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建Pull Request

### 贡献类型
- 🐛 Bug修复
- ✨ 新功能开发
- 📚 文档改进
- 🎨 UI/UX优化
- ⚡ 性能优化

---

## 📄 许可证

本项目采用MIT许可证，详见 [LICENSE](LICENSE) 文件。

---

## 📞 技术支持

### 常见问题
1. **数据库连接失败** - 检查MySQL服务状态和配置
2. **语音识别异常** - 检查Vosk模型文件
3. **API调用失败** - 检查网络连接和API密钥
4. **Android编译失败** - 检查Android SDK和Gradle配置

### 联系方式
- **Issues** - 通过GitHub Issues报告问题
- **文档** - 查看项目文档获取帮助
- **社区** - 参与社区讨论

---

## 🆕 版本更新

### v2.1.0 (最新)
- 🎨 **UI/UX重大更新** - 全新的视觉设计和交互体验
- 🔄 **历史记录系统** - 完整的对话历史管理和检索
- 🎤 **语音交互优化** - 改进的语音识别和合成质量
- 📱 **Android界面** - Material Design 3 + 圆形头像系统
- ⚡ **性能优化** - 更快的响应速度和更好的并发支持

### v2.0.0
- 🌈 **渐变文字效果** - NEXUS标题采用四色渐变设计
- 🔘 **半圆角输入框** - 现代化的输入框设计
- 🎯 **智能按钮状态** - 动态启用/禁用功能
- 📱 **焦点管理** - 智能的输入框焦点处理
- ➕ **新话题功能** - 一键清空对话历史

### v1.0.0
- 🎤 **基础语音功能** - 语音识别和合成
- 🤖 **AI对话系统** - 基于DeepSeek的智能对话
- 📊 **数据分析** - 使用统计和性能监控
- 🚀 **一键部署** - 简化的部署流程

---

## 🎊 项目特色

<div align="center">

| 特性 | 描述 | 状态 |
|------|------|------|
| 🎤 **完整语音交互** | 语音输入 → AI处理 → 语音输出 | ✅ |
| 🤖 **智能对话系统** | 基于DeepSeek API的先进对话能力 | ✅ |
| 📱 **现代移动界面** | Material Design 3 + 优雅设计 | ✅ |
| 📊 **历史记录管理** | 智能的对话历史保存和检索 | ✅ |
| ⚡ **高性能架构** | 支持并发访问和实时响应 | ✅ |
| 🔄 **实时刷新功能** | 支持AI回答的实时刷新 | ✅ |
| 📈 **数据分析系统** | 全面的使用统计和性能监控 | ✅ |
| 🚀 **一键部署** | 简化的部署和启动流程 | ✅ |

</div>

---

<div align="center">

**NEXUS - 让AI对话更智能，让语音交互更自然**

[⭐ Star this project](https://github.com/your-username/NEXUS) • [🐛 Report Bug](https://github.com/your-username/NEXUS/issues) • [💡 Request Feature](https://github.com/your-username/NEXUS/issues)

Made with ❤️ by the NEXUS Team

</div>