#!/usr/bin/env python3
"""
数据库管理器
"""
import pymysql
import hashlib
import uuid
import json
import logging
import time
from datetime import datetime, timedelta
from typing import Optional, List, Dict, Any
from database_config import DATABASE_CONFIG, CREATE_TABLES_SQL, INIT_DATABASE_SQL, DEFAULT_ADMIN

logger = logging.getLogger(__name__)

class DatabaseManager:
    """数据库管理器 - 优化版本"""
    
    def __init__(self):
        self.connection = None
        self.connection_pool = []
        self.max_connections = 5
        # 性能优化：添加查询缓存
        self.query_cache = {}
        self.cache_ttl = 300  # 5分钟缓存
        # 连接重试配置
        self.max_retries = 3
        self.retry_delay = 1
        self.connect()
        self.init_database()
    
    def connect(self):
        """连接到数据库"""
        try:
            # 添加连接参数以提高稳定性
            config = DATABASE_CONFIG.copy()
            config.update({
                'autocommit': True,
                'charset': 'utf8mb4',
                'use_unicode': True,
                'connect_timeout': 10,
                'read_timeout': 30,
                'write_timeout': 30,
                'init_command': "SET SESSION sql_mode='STRICT_TRANS_TABLES'",
                'sql_mode': 'STRICT_TRANS_TABLES',
                'cursorclass': pymysql.cursors.DictCursor
            })
            self.connection = pymysql.connect(**config)
            # 设置连接保持活跃
            self.connection.ping(reconnect=True)
            # 数据库连接成功，不输出日志
        except Exception as e:
            logger.error(f"❌ 数据库连接失败: {e}")
            raise
    
    def reconnect(self):
        """重新连接数据库"""
        try:
            if self.connection:
                try:
                    self.connection.close()
                except:
                    pass  # 忽略关闭时的错误
            self.connect()
            # 数据库重新连接成功，不输出日志
        except Exception as e:
            logger.error(f"❌ 数据库重新连接失败: {e}")
            raise
    
    def _get_fresh_connection(self):
        """获取新的数据库连接"""
        config = DATABASE_CONFIG.copy()
        config.update({
            'autocommit': True,
            'charset': 'utf8mb4',
            'use_unicode': True,
            'connect_timeout': 10,
            'read_timeout': 30,
            'write_timeout': 30,
            'cursorclass': pymysql.cursors.DictCursor
        })
        return pymysql.connect(**config)
    
    def is_connection_healthy(self):
        """检查数据库连接是否健康"""
        try:
            if not self.connection or not self.connection.open:
                return False
            # 执行简单查询测试连接
            self.connection.ping(reconnect=False)
            return True
        except:
            return False
    
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
                    # 表创建成功，不输出日志
                
                self.connection.commit()
                # 数据库初始化完成，不输出日志
                
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
                # 管理员用户已存在，不输出日志
                return
            
            # 创建管理员用户
            self.create_user(
                user_id=DEFAULT_ADMIN['user_id'],
                username=DEFAULT_ADMIN['username'],
                password=DEFAULT_ADMIN['password'],
                is_active=DEFAULT_ADMIN['is_active']
            )
            # 默认管理员用户创建成功，不输出日志
            
        except Exception as e:
            logger.error(f"❌ 创建默认管理员用户失败: {e}")
    
    def hash_password(self, password: str) -> str:
        """密码哈希"""
        return hashlib.sha256(password.encode()).hexdigest()
    
    def verify_password(self, password: str, password_hash: str) -> bool:
        """验证密码"""
        return self.hash_password(password) == password_hash
    
    def execute_with_retry(self, operation, *args, **kwargs):
        """带重试机制的数据库操作"""
        for attempt in range(self.max_retries):
            try:
                # 检查连接是否有效
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{self.max_retries})")
                    self.reconnect()
                else:
                    # 测试连接是否真的可用
                    try:
                        self.connection.ping(reconnect=False)
                    except:
                        logger.warning(f"⚠️ 数据库连接测试失败，尝试重新连接 (尝试 {attempt + 1}/{self.max_retries})")
                        self.reconnect()
                
                return operation(*args, **kwargs)
                
            except (pymysql.OperationalError, pymysql.InterfaceError, pymysql.ProgrammingError, 
                    pymysql.Error, ConnectionError, OSError) as e:
                logger.error(f"❌ 数据库操作失败 (尝试 {attempt + 1}/{self.max_retries}): {e}")
                if attempt < self.max_retries - 1:
                    try:
                        self.reconnect()
                    except:
                        pass  # 重连失败，继续重试
                    time.sleep(self.retry_delay * (attempt + 1))  # 递增延迟
                else:
                    raise
            except Exception as e:
                logger.error(f"❌ 数据库操作失败 (尝试 {attempt + 1}/{self.max_retries}): {e}")
                if attempt < self.max_retries - 1:
                    time.sleep(self.retry_delay * (attempt + 1))
                else:
                    raise
    
    def user_exists(self, user_id: str) -> bool:
        """检查用户是否存在"""
        def _check_user():
            with self.connection.cursor(pymysql.cursors.DictCursor) as cursor:
                # 先尝试按user_id查找，如果没找到再按username查找
                sql = "SELECT COUNT(*) as count FROM users WHERE user_id = %s OR username = %s"
                cursor.execute(sql, (user_id, user_id))
                result = cursor.fetchone()
                return result['count'] > 0 if result else False
        
        try:
            return self.execute_with_retry(_check_user)
        except Exception as e:
            logger.error(f"❌ 检查用户存在失败: {e}")
            return False
    
    def create_user(self, user_id: str, username: str, password: str, email: str = None, is_active: bool = True) -> bool:
        """创建用户"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                # 检查连接是否有效
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()
                
                with self.connection.cursor() as cursor:
                    # 对密码进行哈希处理
                    password_hash = self.hash_password(password)
                    
                    sql = """
                    INSERT INTO users (user_id, username, password_hash, is_active)
                    VALUES (%s, %s, %s, %s)
                    """
                    cursor.execute(sql, (user_id, username, password_hash, is_active))
                    self.connection.commit()
                    # 用户创建成功，不输出日志
                    return True
                    
            except Exception as e:
                logger.error(f"❌ 创建用户失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    try:
                        self.reconnect()
                        # 重新连接成功，不输出日志
                    except Exception as reconnect_error:
                        logger.error(f"❌ 重新连接失败: {reconnect_error}")
                else:
                    logger.error(f"❌ 创建用户最终失败，已重试 {max_retries} 次")
                    return False
    
    
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
    
    def get_all_users(self, limit: int = 1000) -> List[Dict]:
        """获取所有用户"""
        max_retries = 3
        for attempt in range(max_retries):
            connection = None
            try:
                # 使用新的连接避免连接状态问题
                connection = self._get_fresh_connection()
                
                with connection.cursor(pymysql.cursors.DictCursor) as cursor:
                    sql = "SELECT * FROM users ORDER BY created_at DESC LIMIT %s"
                    cursor.execute(sql, (limit,))
                    return cursor.fetchall()
                    
            except Exception as e:
                logger.error(f"❌ 获取所有用户失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    time.sleep(1)
                else:
                    return []
            finally:
                if connection:
                    connection.close()
        return []
    
    def get_all_reading_progress(self, limit: int = 100) -> List[Dict]:
        """获取所有阅读进度"""
        max_retries = 3
        for attempt in range(max_retries):
            connection = None
            try:
                # 使用新的连接避免连接状态问题
                connection = self._get_fresh_connection()
                
                with connection.cursor(pymysql.cursors.DictCursor) as cursor:
                    sql = """
                    SELECT rp.*, u.username 
                    FROM reading_progress rp 
                    LEFT JOIN users u ON rp.user_id = u.user_id 
                    ORDER BY rp.last_read_time DESC 
                    LIMIT %s
                    """
                    cursor.execute(sql, (limit,))
                    return cursor.fetchall()
                    
            except Exception as e:
                logger.error(f"❌ 获取所有阅读进度失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    time.sleep(1)
                else:
                    return []
            finally:
                if connection:
                    connection.close()
        return []
    
    def get_user_details(self, user_id: str) -> Optional[Dict]:
        """获取用户详细信息"""
        def _get_details():
            with self.connection.cursor(pymysql.cursors.DictCursor) as cursor:
                # 获取用户基本信息
                sql = "SELECT * FROM users WHERE user_id = %s"
                cursor.execute(sql, (user_id,))
                user = cursor.fetchone()
                
                if not user:
                    return None
                
                # 获取用户阅读统计
                sql = """
                SELECT 
                    COUNT(*) as total_stories,
                    SUM(CASE WHEN is_completed = 1 THEN 1 ELSE 0 END) as completed_stories,
                    AVG(reading_progress) as avg_progress,
                    MAX(last_read_time) as last_activity
                FROM reading_progress 
                WHERE user_id = %s
                """
                cursor.execute(sql, (user_id,))
                stats = cursor.fetchone()
                
                # 获取最近阅读记录
                sql = """
                SELECT story_title, reading_progress, is_completed, last_read_time
                FROM reading_progress 
                WHERE user_id = %s 
                ORDER BY last_read_time DESC 
                LIMIT 5
                """
                cursor.execute(sql, (user_id,))
                recent_reading = cursor.fetchall()
                
                return {
                    'user': user,
                    'stats': stats,
                    'recent_reading': recent_reading
                }
        return self.execute_with_retry(_get_details)
    
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
    
    # 移除update_user_logout_time函数 - 不再需要登出时间字段
    
    def create_session(self, user_id: str) -> str:
        """创建用户会话"""
        try:
            session_id = str(uuid.uuid4())
            with self.connection.cursor() as cursor:
                sql = """
                INSERT INTO user_sessions (user_id, session_id)
                VALUES (%s, %s)
                """
                cursor.execute(sql, (user_id, session_id))
                self.connection.commit()
                # 会话创建成功，不输出日志
                return session_id
        except Exception as e:
            logger.error(f"❌ 创建会话失败: {e}")
            return None
    
    def get_or_create_active_session(self, user_id: str, reuse_recent: bool = False, timeout_minutes: int = 30) -> str:
        """
        获取或创建session
        
        Args:
            user_id: 用户ID
            reuse_recent: 是否复用最近的session（如果距离上次交互时间在timeout_minutes内）
            timeout_minutes: 如果启用reuse_recent，距离上次交互超过此时间则创建新session
        
        Returns:
            session_id
        """
        # 如果不需要复用，直接创建新session（每个新对话都是新session）
        if not reuse_recent:
            # 创建新对话session，不输出日志
            return self.create_session(user_id)
        
        # 如果需要复用，检查最近的交互时间
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()
                
                with self.connection.cursor(pymysql.cursors.DictCursor) as cursor:
                    # 查找用户最近一次交互的session和时间
                    sql = """
                    SELECT i.session_id, i.timestamp
                    FROM interactions i
                    WHERE i.user_id = %s 
                      AND i.session_id IS NOT NULL 
                      AND i.session_id != ''
                    ORDER BY i.timestamp DESC
                    LIMIT 1
                    """
                    cursor.execute(sql, (user_id,))
                    result = cursor.fetchone()
                    
                    if result and result['session_id']:
                        # 检查距离上次交互的时间
                        last_timestamp = result['timestamp']
                        # 如果timestamp是datetime对象，直接使用；如果是字符串，转换为datetime
                        if isinstance(last_timestamp, str):
                            try:
                                last_timestamp = datetime.fromisoformat(last_timestamp.replace('Z', '+00:00'))
                            except:
                                # 如果解析失败，创建新session
                                logger.warning(f"⚠️ 无法解析timestamp，创建新session")
                                return self.create_session(user_id)
                        
                        time_diff = (datetime.now() - last_timestamp).total_seconds() / 60  # 转换为分钟
                        
                        if time_diff <= timeout_minutes:
                            # 在超时时间内，复用最近的session
                            session_id = result['session_id']
                            # 复用最近的session，不输出日志
                            return session_id
                        else:
                            # 超过超时时间，创建新session
                            # 距离上次交互超过超时时间，创建新session，不输出日志
                            return self.create_session(user_id)
                    
                    # 如果没有找到任何交互记录，创建新session
                    # 未找到历史交互记录，创建新session，不输出日志
                    return self.create_session(user_id)
                    
            except Exception as e:
                logger.error(f"❌ 获取或创建session失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    try:
                        self.reconnect()
                    except:
                        pass
                    time.sleep(self.retry_delay * (attempt + 1))
                else:
                    # 如果获取失败，直接创建一个新session
                    logger.warning(f"⚠️ 获取session失败，创建新session")
                    return self.create_session(user_id)
        
        # 最终失败时创建新session
        return self.create_session(user_id)
    
    def validate_or_create_session(self, user_id: str, session_id: str, timeout_minutes: int = 5) -> str:
        """
        验证session是否有效，如果无效或超时则创建新session
        
        Args:
            user_id: 用户ID
            session_id: 要验证的session_id
            timeout_minutes: 如果session最后一次交互超过此时间，创建新session
        
        Returns:
            有效的session_id（可能是原来的，也可能是新创建的）
        """
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()
                
                with self.connection.cursor(pymysql.cursors.DictCursor) as cursor:
                    # 查找该session的最后一次交互时间
                    sql = """
                    SELECT timestamp
                    FROM interactions 
                    WHERE user_id = %s AND session_id = %s
                    ORDER BY timestamp DESC
                    LIMIT 1
                    """
                    cursor.execute(sql, (user_id, session_id))
                    result = cursor.fetchone()
                    
                    if not result or not result.get('timestamp'):
                        # session不存在或没有任何交互记录，创建新session
                        # session不存在或无交互记录，创建新session，不输出日志
                        return self.create_session(user_id)
                    
                    # 检查距离上次交互的时间
                    last_timestamp = result['timestamp']
                    if isinstance(last_timestamp, str):
                        try:
                            last_timestamp = datetime.fromisoformat(last_timestamp.replace('Z', '+00:00'))
                        except:
                            logger.warning(f"⚠️ 无法解析timestamp，创建新session")
                            return self.create_session(user_id)
                    
                    time_diff = (datetime.now() - last_timestamp).total_seconds() / 60  # 转换为分钟
                    
                    if time_diff > timeout_minutes:
                        # 超过超时时间，创建新session（这是新对话）
                        # session超时，创建新session，不输出日志
                        return self.create_session(user_id)
                    else:
                        # 在超时时间内，使用原session（同一对话的继续）
                        # session有效，继续使用，不输出日志
                        return session_id
                    
            except Exception as e:
                logger.error(f"❌ 验证session失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    try:
                        self.reconnect()
                    except:
                        pass
                    time.sleep(self.retry_delay * (attempt + 1))
                else:
                    # 如果验证失败，创建新session
                    logger.warning(f"⚠️ 验证session失败，创建新session")
                    return self.create_session(user_id)
        
        # 最终失败时创建新session
        return self.create_session(user_id)
    
    def check_session_exists(self, user_id: str, session_id: str) -> bool:
        """检查session是否存在且属于该用户"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()
                
                with self.connection.cursor(pymysql.cursors.DictCursor) as cursor:
                    # 检查session是否存在：在user_sessions表中存在，或者在interactions表中有记录
                    sql = """
                    SELECT COUNT(*) as count
                    FROM (
                        SELECT session_id FROM user_sessions WHERE user_id = %s AND session_id = %s
                        UNION
                        SELECT DISTINCT session_id FROM interactions WHERE user_id = %s AND session_id = %s
                    ) as combined
                    """
                    cursor.execute(sql, (user_id, session_id, user_id, session_id))
                    result = cursor.fetchone()
                    exists = result['count'] > 0 if result else False
                    # 检查session，不输出日志
                    return exists
                    
            except Exception as e:
                logger.error(f"❌ 检查session存在失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    try:
                        self.reconnect()
                    except:
                        pass
                    time.sleep(self.retry_delay * (attempt + 1))
                else:
                    # 如果检查失败，返回False（不存在），这样会创建新session
                    return False
        return False
    
    def end_session(self, session_id: str) -> bool:
        """结束用户会话"""
        try:
            with self.connection.cursor() as cursor:
                sql = """
                DELETE FROM user_sessions 
                WHERE session_id = %s
                """
                cursor.execute(sql, (session_id,))
                self.connection.commit()
                
                if cursor.rowcount > 0:
                    # 会话结束成功，不输出日志
                    return True
                else:
                    logger.warning(f"⚠️ 会话不存在: {session_id}")
                    return False
        except Exception as e:
            logger.error(f"❌ 结束会话失败: {e}")
            return False
    
    def log_interaction(self, user_id: str, interaction_type: str, content: str, 
                       response: str = None, session_id: str = None, 
                       duration_seconds: int = None, success: bool = True, 
                       error_message: str = None, force_new_session: bool = False) -> tuple[bool, str]:
        """记录交互 - 确保每条记录都关联到session"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                # 检查连接是否有效
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()
                
                # 简化逻辑：如果session_id为空，创建新session；如果提供了session_id，直接使用
                if force_new_session or not session_id or session_id.strip() == '':
                    session_id = self.create_session(user_id)
                    if not session_id:
                        logger.error(f"❌ 无法为用户 {user_id} 创建session")
                        return False, None
                    # 创建新session，不输出日志
                else:
                    # 直接使用提供的session_id，不做任何验证或复用检查
                    # 使用提供的session，不输出日志
                    pass
                
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
                    # 交互记录成功，不输出日志
                    return True, session_id
                    
            except Exception as e:
                logger.error(f"❌ 记录交互失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    # 尝试重新连接
                    try:
                        self.reconnect()
                        # 重新连接成功，不输出日志
                    except Exception as reconnect_error:
                        logger.error(f"❌ 重新连接失败: {reconnect_error}")
                else:
                    logger.error(f"❌ 记录交互最终失败，已重试 {max_retries} 次")
                    return False, None
    
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
    
    def get_session_interactions(self, session_id: str, limit: int = 100, offset: int = 0) -> List[Dict]:
        """获取指定session下的所有交互记录"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()
                
                with self.connection.cursor(pymysql.cursors.DictCursor) as cursor:
                    sql = """
                    SELECT * FROM interactions 
                    WHERE session_id = %s 
                    ORDER BY timestamp ASC 
                    LIMIT %s OFFSET %s
                    """
                    cursor.execute(sql, (session_id, limit, offset))
                    results = cursor.fetchall()
                    # 获取session交互记录成功，不输出日志
                    return results
            except Exception as e:
                logger.error(f"❌ 获取session交互记录失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    try:
                        self.reconnect()
                    except:
                        pass
                    time.sleep(self.retry_delay * (attempt + 1))
                else:
                    return []
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
    
    def log_system_event(self, log_level: str, service_name: str, message: str):
        """记录系统日志"""
        try:
            with self.connection.cursor() as cursor:
                sql = """
                INSERT INTO system_logs 
                (log_level, service_name, message)
                VALUES (%s, %s, %s)
                """
                cursor.execute(sql, (log_level, service_name, message))
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
    
    # 移除TTS相关函数 - 不再需要TTS播放计数和时间字段
    
    def get_tts_stats(self, user_id: str = None, days: int = 30) -> Dict:
        """获取TTS播放统计"""
        try:
            with self.connection.cursor(pymysql.cursors.DictCursor) as cursor:
                where_clause = "WHERE timestamp >= DATE_SUB(NOW(), INTERVAL %s DAY)"
                params = [days]
                
                if user_id:
                    where_clause += " AND user_id = %s"
                    params.append(user_id)
                
                sql = f"""
                SELECT 
                    COUNT(*) as total_interactions,
                    SUM(tts_play_count) as total_tts_plays,
                    AVG(tts_play_count) as avg_tts_plays_per_interaction,
                    MAX(tts_play_count) as max_tts_plays,
                    COUNT(CASE WHEN tts_play_count > 0 THEN 1 END) as interactions_with_tts,
                    COUNT(CASE WHEN tts_play_count = 0 THEN 1 END) as interactions_without_tts
                FROM interactions 
                {where_clause}
                """
                cursor.execute(sql, params)
                result = cursor.fetchone()
                
                # 计算TTS播放率
                if result['total_interactions'] > 0:
                    result['tts_play_rate'] = result['interactions_with_tts'] / result['total_interactions']
                else:
                    result['tts_play_rate'] = 0
                
                return result
        except Exception as e:
            logger.error(f"❌ 获取TTS统计失败: {e}")
            return {}
    
    def get_most_played_interactions(self, user_id: str = None, limit: int = 10) -> List[Dict]:
        """获取播放次数最多的交互记录"""
        try:
            with self.connection.cursor(pymysql.cursors.DictCursor) as cursor:
                where_clause = "WHERE tts_play_count > 0"
                params = []
                
                if user_id:
                    where_clause += " AND user_id = %s"
                    params.append(user_id)
                
                params.append(limit)
                
                sql = f"""
                SELECT 
                    id, user_id, interaction_type, content, response,
                    tts_play_count, last_tts_play_time, timestamp
                FROM interactions 
                {where_clause}
                ORDER BY tts_play_count DESC, last_tts_play_time DESC
                LIMIT %s
                """
                cursor.execute(sql, params)
                return cursor.fetchall()
        except Exception as e:
            logger.error(f"❌ 获取最常播放交互失败: {e}")
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
                # 清理旧数据完成，不输出日志
        except Exception as e:
            logger.error(f"❌ 清理旧数据失败: {e}")
    
    def query_interactions(self, interaction_type: str = None, user_id: str = None, limit: int = 10):
        """查询交互记录"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()

                with self.connection.cursor() as cursor:
                    # 构建查询条件
                    conditions = []
                    params = []
                    
                    if interaction_type:
                        conditions.append("interaction_type = %s")
                        params.append(interaction_type)
                    
                    if user_id:
                        conditions.append("user_id = %s")
                        params.append(user_id)
                    
                    where_clause = ""
                    if conditions:
                        where_clause = "WHERE " + " AND ".join(conditions)
                    
                    sql = f"""
                    SELECT id, user_id, interaction_type, content, response, 
                           timestamp, session_id, duration_seconds, success, error_message
                    FROM interactions 
                    {where_clause}
                    ORDER BY timestamp DESC 
                    LIMIT %s
                    """
                    params.append(limit)
                    
                    cursor.execute(sql, params)
                    results = cursor.fetchall()
                    
                    # 转换为字典列表
                    records = []
                    for row in results:
                        record = {
                            'id': row[0],
                            'user_id': row[1],
                            'interaction_type': row[2],
                            'content': row[3],
                            'response': row[4],
                            'timestamp': row[5].isoformat() if row[5] else None,
                            'session_id': row[6],
                            'duration_seconds': row[7],
                            'success': bool(row[8]),
                            'error_message': row[9]
                        }
                        records.append(record)
                    
                    # 查询交互记录成功，不输出日志
                    return records

            except Exception as e:
                logger.error(f"❌ 查询交互记录失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    self.reconnect()
                    time.sleep(1)
                else:
                    return []
        return []

    # ==================== 故事控制相关功能 ====================
    
    def complete_reading(self, user_id: str, story_id: str, story_title: str, 
                        completion_mode: str, device_info: str = None, username: str = None) -> bool:
        """标记故事为已完成，并记录完成方式"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()

                with self.connection.cursor() as cursor:
                    # 验证完成方式
                    valid_modes = ['text', 'audio', 'mixed']
                    if completion_mode not in valid_modes:
                        logger.error(f"❌ 无效的完成方式: {completion_mode}")
                        return False
                    
                    # 检查是否已存在记录
                    check_sql = "SELECT id, is_completed FROM reading_progress WHERE user_id = %s AND story_id = %s"
                    cursor.execute(check_sql, (user_id, story_id))
                    existing = cursor.fetchone()
                    
                    if existing:
                        # 更新现有记录（包括story_title，确保标题信息是最新的）
                        update_sql = """
                        UPDATE reading_progress 
                        SET story_title = %s, is_completed = TRUE, completion_time = NOW(), 
                            completion_mode = %s, last_read_time = NOW(),
                            device_info = %s, username = %s
                        WHERE user_id = %s AND story_id = %s
                        """
                        cursor.execute(update_sql, (
                            story_title, completion_mode, device_info, username, user_id, story_id
                        ))
                    else:
                        # 创建新记录
                        insert_sql = """
                        INSERT INTO reading_progress 
                        (user_id, username, story_id, story_title, current_position, total_length, 
                         reading_progress, is_completed, completion_mode, start_time, completion_time, device_info)
                        VALUES (%s, %s, %s, %s, 0, 0, 100.0, TRUE, %s, NOW(), NOW(), %s)
                        """
                        cursor.execute(insert_sql, (
                            user_id, username, story_id, story_title, completion_mode, device_info
                        ))
                    
                    self.connection.commit()
                    # 标记故事完成成功，不输出日志
                    return True

            except Exception as e:
                logger.error(f"❌ 标记故事完成失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    self.reconnect()
                else:
                    return False
        return False

    def update_reading_progress(self, user_id: str, story_id: str, story_title: str,
                              current_position: int, total_length: int, 
                              device_info: str = None, username: str = None, 
                              completion_mode: str = None) -> bool:
        """更新阅读进度"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()

                with self.connection.cursor() as cursor:
                    # 计算阅读进度百分比
                    reading_progress = (current_position / total_length * 100) if total_length > 0 else 0
                    # 不自动设置完成状态，只有通过完成阅读API才能设置
                    is_completed = False
                    
                    # 检查是否已存在记录
                    check_sql = "SELECT id, start_time FROM reading_progress WHERE user_id = %s AND story_id = %s"
                    cursor.execute(check_sql, (user_id, story_id))
                    existing = cursor.fetchone()
                    
                    if existing:
                        # 更新现有记录（包括story_title，确保标题信息是最新的）
                        update_sql = """
                        UPDATE reading_progress 
                        SET story_title = %s, current_position = %s, total_length = %s, reading_progress = %s,
                            is_completed = %s, last_read_time = NOW(),
                            completion_time = CASE WHEN %s = 1 THEN NOW() ELSE completion_time END,
                            completion_mode = CASE WHEN %s = 1 AND %s IS NOT NULL THEN %s ELSE completion_mode END,
                            device_info = %s, username = %s
                        WHERE user_id = %s AND story_id = %s
                        """
                        cursor.execute(update_sql, (
                            story_title, current_position, total_length, reading_progress, is_completed,
                            is_completed, is_completed, completion_mode, completion_mode,
                            device_info, username, user_id, story_id
                        ))
                    else:
                        # 创建新记录
                        insert_sql = """
                        INSERT INTO reading_progress 
                        (user_id, username, story_id, story_title, current_position, total_length, 
                         reading_progress, is_completed, completion_mode, start_time, completion_time, device_info)
                        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, NOW(), 
                                CASE WHEN %s = 1 THEN NOW() ELSE NULL END, %s)
                        """
                        cursor.execute(insert_sql, (
                            user_id, username, story_id, story_title, current_position, total_length,
                            reading_progress, is_completed, completion_mode, is_completed, device_info
                        ))
                    
                    self.connection.commit()
                    # 更新阅读进度成功，不输出日志
                    return True

            except Exception as e:
                logger.error(f"❌ 更新阅读进度失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    self.reconnect()
                    time.sleep(1)
                else:
                    return False
        return False

    def get_reading_progress(self, user_id: str, story_id: str = None) -> List[Dict[str, Any]]:
        """获取阅读进度"""
        max_retries = 3
        for attempt in range(max_retries):
            connection = None
            try:
                # 使用新的连接避免连接状态问题
                connection = self._get_fresh_connection()

                with connection.cursor(pymysql.cursors.DictCursor) as cursor:
                    if story_id:
                        # 获取特定故事的进度
                        sql = """
                        SELECT story_id, story_title, current_position, total_length, 
                               reading_progress, is_completed, start_time, last_read_time, 
                               completion_time, reading_duration_seconds
                        FROM reading_progress 
                        WHERE user_id = %s AND story_id = %s
                        ORDER BY last_read_time DESC
                        """
                        cursor.execute(sql, (user_id, story_id))
                    else:
                        # 获取用户所有故事的进度
                        sql = """
                        SELECT story_id, story_title, current_position, total_length, 
                               reading_progress, is_completed, start_time, last_read_time, 
                               completion_time, reading_duration_seconds
                        FROM reading_progress 
                        WHERE user_id = %s
                        ORDER BY last_read_time DESC
                        """
                        cursor.execute(sql, (user_id,))
                    
                    results = cursor.fetchall()
                    
                    # 处理结果
                    progress_list = []
                    for row in results:
                        progress = {
                            'story_id': row['story_id'],
                            'story_title': row['story_title'],
                            'current_position': row['current_position'],
                            'total_length': row['total_length'],
                            'reading_progress': float(row['reading_progress']) if row['reading_progress'] else 0.0,
                            'is_completed': bool(row['is_completed']),
                            'start_time': row['start_time'].isoformat() if row['start_time'] else None,
                            'last_read_time': row['last_read_time'].isoformat() if row['last_read_time'] else None,
                            'completion_time': row['completion_time'].isoformat() if row['completion_time'] else None,
                            'reading_duration_seconds': row['reading_duration_seconds'] or 0
                        }
                        progress_list.append(progress)
                    
                    # 获取阅读进度成功，不输出日志
                    return progress_list

            except Exception as e:
                logger.error(f"❌ 获取阅读进度失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    time.sleep(1)
                else:
                    return []
            finally:
                if connection:
                    connection.close()
        return []

    def log_story_interaction(self, user_id: str, story_id: str, interaction_type: str,
                            interaction_data: Dict[str, Any] = None, device_info: str = None,
                            app_version: str = None) -> bool:
        """记录故事交互"""
        max_retries = 3
        for attempt in range(max_retries):
            connection = None
            try:
                # 使用新的连接避免连接状态问题
                connection = self._get_fresh_connection()
                
                # 获取用户名
                username = None
                try:
                    user_info = self.get_user_by_id(user_id)
                    username = user_info.get('username') if user_info else None
                except:
                    pass  # 如果获取用户名失败，继续使用None
                
                with connection.cursor() as cursor:
                    sql = """
                    INSERT INTO story_interactions 
                    (user_id, username, story_id, interaction_type, interaction_data, device_info, app_version)
                    VALUES (%s, %s, %s, %s, %s, %s, %s)
                    """
                    interaction_json = json.dumps(interaction_data) if interaction_data else None
                    cursor.execute(sql, (user_id, username, story_id, interaction_type, interaction_json, device_info, app_version))
                    connection.commit()
                    
                    logger.info(f"记录故事交互成功: {user_id} ({username}) - {story_id} - {interaction_type}")
                    return True

            except Exception as e:
                logger.error(f"❌ 记录故事交互失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    time.sleep(1)
                else:
                    return False
            finally:
                if connection:
                    connection.close()
        return False

    def get_reading_statistics(self, user_id: str, days: int = 30) -> Dict[str, Any]:
        """获取阅读统计"""
        max_retries = 3
        for attempt in range(max_retries):
            connection = None
            try:
                # 使用新的连接避免连接状态问题
                connection = self._get_fresh_connection()

                with connection.cursor(pymysql.cursors.DictCursor) as cursor:
                    # 获取基本统计
                    stats_sql = """
                    SELECT 
                        COUNT(DISTINCT story_id) as total_stories,
                        COUNT(CASE WHEN is_completed = TRUE THEN 1 END) as completed_stories,
                        SUM(reading_duration_seconds) as total_reading_time,
                        AVG(reading_progress) as avg_progress,
                        MAX(last_read_time) as last_reading_time
                    FROM reading_progress 
                    WHERE user_id = %s AND last_read_time >= DATE_SUB(NOW(), INTERVAL %s DAY)
                    """
                    cursor.execute(stats_sql, (user_id, days))
                    stats_result = cursor.fetchone()
                    
                    # 获取最近阅读的故事
                    recent_sql = """
                    SELECT story_id, story_title, reading_progress, is_completed, last_read_time
                    FROM reading_progress 
                    WHERE user_id = %s
                    ORDER BY last_read_time DESC
                    LIMIT 10
                    """
                    cursor.execute(recent_sql, (user_id,))
                    recent_stories = cursor.fetchall()
                    
                    # 获取每日阅读时长
                    daily_sql = """
                    SELECT DATE(last_read_time) as reading_date, 
                           SUM(reading_duration_seconds) as daily_duration
                    FROM reading_progress 
                    WHERE user_id = %s AND last_read_time >= DATE_SUB(NOW(), INTERVAL %s DAY)
                    GROUP BY DATE(last_read_time)
                    ORDER BY reading_date DESC
                    """
                    cursor.execute(daily_sql, (user_id, days))
                    daily_stats = cursor.fetchall()
                    
                    # 构建统计结果
                    statistics = {
                        'total_stories': stats_result['total_stories'] or 0,
                        'completed_stories': stats_result['completed_stories'] or 0,
                        'total_reading_time_seconds': stats_result['total_reading_time'] or 0,
                        'average_progress': float(stats_result['avg_progress']) if stats_result['avg_progress'] else 0.0,
                        'last_reading_time': stats_result['last_reading_time'].isoformat() if stats_result['last_reading_time'] else None,
                        'recent_stories': [],
                        'daily_reading': []
                    }
                    
                    # 处理最近阅读的故事
                    for story in recent_stories:
                        statistics['recent_stories'].append({
                            'story_id': story['story_id'],
                            'story_title': story['story_title'],
                            'reading_progress': float(story['reading_progress']) if story['reading_progress'] else 0.0,
                            'is_completed': bool(story['is_completed']),
                            'last_read_time': story['last_read_time'].isoformat() if story['last_read_time'] else None
                        })
                    
                    # 处理每日阅读统计
                    for daily in daily_stats:
                        statistics['daily_reading'].append({
                            'date': daily['reading_date'].isoformat() if daily['reading_date'] else None,
                            'duration_seconds': daily['daily_duration'] or 0
                        })
                    
                    # 获取阅读统计成功，不输出日志
                    return statistics

            except Exception as e:
                logger.error(f"❌ 获取阅读统计失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    time.sleep(1)
                else:
                    return {}
            finally:
                if connection:
                    connection.close()
        return {}

    def get_all_users_reading_progress(self, limit=100, offset=0):
        """获取所有用户的阅读进度（管理员功能）"""
        max_retries = 3
        for attempt in range(max_retries):
            connection = None
            try:
                # 使用新的连接避免连接状态问题
                connection = self._get_fresh_connection()

                with connection.cursor(pymysql.cursors.DictCursor) as cursor:
                    sql = """
                    SELECT rp.*, u.username 
                    FROM reading_progress rp
                    LEFT JOIN users u ON rp.user_id = u.user_id
                    WHERE rp.id IN (
                        SELECT MAX(id) 
                        FROM reading_progress 
                        GROUP BY user_id, story_id
                    )
                    ORDER BY rp.last_read_time DESC
                    LIMIT %s OFFSET %s
                    """
                    cursor.execute(sql, (limit, offset))
                    results = cursor.fetchall()
                    
                    # 获取总数（只统计唯一的用户-故事组合）
                    count_sql = """
                    SELECT COUNT(*) as count FROM (
                        SELECT MAX(id) 
                        FROM reading_progress 
                        GROUP BY user_id, story_id
                    ) as unique_records
                    """
                    cursor.execute(count_sql)
                    total_count = cursor.fetchone()['count']
                    
                    return {
                        'progress_list': results,
                        'total_count': total_count,
                        'limit': limit,
                        'offset': offset
                    }

            except Exception as e:
                logger.error(f"❌ 获取所有用户阅读进度失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    time.sleep(1)
                else:
                    return None
            finally:
                if connection:
                    connection.close()
        return None

    def delete_reading_record(self, record_id: int) -> bool:
        """删除阅读记录"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()

                with self.connection.cursor() as cursor:
                    # 删除指定的阅读记录
                    delete_sql = "DELETE FROM reading_progress WHERE id = %s"
                    cursor.execute(delete_sql, (record_id,))
                    
                    if cursor.rowcount > 0:
                        self.connection.commit()
                        # 删除阅读记录成功，不输出日志
                        return True
                    else:
                        logger.warning(f"⚠️ 未找到要删除的记录: ID={record_id}")
                        return False

            except Exception as e:
                logger.error(f"❌ 删除阅读记录失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    self.reconnect()
                    time.sleep(1)
                else:
                    return False
        return False

    def reset_user_password(self, user_id: str, new_password: str) -> bool:
        """重置用户密码"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()

                with self.connection.cursor() as cursor:
                    # 生成密码哈希
                    import hashlib
                    password_hash = hashlib.sha256(new_password.encode()).hexdigest()
                    
                    # 更新用户密码（同时保存原始密码和哈希值）
                    update_sql = "UPDATE users SET password_hash = %s, original_password = %s WHERE user_id = %s"
                    cursor.execute(update_sql, (password_hash, new_password, user_id))
                    
                    if cursor.rowcount > 0:
                        self.connection.commit()
                        # 重置用户密码成功，不输出日志
                        return True
                    else:
                        logger.warning(f"⚠️ 未找到要重置密码的用户: {user_id}")
                        return False

            except Exception as e:
                logger.error(f"❌ 重置用户密码失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    self.reconnect()
                    time.sleep(1)
                else:
                    return False
        return False

    def get_user_password_info(self, user_id: str) -> dict:
        """获取用户密码信息"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()

                with self.connection.cursor(pymysql.cursors.DictCursor) as cursor:
                    # 获取用户密码信息
                    sql = """
                    SELECT user_id, username, password_hash, original_password, created_at, last_login_at
                    FROM users 
                    WHERE user_id = %s
                    """
                    cursor.execute(sql, (user_id,))
                    result = cursor.fetchone()
                    
                    if result:
                        # 返回密码信息，包括原始密码（用于管理员查看）
                        return {
                            'user_id': result['user_id'],
                            'username': result['username'],
                            'has_password': bool(result['password_hash']),
                            'password': result.get('original_password', '未设置'),
                            'password_set_date': result['created_at'],
                            'last_login': result['last_login_at']
                        }
                    else:
                        return None

            except Exception as e:
                logger.error(f"❌ 获取用户密码信息失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    self.reconnect()
                    time.sleep(1)
                else:
                    return None
        return None

    def admin_update_reading_completion(self, user_id, story_id, is_completed, admin_user_id):
        """管理员更新用户阅读完成状态"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()

                with self.connection.cursor() as cursor:
                    # 检查记录是否存在
                    check_sql = "SELECT id, is_completed FROM reading_progress WHERE user_id = %s AND story_id = %s"
                    cursor.execute(check_sql, (user_id, story_id))
                    existing = cursor.fetchone()
                    
                    if not existing:
                        return False, "阅读记录不存在"
                    
                    record_id, current_status = existing
                    
                    # 更新完成状态
                    if is_completed:
                        update_sql = """
                        UPDATE reading_progress 
                        SET is_completed = 1, completion_time = NOW(), last_read_time = NOW()
                        WHERE user_id = %s AND story_id = %s
                        """
                        cursor.execute(update_sql, (user_id, story_id))
                        
                        # 记录管理员操作
                        self.log_admin_operation(admin_user_id, user_id, story_id, 'mark_completed')
                    else:
                        update_sql = """
                        UPDATE reading_progress 
                        SET is_completed = 0, completion_time = NULL, last_read_time = NOW()
                        WHERE user_id = %s AND story_id = %s
                        """
                        cursor.execute(update_sql, (user_id, story_id))
                        
                        # 记录管理员操作
                        self.log_admin_operation(admin_user_id, user_id, story_id, 'unmark_completed')
                    
                    self.connection.commit()
                    
                    action = "标记为已完成" if is_completed else "取消完成状态"
                    # 管理员操作成功，不输出日志
                    return True, f"成功{action}"

            except Exception as e:
                logger.error(f"❌ 管理员更新阅读完成状态失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    self.reconnect()
                    time.sleep(1)
                else:
                    return False, str(e)
        return False, "操作失败"

    def log_admin_operation(self, admin_user_id, target_user_id, story_id, operation_type):
        """记录管理员操作日志"""
        connection = None
        try:
            # 使用新的连接避免连接状态问题
            connection = self._get_fresh_connection()
            
            with connection.cursor() as cursor:
                # 处理NULL值
                target_user_id = target_user_id or 'system'
                story_id = story_id or 'N/A'
                
                sql = """
                INSERT INTO admin_operations 
                (admin_user_id, target_user_id, story_id, operation_type, operation_time, details)
                VALUES (%s, %s, %s, %s, NOW(), %s)
                """
                details = f"管理员 {admin_user_id} 对用户 {target_user_id} 的故事 {story_id} 执行了 {operation_type} 操作"
                cursor.execute(sql, (admin_user_id, target_user_id, story_id, operation_type, details))
                connection.commit()
        except Exception as e:
            logger.error(f"❌ 记录管理员操作日志失败: {e}")
        finally:
            if connection:
                connection.close()

    def get_user_by_id(self, user_id):
        """根据用户ID获取用户基本信息"""
        max_retries = 3
        for attempt in range(max_retries):
            connection = None
            try:
                # 使用新的连接避免连接状态问题
                connection = self._get_fresh_connection()

                with connection.cursor(pymysql.cursors.DictCursor) as cursor:
                    sql = """
                    SELECT user_id, username, created_at, last_login_at, is_active
                    FROM users 
                    WHERE user_id = %s
                    """
                    cursor.execute(sql, (user_id,))
                    return cursor.fetchone()

            except Exception as e:
                logger.error(f"❌ 获取用户信息失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    time.sleep(1)
                else:
                    return None
            finally:
                if connection:
                    connection.close()
        return None

    def get_user_reading_progress_details(self, user_id):
        """获取用户阅读进度详情"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()

                with self.connection.cursor() as cursor:
                    sql = """
                    SELECT rp.story_id, rp.story_title, rp.current_position, rp.total_length,
                           rp.reading_progress, rp.is_completed, rp.start_time, rp.last_read_time,
                           rp.completion_time, rp.reading_duration_seconds, rp.device_info
                    FROM reading_progress rp
                    WHERE rp.user_id = %s
                    ORDER BY rp.last_read_time DESC
                    """
                    cursor.execute(sql, (user_id,))
                    columns = [desc[0] for desc in cursor.description]
                    progress_list = []
                    
                    for row in cursor.fetchall():
                        progress = dict(zip(columns, row))
                        progress_list.append(progress)
                    
                    return progress_list

            except Exception as e:
                logger.error(f"❌ 获取用户阅读进度详情失败: {e}")
                if attempt < max_retries - 1:
                    self.reconnect()
                    time.sleep(1)
                else:
                    return []
        return []

    def get_user_reading_summary(self, user_id):
        """获取用户阅读摘要（管理员查看）"""
        max_retries = 3
        for attempt in range(max_retries):
            connection = None
            try:
                # 使用新的连接避免连接状态问题
                connection = self._get_fresh_connection()
                
                with connection.cursor() as cursor:
                    # 获取用户基本信息
                    user_sql = "SELECT username, created_at, last_login_at FROM users WHERE user_id = %s"
                    cursor.execute(user_sql, (user_id,))
                    user_info = cursor.fetchone()
                    
                    if not user_info:
                        return None
                    
                    # 获取阅读统计
                    stats_sql = """
                    SELECT 
                        COUNT(*) as total_stories,
                        SUM(CASE WHEN is_completed = 1 THEN 1 ELSE 0 END) as completed_stories,
                        AVG(reading_progress) as avg_progress,
                        MAX(last_read_time) as last_reading_time
                    FROM reading_progress 
                    WHERE user_id = %s
                    """
                    cursor.execute(stats_sql, (user_id,))
                    stats = cursor.fetchone()
                    
                    return {
                        'user_id': user_id,
                        'username': user_info[0],
                        'created_at': user_info[1],
                        'last_login_at': user_info[2],
                        'total_stories': stats[0] or 0,
                        'completed_stories': stats[1] or 0,
                        'avg_progress': float(stats[2]) if stats[2] else 0.0,
                        'last_reading_time': stats[3]
                    }

            except Exception as e:
                logger.error(f"❌ 获取用户阅读摘要失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    time.sleep(1)
                else:
                    return None
            finally:
                if connection:
                    connection.close()
        return None

    # ==================== 故事管理方法 ====================
    
    def create_story(self, story_id: str, title: str, content: str, audio_file_path: str = None, 
                    audio_duration_seconds: int = None, created_by: str = None) -> bool:
        """创建新故事"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()

                with self.connection.cursor() as cursor:
                    sql = """
                    INSERT INTO stories (story_id, title, content, audio_file_path, 
                                       audio_duration_seconds, created_by, updated_by)
                    VALUES (%s, %s, %s, %s, %s, %s, %s)
                    """
                    cursor.execute(sql, (story_id, title, content, audio_file_path, 
                                       audio_duration_seconds, created_by, created_by))
                    # 故事创建成功，不输出日志
                    return True

            except Exception as e:
                logger.error(f"❌ 创建故事失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    self.reconnect()
                    time.sleep(1)
                else:
                    return False
        return False

    def update_story(self, story_id: str, title: str = None, content: str = None, 
                    audio_file_path: str = None, audio_duration_seconds: int = None, 
                    is_active: bool = None, updated_by: str = None) -> bool:
        """更新故事"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()

                with self.connection.cursor() as cursor:
                    # 构建动态更新SQL
                    update_fields = []
                    params = []
                    
                    if title is not None:
                        update_fields.append("title = %s")
                        params.append(title)
                    if content is not None:
                        update_fields.append("content = %s")
                        params.append(content)
                    if audio_file_path is not None:
                        update_fields.append("audio_file_path = %s")
                        params.append(audio_file_path)
                    if audio_duration_seconds is not None:
                        update_fields.append("audio_duration_seconds = %s")
                        params.append(audio_duration_seconds)
                    if is_active is not None:
                        update_fields.append("is_active = %s")
                        params.append(is_active)
                    if updated_by is not None:
                        update_fields.append("updated_by = %s")
                        params.append(updated_by)
                    
                    update_fields.append("version = version + 1")
                    params.append(story_id)
                    
                    sql = f"UPDATE stories SET {', '.join(update_fields)} WHERE story_id = %s"
                    cursor.execute(sql, params)
                    
                    if cursor.rowcount > 0:
                        # 故事更新成功，不输出日志
                        return True
                    else:
                        logger.warning(f"⚠️ 故事不存在: {story_id}")
                        return False

            except Exception as e:
                logger.error(f"❌ 更新故事失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    self.reconnect()
                    time.sleep(1)
                else:
                    return False
        return False

    def get_story(self, story_id: str) -> Dict[str, Any]:
        """获取单个故事"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()

                with self.connection.cursor() as cursor:
                    sql = """
                    SELECT story_id, title, content, audio_file_path, audio_duration_seconds,
                           is_active, created_at, updated_at, created_by, updated_by, version
                    FROM stories WHERE story_id = %s
                    """
                    cursor.execute(sql, (story_id,))
                    result = cursor.fetchone()
                    
                    if result:
                        return {
                            'story_id': result[0],
                            'title': result[1],
                            'content': result[2],
                            'audio_file_path': result[3],
                            'audio_duration_seconds': result[4],
                            'is_active': bool(result[5]),
                            'created_at': result[6],
                            'updated_at': result[7],
                            'created_by': result[8],
                            'updated_by': result[9],
                            'version': result[10]
                        }
                    return None

            except Exception as e:
                logger.error(f"❌ 获取故事失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    self.reconnect()
                    time.sleep(1)
                else:
                    return None
        return None

    def get_all_stories(self, include_inactive: bool = False) -> List[Dict[str, Any]]:
        """获取所有故事"""
        max_retries = 3
        for attempt in range(max_retries):
            connection = None
            try:
                # 每次都创建新连接，避免连接状态问题
                connection = self._get_fresh_connection()
                
                with connection.cursor() as cursor:
                    if include_inactive:
                        sql = """
                        SELECT story_id, title, content, audio_file_path, audio_duration_seconds,
                               is_active, created_at, updated_at, created_by, updated_by, version
                        FROM stories ORDER BY updated_at DESC
                        """
                        cursor.execute(sql)
                    else:
                        sql = """
                        SELECT story_id, title, content, audio_file_path, audio_duration_seconds,
                               is_active, created_at, updated_at, created_by, updated_by, version
                        FROM stories WHERE is_active = 1 ORDER BY updated_at DESC
                        """
                        cursor.execute(sql)
                    
                    results = cursor.fetchall()
                    stories = []
                    for result in results:
                        stories.append({
                            'story_id': result['story_id'],
                            'title': result['title'],
                            'content': result['content'],
                            'audio_file_path': result['audio_file_path'],
                            'audio_duration_seconds': result['audio_duration_seconds'],
                            'is_active': bool(result['is_active']),
                            'created_at': result['created_at'],
                            'updated_at': result['updated_at'],
                            'created_by': result['created_by'],
                            'updated_by': result['updated_by'],
                            'version': result['version']
                        })
                    return stories

            except Exception as e:
                logger.error(f"❌ 获取故事列表失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    time.sleep(1)
                else:
                    return []
            finally:
                if connection:
                    connection.close()
        return []

    def delete_story(self, story_id: str) -> bool:
        """删除故事（软删除，设置为不活跃）"""
        return self.update_story(story_id, is_active=False)

    def activate_story(self, story_id: str) -> bool:
        """激活故事"""
        return self.update_story(story_id, is_active=True)

    def close(self):
        """关闭数据库连接"""
        if self.connection:
            self.connection.close()
            # 数据库连接已关闭，不输出日志

# 全局数据库管理器实例
db_manager = DatabaseManager()
