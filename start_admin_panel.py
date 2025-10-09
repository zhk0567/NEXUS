#!/usr/bin/env python3
"""
启动管理员面板
"""
import webbrowser
import os
import time
import subprocess
import sys

def start_admin_panel():
    """启动管理员面板"""
    print("Starting Story Control Admin Panel...")
    
    # 检查后端服务器是否运行
    try:
        import requests
        response = requests.get("http://192.168.50.205:5000/api/admin/users?admin_user_id=admin_001&limit=1", timeout=5)
        if response.status_code == 200:
            print("Backend server is running normally")
        else:
            print("Backend server response is abnormal")
            return
    except Exception as e:
        print(f"Cannot connect to backend server: {e}")
        print("Please ensure the backend server is running...")
        return
    
    # 获取当前目录
    current_dir = os.path.dirname(os.path.abspath(__file__))
    admin_panel_path = os.path.join(current_dir, "admin_panel.html")
    
    # 检查管理员面板文件是否存在
    if not os.path.exists(admin_panel_path):
        print(f"Admin panel file does not exist: {admin_panel_path}")
        return
    
    # 获取文件URL
    file_url = f"file:///{admin_panel_path.replace(os.sep, '/')}"
    
    print(f"Admin panel URL: {file_url}")
    print("Opening admin panel...")
    
    # 打开浏览器
    try:
        webbrowser.open(file_url)
        print("Admin panel opened successfully")
        print("\nAdmin features:")
        print("  - Overview: View system statistics")
        print("  - User Management: View all users and statistics")
        print("  - Reading Progress: View and manage all users' reading progress")
        print("  - Can directly mark/unmark users' reading completion status")
        print("\nAdmin User ID: admin_001")
        print("Current system status:")
        
        # 显示系统统计
        try:
            import requests
            response = requests.get("http://192.168.50.205:5000/api/admin/users/reading-progress?admin_user_id=admin_001&limit=1000", timeout=10)
            if response.status_code == 200:
                data = response.json()
                if data.get('success'):
                    total_stories = data['data']['total_count']
                    completed_stories = len([p for p in data['data']['progress_list'] if p['is_completed']])
                    completion_rate = (completed_stories / total_stories * 100) if total_stories > 0 else 0
                    
                    print(f"  - Total stories: {total_stories}")
                    print(f"  - Completed stories: {completed_stories}")
                    print(f"  - Completion rate: {completion_rate:.1f}%")
        except Exception as e:
            print(f"  - Cannot get statistics: {e}")
            
    except Exception as e:
        print(f"Failed to open admin panel: {e}")
        print(f"Please manually open file: {admin_panel_path}")

if __name__ == "__main__":
    start_admin_panel()
