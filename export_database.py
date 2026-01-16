#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
数据库导出脚本：导出所有表的数据到文件
"""
import os
import sys
import json
import csv
import pymysql
from datetime import datetime
from database_config import DATABASE_CONFIG

# 设置输出编码
if sys.platform == 'win32':
    import codecs
    sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer, 'strict')
    sys.stderr = codecs.getwriter('utf-8')(sys.stderr.buffer, 'strict')

def export_table_to_json(cursor, table_name, output_dir):
    """导出表数据为JSON格式"""
    try:
        # 获取表的所有数据
        cursor.execute(f"SELECT * FROM {table_name}")
        rows = cursor.fetchall()
        
        # 转换为字典列表
        columns = [desc[0] for desc in cursor.description]
        data = []
        for row in rows:
            row_dict = {}
            for i, col in enumerate(columns):
                value = row[i]
                # 处理日期时间类型
                if isinstance(value, (datetime,)):
                    value = value.strftime('%Y-%m-%d %H:%M:%S')
                elif isinstance(value, bytes):
                    value = value.decode('utf-8', errors='ignore')
                elif hasattr(value, 'isoformat'):  # 处理date类型
                    value = value.isoformat()
                row_dict[col] = value
            data.append(row_dict)
        
        # 保存为JSON文件
        json_file = os.path.join(output_dir, f"{table_name}.json")
        with open(json_file, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2, default=str)
        
        print(f"  ✓ {table_name}.json ({len(data)} 条记录)")
        return len(data)
    except Exception as e:
        print(f"  ✗ {table_name}.json 导出失败: {e}")
        return 0

def export_table_to_csv(cursor, table_name, output_dir):
    """导出表数据为CSV格式"""
    try:
        # 获取表的所有数据
        cursor.execute(f"SELECT * FROM {table_name}")
        rows = cursor.fetchall()
        
        if not rows:
            return 0
        
        # 获取列名
        columns = [desc[0] for desc in cursor.description]
        
        # 保存为CSV文件
        csv_file = os.path.join(output_dir, f"{table_name}.csv")
        with open(csv_file, 'w', encoding='utf-8-sig', newline='') as f:
            writer = csv.writer(f)
            # 写入列名
            writer.writerow(columns)
            # 写入数据
            for row in rows:
                row_data = []
                for value in row:
                    if isinstance(value, (datetime,)):
                        value = value.strftime('%Y-%m-%d %H:%M:%S')
                    elif isinstance(value, bytes):
                        value = value.decode('utf-8', errors='ignore')
                    elif value is None:
                        value = ''
                    row_data.append(str(value))
                writer.writerow(row_data)
        
        print(f"  ✓ {table_name}.csv ({len(rows)} 条记录)")
        return len(rows)
    except Exception as e:
        print(f"  ✗ {table_name}.csv 导出失败: {e}")
        return 0

def export_table_structure(cursor, table_name, output_dir):
    """导出表结构为SQL"""
    try:
        cursor.execute(f"SHOW CREATE TABLE {table_name}")
        result = cursor.fetchone()
        if result:
            # 处理不同的返回格式
            if isinstance(result, dict):
                create_sql = result.get('Create Table', '')
            elif isinstance(result, tuple):
                create_sql = result[1] if len(result) > 1 else ''
            else:
                create_sql = str(result)
            
            if create_sql:
                sql_file = os.path.join(output_dir, f"{table_name}_structure.sql")
                with open(sql_file, 'w', encoding='utf-8') as f:
                    f.write(f"-- 表结构: {table_name}\n")
                    f.write(f"-- 导出时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
                    f.write(create_sql)
                    if not create_sql.strip().endswith(';'):
                        f.write(";\n")
                
                print(f"  ✓ {table_name}_structure.sql")
                return True
    except Exception as e:
        print(f"  ✗ {table_name}_structure.sql 导出失败: {e}")
        return False

def export_database():
    """导出整个数据库"""
    connection = None
    try:
        # 创建输出目录（如果已存在则询问是否覆盖）
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        output_dir = f"database_export_{timestamp}"
        
        # 检查是否有旧的导出文件夹（同一天）
        today_prefix = datetime.now().strftime('%Y%m%d')
        existing_dirs = [d for d in os.listdir('.') if os.path.isdir(d) and d.startswith(f'database_export_{today_prefix}')]
        if existing_dirs:
            print(f"⚠️  发现 {len(existing_dirs)} 个今天的导出文件夹:")
            for d in existing_dirs:
                print(f"   - {d}")
            print(f"   新导出将创建: {output_dir}")
        
        os.makedirs(output_dir, exist_ok=True)
        
        print(f"📁 输出目录: {output_dir}")
        print("=" * 60)
        
        # 连接数据库
        print("🔌 正在连接数据库...")
        connection = pymysql.connect(
            host=DATABASE_CONFIG['host'],
            port=DATABASE_CONFIG['port'],
            user=DATABASE_CONFIG['user'],
            password=DATABASE_CONFIG['password'],
            database=DATABASE_CONFIG['database'],
            charset='utf8mb4'
        )
        print("✓ 数据库连接成功")
        print("=" * 60)
        
        # 获取所有表名
        with connection.cursor() as cursor:
            cursor.execute("SHOW TABLES")
            rows = cursor.fetchall()
            # 处理不同的返回格式
            if rows and isinstance(rows[0], dict):
                tables = [row[f"Tables_in_{DATABASE_CONFIG['database']}"] for row in rows]
            else:
                tables = [row[0] for row in rows]
        
        print(f"📊 找到 {len(tables)} 个表")
        print("=" * 60)
        
        total_records = 0
        
        # 导出每个表
        for table_name in tables:
            print(f"\n📋 导出表: {table_name}")
            with connection.cursor() as cursor:
                # 只导出CSV格式
                count_csv = export_table_to_csv(cursor, table_name, output_dir)
                
                total_records += count_csv
        
        # 创建导出信息文件
        info_file = os.path.join(output_dir, "export_info.txt")
        with open(info_file, 'w', encoding='utf-8') as f:
            f.write("=" * 60 + "\n")
            f.write("数据库导出信息\n")
            f.write("=" * 60 + "\n")
            f.write(f"导出时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            f.write(f"数据库: {DATABASE_CONFIG['database']}\n")
            f.write(f"主机: {DATABASE_CONFIG['host']}:{DATABASE_CONFIG['port']}\n")
            f.write(f"表数量: {len(tables)}\n")
            f.write(f"总记录数: {total_records}\n")
            f.write("\n导出的表:\n")
            for table_name in tables:
                f.write(f"  - {table_name}\n")
            f.write("\n文件格式说明:\n")
            f.write("  - *.csv: CSV格式数据（可用Excel打开）\n")
        
        print("\n" + "=" * 60)
        print("✅ 导出完成！")
        print(f"📁 输出目录: {os.path.abspath(output_dir)}")
        print(f"📊 总记录数: {total_records}")
        print(f"📋 表数量: {len(tables)}")
        print("=" * 60)
        
        return True
        
    except Exception as e:
        print(f"\n❌ 导出失败: {e}")
        import traceback
        traceback.print_exc()
        return False
    finally:
        if connection:
            connection.close()

if __name__ == '__main__':
    print("=" * 60)
    print("数据库导出工具")
    print("=" * 60)
    
    success = export_database()
    sys.exit(0 if success else 1)

