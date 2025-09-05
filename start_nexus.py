#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NEXUS高性能后端启动器
自动激活llasm环境并启动服务
"""

import os
import sys
import subprocess
import time
import logging
from pathlib import Path

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

def activate_llasm_and_run():
    """激活llasm环境并运行后端服务"""
    try:
        logger.info("🚀 启动NEXUS后端服务...")
        logger.info("🔧 使用llasm环境")
        
        # 获取项目根目录
        project_root = Path(__file__).parent
        
        # 构建启动命令
        if os.name == 'nt':  # Windows
            activate_cmd = "conda activate llasm && "
        else:  # Linux/Mac
            activate_cmd = "source activate llasm && "
        
        # 启动命令
        start_cmd = f"{activate_cmd}python high_performance_backend.py"
        
        logger.info("🌐 服务地址: http://localhost:5000")
        logger.info("📊 健康检查: http://localhost:5000/health")
        logger.info("📈 系统统计: http://localhost:5000/api/stats")
        logger.info("=" * 60)
        logger.info("✅ 系统就绪！等待连接...")
        logger.info("按 Ctrl+C 停止服务")
        logger.info("=" * 60)
        
        # 执行启动命令
        if os.name == 'nt':  # Windows
            subprocess.run(start_cmd, shell=True)
        else:  # Linux/Mac
            subprocess.run(start_cmd, shell=True, executable='/bin/bash')
            
    except KeyboardInterrupt:
        logger.info("🛑 服务已停止")
    except Exception as e:
        logger.error(f"❌ 启动失败: {e}")
        sys.exit(1)

if __name__ == '__main__':
    activate_llasm_and_run()