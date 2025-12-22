# -*- coding: utf-8 -*-
"""
服务监控模块
"""
import time
import psutil
import threading
import tempfile
import os
import requests
from datetime import datetime
from collections import defaultdict, deque
from backend.logger_config import logger
from backend.config import TTS_CONFIG


class ServiceMonitor:
    """服务监控和健康检查类"""

    def __init__(self):
        """初始化服务监控"""
        self.service_stats = {
            'tts': {
                'total_requests': 0,
                'successful_requests': 0,
                'failed_requests': 0,
                'last_success': None,
                'last_failure': None,
                'consecutive_failures': 0,
                'response_times': deque(maxlen=100),
                'error_types': defaultdict(int)
            },
            'asr': {
                'total_requests': 0,
                'successful_requests': 0,
                'failed_requests': 0,
                'last_success': None,
                'last_failure': None,
                'consecutive_failures': 0,
                'response_times': deque(maxlen=100),
                'error_types': defaultdict(int)
            },
            'chat': {
                'total_requests': 0,
                'successful_requests': 0,
                'failed_requests': 0,
                'last_success': None,
                'last_failure': None,
                'consecutive_failures': 0,
                'response_times': deque(maxlen=100),
                'error_types': defaultdict(int)
            }
        }
        self.system_stats = {
            'cpu_percent': 0,
            'memory_percent': 0,
            'disk_usage': 0,
            'last_update': None
        }
        self.health_status = {
            'overall': 'healthy',
            'services': {
                'tts': 'healthy',
                'asr': 'healthy',
                'chat': 'healthy'
            },
            'last_check': None
        }
        self.auto_recovery_enabled = True
        self.recovery_attempts = defaultdict(int)
        self.max_recovery_attempts = 3

    def update_service_stats(self, service_name, success=True,
                             response_time=None, error_type=None):
        """更新服务统计信息"""
        if service_name not in self.service_stats:
            return

        stats = self.service_stats[service_name]
        stats['total_requests'] += 1

        if success:
            stats['successful_requests'] += 1
            stats['last_success'] = datetime.now()
            stats['consecutive_failures'] = 0
            self.health_status['services'][service_name] = 'healthy'
        else:
            stats['failed_requests'] += 1
            stats['last_failure'] = datetime.now()
            stats['consecutive_failures'] += 1
            if error_type:
                stats['error_types'][error_type] += 1

            # 检查是否需要标记为不健康
            if stats['consecutive_failures'] >= 3:
                self.health_status['services'][service_name] = 'unhealthy'
                logger.warning(
                    f"⚠️ 服务 {service_name} 连续失败 "
                    f"{stats['consecutive_failures']} 次，标记为不健康"
                )

        if response_time is not None:
            stats['response_times'].append(response_time)

    def update_system_stats(self):
        """更新系统统计信息"""
        try:
            self.system_stats['cpu_percent'] = psutil.cpu_percent(interval=1)
            self.system_stats['memory_percent'] = psutil.virtual_memory().percent
            self.system_stats['disk_usage'] = psutil.disk_usage('/').percent
            self.system_stats['last_update'] = datetime.now()
        except Exception as e:
            logger.error(f"❌ 更新系统统计失败: {e}")

    def check_health(self):
        """检查服务健康状态"""
        self.update_system_stats()

        unhealthy_services = []
        for service, status in self.health_status['services'].items():
            if status == 'unhealthy':
                unhealthy_services.append(service)

        if unhealthy_services:
            self.health_status['overall'] = 'degraded'
            logger.warning(
                f"⚠️ 服务健康检查: 以下服务不健康: {unhealthy_services}"
            )
        else:
            self.health_status['overall'] = 'healthy'

        self.health_status['last_check'] = datetime.now()
        return self.health_status

    def get_service_metrics(self, service_name):
        """获取服务指标"""
        if service_name not in self.service_stats:
            return None

        stats = self.service_stats[service_name]
        response_times = list(stats['response_times'])

        metrics = {
            'total_requests': stats['total_requests'],
            'success_rate': (
                stats['successful_requests'] / max(stats['total_requests'], 1) * 100
            ),
            'consecutive_failures': stats['consecutive_failures'],
            'last_success': (
                stats['last_success'].isoformat() if stats['last_success'] else None
            ),
            'last_failure': (
                stats['last_failure'].isoformat() if stats['last_failure'] else None
            ),
            'avg_response_time': (
                sum(response_times) / len(response_times) if response_times else 0
            ),
            'error_types': dict(stats['error_types'])
        }

        return metrics

    def should_trigger_recovery(self, service_name):
        """判断是否应该触发自动恢复"""
        if not self.auto_recovery_enabled:
            return False

        if service_name not in self.service_stats:
            return False

        stats = self.service_stats[service_name]
        return (
            stats['consecutive_failures'] >= 3 and
            self.recovery_attempts[service_name] < self.max_recovery_attempts
        )

    def record_recovery_attempt(self, service_name):
        """记录恢复尝试"""
        self.recovery_attempts[service_name] += 1
        logger.info(
            f"🔄 服务 {service_name} 恢复尝试 "
            f"{self.recovery_attempts[service_name]}/{self.max_recovery_attempts}"
        )

    def reset_recovery_attempts(self, service_name):
        """重置恢复尝试计数"""
        self.recovery_attempts[service_name] = 0
        logger.info(f"✅ 服务 {service_name} 恢复成功，重置尝试计数")


