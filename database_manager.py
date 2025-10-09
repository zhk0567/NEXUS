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
from database_config import DATABASE_CONFIG, CREATE_TABLES_SQL, INIT_DATABASE_SQL, DEFAULT_ADMIN, TEST_USERS

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
                
                # 创建测试用户
                self.create_test_users()
                
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
            self.create_user(
                user_id=DEFAULT_ADMIN['user_id'],
                username=DEFAULT_ADMIN['username'],
                password=DEFAULT_ADMIN['password'],
                is_active=DEFAULT_ADMIN['is_active']
            )
            logger.info("✅ 默认管理员用户创建成功")
            
        except Exception as e:
            logger.error(f"❌ 创建默认管理员用户失败: {e}")
    
    def create_test_users(self):
        """创建测试用户"""
        try:
            for user_data in TEST_USERS:
                # 检查用户是否已存在
                if self.get_user_by_username(user_data['username']):
                    logger.info(f"ℹ️ 测试用户 {user_data['username']} 已存在")
                    continue
                
                # 创建用户
                self.create_user(
                    user_id=user_data['user_id'],
                    username=user_data['username'],
                    password=user_data['password'],
                    is_active=user_data['is_active']
                )
                logger.info(f"✅ 测试用户创建成功: {user_data['username']}")
            
            logger.info("✅ 所有测试用户创建完成")
            
        except Exception as e:
            logger.error(f"❌ 创建测试用户失败: {e}")
    
    def hash_password(self, password: str) -> str:
        """密码哈希"""
        return hashlib.sha256(password.encode()).hexdigest()
    
    def verify_password(self, password: str, password_hash: str) -> bool:
        """验证密码"""
        return self.hash_password(password) == password_hash
    
    def user_exists(self, user_id: str) -> bool:
        """检查用户是否存在"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                # 检查连接是否有效
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()
                
                with self.connection.cursor() as cursor:
                    sql = "SELECT COUNT(*) FROM users WHERE user_id = %s"
                    cursor.execute(sql, (user_id,))
                    result = cursor.fetchone()
                    return result[0] > 0
                    
            except Exception as e:
                logger.error(f"❌ 检查用户存在失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    self.reconnect()
                    time.sleep(1)
                else:
                    return False
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
                logger.info(f"✅ 会话创建成功: {session_id}")
                return session_id
        except Exception as e:
            logger.error(f"❌ 创建会话失败: {e}")
            return None
    
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
                    logger.info(f"✅ 会话结束成功: {session_id}")
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
                logger.info(f"✅ 清理 {days} 天前的旧数据完成")
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
                    
                    logger.info(f"✅ 查询交互记录成功: 找到 {len(records)} 条记录")
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
    

    def update_reading_progress(self, user_id: str, story_id: str, story_title: str,
                              current_position: int, total_length: int, 
                              device_info: str = None, username: str = None) -> bool:
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
                    is_completed = reading_progress >= 100.0
                    
                    # 检查是否已存在记录
                    check_sql = "SELECT id, start_time FROM reading_progress WHERE user_id = %s AND story_id = %s"
                    cursor.execute(check_sql, (user_id, story_id))
                    existing = cursor.fetchone()
                    
                    if existing:
                        # 更新现有记录
                        update_sql = """
                        UPDATE reading_progress 
                        SET current_position = %s, total_length = %s, reading_progress = %s,
                            is_completed = %s, last_read_time = NOW(),
                            completion_time = CASE WHEN %s = 1 AND completion_time IS NULL THEN NOW() ELSE completion_time END,
                            device_info = %s, username = %s
                        WHERE user_id = %s AND story_id = %s
                        """
                        cursor.execute(update_sql, (
                            current_position, total_length, reading_progress, is_completed,
                            is_completed, device_info, username, user_id, story_id
                        ))
                    else:
                        # 创建新记录
                        insert_sql = """
                        INSERT INTO reading_progress 
                        (user_id, username, story_id, story_title, current_position, total_length, 
                         reading_progress, is_completed, start_time, completion_time, device_info)
                        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, NOW(), 
                                CASE WHEN %s = 1 THEN NOW() ELSE NULL END, %s)
                        """
                        cursor.execute(insert_sql, (
                            user_id, username, story_id, story_title, current_position, total_length,
                            reading_progress, is_completed, is_completed, device_info
                        ))
                    
                    self.connection.commit()
                    logger.info(f"✅ 更新阅读进度成功: {user_id} - {story_id} ({reading_progress:.1f}%)")
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
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()

                with self.connection.cursor() as cursor:
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
                    
                    # 转换为字典列表
                    progress_list = []
                    for row in results:
                        progress = {
                            'story_id': row[0],
                            'story_title': row[1],
                            'current_position': row[2],
                            'total_length': row[3],
                            'reading_progress': float(row[4]) if row[4] else 0.0,
                            'is_completed': bool(row[5]),
                            'start_time': row[6].isoformat() if row[6] else None,
                            'last_read_time': row[7].isoformat() if row[7] else None,
                            'completion_time': row[8].isoformat() if row[8] else None,
                            'reading_duration_seconds': row[9] or 0
                        }
                        progress_list.append(progress)
                    
                    logger.info(f"✅ 获取阅读进度成功: 找到 {len(progress_list)} 条记录")
                    return progress_list

            except Exception as e:
                logger.error(f"❌ 获取阅读进度失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    self.reconnect()
                    time.sleep(1)
                else:
                    return []
        return []

    def log_story_interaction(self, user_id: str, story_id: str, interaction_type: str,
                            interaction_data: Dict[str, Any] = None, device_info: str = None,
                            app_version: str = None) -> bool:
        """记录故事交互"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()

                with self.connection.cursor() as cursor:
                    sql = """
                    INSERT INTO story_interactions 
                    (user_id, story_id, interaction_type, interaction_data, device_info, app_version)
                    VALUES (%s, %s, %s, %s, %s, %s)
                    """
                    interaction_json = json.dumps(interaction_data) if interaction_data else None
                    cursor.execute(sql, (user_id, story_id, interaction_type, interaction_json, device_info, app_version))
                    self.connection.commit()
                    
                    logger.info(f"记录故事交互成功: {user_id} - {story_id} - {interaction_type}")
                    return True

            except Exception as e:
                logger.error(f"❌ 记录故事交互失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    self.reconnect()
                    time.sleep(1)
                else:
                    return False
        return False

    def get_reading_statistics(self, user_id: str, days: int = 30) -> Dict[str, Any]:
        """获取阅读统计"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()

                with self.connection.cursor() as cursor:
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
                        'total_stories': stats_result[0] or 0,
                        'completed_stories': stats_result[1] or 0,
                        'total_reading_time_seconds': stats_result[2] or 0,
                        'average_progress': float(stats_result[3]) if stats_result[3] else 0.0,
                        'last_reading_time': stats_result[4].isoformat() if stats_result[4] else None,
                        'recent_stories': [],
                        'daily_reading': []
                    }
                    
                    # 处理最近阅读的故事
                    for story in recent_stories:
                        statistics['recent_stories'].append({
                            'story_id': story[0],
                            'story_title': story[1],
                            'reading_progress': float(story[2]) if story[2] else 0.0,
                            'is_completed': bool(story[3]),
                            'last_read_time': story[4].isoformat() if story[4] else None
                        })
                    
                    # 处理每日阅读统计
                    for daily in daily_stats:
                        statistics['daily_reading'].append({
                            'date': daily[0].isoformat() if daily[0] else None,
                            'duration_seconds': daily[1] or 0
                        })
                    
                    logger.info(f"✅ 获取阅读统计成功: {user_id}")
                    return statistics

            except Exception as e:
                logger.error(f"❌ 获取阅读统计失败 (尝试 {attempt + 1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    self.reconnect()
                    time.sleep(1)
                else:
                    return {}
        return {}

    def get_all_users_reading_progress(self, limit=100, offset=0):
        """获取所有用户的阅读进度（管理员功能）"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()

                with self.connection.cursor() as cursor:
                    sql = """
                    SELECT rp.*, u.username 
                    FROM reading_progress rp
                    LEFT JOIN users u ON rp.user_id = u.user_id
                    ORDER BY rp.last_read_time DESC
                    LIMIT %s OFFSET %s
                    """
                    cursor.execute(sql, (limit, offset))
                    columns = [desc[0] for desc in cursor.description]
                    results = []
                    for row in cursor.fetchall():
                        result = dict(zip(columns, row))
                        results.append(result)
                    
                    # 获取总数
                    count_sql = "SELECT COUNT(*) FROM reading_progress"
                    cursor.execute(count_sql)
                    total_count = cursor.fetchone()[0]
                    
                    return {
                        'progress_list': results,
                        'total_count': total_count,
                        'limit': limit,
                        'offset': offset
                    }

            except Exception as e:
                logger.error(f"❌ 获取所有用户阅读进度失败 (尝试 {attempt + 1}/{max_retries}): {e}")
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
                    logger.info(f"✅ 管理员操作成功: {admin_user_id} {action} - 用户: {user_id}, 故事: {story_id}")
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
        try:
            with self.connection.cursor() as cursor:
                sql = """
                INSERT INTO admin_operations 
                (admin_user_id, target_user_id, story_id, operation_type, operation_time, details)
                VALUES (%s, %s, %s, %s, NOW(), %s)
                """
                details = f"管理员 {admin_user_id} 对用户 {target_user_id} 的故事 {story_id} 执行了 {operation_type} 操作"
                cursor.execute(sql, (admin_user_id, target_user_id, story_id, operation_type, details))
                self.connection.commit()
        except Exception as e:
            logger.error(f"❌ 记录管理员操作日志失败: {e}")

    def get_user_by_id(self, user_id):
        """根据用户ID获取用户基本信息"""
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()

                with self.connection.cursor() as cursor:
                    sql = """
                    SELECT user_id, username, created_at, last_login_at, is_active
                    FROM users 
                    WHERE user_id = %s
                    """
                    cursor.execute(sql, (user_id,))
                    columns = [desc[0] for desc in cursor.description]
                    row = cursor.fetchone()
                    
                    if row:
                        return dict(zip(columns, row))
                    return None

            except Exception as e:
                logger.error(f"❌ 获取用户信息失败: {e}")
                if attempt < max_retries - 1:
                    self.reconnect()
                    time.sleep(1)
                else:
                    return None
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
            try:
                if not self.connection or not self.connection.open:
                    logger.warning(f"⚠️ 数据库连接已关闭，尝试重新连接 (尝试 {attempt + 1}/{max_retries})")
                    self.reconnect()

                with self.connection.cursor() as cursor:
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
                    self.reconnect()
                    time.sleep(1)
                else:
                    return None
        return None

    def close(self):
        """关闭数据库连接"""
        if self.connection:
            self.connection.close()
            logger.info("✅ 数据库连接已关闭")

# 全局数据库管理器实例
db_manager = DatabaseManager()
