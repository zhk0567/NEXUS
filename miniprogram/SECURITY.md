# 安全配置说明

## AppSecret 安全注意事项

**重要：AppSecret 是敏感信息，必须妥善保管！**

### 当前配置

- **AppID**: `wxc726bdd9b8ac6e5a`
- **AppSecret**: `35cb822813eee1ad1faebd64610b9eb4`

### 安全建议

1. **不要在前端代码中使用 AppSecret**
   - AppSecret 已添加到 `utils/config.js` 中，但仅用于参考
   - **实际使用时，AppSecret 应该只在后端服务器中使用**
   - 前端代码会被用户看到，AppSecret 不应暴露在前端

2. **正确的使用方式**

   如果需要使用 AppSecret（例如换取 openId），应该：
   
   ```javascript
   // ❌ 错误：在前端直接使用 AppSecret
   // 不要这样做！
   wx.request({
     url: 'https://api.weixin.qq.com/sns/jscode2session',
     data: {
       appid: 'wxc726bdd9b8ac6e5a',
       secret: '35cb822813eee1ad1faebd64610b9eb4', // 危险！
       js_code: code
     }
   })
   
   // ✅ 正确：将 code 发送到后端，由后端处理
   wx.request({
     url: 'https://your-backend.com/api/wechat/login',
     data: {
       code: code // 只发送 code
     }
   })
   ```

3. **后端处理示例（Python）**

   如果需要获取用户的 openId，应该在 `nexus_backend.py` 中添加接口：

   ```python
   @app.route('/api/wechat/login', methods=['POST'])
   def wechat_login():
       code = request.json.get('code')
       appid = 'wxc726bdd9b8ac6e5a'
       secret = '35cb822813eee1ad1faebd64610b9eb4'
       
       # 在后端调用微信API
       url = f'https://api.weixin.qq.com/sns/jscode2session'
       params = {
           'appid': appid,
           'secret': secret,
           'js_code': code,
           'grant_type': 'authorization_code'
       }
       response = requests.get(url, params=params)
       data = response.json()
       
       # 返回 openId 给前端
       return jsonify({
           'success': True,
           'openid': data.get('openid')
       })
   ```

4. **版本控制**

   - `project.config.json` 中的 AppID 可以提交到版本控制
   - AppSecret **不应**提交到版本控制系统
   - 已在 `.gitignore` 中添加了相关配置

5. **如果 AppSecret 泄露**

   如果 AppSecret 已经泄露：
   - 立即在微信公众平台重置 AppSecret
   - 更新所有使用该 AppSecret 的服务
   - 检查是否有异常访问

## 当前项目中的使用

本项目目前使用 NEXUS 自己的用户认证系统，不依赖微信的 openId。因此：

- AppID 仅用于小程序的基本功能（如获取用户信息等）
- AppSecret 暂时不需要使用
- 用户登录仍然使用 NEXUS 的用户名密码系统

如果将来需要集成微信登录功能，请按照上述安全建议在后端实现。

