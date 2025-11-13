-- 更新user_sessions表，添加app_type、device_info和ip_address字段
-- 用于支持同一账号同一app不能在不同设备同时登录的检测

USE nexus_unified;

-- 添加新字段（如果不存在）
ALTER TABLE user_sessions 
ADD COLUMN IF NOT EXISTS app_type VARCHAR(50) NOT NULL DEFAULT 'unknown' COMMENT '应用类型：ai_chat/story_control' AFTER session_id;

ALTER TABLE user_sessions 
ADD COLUMN IF NOT EXISTS device_info VARCHAR(500) NULL COMMENT '设备信息' AFTER app_type;

ALTER TABLE user_sessions 
ADD COLUMN IF NOT EXISTS ip_address VARCHAR(50) NULL COMMENT 'IP地址' AFTER device_info;

-- 添加索引以提高查询性能
ALTER TABLE user_sessions 
ADD INDEX IF NOT EXISTS idx_user_app (user_id, app_type);

-- 查看表结构
DESCRIBE user_sessions;