class AutoRecovery:
    """自动恢复机制"""

    def __init__(self, monitor):
        """初始化自动恢复"""
        self.monitor = monitor
        self.recovery_thread = None
        self.running = False
        self.recovery_interval = 30  # 30秒检查一次

    def start(self):
        """启动自动恢复监控"""
        if self.running:
            return

        self.running = True
        self.recovery_thread = threading.Thread(
            target=self._recovery_loop, daemon=True
        )
        self.recovery_thread.start()
        logger.info("🔄 自动恢复监控已启动")

    def stop(self):
        """停止自动恢复监控"""
        self.running = False
        if self.recovery_thread:
            self.recovery_thread.join(timeout=5)
        logger.info("⏹️ 自动恢复监控已停止")

    def _recovery_loop(self):
        """恢复监控循环"""
        while self.running:
            try:
                # 检查所有服务的健康状态
                self.monitor.check_health()

                # 检查需要恢复的服务
                for service_name in ['tts', 'asr', 'chat']:
                    if self.monitor.should_trigger_recovery(service_name):
                        self._attempt_recovery(service_name)

                time.sleep(self.recovery_interval)

            except Exception as e:
                logger.error(f"❌ 自动恢复监控异常: {e}")
                time.sleep(self.recovery_interval)

    def _attempt_recovery(self, service_name):
        """尝试恢复服务"""
        try:
            self.monitor.record_recovery_attempt(service_name)

            if service_name == 'tts':
                self._recover_tts_service()
            elif service_name == 'asr':
                self._recover_asr_service()
            elif service_name == 'chat':
                self._recover_chat_service()

            # 等待一段时间后检查恢复是否成功
            time.sleep(TTS_CONFIG['recovery_delay'])

            # 测试服务是否恢复
            if self._test_service(service_name):
                self.monitor.reset_recovery_attempts(service_name)
                logger.info(f"✅ 服务 {service_name} 自动恢复成功")
            else:
                logger.warning(f"⚠️ 服务 {service_name} 自动恢复失败")

        except Exception as e:
            logger.error(f"❌ 服务 {service_name} 恢复尝试异常: {e}")

    def _recover_tts_service(self):
        """恢复TTS服务"""
        logger.info("🔄 尝试恢复TTS服务...")

        # 清理临时文件
        try:
            temp_dir = tempfile.gettempdir()
            for file in os.listdir(temp_dir):
                if file.startswith('temp_tts') and file.endswith('.mp3'):
                    os.remove(os.path.join(temp_dir, file))
            logger.info("🧹 清理TTS临时文件完成")
        except Exception as e:
            logger.warning(f"⚠️ 清理TTS临时文件失败: {e}")

        # 等待一段时间让服务稳定
        time.sleep(5)

    def _recover_asr_service(self):
        """恢复ASR服务"""
        logger.info("🔄 尝试恢复ASR服务...")
        time.sleep(2)

    def _recover_chat_service(self):
        """恢复聊天服务"""
        logger.info("🔄 尝试恢复聊天服务...")
        time.sleep(2)

    def _test_service(self, service_name):
        """测试服务是否正常"""
        try:
            if service_name == 'tts':
                # 测试TTS服务 - 增加超时时间
                test_response = requests.post(
                    'http://localhost:5000/api/tts',
                    json={'text': '测试', 'voice': 'zh-CN-XiaoxiaoNeural'},
                    timeout=30
                )
                return test_response.status_code == 200
            elif service_name == 'asr':
                return True
            elif service_name == 'chat':
                return True
        except Exception as e:
            logger.error(f"❌ 测试服务 {service_name} 失败: {e}")
            return False

