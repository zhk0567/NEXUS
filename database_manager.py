#!/usr/bin/env python3
"""
数据库管理器
"""
import pymysql
import hashlib
import uuid
import json
import logging
from datetime import datetime, timedelta
from typing import Optional, List, Dict, Any
from database_config import DATABASE_CONFIG, CREATE_TABLES_SQL, INIT_DATABASE_SQL, DEFAULT_ADMIN

logger = logging.getLogger(__name__)

class DatabaseManager:
    """数据库管理器"""
    
    def __init__(self):
        self.connection = None
        self.connect()
        self.init_database()
    
    def connect(self):
        """连接到数据库"""
        try:
            self.connection = pymysql.connect(**DATABASE_CONFIG)
            logger.info("✅ 数据库连接成功")
        except Exception as e:
            logger.error(f"❌ 数据库连接失败: {e}")
            raise
    
    def reconnect(self):
        """重新连接数据库"""
        try:
            if self.connection:
                self.connection.close()
            self.connect()
            logger.info("🔄 数据库重新连接成功")
        except Exception as e:
            logger.error(f"❌ 数据库重新连接失败: {e}")
            raise
    
    def init_database(self):
        """初始化数据库和表"""
        try:
            with self.connection.cursor() as cursor:
                # 创建数据库
                cursor.execute(INIT_DATABASE_SQL)
                self.connection.commit()
                
                # 创建表
                for table_name, sql in CREATE_TABLES_SQL.items():
                    cursor.execute(sql)
                    logger.info(f"✅ 创建表 {table_name} 成功")
                
                self.connection.commit()
                logger.info("✅ 数据库初始化完成")
                
                # 创建默认管理员用户
                self.create_default_admin()
                
        except Exception as e:
            logger.error(f"❌ 数据库初始化失败: {e}")
            raise
    
    def create_default_admin(self):
        """创建默认管理员用户"""
        try:
            # 检查是否已存在管理员用户
            if self.get_user_by_username('admin'):
                logger.info("ℹ️ 管理员用户已存在")
                return
            
            # 创建管理员用户
            password_hash = self.hash_password(DEFAULT_ADMIN['password'])
            self.create_user(
                user_id=DEFAULT_ADMIN['user_id'],
                username=DEFAULT_ADMIN['username'],
                password_hash=password_hash,
                is_active=DEFAULT_ADMIN['is_active']
            )
            logger.info("✅ 默认管理员用户创建成功")
            
        except Exception as e:
            logger.error(f"❌ 创建默认管理员用户失败: {e}")
    
    def hash_password(self, password: str) -> str:
        """密码哈希"""
        return hashlib.sha256(password.encode()).hexdigest()
    
    def verify_password(self, password: str, password_hash: str) -> bool:
        """验证密码"""
        return self.hash_password(password) == password_hash
    
    def create_user(self, user_id: str, username: str, password_hash: str, is_active: bool = True) -> bool:
        """创建用户"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                # 检查连接是否有效
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()
                
                with self.connection.cursor() as cursor:
                    sql = """
                    INSERT INTO users (user_id, username, password_hash, is_active)
                    VALUES (%s, %s, %s, %s)
                    """
                    cursor.execute(sql, (user_id, username, password_hash, is_active))
                    self.connection.commit()
                    logger.info(f"✅ 用户创建成功: {username}")
                    return True
                    
            except Exception as e:
                logger.error(f"❌ 创建用户失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    try:
                        self.reconnect()
                        logger.info(f"🔄 重新连接成功，重试创建用户")
                    except Exception as reconnect_error:
                        logger.error(f"❌ 重新连接失败: {reconnect_error}")
                else:
                    logger.error(f"❌ 创建用户最终失败，已重试 {max_retries} 次")
                    return False
    
    def get_user_by_id(self, user_id: str) -> Optional[Dict]:
        """根据用户ID获取用户信息"""
        try:
            with self.connection.cursor(pymysql.cursors.DictCursor) as cursor:
                sql = "SELECT * FROM users WHERE user_id = %s"
                cursor.execute(sql, (user_id,))
                return cursor.fetchone()
        except Exception as e:
            logger.error(f"❌ 获取用户信息失败: {e}")
            return None
    
    def get_user_by_username(self, username: str) -> Optional[Dict]:
        """根据用户名获取用户信息"""
        try:
            with self.connection.cursor(pymysql.cursors.DictCursor) as cursor:
                sql = "SELECT * FROM users WHERE username = %s"
                cursor.execute(sql, (username,))
                return cursor.fetchone()
        except Exception as e:
            logger.error(f"❌ 获取用户信息失败: {e}")
            return None
    
    def authenticate_user(self, username: str, password: str) -> Optional[Dict]:
        """用户认证"""
        try:
            user = self.get_user_by_username(username)
            if user and self.verify_password(password, user['password_hash']):
                # 更新最后登录时间
                self.update_user_login_time(user['user_id'])
                return user
            return None
        except Exception as e:
            logger.error(f"❌ 用户认证失败: {e}")
            return None
    
    def update_user_login_time(self, user_id: str):
        """更新用户登录时间"""
        try:
            with self.connection.cursor() as cursor:
                sql = "UPDATE users SET last_login_at = NOW() WHERE user_id = %s"
                cursor.execute(sql, (user_id,))
                self.connection.commit()
        except Exception as e:
            logger.error(f"❌ 更新登录时间失败: {e}")
    
    def update_user_logout_time(self, user_id: str):
        """更新用户登出时间"""
        try:
            with self.connection.cursor() as cursor:
                sql = "UPDATE users SET last_logout_at = NOW() WHERE user_id = %s"
                cursor.execute(sql, (user_id,))
                self.connection.commit()
        except Exception as e:
            logger.error(f"❌ 更新登出时间失败: {e}")
    
    def create_session(self, user_id: str, device_info: str = None, ip_address: str = None, user_agent: str = None) -> str:
        """创建用户会话"""
        try:
            session_id = str(uuid.uuid4())
            with self.connection.cursor() as cursor:
                sql = """
                INSERT INTO user_sessions (user_id, session_id, device_info, ip_address, user_agent)
                VALUES (%s, %s, %s, %s, %s)
                """
                cursor.execute(sql, (user_id, session_id, device_info, ip_address, user_agent))
                self.connection.commit()
                logger.info(f"✅ 会话创建成功: {session_id}")
                return session_id
        except Exception as e:
            logger.error(f"❌ 创建会话失败: {e}")
            return None
    
    def end_session(self, session_id: str):
        """结束用户会话"""
        try:
            with self.connection.cursor() as cursor:
                sql = """
                UPDATE user_sessions 
                SET logout_time = NOW(), 
                    duration_seconds = TIMESTAMPDIFF(SECOND, login_time, NOW())
                WHERE session_id = %s AND logout_time IS NULL
                """
                cursor.execute(sql, (session_id,))
                self.connection.commit()
                logger.info(f"✅ 会话结束成功: {session_id}")
        except Exception as e:
            logger.error(f"❌ 结束会话失败: {e}")
    
    def log_interaction(self, user_id: str, interaction_type: str, content: str, 
                       response: str = None, session_id: str = None, 
                       duration_seconds: int = None, success: bool = True, 
                       error_message: str = None) -> bool:
        """记录交互"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                # 检查连接是否有效
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()
                
                with self.connection.cursor() as cursor:
                    sql = """
                    INSERT INTO interactions 
                    (user_id, interaction_type, content, response, session_id, 
                     duration_seconds, success, error_message)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                    """
                    cursor.execute(sql, (user_id, interaction_type, content, response, 
                                       session_id, duration_seconds, success, error_message))
                    self.connection.commit()
                    logger.info(f"✅ 交互记录成功: {interaction_type}")
                    return True
                    
            except Exception as e:
                logger.error(f"❌ 记录交互失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    # 尝试重新连接
                    try:
                        self.reconnect()
                        logger.info(f"🔄 重新连接成功，重试记录交互")
                    except Exception as reconnect_error:
                        logger.error(f"❌ 重新连接失败: {reconnect_error}")
                else:
                    logger.error(f"❌ 记录交互最终失败，已重试 {max_retries} 次")
                    return False
    
    def get_user_interactions(self, user_id: str, limit: int = 50, offset: int = 0) -> List[Dict]:
        """获取用户交互记录"""
        try:
            with self.connection.cursor(pymysql.cursors.DictCursor) as cursor:
                sql = """
                SELECT * FROM interactions 
                WHERE user_id = %s 
                ORDER BY timestamp DESC 
                LIMIT %s OFFSET %s
                """
                cursor.execute(sql, (user_id, limit, offset))
                return cursor.fetchall()
        except Exception as e:
            logger.error(f"❌ 获取交互记录失败: {e}")
            return []
    
    def get_interaction_stats(self, user_id: str = None, days: int = 30) -> Dict:
        """获取交互统计"""
        try:
            with self.connection.cursor(pymysql.cursors.DictCursor) as cursor:
                where_clause = "WHERE timestamp >= DATE_SUB(NOW(), INTERVAL %s DAY)"
                params = [days]
                
                if user_id:
                    where_clause += " AND user_id = %s"
                    params.append(user_id)
                
                sql = f"""
                SELECT 
                    interaction_type,
                    COUNT(*) as count,
                    AVG(duration_seconds) as avg_duration,
                    SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) as success_count,
                    SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) as failure_count
                FROM interactions 
                {where_clause}
                GROUP BY interaction_type
                """
                cursor.execute(sql, params)
                stats = cursor.fetchall()
                
                # 计算总体统计
                total_sql = f"""
                SELECT 
                    COUNT(*) as total_interactions,
                    AVG(duration_seconds) as avg_duration,
                    SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) as total_success,
                    SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) as total_failure
                FROM interactions 
                {where_clause}
                """
                cursor.execute(total_sql, params)
                total_stats = cursor.fetchone()
                
                return {
                    'by_type': stats,
                    'total': total_stats
                }
        except Exception as e:
            logger.error(f"❌ 获取交互统计失败: {e}")
            return {}
    
    def log_system_event(self, log_level: str, service_name: str, message: str, 
                        details: Dict = None, user_id: str = None, session_id: str = None):
        """记录系统日志"""
        try:
            with self.connection.cursor() as cursor:
                sql = """
                INSERT INTO system_logs 
                (log_level, service_name, message, details, user_id, session_id)
                VALUES (%s, %s, %s, %s, %s, %s)
                """
                details_json = json.dumps(details) if details else None
                cursor.execute(sql, (log_level, service_name, message, details_json, user_id, session_id))
                self.connection.commit()
        except Exception as e:
            logger.error(f"❌ 记录系统日志失败: {e}")
    
    def get_active_users(self, hours: int = 24) -> List[Dict]:
        """获取活跃用户"""
        try:
            with self.connection.cursor(pymysql.cursors.DictCursor) as cursor:
                sql = """
                SELECT DISTINCT u.user_id, u.username, u.last_login_at, u.last_logout_at,
                       COUNT(i.id) as interaction_count
                FROM users u
                LEFT JOIN interactions i ON u.user_id = i.user_id 
                    AND i.timestamp >= DATE_SUB(NOW(), INTERVAL %s HOUR)
                WHERE u.last_login_at >= DATE_SUB(NOW(), INTERVAL %s HOUR)
                GROUP BY u.user_id, u.username, u.last_login_at, u.last_logout_at
                ORDER BY u.last_login_at DESC
                """
                cursor.execute(sql, (hours, hours))
                return cursor.fetchall()
        except Exception as e:
            logger.error(f"❌ 获取活跃用户失败: {e}")
            return []
    
    def cleanup_old_data(self, days: int = 90):
        """清理旧数据"""
        try:
            with self.connection.cursor() as cursor:
                # 清理旧的交互记录
                cursor.execute("""
                    DELETE FROM interactions 
                    WHERE timestamp < DATE_SUB(NOW(), INTERVAL %s DAY)
                """, (days,))
                
                # 清理旧的系统日志
                cursor.execute("""
                    DELETE FROM system_logs 
                    WHERE timestamp < DATE_SUB(NOW(), INTERVAL %s DAY)
                """, (days,))
                
                # 清理已结束的旧会话
                cursor.execute("""
                    DELETE FROM user_sessions 
                    WHERE logout_time IS NOT NULL 
                    AND logout_time < DATE_SUB(NOW(), INTERVAL %s DAY)
                """, (days,))
                
                self.connection.commit()
                logger.info(f"✅ 清理 {days} 天前的旧数据完成")
        except Exception as e:
            logger.error(f"❌ 清理旧数据失败: {e}")
    
    def close(self):
        """关闭数据库连接"""
        if self.connection:
            self.connection.close()
            logger.info("✅ 数据库连接已关闭")

# 全局数据库管理器实例
db_manager = DatabaseManager()
