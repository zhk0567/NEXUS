# NEXUS - 智能AI对话系统

## 📖 项目简介

NEXUS是一个基于AI的智能对话系统，集成了语音识别、自然语言处理和语音合成技术，为用户提供流畅的语音交互体验。系统采用高性能架构设计，支持大规模并发用户访问。

## ✨ 核心功能

### 🎤 语音交互
- **语音识别** - 基于Vosk的中文语音识别
- **语音合成** - 支持多种TTS引擎
- **实时对话** - 低延迟的语音交互体验

### 🤖 AI对话
- **智能问答** - 基于DeepSeek API的智能对话
- **上下文理解** - 支持多轮对话和上下文记忆
- **多语言支持** - 中文优化的对话体验

### 📊 数据分析
- **使用统计** - 用户交互数据统计
- **性能监控** - 系统性能指标监控
- **可视化报告** - 综合数据分析仪表板

## 🏗️ 技术架构

### 后端技术栈
- **Python 3.8+** - 核心开发语言
- **Flask** - Web框架
- **MySQL** - 数据存储
- **Vosk** - 语音识别引擎
- **DeepSeek API** - AI对话服务
- **Edge TTS** - 语音合成

### 前端技术栈
- **Android** - 移动端应用
- **Kotlin** - 开发语言
- **Jetpack Compose** - 现代UI框架
- **Material Design 3** - 设计规范

### 数据库设计
- **用户会话管理** - 会话状态跟踪
- **交互记录** - 用户交互历史
- **性能指标** - 系统性能数据
- **使用统计** - 业务数据分析

## 🚀 快速开始

### 环境要求
- Python 3.8+
- MySQL 8.0+
- Android Studio (开发Android应用)
- Conda环境管理

### 安装步骤

1. **克隆项目**
```bash
git clone <repository-url>
cd NEXUS
```

2. **创建Conda环境**
```bash
conda create -n llasm python=3.8
conda activate llasm
```

3. **安装后端依赖**
```bash
pip install -r requirements_backend.txt
```

4. **配置数据库**
```bash
python mysql_config.py
```

5. **启动后端服务**
```bash
python start_nexus.py
```

6. **构建Android应用**
```bash
cd android
./gradlew assembleDebug
```

## 📁 项目结构

```
NEXUS/
├── start_nexus.py                    # 🚀 一键启动脚本
├── high_performance_backend.py       # 核心后端服务
├── mysql_config.py                   # 数据库配置
├── requirements_backend.txt          # 后端依赖
├── data_analysis.py                  # 📊 数据分析工具
├── requirements_analysis.txt         # 分析工具依赖
├── nexus_comprehensive_dashboard.html # 📊 综合仪表板
├── models/                           # AI模型文件
│   └── vosk/
│       └── vosk-model-small-cn-0.22/ # 中文语音识别模型
├── android/                          # Android应用
│   └── app/
│       └── src/main/
│           ├── java/com/llasm/voiceassistant/  # Kotlin源码
│           └── res/                            # 应用资源
├── README.md                         # 项目文档
└── LICENSE                           # 开源许可证
```

## 📡 API接口

### 核心接口
| 方法 | 端点 | 功能 | 说明 |
|------|------|------|------|
| POST | `/api/chat` | AI对话 | 文本对话接口 |
| POST | `/api/transcribe` | 语音识别 | 音频转文字 |
| POST | `/api/tts` | 语音合成 | 文字转语音 |
| POST | `/api/voice_chat` | 语音对话 | 完整语音交互 |
| POST | `/api/start_session` | 开始会话 | 创建新会话 |
| POST | `/api/end_session` | 结束会话 | 结束当前会话 |
| GET | `/health` | 健康检查 | 服务状态检查 |
| GET | `/api/stats` | 系统统计 | 获取系统统计信息 |

### 请求示例
```bash
# AI对话
curl -X POST http://localhost:5000/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "你好，请介绍一下自己"}'

# 语音识别
curl -X POST http://localhost:5000/api/transcribe \
  -F "audio=@audio.wav"

# 语音合成
curl -X POST http://localhost:5000/api/tts \
  -H "Content-Type: application/json" \
  -d '{"text": "你好，我是NEXUS智能助手"}'
```

