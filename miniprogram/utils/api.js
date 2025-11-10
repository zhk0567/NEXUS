// API工具类
const config = require('./config.js')

/**
 * 发送HTTP请求
 */
function request(url, method = 'GET', data = {}, header = {}) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: url,
      method: method,
      data: data,
      header: {
        'Content-Type': 'application/json',
        ...header
      },
      success: (res) => {
        if (res.statusCode === 200) {
          resolve(res.data)
        } else if (res.statusCode === 401) {
          // 401错误，可能是用户未注册
          const errorMsg = res.data && res.data.error ? res.data.error : '需要用户认证'
          reject(new Error(errorMsg))
        } else {
          const errorMsg = res.data && res.data.error ? res.data.error : `请求失败: ${res.statusCode}`
          reject(new Error(errorMsg))
        }
      },
      fail: (err) => {
        reject(err)
      }
    })
  })
}

/**
 * 健康检查
 */
function healthCheck() {
  return request(config.getApiUrl(config.ENDPOINTS.HEALTH))
}

/**
 * 用户登录
 */
function login(username, password) {
  return request(
    config.getApiUrl(config.ENDPOINTS.AUTH_LOGIN),
    'POST',
    { username, password }
  )
}

/**
 * 用户注册
 * 注意：后端注册API需要username和password
 * 后端会生成新的user_id，但user_exists会同时检查user_id和username
 * 所以我们使用userId作为username，这样即使user_id不同，也能通过username找到用户
 */
function register(userId, username, password = null, email = null) {
  // 如果没有提供密码，生成一个随机密码
  const finalPassword = password || `mp_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  
  // 使用userId作为username，确保后端能通过username找到用户
  const finalUsername = username || userId
  
  return request(
    config.getApiUrl(config.ENDPOINTS.AUTH_REGISTER),
    'POST',
    { 
      username: finalUsername,
      password: finalPassword
      // 注意：后端会生成新的user_id，但user_exists会同时检查user_id和username
      // 所以我们用userId作为username，这样就能通过验证
    }
  )
}

/**
 * AI聊天
 */
function chat(message, userId, sessionId = null, conversationHistory = []) {
  const data = {
    message: message,
    user_id: userId
  }
  
  if (sessionId) {
    data.session_id = sessionId
  }
  
  if (conversationHistory.length > 0) {
    data.conversation_history = conversationHistory
  }
  
  return request(
    config.getApiUrl(config.ENDPOINTS.CHAT),
    'POST',
    data
  )
}

/**
 * 流式AI聊天（模拟流式显示）
 * 注意：微信小程序不支持直接流式HTTP响应，这里使用模拟逐字显示
 */
function chatStreaming(message, userId, sessionId = null, conversationHistory = [], onMessage, onError, onComplete) {
  return new Promise((resolve, reject) => {
    const data = {
      message: message,
      user_id: userId
    }
    
    if (sessionId) {
      data.session_id = sessionId
    }
    
    if (conversationHistory.length > 0) {
      data.conversation_history = conversationHistory
    }
    
    // 使用普通请求，后端返回完整响应后模拟流式显示
    wx.request({
      url: config.getApiUrl(config.ENDPOINTS.CHAT),
      method: 'POST',
      data: data,
      header: {
        'Content-Type': 'application/json'
      },
      success: (res) => {
        if (res.statusCode === 200 && res.data.success) {
          // 模拟流式显示（逐字显示）
          const response = res.data.response
          let index = 0
          const timer = setInterval(() => {
            if (index < response.length) {
              const chunk = response.substring(index, Math.min(index + 3, response.length))
              if (onMessage) {
                onMessage({ content: chunk })
              }
              index += 3
            } else {
              clearInterval(timer)
              if (onComplete) onComplete()
              resolve(res.data)
            }
          }, 50) // 每50ms显示一次
        } else {
          const error = new Error(res.data.message || '请求失败')
          if (onError) onError(error)
          reject(error)
        }
      },
      fail: (err) => {
        if (onError) onError(err)
        reject(err)
      }
    })
  })
}

/**
 * 语音识别（ASR）
 */
function transcribe(audioFilePath) {
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: config.getApiUrl(config.ENDPOINTS.TRANSCRIBE),
      filePath: audioFilePath,
      name: 'audio',
      formData: {},
      header: {
        'Content-Type': 'multipart/form-data'
      },
      success: (res) => {
        try {
          const data = JSON.parse(res.data)
          resolve(data)
        } catch (e) {
          reject(new Error('解析响应失败'))
        }
      },
      fail: (err) => {
        reject(err)
      }
    })
  })
}

/**
 * 语音合成（TTS）
 */
function textToSpeech(text, voice = 'zh-CN-XiaoxiaoNeural') {
  return request(
    config.getApiUrl(config.ENDPOINTS.TTS),
    'POST',
    { text, voice }
  )
}

/**
 * 开始新对话
 */
function startNewConversation(userId) {
  return request(
    config.getApiUrl(config.ENDPOINTS.CONVERSATION_START),
    'POST',
    { user_id: userId }
  )
}

/**
 * 记录交互
 */
function logInteraction(userId, interactionType, data = {}) {
  return request(
    config.getApiUrl(config.ENDPOINTS.INTERACTIONS_LOG),
    'POST',
    {
      user_id: userId,
      interaction_type: interactionType,
      ...data
    }
  )
}

module.exports = {
  request,
  healthCheck,
  login,
  register,
  chat,
  chatStreaming,
  transcribe,
  textToSpeech,
  startNewConversation,
  logInteraction
}

