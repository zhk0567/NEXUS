# NEXUS 微信小程序版本

## 项目简介

这是NEXUS智能语音交互平台的微信小程序版本，提供与Android应用相同的核心功能。

## 功能特性

- 智能AI对话：支持文本和语音输入，流式回复显示
- 语音识别（ASR）：录音转文字
- 语音合成（TTS）：文字转语音播放
- 历史记录：保存和管理对话历史
- 用户登录：用户认证和会话管理
- 个性化设置：主题、字体大小、服务器配置

## 项目结构

```
miniprogram/
├── app.js                 # 应用入口
├── app.json               # 应用配置
├── app.wxss               # 全局样式
├── pages/                 # 页面目录
│   ├── index/            # 聊天主页面
│   ├── login/            # 登录页面
│   ├── settings/         # 设置页面
│   └── history/          # 历史记录页面
├── utils/                 # 工具类
│   ├── api.js            # API接口封装
│   ├── config.js         # 配置管理
│   └── storage.js        # 本地存储工具
└── project.config.json    # 项目配置
```

## 配置说明

### 1. AppID配置

项目的AppID已配置在 `project.config.json` 中：
- AppID: `wxc726bdd9b8ac6e5a`

如需修改，请编辑 `project.config.json` 文件。

### 2. 配置服务器地址

在 `utils/config.js` 中修改服务器地址：

```javascript
CURRENT_SERVER: 'http://your-server-ip:5000'
```

也可以在设置页面中动态修改服务器地址。

### 3. 配置域名白名单

在微信公众平台配置服务器域名白名单：
- request合法域名：你的后端服务器地址
- uploadFile合法域名：你的后端服务器地址
- downloadFile合法域名：你的后端服务器地址

## 使用说明

### 开发环境

1. 安装微信开发者工具
2. 导入项目目录 `miniprogram`
3. 配置AppID和服务器域名
4. 启动后端服务器（nexus_backend.py）
5. 开始开发调试

### 功能使用

1. **登录**：首次使用需要登录，使用与Android应用相同的账号
2. **聊天**：在首页输入文字或按住录音按钮进行语音输入
3. **语音播放**：点击AI消息的播放按钮播放语音回复
4. **历史记录**：在历史页面查看所有对话记录
5. **设置**：在设置页面调整主题、字体和服务器地址

## API接口

小程序调用以下后端API：

- `/api/health` - 健康检查
- `/api/auth/login` - 用户登录
- `/api/chat` - AI聊天
- `/api/chat_streaming` - 流式AI聊天
- `/api/transcribe` - 语音识别
- `/api/tts` - 语音合成
- `/api/conversation/start` - 开始新对话

## 注意事项

1. **网络配置**：确保小程序可以访问后端服务器（需要配置域名白名单）
2. **录音权限**：首次使用需要授权录音权限
3. **服务器地址**：默认使用本地IP，可根据实际情况修改
4. **数据存储**：对话历史存储在微信小程序本地存储中

## 开发说明

### 添加新页面

1. 在 `pages` 目录下创建新页面文件夹
2. 创建 `.js`、`.wxml`、`.wxss` 文件
3. 在 `app.json` 的 `pages` 数组中添加页面路径

### 修改API配置

修改 `utils/config.js` 中的服务器地址和端点配置。

### 样式定制

- 全局样式：`app.wxss`
- 页面样式：各页面的 `.wxss` 文件

## 后续优化

- [ ] 支持WebSocket实时语音通话
- [ ] 优化流式显示体验
- [ ] 添加更多语音选项
- [ ] 支持离线功能
- [ ] 数据同步功能

