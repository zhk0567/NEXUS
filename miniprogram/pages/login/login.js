// login.js
const api = require('../../utils/api.js')
const app = getApp()

Page({
  data: {
    username: '',
    password: '',
    showPassword: false,
    isLoading: false
  },

  onLoad() {
    // 如果已登录，跳转到首页
    if (app.globalData.isLoggedIn) {
      wx.redirectTo({
        url: '/pages/index/index'
      })
    }
  },

  // 用户名输入
  onUsernameInput(e) {
    this.setData({
      username: e.detail.value
    })
  },

  // 密码输入
  onPasswordInput(e) {
    this.setData({
      password: e.detail.value
    })
  },

  // 切换密码显示
  togglePassword() {
    this.setData({
      showPassword: !this.data.showPassword
    })
  },

  // 登录
  async onLogin() {
    const { username, password } = this.data
    
    if (!username || !password) {
      wx.showToast({
        title: '请输入用户名和密码',
        icon: 'none'
      })
      return
    }

    this.setData({
      isLoading: true
    })

    try {
      const response = await api.login(username, password)
      
      if (response.success) {
        // 保存用户信息
        app.setUserInfo(
          { username: username },
          response.user_id || username,
          response.session_id
        )
        
        wx.showToast({
          title: '登录成功',
          icon: 'success'
        })
        
        // 跳转到首页
        setTimeout(() => {
          wx.redirectTo({
            url: '/pages/index/index'
          })
        }, 1000)
      } else {
        wx.showToast({
          title: response.message || '登录失败',
          icon: 'none'
        })
      }
    } catch (error) {
      console.error('登录失败:', error)
      wx.showToast({
        title: '网络错误，请检查服务器连接',
        icon: 'none',
        duration: 2000
      })
    } finally {
      this.setData({
        isLoading: false
      })
    }
  }
})

