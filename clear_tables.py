#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
清空数据库表（保留users表）
"""
import sys
import io
import pymysql
from database_config import DATABASE_CONFIG

# 设置标准输出为UTF-8编码（Windows兼容）
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

# 需要清空的表（除了users表）
TABLES_TO_CLEAR = [
    'interactions',
    'system_logs',
    'reading_progress',
    'story_interactions',
    'interaction_progress'
]

def clear_tables():
    """清空指定的表"""
    connection = None
    try:
        # 连接数据库
        config = DATABASE_CONFIG.copy()
        config.update({
            'autocommit': True,
            'charset': 'utf8mb4',
            'use_unicode': True,
            'cursorclass': pymysql.cursors.DictCursor
        })
        connection = pymysql.connect(**config)
        
        with connection.cursor() as cursor:
            # 禁用外键检查（临时）
            cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
            
            cleared_count = 0
            for table_name in TABLES_TO_CLEAR:
                try:
                    # 使用TRUNCATE TABLE快速清空表
                    cursor.execute(f"TRUNCATE TABLE `{table_name}`")
                    cleared_count += 1
                    print(f"✅ 已清空表: {table_name}")
                except Exception as e:
                    print(f"❌ 清空表 {table_name} 失败: {e}")
            
            # 重新启用外键检查
            cursor.execute("SET FOREIGN_KEY_CHECKS = 1")
            connection.commit()
            
            print(f"\n✅ 完成！已清空 {cleared_count} 个表的数据（users表已保留）")
            
    except Exception as e:
        print(f"❌ 操作失败: {e}")
        raise
    finally:
        if connection:
            connection.close()

if __name__ == "__main__":
    import sys
    
    print("开始清空数据库表（保留users表）...")
    print(f"将清空以下表: {', '.join(TABLES_TO_CLEAR)}\n")
    
    # 如果提供了--yes参数，跳过确认
    if '--yes' in sys.argv or '-y' in sys.argv:
        clear_tables()
    else:
        # 确认操作
        confirm = input("确认要清空这些表的数据吗？(yes/no): ")
        if confirm.lower() in ['yes', 'y']:
            clear_tables()
        else:
            print("操作已取消")
