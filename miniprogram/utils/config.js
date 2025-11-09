// 服务器配置
const config = {
  // 本地服务器配置
  LOCAL_SERVER: 'http://192.168.50.205:5000',
  LOCAL_WEBSOCKET: 'ws://192.168.50.205:5000',
  
  // 公网隧道配置（如果需要）
  FGNWCT_SERVER: 'http://nexus.free.svipss.top',
  FGNWCT_WEBSOCKET: 'ws://nexus.free.svipss.top',
  
  // 当前使用的配置
  CURRENT_SERVER: 'http://192.168.50.205:5000',
  CURRENT_WEBSOCKET: 'ws://192.168.50.205:5000',
  
  // 微信小程序配置
  // 注意：APP_SECRET 是敏感信息，不应在前端代码中使用
  // 如果需要使用 AppSecret，应该在后端服务器中处理
  APP_ID: 'wxc726bdd9b8ac6e5a',
  // APP_SECRET: '35cb822813eee1ad1faebd64610b9eb4', // 已注释，仅用于后端
  
  // API端点
  ENDPOINTS: {
    HEALTH: 'api/health',
    CHAT: 'api/chat',
    CHAT_STREAMING: 'api/chat_streaming',
    TRANSCRIBE: 'api/transcribe',
    TTS: 'api/tts',
    VOICE_CHAT: 'api/voice_chat',
    VOICE_CHAT_STREAMING: 'api/voice_chat_streaming',
    AUTH_LOGIN: 'api/auth/login',
    AUTH_LOGOUT: 'api/auth/logout',
    AUTH_REGISTER: 'api/auth/register',
    INTERACTIONS_LOG: 'api/interactions/log',
    INTERACTIONS_HISTORY: 'api/interactions/history',
    CONVERSATION_START: 'api/conversation/start'
  },
  
  // 获取完整的API URL
  getApiUrl(endpoint) {
    return `${this.CURRENT_SERVER}/${endpoint.replace(/^\//, '')}`
  },
  
  // 获取WebSocket URL
  getWebSocketUrl(endpoint) {
    return `${this.CURRENT_WEBSOCKET}/${endpoint.replace(/^\//, '')}`
  }
}

module.exports = config
