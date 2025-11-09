// 本地存储工具类
const storage = {
  // 保存消息
  saveMessage(conversationId, message) {
    const key = `conversation_${conversationId}`
    const messages = this.getMessages(conversationId)
    messages.push({
      ...message,
      timestamp: Date.now()
    })
    wx.setStorageSync(key, messages)
  },
  
  // 获取消息列表
  getMessages(conversationId) {
    const key = `conversation_${conversationId}`
    return wx.getStorageSync(key) || []
  },
  
  // 保存对话列表
  saveConversation(conversation) {
    const conversations = this.getConversations()
    const index = conversations.findIndex(c => c.id === conversation.id)
    if (index >= 0) {
      conversations[index] = conversation
    } else {
      conversations.unshift(conversation)
    }
    wx.setStorageSync('conversations', conversations)
  },
  
  // 获取对话列表
  getConversations() {
    return wx.getStorageSync('conversations') || []
  },
  
  // 删除对话
  deleteConversation(conversationId) {
    const conversations = this.getConversations()
    const filtered = conversations.filter(c => c.id !== conversationId)
    wx.setStorageSync('conversations', filtered)
    wx.removeStorageSync(`conversation_${conversationId}`)
  },
  
  // 获取当前对话ID
  getCurrentConversationId() {
    return wx.getStorageSync('currentConversationId')
  },
  
  // 设置当前对话ID
  setCurrentConversationId(conversationId) {
    wx.setStorageSync('currentConversationId', conversationId)
  },
  
  // 生成新的对话ID
  generateConversationId() {
    return `conv_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
  }
}

module.exports = storage

