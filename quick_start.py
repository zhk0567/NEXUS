#!/usr/bin/env python3
"""
NEXUS服务器快速启动脚本
简化版本，快速启动所有服务
"""
import os
import sys
import time
import subprocess
import requests
import signal
import threading
from datetime import datetime

def print_banner():
    """打印启动横幅"""
    print("=" * 60)
    print("🚀 NEXUS服务器快速启动")
    print("=" * 60)
    print("正在启动所有服务...")
    print("=" * 60)

def check_server_running():
    """检查服务器是否已运行"""
    try:
        response = requests.get('http://localhost:5000/api/health', timeout=2)
        return response.status_code == 200
    except:
        return False

def start_backend():
    """启动后端服务器"""
    print("🚀 启动NEXUS后端服务器...")
    
    if check_server_running():
        print("✅ 后端服务器已在运行")
        return True
    
    try:
        process = subprocess.Popen([sys.executable, 'nexus_backend.py'])
        time.sleep(3)
        
        if check_server_running():
            print("✅ 后端服务器启动成功")
            return True
        else:
            print("❌ 后端服务器启动失败")
            return False
    except Exception as e:
        print(f"❌ 启动后端服务器失败: {e}")
        return False

def start_ngrok():
    """启动ngrok隧道"""
    print("🌐 启动ngrok隧道...")
    
    try:
        process = subprocess.Popen(['ngrok', 'http', '5000'])
        time.sleep(5)
        
        # 获取ngrok地址
        try:
            response = requests.get('http://localhost:4040/api/tunnels', timeout=5)
            if response.status_code == 200:
                data = response.json()
                tunnels = data.get('tunnels', [])
                for tunnel in tunnels:
                    if tunnel.get('proto') == 'https':
                        url = tunnel.get('public_url')
                        if url:
                            print(f"✅ ngrok隧道启动成功: {url}")
                            return url
        except:
            pass
        
        print("⚠️ ngrok启动，但无法获取公网地址")
        return None
    except Exception as e:
        print(f"❌ 启动ngrok失败: {e}")
        return None

def update_config(ngrok_url):
    """更新客户端配置"""
    if not ngrok_url:
        print("⚠️ 无ngrok地址，跳过配置更新")
        return
    
    print("📱 更新客户端配置...")
    
    try:
        domain = ngrok_url[8:] if ngrok_url.startswith('https://') else ngrok_url
        websocket_url = f"wss://{domain}"
        
        config = f'''package com.llasm.nexusunified.config

object ServerConfig {{
    const val NGROK_SERVER = "{ngrok_url}/"
    const val NGROK_WEBSOCKET = "{websocket_url}"
    const val CURRENT_SERVER = NGROK_SERVER
    const val CURRENT_WEBSOCKET = NGROK_WEBSOCKET
    
    object Endpoints {{
        const val HEALTH = "api/health"
        const val CHAT = "api/chat"
        const val CHAT_STREAMING = "api/chat_streaming"
        const val TRANSCRIBE = "api/transcribe"
        const val TTS = "api/tts"
        const val VOICE_CHAT = "api/voice_chat"
        const val VOICE_CHAT_STREAMING = "api/voice_chat_streaming"
        const val AUTH_LOGIN = "api/auth/login"
        const val AUTH_LOGOUT = "api/auth/logout"
        const val AUTH_REGISTER = "api/auth/register"
        const val INTERACTIONS_LOG = "api/interactions/log"
        const val INTERACTIONS_HISTORY = "api/interactions/history"
        const val STATS_INTERACTIONS = "api/stats/interactions"
        const val STATS_ACTIVE_USERS = "api/stats/active_users"
        const val ADMIN_CLEANUP = "api/admin/cleanup"
    }}
    
    fun getApiUrl(endpoint: String): String {{
        return CURRENT_SERVER + endpoint.removePrefix("/")
    }}
    
    fun getWebSocketUrl(endpoint: String): String {{
        return CURRENT_WEBSOCKET + "/" + endpoint.removePrefix("/")
    }}
}}'''
        
        with open("app/src/main/java/com/llasm/nexusunified/config/ServerConfig.kt", "w", encoding="utf-8") as f:
            f.write(config)
        
        print("✅ 客户端配置已更新")
    except Exception as e:
        print(f"❌ 更新配置失败: {e}")

def test_access(ngrok_url):
    """测试访问"""
    if not ngrok_url:
        print("⚠️ 无ngrok地址，跳过访问测试")
        return
    
    print("🧪 测试外网访问...")
    
    try:
        response = requests.get(f"{ngrok_url}/api/health", timeout=10)
        if response.status_code == 200:
            print("✅ 外网访问成功")
        else:
            print(f"❌ 外网访问失败: {response.status_code}")
    except Exception as e:
        print(f"❌ 访问测试失败: {e}")

def main():
    """主函数"""
    print_banner()
    
    # 启动后端服务器
    if not start_backend():
        print("❌ 后端服务器启动失败，退出")
        return
    
    # 启动ngrok隧道
    ngrok_url = start_ngrok()
    
    # 更新客户端配置
    update_config(ngrok_url)
    
    # 测试访问
    test_access(ngrok_url)
    
    # 显示结果
    print("\n" + "=" * 60)
    print("🎉 NEXUS服务器启动完成！")
    print("=" * 60)
    print(f"本地地址: http://localhost:5000")
    if ngrok_url:
        print(f"外网地址: {ngrok_url}")
    print("ngrok控制台: http://localhost:4040")
    print("=" * 60)
    print("⏹️ 按 Ctrl+C 停止服务器")
    print("=" * 60)
    
    # 保持运行
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\n⏹️ 服务器已停止")

if __name__ == "__main__":
    main()
