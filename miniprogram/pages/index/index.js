// index.js
const api = require('../../utils/api.js')
const storage = require('../../utils/storage.js')
const app = getApp()

Page({
  data: {
    messages: [],
    inputText: '',
    isRecording: false,
    isLoading: false,
    isStreaming: false,
    streamingText: '',
    currentMessageId: null,
    isPlayingTTS: false,
    currentConversationId: null,
    showVoiceMode: false,
    recordingDuration: 0,
    recordingTimer: null
  },

  onLoad() {
    // 确保用户已初始化
    if (!app.globalData.userId) {
      const defaultUserId = 'miniprogram_user_' + Date.now()
      app.globalData.userId = defaultUserId
      app.globalData.userInfo = { username: '小程序用户' }
      app.globalData.isLoggedIn = true
      wx.setStorageSync('userId', defaultUserId)
      wx.setStorageSync('userInfo', { username: '小程序用户' })
    }
    
    // 加载当前对话
    this.loadCurrentConversation()
  },

  onShow() {
    // 确保用户已初始化
    if (!app.globalData.userId) {
      const defaultUserId = 'miniprogram_user_' + Date.now()
      app.globalData.userId = defaultUserId
      app.globalData.userInfo = { username: '小程序用户' }
      app.globalData.isLoggedIn = true
      wx.setStorageSync('userId', defaultUserId)
      wx.setStorageSync('userInfo', { username: '小程序用户' })
    }
  },

  // 加载当前对话
  loadCurrentConversation() {
    const conversationId = storage.getCurrentConversationId()
    if (conversationId) {
      this.setData({
        currentConversationId: conversationId,
        messages: storage.getMessages(conversationId)
      })
    } else {
      // 创建新对话
      this.startNewConversation()
    }
  },

  // 开始新对话
  startNewConversation() {
    const conversationId = storage.generateConversationId()
    storage.setCurrentConversationId(conversationId)
    
    const conversation = {
      id: conversationId,
      title: '新对话',
      createTime: Date.now(),
      lastMessageTime: Date.now()
    }
    storage.saveConversation(conversation)
    
    this.setData({
      currentConversationId: conversationId,
      messages: []
    })
  },

  // 输入文本变化
  onInputChange(e) {
    this.setData({
      inputText: e.detail.value
    })
  },

  // 发送文本消息
  async sendMessage() {
    const text = this.data.inputText.trim()
    if (!text) return

    this.setData({
      inputText: '',
      isLoading: true
    })

    // 添加用户消息
    const userMessage = {
      id: `msg_${Date.now()}`,
      type: 'user',
      content: text,
      timestamp: Date.now()
    }
    this.addMessage(userMessage)

    try {
      // 调用AI接口
      const response = await api.chat(
        text,
        app.globalData.userId,
        app.globalData.sessionId,
        this.getConversationHistory()
      )

      if (response.success) {
        const aiMessage = {
          id: `msg_${Date.now()}`,
          type: 'assistant',
          content: response.response,
          timestamp: Date.now()
        }
        this.addMessage(aiMessage)
      } else {
        wx.showToast({
          title: 'AI回复失败',
          icon: 'none'
        })
      }
    } catch (error) {
      console.error('发送消息失败:', error)
      let errorMsg = '网络错误'
      
      // 如果是401错误，可能是用户未注册或user_id不匹配
      if (error.message && error.message.includes('401')) {
        errorMsg = '用户未注册，正在自动注册...'
        // 尝试自动注册用户
        try {
          const api = require('../../utils/api.js')
          const username = app.globalData.userInfo?.username || app.globalData.userId || '小程序用户'
          const response = await api.register(
            app.globalData.userId,
            username,
            null,
            null
          )
          if (response.success) {
            wx.setStorageSync('userRegistered', true)
            // 如果后端返回了新的user_id，更新它
            if (response.user_id && response.user_id !== app.globalData.userId) {
              app.globalData.userId = response.user_id
              wx.setStorageSync('userId', response.user_id)
              console.log('已更新用户ID为:', response.user_id)
            }
            // 注册成功后重试发送消息
            setTimeout(() => {
              this.sendMessage()
            }, 500)
            return
          }
        } catch (regError) {
          console.error('自动注册失败:', regError)
          // 如果是因为用户名已存在，说明用户可能已经注册过了
          // 后端user_exists会同时检查user_id和username，所以应该能通过
          if (regError.message && regError.message.includes('已存在')) {
            wx.setStorageSync('userRegistered', true)
            // 用户名已存在，重试一次
            setTimeout(() => {
              this.sendMessage()
            }, 500)
            return
          }
        }
        errorMsg = '用户认证失败，请检查服务器连接'
      } else if (error.message) {
        errorMsg = error.message
      }
      
      wx.showToast({
        title: errorMsg,
        icon: 'none',
        duration: 2000
      })
    } finally {
      this.setData({
        isLoading: false
      })
    }
  },

  // 流式发送消息
  async sendMessageStreaming() {
    const text = this.data.inputText.trim()
    if (!text) return

    this.setData({
      inputText: '',
      isStreaming: true,
      streamingText: ''
    })

    // 添加用户消息
    const userMessage = {
      id: `msg_${Date.now()}`,
      type: 'user',
      content: text,
      timestamp: Date.now()
    }
    this.addMessage(userMessage)

    // 创建AI消息占位符
    const aiMessageId = `msg_${Date.now()}`
    const aiMessage = {
      id: aiMessageId,
      type: 'assistant',
      content: '',
      timestamp: Date.now()
    }
    this.addMessage(aiMessage)
    this.setData({
      currentMessageId: aiMessageId
    })

    try {
      await api.chatStreaming(
        text,
        app.globalData.userId,
        app.globalData.sessionId,
        this.getConversationHistory(),
        (data) => {
          // 处理流式数据
          if (data.content) {
            const currentText = this.data.streamingText + data.content
            this.setData({
              streamingText: currentText
            })
            // 更新消息内容
            this.updateMessageContent(aiMessageId, currentText)
          }
        },
        (error) => {
          console.error('流式请求失败:', error)
          wx.showToast({
            title: '网络错误',
            icon: 'none'
          })
        },
        () => {
          this.setData({
            isStreaming: false,
            streamingText: '',
            currentMessageId: null
          })
        }
      )
    } catch (error) {
      console.error('发送消息失败:', error)
      this.setData({
        isStreaming: false,
        streamingText: '',
        currentMessageId: null
      })
    }
  },

  // 开始录音
  startRecord() {
    // 先请求录音权限
    wx.authorize({
      scope: 'scope.record',
      success: () => {
        // 权限获取成功，开始录音
        this.doStartRecord()
      },
      fail: () => {
        // 权限被拒绝，提示用户
        wx.showModal({
          title: '需要录音权限',
          content: '请在小程序设置中开启录音权限',
          showCancel: false,
          success: (res) => {
            if (res.confirm) {
              // 打开设置页面
              wx.openSetting({
                success: (settingRes) => {
                  if (settingRes.authSetting['scope.record']) {
                    // 用户已授权，开始录音
                    this.doStartRecord()
                  }
                }
              })
            }
          }
        })
      }
    })
  },

  // 执行录音
  doStartRecord() {
    this.setData({
      isRecording: true,
      recordingDuration: 0
    })

    // 开始计时
    const timer = setInterval(() => {
      this.setData({
        recordingDuration: this.data.recordingDuration + 1
      })
    }, 1000)
    this.setData({
      recordingTimer: timer
    })

    const recorderManager = wx.getRecorderManager()
    recorderManager.onStart(() => {
      console.log('录音开始')
    })

    recorderManager.onError((err) => {
      console.error('录音错误:', err)
      wx.showToast({
        title: '录音失败',
        icon: 'none'
      })
      this.stopRecord()
    })

    recorderManager.start({
      duration: 60000, // 最长60秒
      sampleRate: 16000,
      numberOfChannels: 1,
      encodeBitRate: 96000,
      format: 'wav'
    })

    this.recorderManager = recorderManager
  },

  // 停止录音
  stopRecord() {
    if (this.data.recordingTimer) {
      clearInterval(this.data.recordingTimer)
      this.setData({
        recordingTimer: null
      })
    }

    if (this.recorderManager) {
      this.recorderManager.stop()
      this.recorderManager.onStop((res) => {
        this.setData({
          isRecording: false
        })

        if (res.tempFilePath) {
          this.transcribeAudio(res.tempFilePath)
        }
      })
    }
  },

  // 语音识别
  async transcribeAudio(audioFilePath) {
    wx.showLoading({
      title: '识别中...'
    })

    try {
      const response = await api.transcribe(audioFilePath)
      
      if (response.success && response.text) {
        // 识别成功，发送消息
        this.setData({
          inputText: response.text
        })
        await this.sendMessage()
      } else {
        wx.showToast({
          title: '识别失败',
          icon: 'none'
        })
      }
    } catch (error) {
      console.error('语音识别失败:', error)
      wx.showToast({
        title: '识别失败',
        icon: 'none'
      })
    } finally {
      wx.hideLoading()
    }
  },

  // 播放TTS
  async playTTS(e) {
    const text = e.currentTarget.dataset.text
    const messageId = e.currentTarget.dataset.id
    
    if (!text) return

    if (this.data.isPlayingTTS) {
      // 停止当前播放
      if (this.innerAudioContext) {
        this.innerAudioContext.stop()
        this.innerAudioContext.destroy()
        this.innerAudioContext = null
      }
    }

    this.setData({
      isPlayingTTS: true
    })

    try {
      wx.showLoading({
        title: '生成语音中...'
      })
      
      // 获取TTS音频
      const response = await api.textToSpeech(text)
      
      wx.hideLoading()
      
      if (response.success) {
        // 检查返回的是base64数据还是URL
        if (response.audio) {
          // base64数据，需要保存为文件
          const fs = wx.getFileSystemManager()
          const filePath = `${wx.env.USER_DATA_PATH}/tts_${Date.now()}.mp3`
          
          try {
            // 将base64转换为ArrayBuffer
            const base64 = response.audio.replace(/^data:audio\/\w+;base64,/, '')
            const arrayBuffer = wx.base64ToArrayBuffer(base64)
            
            fs.writeFileSync(filePath, arrayBuffer, 'binary')
            this.playAudioFile(filePath)
          } catch (err) {
            console.error('保存音频失败:', err)
            // 如果保存失败，尝试直接使用音频URL
            if (response.audio_url) {
              this.playAudioFile(response.audio_url)
            } else {
              this.setData({
                isPlayingTTS: false
              })
              wx.showToast({
                title: '播放失败',
                icon: 'none'
              })
            }
          }
        } else if (response.audio_url) {
          // 直接使用URL播放
          this.playAudioFile(response.audio_url)
        } else {
          this.setData({
            isPlayingTTS: false
          })
          wx.showToast({
            title: '未获取到音频',
            icon: 'none'
          })
        }
      } else {
        this.setData({
          isPlayingTTS: false
        })
        wx.showToast({
          title: response.message || '生成语音失败',
          icon: 'none'
        })
      }
    } catch (error) {
      wx.hideLoading()
      console.error('TTS失败:', error)
      this.setData({
        isPlayingTTS: false
      })
      wx.showToast({
        title: '网络错误',
        icon: 'none'
      })
    }
  },

  // 播放音频文件
  playAudioFile(filePath) {
    // 创建新的音频上下文
    if (this.innerAudioContext) {
      this.innerAudioContext.destroy()
    }
    
    this.innerAudioContext = wx.createInnerAudioContext()
    
    this.innerAudioContext.onPlay(() => {
      console.log('开始播放')
    })
    
    this.innerAudioContext.onEnded(() => {
      console.log('播放结束')
      this.setData({
        isPlayingTTS: false
      })
      if (this.innerAudioContext) {
        this.innerAudioContext.destroy()
        this.innerAudioContext = null
      }
    })
    
    this.innerAudioContext.onError((err) => {
      console.error('播放失败:', err)
      this.setData({
        isPlayingTTS: false
      })
      wx.showToast({
        title: '播放失败',
        icon: 'none'
      })
      if (this.innerAudioContext) {
        this.innerAudioContext.destroy()
        this.innerAudioContext = null
      }
    })
    
    this.innerAudioContext.src = filePath
    this.innerAudioContext.play()
  },

  // 添加消息
  addMessage(message) {
    const messages = [...this.data.messages, message]
    this.setData({
      messages: messages
    })
    
    // 保存到本地存储
    if (this.data.currentConversationId) {
      storage.saveMessage(this.data.currentConversationId, message)
    }
    
    // 滚动到底部
    setTimeout(() => {
      this.scrollToBottom()
    }, 100)
  },

  // 更新消息内容
  updateMessageContent(messageId, content) {
    const messages = this.data.messages.map(msg => {
      if (msg.id === messageId) {
        return { ...msg, content: content }
      }
      return msg
    })
    this.setData({
      messages: messages
    })
  },

  // 获取对话历史
  getConversationHistory() {
    return this.data.messages.slice(-10).map(msg => ({
      role: msg.type === 'user' ? 'user' : 'assistant',
      content: msg.content
    }))
  },

  // 滚动到底部
  scrollToBottom() {
    wx.createSelectorQuery().select('#messages').boundingClientRect((rect) => {
      if (rect) {
        wx.pageScrollTo({
          scrollTop: rect.height,
          duration: 300
        })
      }
    }).exec()
  },

  // 新话题
  onNewTopic() {
    wx.showModal({
      title: '确认',
      content: '确定要开始新话题吗？',
      success: (res) => {
        if (res.confirm) {
          this.startNewConversation()
        }
      }
    })
  },

  onUnload() {
    // 清理资源
    if (this.innerAudioContext) {
      this.innerAudioContext.destroy()
    }
    if (this.data.recordingTimer) {
      clearInterval(this.data.recordingTimer)
    }
  }
})

