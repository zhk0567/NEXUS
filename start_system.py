#!/usr/bin/env python3
"""
统一启动脚本 - 同时启动后端服务器和管理员面板
"""
import subprocess
import threading
import time
import webbrowser
import os
import sys
import signal
import requests
from datetime import datetime

class SystemLauncher:
    def __init__(self):
        self.backend_process = None
        self.admin_panel_opened = False
        self.running = True
        
    def start_backend_server(self):
        """启动后端服务器"""
        try:
            print("🚀 启动后端服务器...")
            self.backend_process = subprocess.Popen(
                [sys.executable, "nexus_backend.py"],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1,
                universal_newlines=True
            )
            
            # 等待服务器启动
            print("⏳ 等待后端服务器启动...")
            for i in range(30):  # 最多等待30秒
                try:
                    response = requests.get("http://192.168.50.205:5000/api/health", timeout=2)
                    if response.status_code == 200:
                        print("✅ 后端服务器启动成功")
                        return True
                except:
                    pass
                time.sleep(1)
                print(f"⏳ 等待中... ({i+1}/30)")
            
            print("❌ 后端服务器启动超时")
            return False
            
        except Exception as e:
            print(f"❌ 启动后端服务器失败: {e}")
            return False
    
    def start_admin_panel(self):
        """启动管理员面板"""
        try:
            print("📱 启动管理员面板...")
            
            # 获取当前目录
            current_dir = os.path.dirname(os.path.abspath(__file__))
            admin_panel_path = os.path.join(current_dir, "admin_panel.html")
            
            # 检查管理员面板文件是否存在
            if not os.path.exists(admin_panel_path):
                print(f"❌ 管理员面板文件不存在: {admin_panel_path}")
                return False
            
            # 获取文件URL
            file_url = f"file:///{admin_panel_path.replace(os.sep, '/')}"
            
            print(f"🌐 管理员面板地址: {file_url}")
            
            # 打开浏览器
            webbrowser.open(file_url)
            self.admin_panel_opened = True
            print("✅ 管理员面板已打开")
            
            return True
            
        except Exception as e:
            print(f"❌ 启动管理员面板失败: {e}")
            return False
    
    def check_backend_status(self):
        """检查后端服务器状态"""
        try:
            response = requests.get("http://192.168.50.205:5000/api/health", timeout=5)
            return response.status_code == 200
        except:
            return False
    
    def get_system_status(self):
        """获取系统状态"""
        try:
            # 获取用户统计
            users_response = requests.get("http://192.168.50.205:5000/api/admin/users?admin_user_id=admin_001&limit=1", timeout=5)
            users_data = users_response.json() if users_response.status_code == 200 else {}
            
            # 获取阅读进度统计
            progress_response = requests.get("http://192.168.50.205:5000/api/admin/users/reading-progress?admin_user_id=admin_001&limit=1000", timeout=5)
            progress_data = progress_response.json() if progress_response.status_code == 200 else {}
            
            if users_data.get('success') and progress_data.get('success'):
                total_users = users_data.get('total_count', 0)
                total_stories = progress_data['data'].get('total_count', 0)
                completed_stories = len([p for p in progress_data['data']['progress_list'] if p.get('is_completed')])
                completion_rate = (completed_stories / total_stories * 100) if total_stories > 0 else 0
                
                return {
                    'total_users': total_users,
                    'total_stories': total_stories,
                    'completed_stories': completed_stories,
                    'completion_rate': completion_rate
                }
        except Exception as e:
            print(f"⚠️ 获取系统状态失败: {e}")
        
        return None
    
    def display_system_info(self):
        """显示系统信息"""
        print("\n" + "="*60)
        print("📚 故事控制系统 - 统一启动")
        print("="*60)
        print(f"🕐 启动时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"🌐 后端地址: http://192.168.50.205:5000")
        print(f"📱 管理员面板: 已在浏览器中打开")
        print(f"🔑 管理员用户ID: admin_001")
        print("="*60)
        
        # 显示系统状态
        status = self.get_system_status()
        if status:
            print("📊 当前系统状态:")
            print(f"  - 总用户数: {status['total_users']}")
            print(f"  - 总故事数: {status['total_stories']}")
            print(f"  - 已完成故事: {status['completed_stories']}")
            print(f"  - 完成率: {status['completion_rate']:.1f}%")
        else:
            print("⚠️ 无法获取系统状态")
        
        print("="*60)
        print("🎯 功能说明:")
        print("  - 后端服务器: 提供API服务")
        print("  - 管理员面板: 管理用户和阅读进度")
        print("  - 支持功能: 用户管理、阅读进度管理、数据统计")
        print("="*60)
        print("💡 使用说明:")
        print("  - 管理员面板: 在浏览器中查看和管理")
        print("  - API测试: 使用Postman或其他工具测试API")
        print("  - 停止服务: 按 Ctrl+C 停止所有服务")
        print("="*60)
    
    def monitor_backend(self):
        """监控后端服务器状态"""
        while self.running:
            try:
                if self.backend_process and self.backend_process.poll() is not None:
                    print("❌ 后端服务器进程已停止")
                    break
                
                if not self.check_backend_status():
                    print("⚠️ 后端服务器无响应")
                else:
                    print("✅ 后端服务器运行正常")
                
                time.sleep(30)  # 每30秒检查一次
                
            except Exception as e:
                print(f"⚠️ 监控后端服务器时出错: {e}")
                time.sleep(30)
    
    def signal_handler(self, signum, frame):
        """信号处理器"""
        print("\n🛑 收到停止信号，正在关闭系统...")
        self.running = False
        
        if self.backend_process:
            print("⏹️ 停止后端服务器...")
            self.backend_process.terminate()
            try:
                self.backend_process.wait(timeout=10)
                print("✅ 后端服务器已停止")
            except subprocess.TimeoutExpired:
                print("⚠️ 强制停止后端服务器...")
                self.backend_process.kill()
        
        print("👋 系统已完全停止")
        sys.exit(0)
    
    def start(self):
        """启动整个系统"""
        try:
            # 设置信号处理器
            signal.signal(signal.SIGINT, self.signal_handler)
            signal.signal(signal.SIGTERM, self.signal_handler)
            
            print("🚀 故事控制系统统一启动器")
            print("="*50)
            
            # 1. 启动后端服务器
            if not self.start_backend_server():
                print("❌ 无法启动后端服务器，退出")
                return False
            
            # 2. 启动管理员面板
            if not self.start_admin_panel():
                print("⚠️ 管理员面板启动失败，但后端服务器仍在运行")
            
            # 3. 显示系统信息
            self.display_system_info()
            
            # 4. 启动监控线程
            monitor_thread = threading.Thread(target=self.monitor_backend, daemon=True)
            monitor_thread.start()
            
            # 5. 保持主线程运行
            print("\n🔄 系统运行中... (按 Ctrl+C 停止)")
            while self.running:
                time.sleep(1)
                
        except KeyboardInterrupt:
            self.signal_handler(signal.SIGINT, None)
        except Exception as e:
            print(f"❌ 系统启动失败: {e}")
            self.signal_handler(signal.SIGTERM, None)

def main():
    """主函数"""
    launcher = SystemLauncher()
    launcher.start()

if __name__ == "__main__":
    main()
