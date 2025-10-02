# NEXUS 登录用户说明

## 测试用户账号

系统已预置以下测试用户，可以直接使用：

### 管理员账号
- **用户名**: `admin`
- **密码**: `admin123`
- **权限**: 管理员权限

### 测试用户账号
- **用户名**: `testuser1`
- **密码**: `password123`

- **用户名**: `testuser2`
- **密码**: `password456`

- **用户名**: `testuser3`
- **密码**: `password789`

## 登录方式

### Android应用
1. 打开NEXUS应用
2. 点击右上角的登录按钮
3. 输入用户名和密码
4. 点击"登录"按钮

### 后端API
```bash
# 登录API
POST http://192.168.50.205:5000/api/auth/login

# 请求体
{
    "username": "testuser1",
    "password": "password123",
    "device_info": "Android"
}

# 响应
{
    "success": true,
    "user": {
        "user_id": "user_001",
        "username": "testuser1",
        "created_at": "2025-10-01T10:00:00",
        "last_login_at": "2025-10-01T10:30:00"
    },
    "session_id": "uuid-session-id"
}
```

## 功能说明

### 已实现功能
- ✅ 用户登录验证
- ✅ 密码哈希存储
- ✅ 会话管理
- ✅ 登录日志记录
- ✅ Android登录界面
- ✅ 后端API接口

### 数据库表
- **users**: 用户信息表
- **user_sessions**: 用户会话表
- **interactions**: 交互记录表
- **system_logs**: 系统日志表

## 安全说明

1. **密码存储**: 使用SHA256哈希存储，不存储明文密码
2. **会话管理**: 每次登录生成唯一会话ID
3. **日志记录**: 记录所有登录尝试和系统事件
4. **输入验证**: 前后端都有输入验证

## 注意事项

1. 测试用户仅用于开发测试，生产环境请修改默认密码
2. 会话ID用于后续API调用认证
3. 所有用户交互都会记录到数据库
4. 支持用户注册功能（通过API）

## 扩展功能

如需添加新用户，可以通过以下方式：

### 1. 直接数据库操作
```sql
INSERT INTO users (user_id, username, password_hash, is_active) 
VALUES ('user_004', 'newuser', SHA2('newpassword', 256), 1);
```

### 2. 通过注册API
```bash
POST http://192.168.50.205:5000/api/auth/register
{
    "username": "newuser",
    "password": "newpassword"
}
```

### 3. 修改配置文件
在 `database_config.py` 中的 `TEST_USERS` 列表添加新用户。
