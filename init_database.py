#!/usr/bin/env python3
"""
数据库初始化脚本
"""
import pymysql
import logging
from database_config import DATABASE_CONFIG, INIT_DATABASE_SQL, CREATE_TABLES_SQL

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def init_database():
    """初始化数据库"""
    try:
        # 连接到MySQL服务器（不指定数据库）
        config = DATABASE_CONFIG.copy()
        del config['database']  # 移除数据库名，先连接到MySQL服务器
        
        connection = pymysql.connect(**config)
        logger.info("✅ 连接到MySQL服务器成功")
        
        with connection.cursor() as cursor:
            # 创建数据库
            cursor.execute(INIT_DATABASE_SQL)
            logger.info("✅ 创建数据库成功")
            
            # 选择数据库
            cursor.execute(f"USE {DATABASE_CONFIG['database']}")
            logger.info("✅ 选择数据库成功")
            
            # 创建表
            for table_name, sql in CREATE_TABLES_SQL.items():
                cursor.execute(sql)
                logger.info(f"✅ 创建表 {table_name} 成功")
            
            connection.commit()
            logger.info("✅ 数据库初始化完成")
            
    except Exception as e:
        logger.error(f"❌ 数据库初始化失败: {e}")
        raise
    finally:
        if 'connection' in locals():
            connection.close()

def test_connection():
    """测试数据库连接"""
    try:
        # 先测试MySQL服务器连接（不指定数据库）
        config = DATABASE_CONFIG.copy()
        del config['database']
        
        connection = pymysql.connect(**config)
        logger.info("✅ MySQL服务器连接测试成功")
        
        with connection.cursor() as cursor:
            cursor.execute("SELECT VERSION()")
            version = cursor.fetchone()
            logger.info(f"✅ MySQL版本: {version[0]}")
            
        connection.close()
        return True
        
    except Exception as e:
        logger.error(f"❌ MySQL服务器连接测试失败: {e}")
        return False

def test_database_connection():
    """测试数据库连接（指定数据库）"""
    try:
        connection = pymysql.connect(**DATABASE_CONFIG)
        logger.info("✅ 数据库连接测试成功")
        
        with connection.cursor() as cursor:
            cursor.execute("SHOW TABLES")
            tables = cursor.fetchall()
            logger.info(f"✅ 数据库表: {[table[0] for table in tables]}")
            
        connection.close()
        return True
        
    except Exception as e:
        logger.error(f"❌ 数据库连接测试失败: {e}")
        return False

if __name__ == "__main__":
    print("🚀 开始初始化数据库...")
    
    # 测试MySQL服务器连接
    if test_connection():
        print("✅ MySQL服务器连接正常")
    else:
        print("❌ MySQL服务器连接失败，请检查配置")
        exit(1)
    
    # 初始化数据库
    try:
        init_database()
        print("🎉 数据库初始化完成！")
        
        # 测试数据库连接
        if test_database_connection():
            print("✅ 数据库初始化验证成功")
        else:
            print("❌ 数据库初始化验证失败")
            
    except Exception as e:
        print(f"❌ 数据库初始化失败: {e}")
        exit(1)
