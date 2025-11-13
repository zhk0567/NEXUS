-- 创建10个测试用户账号
-- 用户名和密码简单易记：user01-user10，密码都是123456

USE nexus_unified;

-- 使用SHA256哈希密码（与database_manager.py中的hash_password方法一致）
-- 密码123456的SHA256哈希值: 8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92

INSERT INTO users (user_id, username, password_hash, is_active) VALUES
('user_00000001', 'user01', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', TRUE),
('user_00000002', 'user02', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', TRUE),
('user_00000003', 'user03', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', TRUE),
('user_00000004', 'user04', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', TRUE),
('user_00000005', 'user05', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', TRUE),
('user_00000006', 'user06', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', TRUE),
('user_00000007', 'user07', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', TRUE),
('user_00000008', 'user08', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', TRUE),
('user_00000009', 'user09', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', TRUE),
('user_00000010', 'user10', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', TRUE)
ON DUPLICATE KEY UPDATE username=username;

-- 显示创建的用户
SELECT user_id, username, created_at FROM users WHERE username LIKE 'user%' ORDER BY username;

