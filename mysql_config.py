#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MySQL数据库配置文件
用于配置数据库连接、创建数据库和表结构
"""

import mysql.connector
from mysql.connector import Error
import logging
from datetime import datetime
import time

class MySQLConfig:
    def __init__(self):
        # 数据库连接配置
        self.config = {
            'host': 'localhost',
            'user': 'root',
            'password': 'zhk050607',
            'charset': 'utf8mb4',
            'collation': 'utf8mb4_unicode_ci'
        }
        
        # 数据库名称
        self.database_name = 'llasm_usage_data'
        
        # 设置日志
        logging.basicConfig(level=logging.INFO)
        self.logger = logging.getLogger(__name__)
        
        # 数据库连接
        self.connection = None
        self.cursor = None
        
    def create_database(self):
        """创建数据库"""
        try:
            # 连接MySQL服务器（不指定数据库）
            connection = mysql.connector.connect(
                host=self.config['host'],
                user=self.config['user'],
                password=self.config['password'],
                charset=self.config['charset']
            )
            
            if connection.is_connected():
                cursor = connection.cursor()
                
                # 创建数据库
                cursor.execute(f"CREATE DATABASE IF NOT EXISTS {self.database_name} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
                self.logger.info(f"✅ 数据库 {self.database_name} 创建成功")
                
                cursor.close()
                connection.close()
                return True
                
        except Error as e:
            self.logger.error(f"❌ 创建数据库失败: {e}")
            return False
    
    def create_tables(self):
        """创建数据表"""
        try:
            # 连接到指定数据库
            connection = mysql.connector.connect(
                host=self.config['host'],
                user=self.config['user'],
                password=self.config['password'],
                database=self.database_name,
                charset=self.config['charset']
            )
            
            if connection.is_connected():
                cursor = connection.cursor()
                
                # 1. 用户表
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id VARCHAR(64) UNIQUE NOT NULL,
                        device_id VARCHAR(64),
                        phone VARCHAR(20),
                        email VARCHAR(100),
                        nickname VARCHAR(50),
                        user_type ENUM('device', 'registered') DEFAULT 'device',
                        is_active BOOLEAN DEFAULT TRUE,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        INDEX idx_user_id (user_id),
                        INDEX idx_device_id (device_id),
                        INDEX idx_phone (phone)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """)
                
                # 2. 用户会话表
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS user_sessions (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id VARCHAR(64) NOT NULL,
                        session_id VARCHAR(64) UNIQUE NOT NULL,
                        start_time DATETIME NOT NULL,
                        end_time DATETIME NULL,
                        duration_seconds INT DEFAULT 0,
                        status ENUM('active', 'completed', 'interrupted') DEFAULT 'active',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                        INDEX idx_user_id (user_id),
                        INDEX idx_session_id (session_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """)
                
                # 3. 交互记录表
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS interactions (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id VARCHAR(64) NOT NULL,
                        session_id VARCHAR(64) NOT NULL,
                        interaction_type ENUM('voice_input', 'text_input', 'ai_response', 'tts_play', 'command') NOT NULL,
                        content TEXT,
                        timestamp DATETIME NOT NULL,
                        response_time_ms INT DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                        FOREIGN KEY (session_id) REFERENCES user_sessions(session_id) ON DELETE CASCADE,
                        INDEX idx_user_id (user_id),
                        INDEX idx_session_id (session_id),
                        INDEX idx_timestamp (timestamp)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """)
                
                # 4. 使用统计表
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS usage_stats (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id VARCHAR(64) NOT NULL,
                        date DATE NOT NULL,
                        total_sessions INT DEFAULT 0,
                        total_interactions INT DEFAULT 0,
                        total_duration_seconds INT DEFAULT 0,
                        avg_session_duration FLOAT DEFAULT 0,
                        avg_interactions_per_session FLOAT DEFAULT 0,
                        voice_inputs INT DEFAULT 0,
                        text_inputs INT DEFAULT 0,
                        ai_responses INT DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                        UNIQUE KEY unique_user_date (user_id, date),
                        INDEX idx_user_id (user_id),
                        INDEX idx_date (date)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """)
                
                # 5. 性能指标表
                cursor.execute("""
                    CREATE TABLE IF NOT EXISTS performance_metrics (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        user_id VARCHAR(64) NOT NULL,
                        session_id VARCHAR(64) NOT NULL,
                        metric_type ENUM('asr_speed', 'tts_speed', 'api_response_time') NOT NULL,
                        value FLOAT NOT NULL,
                        unit VARCHAR(20) NOT NULL,
                        timestamp DATETIME NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                        FOREIGN KEY (session_id) REFERENCES user_sessions(session_id) ON DELETE CASCADE,
                        INDEX idx_user_id (user_id),
                        INDEX idx_session_id (session_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """)
                
                # 创建额外索引（使用MySQL兼容的语法）
                try:
                    cursor.execute("CREATE INDEX idx_sessions_start_time ON user_sessions(start_time)")
                except:
                    pass  # 索引可能已存在
                
                try:
                    cursor.execute("CREATE INDEX idx_interactions_timestamp ON interactions(timestamp)")
                except:
                    pass
                
                try:
                    cursor.execute("CREATE INDEX idx_usage_stats_date ON usage_stats(date)")
                except:
                    pass
                
                try:
                    cursor.execute("CREATE INDEX idx_performance_session ON performance_metrics(session_id)")
                except:
                    pass
                
                try:
                    cursor.execute("CREATE INDEX idx_users_created_at ON users(created_at)")
                except:
                    pass
                
                connection.commit()
                self.logger.info("✅ 数据表创建成功")
                
                cursor.close()
                connection.close()
                return True
                
        except Error as e:
            self.logger.error(f"❌ 创建数据表失败: {e}")
            return False
    
    def test_connection(self):
        """测试数据库连接"""
        try:
            connection = mysql.connector.connect(
                host=self.config['host'],
                user=self.config['user'],
                password=self.config['password'],
                database=self.database_name,
                charset=self.config['charset']
            )
            
            if connection.is_connected():
                self.logger.info("✅ MySQL数据库连接测试成功")
                connection.close()
                return True
            else:
                self.logger.error("❌ MySQL数据库连接失败")
                return False
                
        except Error as e:
            self.logger.error(f"❌ MySQL数据库连接测试失败: {e}")
            return False
    
    def setup_database(self):
        """完整设置数据库"""
        self.logger.info("🚀 开始设置MySQL数据库...")
        
        # 1. 创建数据库
        if not self.create_database():
            return False
        
        # 2. 创建表
        if not self.create_tables():
            return False
        
        # 3. 测试连接
        if not self.test_connection():
            return False
        
        self.logger.info("🎉 MySQL数据库设置完成！")
        return True

# 数据分析工具需要的配置
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': 'zhk050607',
    'database': 'llasm_usage_data',
    'charset': 'utf8mb4',
    'collation': 'utf8mb4_unicode_ci'
}

if __name__ == "__main__":
    # 测试数据库设置
    mysql_config = MySQLConfig()
    mysql_config.setup_database()
