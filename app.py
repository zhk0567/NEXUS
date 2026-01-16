#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NEXUS后端服务器 - 主启动文件
提供ASR、TTS、AI聊天等完整功能
"""
import sys
import os
# 设置标准输出编码为UTF-8，解决Windows PowerShell编码问题
if sys.platform == 'win32':
    # 使用环境变量设置编码，避免直接替换sys.stdout导致的问题
    os.environ['PYTHONIOENCODING'] = 'utf-8'

from flask import Flask
from flask_cors import CORS
from backend.config import PRIVATE_IP
from backend.logger_config import startup_logger, logger
from backend.service_monitor import ServiceMonitor, AutoRecovery
from backend.asr_service import initialize_dolphin_model
from backend.routes import (
    health_routes,
    tts_routes,
    asr_routes,
    chat_routes,
    auth_routes,
    interaction_routes,
    story_routes,
    admin_user_routes,
    realtime_routes
)

# 创建Flask应用
app = Flask(__name__)
CORS(app)

# 初始化服务监控
service_monitor = ServiceMonitor()
auto_recovery = AutoRecovery(service_monitor)

# 注册所有路由
health_routes.register_health_routes(app, service_monitor, auto_recovery)
tts_routes.register_tts_routes(app, service_monitor)
asr_routes.register_asr_routes(app, service_monitor)
chat_routes.register_chat_routes(app)
auth_routes.register_auth_routes(app)
interaction_routes.register_interaction_routes(app)
story_routes.register_story_routes(app)
admin_user_routes.register_admin_user_routes(app)
realtime_routes.register_realtime_routes(app)

if __name__ == '__main__':
    import socket
    
    # 获取本机IP地址
    def get_local_ip():
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            s.close()
            return ip
        except:
            return "127.0.0.1"
    
    startup_logger.info("🚀 NEXUS后端服务器启动中...")
    startup_logger.info(f"🌐 地址: http://{PRIVATE_IP}:5000")
    startup_logger.info(f"📊 管理员面板: http://{PRIVATE_IP}:5000/admin")
    
    # 初始化Dolphin ASR模型
    dolphin_available = initialize_dolphin_model()
    
    if dolphin_available:
        startup_logger.info("🎤 语音识别: Dolphin ASR")
    else:
        startup_logger.info("🎤 语音识别: 模拟模式")
        
    startup_logger.info("🎵 语音合成: edge-tts | 🤖 AI聊天: DeepSeek")
    
    # 启动自动恢复监控
    try:
        auto_recovery.start()
    except Exception as e:
        logger.error(f"启动自动恢复监控失败: {e}")
    
    startup_logger.info("✅ 服务器已启动，等待请求...")
    
    # 禁用Flask的请求日志输出
    import werkzeug
    import logging
    werkzeug_logger = logging.getLogger('werkzeug')
    werkzeug_logger.setLevel(logging.ERROR)
    
    try:
        app.run(host='0.0.0.0', port=5000, debug=False)
    except KeyboardInterrupt:
        startup_logger.info("⏹️ 正在关闭服务...")
    finally:
        # 停止自动恢复监控
        try:
            auto_recovery.stop()
        except Exception as e:
            logger.error(f"停止自动恢复监控失败: {e}")