## 📊 数据分析

### 分析功能
- **用户行为分析** - 交互模式、使用习惯、活跃度统计
- **性能监控** - 响应时间、资源使用、系统负载分析
- **业务洞察** - 热门查询、用户需求、趋势预测
- **可视化报告** - 交互式图表、静态图片、HTML报告

### 快速使用
```bash
# 安装分析依赖
pip install -r requirements_analysis.txt

# 设置数据库
python mysql_config.py

# 运行数据分析（仅提供完整分析功能）
python data_analysis.py
```

### 生成报告
- **综合仪表板** - 所有图表整合在一个页面中，包含：
  - 每日交互次数、用户数、响应时间趋势
  - 小时使用模式、热门查询分析
  - 使用时间统计、会话统计、性能指标
  - 使用热力图、交互分布等

## ⚡ 性能特性

### 高并发支持
- **连接池管理** - MySQL连接池优化
- **异步处理** - 非阻塞I/O操作
- **多级缓存** - 内存和数据库缓存
- **负载均衡** - 支持水平扩展

### 性能指标
- **响应时间** - 平均API响应时间 < 5秒
- **ASR处理时间** - 语音识别平均 < 0.2秒
- **TTS处理时间** - 语音合成平均 < 1秒
- **并发支持** - 支持1000+并发用户

## 🔧 配置说明

### 数据库配置
```python
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': 'your_password',
    'database': 'llasm_usage_data',
    'charset': 'utf8mb4'
}
```

### API配置
```python
DEEPSEEK_API_KEY = "your_deepseek_api_key"
DEEPSEEK_BASE_URL = "https://api.deepseek.com"
```

### 服务配置
```python
HOST = "0.0.0.0"
PORT = 5000
DEBUG = False
```

## 🛠️ 开发指南

### 后端开发
1. **环境设置**
```bash
conda activate llasm
pip install -r requirements_backend.txt
```

2. **数据库初始化**
```bash
python mysql_config.py
```

3. **启动开发服务器**
```bash
python high_performance_backend.py
```

### Android开发
1. **打开项目**
```bash
cd android
# 使用Android Studio打开项目
```

2. **构建应用**
```bash
./gradlew assembleDebug
```

3. **安装到设备**
```bash
./gradlew installDebug
```

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

## 🔒 安全考虑

### 数据安全
- **数据加密** - 敏感数据加密存储
- **访问控制** - API访问权限控制
- **隐私保护** - 用户数据隐私保护

### 系统安全
- **输入验证** - 严格的输入数据验证
- **错误处理** - 完善的异常处理机制
- **安全日志** - 安全事件记录

## 🤝 贡献指南

### 开发流程
1. Fork项目
2. 创建功能分支
3. 提交代码
4. 创建Pull Request

### 代码规范
- **Python** - 遵循PEP 8规范
- **Kotlin** - 遵循Kotlin编码规范
- **文档** - 完善的代码注释和文档

## 📄 许可证

本项目采用MIT许可证，详见 [LICENSE](LICENSE) 文件。

## 📞 技术支持

### 常见问题
1. **数据库连接失败** - 检查MySQL服务状态和配置
2. **语音识别异常** - 检查Vosk模型文件
3. **API调用失败** - 检查网络连接和API密钥

### 联系方式
- **Issues** - 通过GitHub Issues报告问题
- **文档** - 查看项目文档获取帮助
- **社区** - 参与社区讨论

---

## 🎊 项目特色

✅ **高性能架构** - 支持大规模并发访问  
✅ **完整功能** - 语音识别、AI对话、语音合成  
✅ **数据分析** - 全面的使用统计和性能监控  
✅ **现代UI** - 基于Material Design 3的现代界面  
✅ **易于部署** - 一键启动脚本和详细文档  
✅ **开源免费** - MIT许可证，完全开源  

NEXUS致力于为用户提供最优质的AI对话体验，通过持续的技术创新和优化，打造智能、高效、易用的语音交互系统。#   N E X U S 
 
 
