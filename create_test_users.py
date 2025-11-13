#!/usr/bin/env python3
"""
创建10个测试用户账号
用户名和密码简单易记
通过API创建用户，避免直接连接数据库
"""
import sys
import requests
import json

# 后端API地址（如果后端正在运行）
API_BASE = "http://localhost:5000"

def create_test_users():
    """通过API创建10个测试用户"""
    
    # 10个测试用户账号（用户名和密码简单易记）
    test_users = [
        {"username": "user01", "password": "123456"},
        {"username": "user02", "password": "123456"},
        {"username": "user03", "password": "123456"},
        {"username": "user04", "password": "123456"},
        {"username": "user05", "password": "123456"},
        {"username": "user06", "password": "123456"},
        {"username": "user07", "password": "123456"},
        {"username": "user08", "password": "123456"},
        {"username": "user09", "password": "123456"},
        {"username": "user10", "password": "123456"},
    ]
    
    print("=" * 50)
    print("开始创建测试用户账号（通过API）...")
    print("=" * 50)
    print(f"API地址: {API_BASE}")
    print()
    
    created_count = 0
    failed_count = 0
    skipped_count = 0
    
    for i, user_info in enumerate(test_users, 1):
        username = user_info["username"]
        password = user_info["password"]
        
        try:
            # 通过API注册用户
            response = requests.post(
                f"{API_BASE}/api/auth/register",
                json={
                    "username": username,
                    "password": password
                },
                timeout=10
            )
            
            if response.status_code == 200:
                data = response.json()
                if data.get('success'):
                    created_count += 1
                    print(f"[{i}/10] 创建用户成功: {username} (密码: {password})")
                else:
                    error_msg = data.get('error', '未知错误')
                    if '已存在' in error_msg or 'exists' in error_msg.lower():
                        skipped_count += 1
                        print(f"[{i}/10] 用户 '{username}' 已存在，跳过")
                    else:
                        failed_count += 1
                        print(f"[{i}/10] 创建用户失败: {username} - {error_msg}")
            else:
                error_msg = response.text
                if '已存在' in error_msg or 'exists' in error_msg.lower():
                    skipped_count += 1
                    print(f"[{i}/10] 用户 '{username}' 已存在，跳过")
                else:
                    failed_count += 1
                    print(f"[{i}/10] 创建用户失败: {username} - HTTP {response.status_code}")
        except requests.exceptions.ConnectionError:
            print(f"[{i}/10] 无法连接到后端服务器: {API_BASE}")
            print("请确保后端服务正在运行: python nexus_backend.py")
            print("\n或者使用SQL脚本直接创建用户:")
            print("mysql -u root -p nexus_unified < create_test_users.sql")
            return
        except Exception as e:
            failed_count += 1
            print(f"[{i}/10] 创建用户失败: {username} - {str(e)}")
    
    print("=" * 50)
    print(f"创建完成！成功: {created_count} 个，跳过: {skipped_count} 个，失败: {failed_count} 个")
    print("=" * 50)
    print("\n账号列表：")
    print("-" * 50)
    for user_info in test_users:
        print(f"用户名: {user_info['username']:<10} 密码: {user_info['password']}")
    print("-" * 50)
    print("\n提示：如果后端服务未运行，请先启动: python nexus_backend.py")

if __name__ == "__main__":
    try:
        create_test_users()
    except Exception as e:
        print(f"创建用户失败: {e}")
        import traceback
        traceback.print_exc()
        print("\n提示：如果后端服务未运行，可以使用SQL脚本直接创建:")
        print("mysql -u root -p nexus_unified < create_test_users.sql")
        sys.exit(1)

