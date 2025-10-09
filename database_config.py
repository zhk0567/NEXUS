#!/usr/bin/env python3
"""
数据库配置文件
"""
import os
from datetime import datetime

# 数据库配置
DATABASE_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': 'zhk050607',
    'database': 'nexus_unified',
    'charset': 'utf8mb4',
    'autocommit': True,
    'connect_timeout': 10,
    'read_timeout': 30,
    'write_timeout': 30
}

# 数据库表结构（优化后 - 移除未使用字段）
CREATE_TABLES_SQL = {
    'users': """
    CREATE TABLE IF NOT EXISTS users (
        id INT AUTO_INCREMENT PRIMARY KEY,
        user_id VARCHAR(255) UNIQUE NOT NULL COMMENT '用户唯一标识',
        username VARCHAR(100) NOT NULL COMMENT '用户名',
        password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
        last_login_at TIMESTAMP NULL COMMENT '最后登录时间',
        is_active BOOLEAN DEFAULT TRUE COMMENT '是否激活',
        INDEX idx_user_id (user_id),
        INDEX idx_username (username),
        INDEX idx_created_at (created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表'
    """,
    
    'interactions': """
    CREATE TABLE IF NOT EXISTS interactions (
        id INT AUTO_INCREMENT PRIMARY KEY,
        user_id VARCHAR(255) NOT NULL COMMENT '用户ID',
        interaction_type ENUM('text', 'voice_home', 'voice_call', 'tts_play') NOT NULL COMMENT '交互类型',
        content TEXT NOT NULL COMMENT '交互内容',
        response TEXT NULL COMMENT 'AI回复内容',
        timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '交互时间',
        session_id VARCHAR(255) NULL COMMENT '会话ID',
        duration_seconds INT NULL COMMENT '交互持续时间（秒）',
        success BOOLEAN DEFAULT TRUE COMMENT '是否成功',
        error_message TEXT NULL COMMENT '错误信息',
        INDEX idx_user_id (user_id),
        INDEX idx_interaction_type (interaction_type),
        INDEX idx_timestamp (timestamp),
        INDEX idx_session_id (session_id),
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交互记录表'
    """,
    
    'user_sessions': """
    CREATE TABLE IF NOT EXISTS user_sessions (
        id INT AUTO_INCREMENT PRIMARY KEY,
        user_id VARCHAR(255) NOT NULL COMMENT '用户ID',
        session_id VARCHAR(255) UNIQUE NOT NULL COMMENT '会话ID',
        login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
        INDEX idx_user_id (user_id),
        INDEX idx_session_id (session_id),
        INDEX idx_login_time (login_time),
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户会话表'
    """,
    
    'system_logs': """
    CREATE TABLE IF NOT EXISTS system_logs (
        id INT AUTO_INCREMENT PRIMARY KEY,
        log_level ENUM('DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL') NOT NULL COMMENT '日志级别',
        service_name VARCHAR(50) NOT NULL COMMENT '服务名称',
        message TEXT NOT NULL COMMENT '日志消息',
        timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '时间戳',
        INDEX idx_log_level (log_level),
        INDEX idx_service_name (service_name),
        INDEX idx_timestamp (timestamp)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统日志表'
    """,
    
    'reading_progress': """
    CREATE TABLE IF NOT EXISTS reading_progress (
        id INT AUTO_INCREMENT PRIMARY KEY,
        user_id VARCHAR(255) NOT NULL COMMENT '用户ID',
        story_id VARCHAR(255) NOT NULL COMMENT '故事ID',
        story_title VARCHAR(500) NOT NULL COMMENT '故事标题',
        current_position INT DEFAULT 0 COMMENT '当前阅读位置（字符数）',
        total_length INT DEFAULT 0 COMMENT '故事总长度（字符数）',
        reading_progress DECIMAL(5,2) DEFAULT 0.00 COMMENT '阅读进度百分比',
        is_completed BOOLEAN DEFAULT FALSE COMMENT '是否已完成阅读',
        start_time TIMESTAMP NULL COMMENT '开始阅读时间',
        last_read_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后阅读时间',
        completion_time TIMESTAMP NULL COMMENT '完成阅读时间',
        reading_duration_seconds INT DEFAULT 0 COMMENT '总阅读时长（秒）',
        session_id VARCHAR(255) NULL COMMENT '阅读会话ID',
        device_info VARCHAR(500) NULL COMMENT '设备信息',
        INDEX idx_user_id (user_id),
        INDEX idx_story_id (story_id),
        INDEX idx_user_story (user_id, story_id),
        INDEX idx_is_completed (is_completed),
        INDEX idx_last_read_time (last_read_time),
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='阅读进度表'
    """,
    
    
    'story_interactions': """
    CREATE TABLE IF NOT EXISTS story_interactions (
        id INT AUTO_INCREMENT PRIMARY KEY,
        user_id VARCHAR(255) NOT NULL COMMENT '用户ID',
        story_id VARCHAR(255) NOT NULL COMMENT '故事ID',
        interaction_type ENUM(
            'start_reading', 'pause_reading', 'resume_reading', 'complete_reading',
            'audio_play', 'audio_pause', 'audio_stop', 'audio_seek',
            'scroll_start', 'scroll_pause', 'scroll_resume',
            'bookmark', 'share', 'rate', 'view_details', 'filter_change',
            'bulk_action', 'admin_operation'
        ) NOT NULL COMMENT '交互类型',
        interaction_data JSON NULL COMMENT '交互数据（如位置、进度、操作参数等）',
        timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '交互时间',
        device_info VARCHAR(500) NULL COMMENT '设备信息',
        app_version VARCHAR(50) NULL COMMENT '应用版本',
        INDEX idx_user_id (user_id),
        INDEX idx_story_id (story_id),
        INDEX idx_interaction_type (interaction_type),
        INDEX idx_timestamp (timestamp),
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='故事交互记录表'
    """,
    
    'admin_operations': """
    CREATE TABLE IF NOT EXISTS admin_operations (
        id INT AUTO_INCREMENT PRIMARY KEY,
        admin_user_id VARCHAR(255) NOT NULL COMMENT '管理员用户ID',
        target_user_id VARCHAR(255) NOT NULL COMMENT '目标用户ID',
        story_id VARCHAR(255) NOT NULL COMMENT '故事ID',
        operation_type VARCHAR(50) NOT NULL COMMENT '操作类型',
        operation_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
        details TEXT NULL COMMENT '操作详情',
        INDEX idx_admin_user_id (admin_user_id),
        INDEX idx_target_user_id (target_user_id),
        INDEX idx_story_id (story_id),
        INDEX idx_operation_type (operation_type),
        INDEX idx_operation_time (operation_time),
        FOREIGN KEY (admin_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
        FOREIGN KEY (target_user_id) REFERENCES users(user_id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员操作日志表'
    """
}

# 数据库初始化SQL
INIT_DATABASE_SQL = """
CREATE DATABASE IF NOT EXISTS nexus_unified 
DEFAULT CHARACTER SET utf8mb4 
DEFAULT COLLATE utf8mb4_unicode_ci;
"""

# 默认管理员用户
DEFAULT_ADMIN = {
    'user_id': 'admin_001',
    'username': 'admin',
    'password': 'admin123',  # 实际使用时应该使用哈希
    'is_active': True
}

# 测试用户
TEST_USERS = [
    {
        'user_id': 'user_001',
        'username': 'testuser1',
        'password': 'password123',
        'is_active': True
    },
    {
        'user_id': 'user_002',
        'username': 'testuser2',
        'password': 'password456',
        'is_active': True
    },
    {
        'user_id': 'user_003',
        'username': 'testuser3',
        'password': 'password789',
        'is_active': True
    }
]
