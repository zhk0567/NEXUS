#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NEXUS Backend Server - 本地启动脚本
"""

import subprocess
import time
import requests
import logging
import sys
import signal

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler('nexus_server.log', encoding='utf-8')
    ]
)
logger = logging.getLogger(__name__)

class NexusLocalServer:
    def __init__(self):
        self.backend_process = None
        self.running = False
        
    def start_backend(self):
        """启动后端服务器"""
        try:
            logger.info("正在启动NEXUS后端服务器...")
            self.backend_process = subprocess.Popen(
                [sys.executable, 'nexus_backend.py'],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                encoding='utf-8'
            )
            
            # 等待服务器启动
            time.sleep(5)
            
            # 检查服务器是否启动成功
            try:
                response = requests.get('http://localhost:5000/api/health', timeout=10)
                if response.status_code == 200:
                    logger.info("后端服务器启动成功！")
                    return True
                else:
                    logger.error(f"后端服务器健康检查失败: {response.status_code}")
                    return False
            except Exception as e:
                logger.error(f"无法连接到后端服务器: {e}")
                return False
                
        except Exception as e:
            logger.error(f"启动后端服务器失败: {e}")
            return False
    
    def start(self):
        """启动服务器"""
        try:
            logger.info("=" * 50)
            logger.info("NEXUS 本地服务器启动")
            logger.info("=" * 50)
            
            # 启动后端服务器
            if not self.start_backend():
                logger.error("后端服务器启动失败，退出")
                return False
            
            # 显示连接信息
            logger.info("=" * 50)
            logger.info("NEXUS 服务器已启动！")
            logger.info("=" * 50)
            logger.info("本地地址: http://localhost:5000")
            logger.info("Android地址: http://192.168.50.205:5000")
            logger.info("健康检查: http://localhost:5000/api/health")
            logger.info("=" * 50)
            logger.info("注意: Android设备和PC需要在同一WiFi网络")
            logger.info("按 Ctrl+C 停止服务器")
            logger.info("=" * 50)
            
            self.running = True
            
            # 保持运行
            try:
                while self.running:
                    time.sleep(1)
            except KeyboardInterrupt:
                logger.info("收到停止信号...")
                self.stop()
            
            return True
            
        except Exception as e:
            logger.error(f"启动失败: {e}")
            return False
    
    def stop(self):
        """停止服务器"""
        logger.info("正在停止服务器...")
        self.running = False
        
        if self.backend_process:
            try:
                self.backend_process.terminate()
                self.backend_process.wait(timeout=5)
                logger.info("后端服务器已停止")
            except:
                self.backend_process.kill()
                logger.info("强制停止后端服务器")
        
        logger.info("服务器已完全停止")

def main():
    """主函数"""
    server = NexusLocalServer()
    
    # 设置信号处理
    def signal_handler(signum, frame):
        logger.info("收到停止信号...")
        server.stop()
        sys.exit(0)
    
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    try:
        success = server.start()
        if not success:
            sys.exit(1)
    except Exception as e:
        logger.error(f"启动失败: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
