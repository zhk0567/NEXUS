// history.js
const storage = require('../../utils/storage.js')
const app = getApp()

Page({
  data: {
    conversations: [],
    currentConversationId: null
  },

  onLoad() {
    this.loadConversations()
  },

  onShow() {
    this.loadConversations()
  },

  // 加载对话列表
  loadConversations() {
    const conversations = storage.getConversations()
    const currentConversationId = storage.getCurrentConversationId()
    
    // 按时间排序
    conversations.sort((a, b) => b.lastMessageTime - a.lastMessageTime)
    
    this.setData({
      conversations: conversations,
      currentConversationId: currentConversationId
    })
  },

  // 选择对话
  selectConversation(e) {
    const conversationId = e.currentTarget.dataset.id
    storage.setCurrentConversationId(conversationId)
    
    // 跳转到聊天页面
    wx.switchTab({
      url: '/pages/index/index'
    })
  },

  // 删除对话
  deleteConversation(e) {
    const conversationId = e.currentTarget.dataset.id
    
    wx.showModal({
      title: '确认删除',
      content: '确定要删除这个对话吗？',
      success: (res) => {
        if (res.confirm) {
          storage.deleteConversation(conversationId)
          this.loadConversations()
          
          wx.showToast({
            title: '删除成功',
            icon: 'success'
          })
        }
      }
    })
  },

  // 格式化时间
  formatTime(timestamp) {
    const date = new Date(timestamp)
    const now = new Date()
    const diff = now - date
    
    if (diff < 60000) {
      return '刚刚'
    } else if (diff < 3600000) {
      return `${Math.floor(diff / 60000)}分钟前`
    } else if (diff < 86400000) {
      return `${Math.floor(diff / 3600000)}小时前`
    } else {
      return `${date.getMonth() + 1}月${date.getDate()}日`
    }
  }
})

