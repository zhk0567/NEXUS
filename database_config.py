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

# 数据库表结构
CREATE_TABLES_SQL = {
    'users': """
    CREATE TABLE IF NOT EXISTS users (
        id INT AUTO_INCREMENT PRIMARY KEY,
        user_id VARCHAR(255) UNIQUE NOT NULL COMMENT '用户唯一标识',
        username VARCHAR(100) NOT NULL COMMENT '用户名',
        password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
        last_login_at TIMESTAMP NULL COMMENT '最后登录时间',
        last_logout_at TIMESTAMP NULL COMMENT '最后登出时间',
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
        interaction_type ENUM('text', 'voice_home', 'voice_call') NOT NULL COMMENT '交互类型',
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
        logout_time TIMESTAMP NULL COMMENT '登出时间',
        duration_seconds INT NULL COMMENT '会话持续时间（秒）',
        device_info TEXT NULL COMMENT '设备信息',
        ip_address VARCHAR(45) NULL COMMENT 'IP地址',
        user_agent TEXT NULL COMMENT '用户代理',
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
        details JSON NULL COMMENT '详细信息',
        timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '时间戳',
        user_id VARCHAR(255) NULL COMMENT '相关用户ID',
        session_id VARCHAR(255) NULL COMMENT '相关会话ID',
        INDEX idx_log_level (log_level),
        INDEX idx_service_name (service_name),
        INDEX idx_timestamp (timestamp),
        INDEX idx_user_id (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统日志表'
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
