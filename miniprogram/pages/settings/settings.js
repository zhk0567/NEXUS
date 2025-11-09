// settings.js
const app = getApp()

Page({
  data: {
    theme: 'light',
    fontSize: 'medium',
    serverUrl: '',
    isLoggedIn: false,
    username: ''
  },

  onLoad() {
    this.loadSettings()
    this.loadUserInfo()
  },

  loadSettings() {
    const theme = wx.getStorageSync('theme') || app.globalData.theme || 'light'
    const fontSize = wx.getStorageSync('fontSize') || app.globalData.fontSize || 'medium'
    const serverUrl = wx.getStorageSync('serverUrl') || app.globalData.serverUrl || 'http://192.168.50.205:5000'
    
    this.setData({
      theme: theme,
      fontSize: fontSize,
      serverUrl: serverUrl
    })
  },

  loadUserInfo() {
    const userInfo = app.globalData.userInfo
    this.setData({
      isLoggedIn: app.globalData.isLoggedIn,
      username: userInfo ? userInfo.username : ''
    })
  },

  // 切换主题
  onThemeChange(e) {
    const theme = e.detail.value
    this.setData({
      theme: theme
    })
    wx.setStorageSync('theme', theme)
    app.globalData.theme = theme
  },

  // 切换字体大小
  onFontSizeChange(e) {
    const fontSize = e.detail.value
    this.setData({
      fontSize: fontSize
    })
    wx.setStorageSync('fontSize', fontSize)
    app.globalData.fontSize = fontSize
  },

  // 服务器地址输入
  onServerUrlInput(e) {
    this.setData({
      serverUrl: e.detail.value
    })
  },

  // 保存服务器地址
  saveServerUrl() {
    const serverUrl = this.data.serverUrl.trim()
    if (!serverUrl) {
      wx.showToast({
        title: '请输入服务器地址',
        icon: 'none'
      })
      return
    }
    
    wx.setStorageSync('serverUrl', serverUrl)
    app.globalData.serverUrl = serverUrl
    
    wx.showToast({
      title: '保存成功',
      icon: 'success'
    })
  },

  // 清除数据（替代退出登录）
  onClearData() {
    wx.showModal({
      title: '确认',
      content: '确定要清除所有本地数据吗？',
      success: (res) => {
        if (res.confirm) {
          // 清除所有本地存储
          wx.clearStorageSync()
          // 重新初始化用户
          const defaultUserId = 'miniprogram_user_' + Date.now()
          app.globalData.userId = defaultUserId
          app.globalData.userInfo = { username: '小程序用户' }
          app.globalData.isLoggedIn = true
          wx.setStorageSync('userId', defaultUserId)
          wx.setStorageSync('userInfo', { username: '小程序用户' })
          
          wx.showToast({
            title: '数据已清除',
            icon: 'success'
          })
          
          // 刷新页面
          this.loadUserInfo()
        }
      }
    })
  }
})

