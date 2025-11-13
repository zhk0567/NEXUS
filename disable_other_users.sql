-- 禁用除user01-user10之外的所有用户账号
-- 只保留这10个测试账号可用

USE nexus_unified;

-- 禁用所有不在白名单中的用户
UPDATE users 
SET is_active = FALSE 
WHERE username NOT IN ('user01', 'user02', 'user03', 'user04', 'user05', 
                       'user06', 'user07', 'user08', 'user09', 'user10');

-- 确保这10个账号是激活状态
UPDATE users 
SET is_active = TRUE 
WHERE username IN ('user01', 'user02', 'user03', 'user04', 'user05', 
                   'user06', 'user07', 'user08', 'user09', 'user10');

-- 显示当前所有用户状态
SELECT user_id, username, is_active, created_at, last_login_at 
FROM users 
ORDER BY 
    CASE WHEN username IN ('user01', 'user02', 'user03', 'user04', 'user05', 
                          'user06', 'user07', 'user08', 'user09', 'user10') THEN 0 ELSE 1 END,
    username;

