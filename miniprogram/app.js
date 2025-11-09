// app.js
App({
  onLaunch() {
    // 展示本地存储能力
    const logs = wx.getStorageSync('logs') || []
    logs.unshift(Date.now())
    wx.setStorageSync('logs', logs)

    // 微信登录（获取code，可用于后续换取openId等）
    wx.login({
      success: res => {
        // 发送 res.code 到后台换取 openId, sessionKey, unionId
        console.log('微信登录成功', res.code)
        // 可以在这里将code发送到后端服务器，换取用户信息
        // 注意：这里只是获取code，实际的用户认证还是使用NEXUS的用户系统
      },
      fail: err => {
        console.error('微信登录失败', err)
      }
    })
    
    // 检查登录状态
    this.checkLoginStatus()
  },
  
  globalData: {
    userInfo: null,
    isLoggedIn: false,
    userId: null,
    sessionId: null,
    serverUrl: 'http://192.168.50.205:5000', // 默认本地服务器地址
    theme: 'light', // 'light' or 'dark'
    fontSize: 'medium' // 'small', 'medium', 'large'
  },
  
  checkLoginStatus() {
    // 自动登录，使用默认用户ID
    const userInfo = wx.getStorageSync('userInfo')
    const userId = wx.getStorageSync('userId')
    const sessionId = wx.getStorageSync('sessionId')
    const userRegistered = wx.getStorageSync('userRegistered') // 标记用户是否已注册到后端
    
    // 如果没有用户信息，使用默认值
    if (!userId) {
      const defaultUserId = 'miniprogram_user_' + Date.now()
      this.globalData.userId = defaultUserId
      this.globalData.userInfo = { username: '小程序用户' }
      this.globalData.isLoggedIn = true
      wx.setStorageSync('userId', defaultUserId)
      wx.setStorageSync('userInfo', { username: '小程序用户' })
      
      // 自动注册用户到后端
      if (!userRegistered) {
        this.registerUser(defaultUserId)
      }
    } else {
      this.globalData.userInfo = userInfo || { username: '小程序用户' }
      this.globalData.userId = userId
      this.globalData.sessionId = sessionId
      this.globalData.isLoggedIn = true
      
      // 如果用户未注册，尝试注册
      if (!userRegistered) {
        this.registerUser(userId)
      }
    }
  },
  
  // 自动注册用户到后端
  async registerUser(userId) {
    const api = require('./utils/api.js')
    const username = this.globalData.userInfo?.username || '小程序用户'
    
    try {
      console.log('正在注册用户到后端...', userId)
      const response = await api.register(userId, username, null, null)
      
      if (response.success) {
        console.log('用户注册成功', response)
        // 重要：使用后端返回的user_id，而不是小程序生成的
        if (response.user_id) {
          this.globalData.userId = response.user_id
          wx.setStorageSync('userId', response.user_id)
          console.log('已更新用户ID为:', response.user_id)
        }
        wx.setStorageSync('userRegistered', true)
      } else {
        console.warn('用户注册失败，但继续使用:', response.message || response.error)
        // 即使注册失败，也标记为已尝试，避免重复尝试
        wx.setStorageSync('userRegistered', true)
      }
    } catch (error) {
      console.error('注册用户失败:', error)
      // 注册失败不影响使用，稍后重试
      // 如果后端返回用户已存在，也标记为已注册
      if (error.message && (error.message.includes('已存在') || error.message.includes('400'))) {
        wx.setStorageSync('userRegistered', true)
        // 如果用户名已存在，尝试使用用户名作为user_id进行查找
        // 因为后端user_exists会同时检查user_id和username
      }
    }
  },
  
  setUserInfo(userInfo, userId, sessionId) {
    this.globalData.userInfo = userInfo
    this.globalData.userId = userId
    this.globalData.sessionId = sessionId
    this.globalData.isLoggedIn = true
    
    wx.setStorageSync('userInfo', userInfo)
    wx.setStorageSync('userId', userId)
    if (sessionId) {
      wx.setStorageSync('sessionId', sessionId)
    }
  },
  
  logout() {
    this.globalData.userInfo = null
    this.globalData.userId = null
    this.globalData.sessionId = null
    this.globalData.isLoggedIn = false
    
    wx.removeStorageSync('userInfo')
    wx.removeStorageSync('userId')
    wx.removeStorageSync('sessionId')
  }
})

