#!/usr/bin/env python3
"""
系统状态检查脚本
"""
import requests
import json
from datetime import datetime

def check_system_status():
    """检查系统状态"""
    base_url = "http://192.168.50.205:5000"
    admin_user_id = "admin_001"
    
    print("Checking Story Control System Status...")
    print("="*50)
    print(f"Check Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"Server Address: {base_url}")
    print("="*50)
    
    # 1. 检查后端服务器健康状态
    print("\n1. Checking backend server health...")
    try:
        response = requests.get(f"{base_url}/api/health", timeout=5)
        if response.status_code == 200:
            health_data = response.json()
            print(f"[OK] Backend server status: {health_data.get('overall', 'unknown')}")
            
            # 显示服务状态
            services = health_data.get('services', {})
            for service, status in services.items():
                status_icon = "[OK]" if status == "healthy" else "[ERROR]"
                print(f"   {status_icon} {service.upper()}: {status}")
        else:
            print(f"[ERROR] Backend server response abnormal: {response.status_code}")
            return False
    except Exception as e:
        print(f"[ERROR] Cannot connect to backend server: {e}")
        return False
    
    # 2. 检查管理员API
    print("\n2. Checking admin API...")
    try:
        response = requests.get(f"{base_url}/api/admin/users?admin_user_id={admin_user_id}&limit=1", timeout=5)
        if response.status_code == 200:
            print("[OK] Admin API is working")
        else:
            print(f"[ERROR] Admin API abnormal: {response.status_code}")
    except Exception as e:
        print(f"[ERROR] Admin API check failed: {e}")
    
    # 3. 获取系统统计信息
    print("\n3. Getting system statistics...")
    try:
        # 获取用户统计
        users_response = requests.get(f"{base_url}/api/admin/users?admin_user_id={admin_user_id}&limit=1000", timeout=10)
        users_data = users_response.json() if users_response.status_code == 200 else {}
        
        # 获取阅读进度统计
        progress_response = requests.get(f"{base_url}/api/admin/users/reading-progress?admin_user_id={admin_user_id}&limit=1000", timeout=10)
        progress_data = progress_response.json() if progress_response.status_code == 200 else {}
        
        if users_data.get('success') and progress_data.get('success'):
            total_users = users_data.get('total_count', 0)
            total_stories = progress_data['data'].get('total_count', 0)
            completed_stories = len([p for p in progress_data['data']['progress_list'] if p.get('is_completed')])
            completion_rate = (completed_stories / total_stories * 100) if total_stories > 0 else 0
            
            print("System Statistics:")
            print(f"   - Total Users: {total_users}")
            print(f"   - Total Stories: {total_stories}")
            print(f"   - Completed Stories: {completed_stories}")
            print(f"   - Completion Rate: {completion_rate:.1f}%")
        else:
            print("[WARNING] Cannot get system statistics")
    except Exception as e:
        print(f"[WARNING] Failed to get system statistics: {e}")
    
    # 4. 检查TTS服务
    print("\n4. Checking TTS service...")
    try:
        response = requests.get(f"{base_url}/api/tts/status", timeout=5)
        if response.status_code == 200:
            tts_data = response.json()
            print(f"[OK] TTS service status: {tts_data.get('tts_health', 'unknown')}")
        else:
            print(f"[ERROR] TTS service abnormal: {response.status_code}")
    except Exception as e:
        print(f"[WARNING] TTS service check failed: {e}")
    
    # 5. 检查ASR服务
    print("\n5. Checking ASR service...")
    try:
        response = requests.get(f"{base_url}/api/asr/status", timeout=5)
        if response.status_code == 200:
            asr_data = response.json()
            print(f"[OK] ASR service status: {asr_data.get('asr_health', 'unknown')}")
        else:
            print(f"[ERROR] ASR service abnormal: {response.status_code}")
    except Exception as e:
        print(f"[WARNING] ASR service check failed: {e}")
    
    print("\n" + "="*50)
    print("[OK] System status check completed")
    print("="*50)
    
    return True

if __name__ == "__main__":
    check_system_status()
