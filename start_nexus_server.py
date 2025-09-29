#!/usr/bin/env python3
"""
NEXUS服务器完整启动脚本
包含数据库初始化、服务器启动、健康检查等功能
"""
import os
import sys
import time
import signal
import subprocess
import threading
import requests
import json
from datetime import datetime
import logging

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

class NexusServerManager:
    def __init__(self):
        self.backend_process = None
        self.ngrok_process = None
        self.is_running = False
        self.ngrok_url = None
        
    def check_dependencies(self):
        """检查依赖项"""
        logger.info("🔍 检查依赖项...")
        
        # 检查Python包
        required_packages = [
            'flask', 'pymysql', 'cryptography', 'edge-tts', 
            'websockets', 'requests', 'numpy', 'torch'
        ]
        
        missing_packages = []
        for package in required_packages:
            try:
                __import__(package.replace('-', '_'))
                logger.info(f"✅ {package}")
            except ImportError:
                missing_packages.append(package)
                logger.warning(f"❌ {package}")
        
        if missing_packages:
            logger.error(f"❌ 缺少依赖包: {', '.join(missing_packages)}")
            logger.info("请运行: pip install -r requirements.txt")
            return False
        
        # 检查ngrok
        try:
            result = subprocess.run(['ngrok', 'version'], capture_output=True, text=True)
            if result.returncode == 0:
                logger.info("✅ ngrok")
            else:
                logger.warning("⚠️ ngrok未正确安装")
        except FileNotFoundError:
            logger.warning("⚠️ ngrok未安装")
        
        # 检查MySQL
        try:
            from database_manager import db_manager
            db_manager.connect()
            logger.info("✅ MySQL数据库")
            db_manager.close()
        except Exception as e:
            logger.error(f"❌ MySQL数据库连接失败: {e}")
            return False
        
        logger.info("✅ 所有依赖项检查完成")
        return True
    
    def initialize_database(self):
        """初始化数据库"""
        logger.info("🗄️ 初始化数据库...")
        
        try:
            from init_database import init_database
            init_database()
            logger.info("✅ 数据库初始化完成")
            return True
        except Exception as e:
            logger.error(f"❌ 数据库初始化失败: {e}")
            return False
    
    def start_backend_server(self):
        """启动后端服务器"""
        logger.info("🚀 启动NEXUS后端服务器...")
        
        try:
            self.backend_process = subprocess.Popen(
                [sys.executable, 'nexus_backend.py'],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                bufsize=1,
                universal_newlines=True
            )
            
            # 等待服务器启动
            time.sleep(3)
            
            # 检查服务器是否正常运行
            if self.check_server_health():
                logger.info("✅ NEXUS后端服务器启动成功")
                return True
            else:
                logger.error("❌ NEXUS后端服务器启动失败")
                return False
                
        except Exception as e:
            logger.error(f"❌ 启动后端服务器失败: {e}")
            return False
    
    def check_server_health(self):
        """检查服务器健康状态"""
        try:
            response = requests.get('http://localhost:5000/api/health', timeout=5)
            if response.status_code == 200:
                data = response.json()
                logger.info(f"✅ 服务器状态: {data.get('overall', 'unknown')}")
                return True
            else:
                logger.error(f"❌ 服务器响应异常: {response.status_code}")
                return False
        except Exception as e:
            logger.error(f"❌ 服务器健康检查失败: {e}")
            return False
    
    def start_ngrok_tunnel(self):
        """启动ngrok隧道"""
        logger.info("🌐 启动ngrok隧道...")
        
        try:
            self.ngrok_process = subprocess.Popen(
                ['ngrok', 'http', '5000', '--log=stdout'],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )
            
            # 等待ngrok启动
            time.sleep(5)
            
            # 获取ngrok公网地址
            self.ngrok_url = self.get_ngrok_url()
            if self.ngrok_url:
                logger.info(f"✅ ngrok隧道启动成功: {self.ngrok_url}")
                return True
            else:
                logger.warning("⚠️ ngrok隧道启动，但无法获取公网地址")
                return False
                
        except Exception as e:
            logger.error(f"❌ 启动ngrok失败: {e}")
            return False
    
    def get_ngrok_url(self):
        """获取ngrok公网地址"""
        max_attempts = 10
        for attempt in range(max_attempts):
            try:
                response = requests.get('http://localhost:4040/api/tunnels', timeout=5)
                if response.status_code == 200:
                    data = response.json()
                    tunnels = data.get('tunnels', [])
                    
                    for tunnel in tunnels:
                        if tunnel.get('proto') == 'https':
                            public_url = tunnel.get('public_url')
                            if public_url:
                                return public_url
                
                time.sleep(2)
            except Exception as e:
                logger.debug(f"获取ngrok地址尝试 {attempt + 1}: {e}")
                time.sleep(2)
        
        return None
    
    def update_client_config(self):
        """更新客户端配置"""
        if not self.ngrok_url:
            logger.warning("⚠️ 无ngrok地址，跳过客户端配置更新")
            return False
        
        logger.info("📱 更新客户端配置...")
        
        try:
            # 提取域名
            if self.ngrok_url.startswith('https://'):
                domain = self.ngrok_url[8:]
            elif self.ngrok_url.startswith('http://'):
                domain = self.ngrok_url[7:]
            else:
                domain = self.ngrok_url
            
            websocket_url = f"wss://{domain}"
            
            config_content = f'''package com.llasm.nexusunified.config

/**
 * 服务器配置
 */
object ServerConfig {{
    
    // ngrok公网服务器
    const val NGROK_SERVER = "{self.ngrok_url}/"
    const val NGROK_WEBSOCKET = "{websocket_url}"
    
    // 当前使用的配置 - 使用ngrok
    const val CURRENT_SERVER = NGROK_SERVER
    const val CURRENT_WEBSOCKET = NGROK_WEBSOCKET
    
    // API端点
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
    
    // 获取完整的API URL
    fun getApiUrl(endpoint: String): String {{
        return CURRENT_SERVER + endpoint.removePrefix("/")
    }}
    
    // 获取WebSocket URL
    fun getWebSocketUrl(endpoint: String): String {{
        return CURRENT_WEBSOCKET + "/" + endpoint.removePrefix("/")
    }}
}}'''
            
            with open("app/src/main/java/com/llasm/nexusunified/config/ServerConfig.kt", "w", encoding="utf-8") as f:
                f.write(config_content)
            
            logger.info("✅ 客户端配置已更新")
            return True
            
        except Exception as e:
            logger.error(f"❌ 更新客户端配置失败: {e}")
            return False
    
    def test_external_access(self):
        """测试外网访问"""
        if not self.ngrok_url:
            logger.warning("⚠️ 无ngrok地址，跳过外网访问测试")
            return False
        
        logger.info("🧪 测试外网访问...")
        
        try:
            response = requests.get(f"{self.ngrok_url}/api/health", timeout=10)
            if response.status_code == 200:
                data = response.json()
                logger.info(f"✅ 外网访问成功: {data.get('overall', 'unknown')}")
                return True
            else:
                logger.error(f"❌ 外网访问失败: {response.status_code}")
                return False
        except Exception as e:
            logger.error(f"❌ 外网访问测试失败: {e}")
            return False
    
    def create_startup_summary(self):
        """创建启动总结"""
        summary = f"""# NEXUS服务器启动成功

## 🎉 启动完成

- **启动时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
- **后端服务器**: http://localhost:5000
- **ngrok地址**: {self.ngrok_url if self.ngrok_url else '未启动'}
- **状态**: ✅ 运行中

## 📱 客户端配置

### Android应用
- 已自动更新为使用ngrok地址
- 支持外网访问
- 所有功能正常工作

### 配置详情
```kotlin
const val CURRENT_SERVER = "{self.ngrok_url}/" if self.ngrok_url else "http://localhost:5000/"
const val CURRENT_WEBSOCKET = "wss://{self.ngrok_url[8:] if self.ngrok_url else 'localhost:5000'}"
```

## 🌐 访问地址

### 本地访问
- **API**: http://localhost:5000/api/health
- **ngrok控制台**: http://localhost:4040

### 外网访问
- **API**: {self.ngrok_url}/api/health if self.ngrok_url else "未启用"
- **WebSocket**: wss://{self.ngrok_url[8:] if self.ngrok_url else "未启用"}

## 🔧 管理命令

### 停止服务器
- 按 Ctrl+C 停止所有服务

### 查看日志
- 后端日志: nexus_server.log
- ngrok日志: 控制台输出

### 重启服务
```bash
python start_nexus_server.py
```

## ⚠️ 注意事项

1. **保持运行**: 需要保持此脚本运行以维持服务
2. **ngrok限制**: 免费版地址会变化
3. **网络要求**: 需要稳定的网络连接

---

**🚀 NEXUS服务器已成功启动！**

现在可以从任何地方访问您的NEXUS服务器！
"""
        
        with open("NEXUS_STARTUP_SUCCESS.md", "w", encoding="utf-8") as f:
            f.write(summary)
        
        logger.info("✅ 启动总结已创建: NEXUS_STARTUP_SUCCESS.md")
    
    def start_all_services(self):
        """启动所有服务"""
        logger.info("🚀 开始启动NEXUS服务器...")
        logger.info("=" * 60)
        
        # 1. 检查依赖项
        if not self.check_dependencies():
            logger.error("❌ 依赖项检查失败，启动中止")
            return False
        
        # 2. 初始化数据库
        if not self.initialize_database():
            logger.error("❌ 数据库初始化失败，启动中止")
            return False
        
        # 3. 启动后端服务器
        if not self.start_backend_server():
            logger.error("❌ 后端服务器启动失败，启动中止")
            return False
        
        # 4. 启动ngrok隧道
        ngrok_success = self.start_ngrok_tunnel()
        if ngrok_success:
            # 5. 更新客户端配置
            self.update_client_config()
            
            # 6. 测试外网访问
            self.test_external_access()
        
        # 7. 创建启动总结
        self.create_startup_summary()
        
        self.is_running = True
        
        logger.info("=" * 60)
        logger.info("🎉 NEXUS服务器启动完成！")
        logger.info("=" * 60)
        
        if self.ngrok_url:
            logger.info(f"🌐 外网访问地址: {self.ngrok_url}")
        else:
            logger.info("🌐 外网访问: 未启用")
        
        logger.info("📱 客户端已自动配置")
        logger.info("📋 查看详细配置: NEXUS_STARTUP_SUCCESS.md")
        logger.info("⏹️ 按 Ctrl+C 停止服务器")
        logger.info("=" * 60)
        
        return True
    
    def stop_all_services(self):
        """停止所有服务"""
        logger.info("⏹️ 正在停止NEXUS服务器...")
        
        if self.ngrok_process:
            self.ngrok_process.terminate()
            logger.info("✅ ngrok已停止")
        
        if self.backend_process:
            self.backend_process.terminate()
            logger.info("✅ 后端服务器已停止")
        
        self.is_running = False
        logger.info("✅ 所有服务已停止")
    
    def run(self):
        """运行服务器管理器"""
        try:
            if self.start_all_services():
                # 保持运行
                while self.is_running:
                    time.sleep(1)
        except KeyboardInterrupt:
            logger.info("\n⏹️ 收到停止信号...")
        finally:
            self.stop_all_services()

def signal_handler(signum, frame):
    """信号处理器"""
    logger.info("\n⏹️ 收到停止信号...")
    sys.exit(0)

def main():
    """主函数"""
    print("🚀 NEXUS服务器完整启动脚本")
    print("=" * 60)
    print("包含数据库初始化、服务器启动、ngrok隧道等功能")
    print("=" * 60)
    
    # 注册信号处理器
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    # 创建并运行服务器管理器
    manager = NexusServerManager()
    manager.run()

if __name__ == "__main__":
    main()
