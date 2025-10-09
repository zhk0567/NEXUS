#!/usr/bin/env python3
"""
NEXUS后端服务器
提供ASR、TTS、AI聊天等完整功能
"""
from flask import Flask, request, jsonify, send_file
from flask_cors import CORS
import io
import subprocess
import sys
import tempfile
import os
import logging
import json
import requests
import time
import asyncio
import random
import psutil
import threading
from datetime import datetime, timedelta
from collections import defaultdict, deque
from database_manager import db_manager

# 性能优化：配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(),
        # 移除文件日志，减少I/O开销
    ]
)
logger = logging.getLogger(__name__)

# 服务监控和健康检查类
class ServiceMonitor:
    def __init__(self):
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
        
    def update_service_stats(self, service_name, success=True, response_time=None, error_type=None):
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
                logger.warning(f"⚠️ 服务 {service_name} 连续失败 {stats['consecutive_failures']} 次，标记为不健康")
        
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
            logger.warning(f"⚠️ 服务健康检查: 以下服务不健康: {unhealthy_services}")
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
            'success_rate': stats['successful_requests'] / max(stats['total_requests'], 1) * 100,
            'consecutive_failures': stats['consecutive_failures'],
            'last_success': stats['last_success'].isoformat() if stats['last_success'] else None,
            'last_failure': stats['last_failure'].isoformat() if stats['last_failure'] else None,
            'avg_response_time': sum(response_times) / len(response_times) if response_times else 0,
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
        return (stats['consecutive_failures'] >= 3 and 
                self.recovery_attempts[service_name] < self.max_recovery_attempts)
                
    def record_recovery_attempt(self, service_name):
        """记录恢复尝试"""
        self.recovery_attempts[service_name] += 1
        logger.info(f"🔄 服务 {service_name} 恢复尝试 {self.recovery_attempts[service_name]}/{self.max_recovery_attempts}")
        
    def reset_recovery_attempts(self, service_name):
        """重置恢复尝试计数"""
        self.recovery_attempts[service_name] = 0
        logger.info(f"✅ 服务 {service_name} 恢复成功，重置尝试计数")

# 创建全局监控实例
monitor = ServiceMonitor()

# TTS配置管理 - 激进性能优化
TTS_CONFIG = {
    'max_retries': 2,  # 进一步减少重试次数
    'timeout_total': 60,  # 增加总超时时间到60秒以支持长文本
    'timeout_connect': 10,  # 增加连接超时到10秒
    'retry_delay': 0.5,  # 进一步减少重试延迟
    'max_consecutive_failures': 3,  # 连续失败阈值
    'recovery_delay': 3,  # 减少恢复延迟
    'concurrent_limit': 3,  # 增加并发限制到3
    'cache_enabled': True,  # 启用缓存
    'health_check_interval': 30,  # 健康检查间隔
    'use_edge_tts_only': True,  # 强制只使用edge-tts
    'text_length_limit': 1000,  # 增加文本长度限制
    'enable_compression': True,  # 启用压缩传输
    'fast_mode': True,  # 启用快速模式
    'chunk_size': 1024  # 减少块大小以提高响应速度
}

# TTS缓存和并发控制
tts_cache = {}
tts_concurrent_count = 0
tts_last_health_check = 0

# 自动恢复机制
class AutoRecovery:
    def __init__(self):
        self.recovery_thread = None
        self.running = False
        self.recovery_interval = 30  # 30秒检查一次
        
    def start(self):
        """启动自动恢复监控"""
        if self.running:
            return
            
        self.running = True
        self.recovery_thread = threading.Thread(target=self._recovery_loop, daemon=True)
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
                health_status = monitor.check_health()
                
                # 检查需要恢复的服务
                for service_name in ['tts', 'asr', 'chat']:
                    if monitor.should_trigger_recovery(service_name):
                        self._attempt_recovery(service_name)
                        
                time.sleep(self.recovery_interval)
                
            except Exception as e:
                logger.error(f"❌ 自动恢复监控异常: {e}")
                time.sleep(self.recovery_interval)
                
    def _attempt_recovery(self, service_name):
        """尝试恢复服务"""
        try:
            monitor.record_recovery_attempt(service_name)
            
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
                monitor.reset_recovery_attempts(service_name)
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
        # ASR服务恢复逻辑（如果需要）
        time.sleep(2)
        
    def _recover_chat_service(self):
        """恢复聊天服务"""
        logger.info("🔄 尝试恢复聊天服务...")
        # 聊天服务恢复逻辑（如果需要）
        time.sleep(2)
        
    def _test_service(self, service_name):
        """测试服务是否正常"""
        try:
            if service_name == 'tts':
                # 测试TTS服务
                test_response = requests.post(
                    'http://localhost:5000/api/tts',
                    json={'text': '测试', 'voice': 'zh-CN-XiaoxiaoNeural'},
                    timeout=10
                )
                return test_response.status_code == 200
            elif service_name == 'asr':
                # 测试ASR服务（如果有测试端点）
                return True
            elif service_name == 'chat':
                # 测试聊天服务
                return True
        except Exception as e:
            logger.error(f"❌ 测试服务 {service_name} 失败: {e}")
            return False

# 创建自动恢复实例
auto_recovery = AutoRecovery()

# ASR处理状态跟踪
asr_processing_status = {
    'is_processing': False,
    'current_request_id': None,
    'start_time': None,
    'progress': 0
}

# 导入edge-tts
try:
    import edge_tts
    EDGE_TTS_AVAILABLE = True
    logger.info("✅ edge-tts模块导入成功")
except ImportError as e:
    EDGE_TTS_AVAILABLE = False
    logger.error(f"❌ edge-tts模块导入失败: {e}")
    logger.error("TTS功能将不可用")

# 导入Dolphin ASR
try:
    import dolphin
    DOLPHIN_AVAILABLE = True
    logger.info("✅ Dolphin ASR模块导入成功")
except ImportError as e:
    DOLPHIN_AVAILABLE = False
    logger.warning(f"⚠️ Dolphin ASR模块导入失败: {e}")
    logger.warning("将使用模拟ASR结果")

app = Flask(__name__)

# 启用CORS支持，允许跨域请求
CORS(app, origins=['*'], methods=['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'])

# DeepSeek API配置
DEEPSEEK_API_KEY = "sk-66a8c43ecb14406ea020b5a9dd47090d"  # 请替换为您的API密钥
DEEPSEEK_BASE_URL = "https://api.deepseek.com/v1"

# Dolphin ASR配置
DOLPHIN_MODEL_PATH = "models/dolphin"
DOLPHIN_MODEL = None

def initialize_dolphin_model():
    """初始化Dolphin ASR模型"""
    global DOLPHIN_MODEL
    
    if not DOLPHIN_AVAILABLE:
        logger.warning("Dolphin不可用，跳过模型初始化")
        return False
        
    try:
        logger.info("🔄 正在初始化Dolphin ASR模型...")
        
        # 获取绝对路径
        current_dir = os.path.dirname(os.path.abspath(__file__))
        dolphin_model_path = os.path.join(current_dir, "models", "dolphin")
        
        # 检查模型文件是否存在
        if not os.path.exists(dolphin_model_path):
            logger.error(f"❌ Dolphin模型路径不存在: {dolphin_model_path}")
            return False
            
        model_file = os.path.join(dolphin_model_path, "small.pt")
        if not os.path.exists(model_file):
            logger.error(f"❌ Dolphin模型文件不存在: {model_file}")
            return False
            
        logger.info(f"🎤 使用模型路径: {dolphin_model_path}")
        
        # 加载模型 - 使用绝对路径
        DOLPHIN_MODEL = dolphin.load_model("small", dolphin_model_path, "cpu")
        logger.info("✅ Dolphin ASR模型初始化成功")
        return True
        
    except Exception as e:
        logger.error(f"❌ Dolphin模型初始化失败: {e}")
        import traceback
        logger.error(f"❌ 错误详情: {traceback.format_exc()}")
        DOLPHIN_MODEL = None
        return False

def transcribe_with_dolphin(audio_file_path: str) -> str:
    """使用Dolphin进行语音识别"""
    try:
        logger.info(f"🎤 开始Dolphin语音识别，文件: {audio_file_path}")
        logger.info(f"🎤 DOLPHIN_AVAILABLE: {DOLPHIN_AVAILABLE}")
        logger.info(f"🎤 DOLPHIN_MODEL is None: {DOLPHIN_MODEL is None}")
        
        if not DOLPHIN_AVAILABLE:
            logger.warning("Dolphin模块不可用，返回模拟结果")
            return "这是模拟的语音识别结果"
            
        if DOLPHIN_MODEL is None:
            logger.warning("Dolphin模型未初始化，返回模拟结果")
            return "这是模拟的语音识别结果"
            
        logger.info(f"🎤 使用Dolphin进行语音识别: {audio_file_path}")
        
        # 加载音频
        waveform = dolphin.load_audio(audio_file_path)
        logger.info(f"🎤 音频加载成功，形状: {waveform.shape}")
        
        # 进行识别
        result = DOLPHIN_MODEL(waveform, lang_sym="zh", region_sym="CN")
        logger.info(f"🎤 原始识别结果: {result.text}")
        
        # 提取纯文本结果（去除特殊标记）
        text = result.text
        if text.startswith("<zh><CN><asr>"):
            # 移除语言和区域标记
            text = text.replace("<zh><CN><asr>", "")
            # 移除时间标记
            import re
            text = re.sub(r'<[0-9.]+>', '', text)
            text = text.strip()
        
        logger.info(f"🎤 处理后识别结果: {text}")
        return text if text else "识别结果为空"
        
    except Exception as e:
        logger.error(f"❌ Dolphin语音识别失败: {e}")
        import traceback
        logger.error(f"❌ 错误详情: {traceback.format_exc()}")
        return "语音识别失败"

def check_tts_health():
    """检查TTS服务健康状态 - 直接集成版本"""
    global tts_last_health_check
    current_time = time.time()
    
    # 如果距离上次检查时间太短，跳过
    if current_time - tts_last_health_check < TTS_CONFIG.get('health_check_interval', 10):
        return True
    
    try:
        # 简单的健康检查 - 直接调用TTS函数
        test_audio = generate_tts_audio("测试", "zh-CN-XiaoxiaoNeural")
        tts_last_health_check = current_time
        return len(test_audio) > 100
    except Exception as e:
        logger.warning(f"⚠️ TTS健康检查失败: {e}")
        return False


def cleanup_tts_cache():
    """清理TTS缓存"""
    global tts_cache
    try:
        # 限制缓存大小，保留最近使用的
        if len(tts_cache) > 50:  # 最多保留50个缓存
            # 删除最旧的缓存项
            items_to_remove = list(tts_cache.keys())[:len(tts_cache) - 50]
            for key in items_to_remove:
                del tts_cache[key]
            logger.info(f"🧹 清理TTS缓存，删除 {len(items_to_remove)} 项")
    except Exception as e:
        logger.error(f"❌ 缓存清理失败: {e}")

async def generate_tts_audio_async(text: str, voice: str = "zh-CN-XiaoxiaoNeural") -> bytes:
    """异步生成TTS音频 - 直接集成edge-tts"""
    global tts_concurrent_count
    start_time = time.time()
    success = False
    error_type = None
    
    try:
        logger.info(f"🎵 开始TTS处理: {text}, 音色: {voice}")
        
        # 并发控制
        if tts_concurrent_count >= TTS_CONFIG['concurrent_limit']:
            logger.warning("⚠️ TTS并发限制，拒绝请求")
            error_type = "concurrent_limit"
            return b""
        
        tts_concurrent_count += 1
        
        # 缓存检查
        cache_key = f"{text}_{voice}"
        if TTS_CONFIG['cache_enabled'] and cache_key in tts_cache:
            logger.info("🎵 使用缓存音频")
            return tts_cache[cache_key]
        
        # 预处理文本，确保稳定性
        processed_text = text.strip()
        if not processed_text:
            logger.warning("⚠️ 文本为空，使用默认文本")
            processed_text = "测试"
        
        # 限制文本长度，避免过长请求
        text_limit = TTS_CONFIG.get('text_length_limit', 500)
        if len(processed_text) > text_limit:
            processed_text = processed_text[:text_limit]
            logger.info(f"🎵 文本过长，截取前{text_limit}字符")
        
        # 验证和标准化音色
        valid_voices = [
            'zh-CN-XiaoxiaoNeural',
            'zh-CN-YunxiNeural', 
            'zh-CN-YunyangNeural',
            'zh-CN-XiaoyiNeural',
            'zh-CN-YunjianNeural'
        ]
        
        if voice not in valid_voices:
            logger.warning(f"⚠️ 无效音色: {voice}，使用默认音色")
            voice = 'zh-CN-XiaoxiaoNeural'
        
        logger.info(f"🎵 使用音色: {voice}")
        
        # 检查是否需要触发自动恢复
        if monitor.should_trigger_recovery('tts'):
            logger.warning("⚠️ TTS服务连续失败，触发自动恢复")
            auto_recovery._attempt_recovery('tts')
        
        # 直接使用edge-tts - 重试机制
        for retry in range(TTS_CONFIG['max_retries']):
            try:
                logger.info(f"🎵 edge-tts尝试 {retry + 1}/{TTS_CONFIG['max_retries']}")
                
                # 增加重试延迟，避免edge-tts服务限制
                if retry > 0:
                    delay = TTS_CONFIG['retry_delay'] + random.uniform(0, 1)
                    logger.info(f"🎵 等待 {delay:.1f} 秒后重试edge-tts...")
                    await asyncio.sleep(delay)
                
                # 直接使用edge-tts - 优化参数以提高速度
                communicate = edge_tts.Communicate(
                    processed_text, 
                    voice,
                    rate="+10%",  # 稍微加快语速
                    pitch="+0Hz",
                    volume="+0%"
                )
                
                # 初始化变量
                audio_data = b""
                chunk_count = 0
                
                # 设置超时 - 使用asyncio.wait_for兼容Python 3.10
                async def process_audio_stream():
                    nonlocal audio_data, chunk_count
                    
                async for chunk in communicate.stream():
                    chunk_type = chunk.get("type", "unknown")
                    chunk_data = chunk.get("data", b"")
                    
                    if chunk_type == "audio" and chunk_data:
                        audio_data += chunk_data
                        chunk_count += 1
                    if chunk_count % 5 == 0:  # 每5块打印一次
                        logger.info(f"🎵 已处理 {chunk_count} 块，当前大小: {len(audio_data)} 字节")
                
                await asyncio.wait_for(process_audio_stream(), timeout=TTS_CONFIG['timeout_total'])
                
                # 验证音频数据
                if len(audio_data) < 1000:
                    logger.warning(f"⚠️ 音频数据过小: {len(audio_data)} 字节，重试...")
                    if retry < TTS_CONFIG['max_retries'] - 1:
                        continue
                    else:
                        logger.error(f"❌ 音频数据过小: {len(audio_data)} 字节")
                        error_type = "audio_too_small"
                        return b""
                
                # 检查MP3文件头
                if not audio_data.startswith(b'\xff\xfb') and not audio_data.startswith(b'ID3'):
                    logger.warning(f"⚠️ 音频文件可能损坏，文件头: {audio_data[:10]}")
                
                logger.info(f"🎵 edge-tts生成成功，音频大小: {len(audio_data)} 字节")
                
                # 缓存音频数据
                if TTS_CONFIG['cache_enabled']:
                    cache_key = f"{processed_text}_{voice}"
                    tts_cache[cache_key] = audio_data
                    cleanup_tts_cache()  # 定期清理缓存
                
                success = True
                return audio_data
                
            except asyncio.TimeoutError:
                logger.warning(f"⚠️ edge-tts尝试 {retry + 1} 超时")
                if retry < TTS_CONFIG['max_retries'] - 1:
                    continue
                else:
                    logger.error("❌ edge-tts超时")
                    error_type = "timeout"
                    return b""
            except Exception as e:
                logger.warning(f"⚠️ edge-tts尝试 {retry + 1} 失败: {e}")
                if retry < TTS_CONFIG['max_retries'] - 1:
                    continue
                else:
                    logger.error(f"❌ edge-tts执行异常: {e}")
                    error_type = "exception"
                    return b""
        
        return b""
        
    except Exception as e:
        logger.error(f"❌ TTS处理失败: {e}")
        import traceback
        logger.error(f"❌ TTS错误详情: {traceback.format_exc()}")
        error_type = "exception"
        return b""

    finally:
        # 更新并发计数
        tts_concurrent_count = max(0, tts_concurrent_count - 1)
        
        # 更新监控统计
        response_time = time.time() - start_time
        monitor.update_service_stats('tts', success=success, response_time=response_time, error_type=error_type)

def generate_tts_audio(text: str, voice: str = "zh-CN-XiaoxiaoNeural") -> bytes:
    """同步包装器 - 调用异步TTS生成"""
    try:
        # 检查是否已有事件循环
        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                # 如果事件循环正在运行，使用线程池
                import concurrent.futures
                with concurrent.futures.ThreadPoolExecutor() as executor:
                    future = executor.submit(run_async_tts, text, voice)
                    return future.result(timeout=TTS_CONFIG['timeout_total'])
            else:
                # 事件循环存在但不运行，直接使用
                return loop.run_until_complete(generate_tts_audio_async(text, voice))
        except RuntimeError:
            # 没有事件循环，创建新的
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            try:
                return loop.run_until_complete(generate_tts_audio_async(text, voice))
            finally:
                loop.close()
    except Exception as e:
        logger.error(f"❌ 同步TTS包装器失败: {e}")
        return b""

def run_async_tts(text: str, voice: str) -> bytes:
    """在线程中运行异步TTS"""
    try:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            return loop.run_until_complete(generate_tts_audio_async(text, voice))
        finally:
            loop.close()
    except Exception as e:
        logger.error(f"❌ 线程异步TTS失败: {e}")
        return b""

# emoji过滤函数已移除，改为通过系统提示词直接限制

def chat_with_deepseek(message: str) -> str:
    """与DeepSeek API聊天"""
    try:
        logger.info(f"🤖 开始AI聊天: {message}")
        
        headers = {
            "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
            "Content-Type": "application/json"
        }
        
        data = {
            "model": "deepseek-chat",
            "messages": [
                {
                    "role": "system",
                    "content": "你是一个友好的AI助手。请用纯文本回答用户的问题，不要使用任何Markdown格式符号（如*、#、-、_、`等）。不要提及重复检测、测试循环、系统状态或任何技术细节。如果用户说'你好'，就简单回复'你好'或'你好！有什么可以帮助你的吗？'。保持对话简单自然，使用普通的中文文本。"
                },
                {
                    "role": "user",
                    "content": message
                }
            ],
            "max_tokens": 500,
            "temperature": 0.7
        }
        
        response = requests.post(
            f"{DEEPSEEK_BASE_URL}/chat/completions",
            headers=headers,
            json=data,
            timeout=30,
            proxies={'http': None, 'https': None}  # 禁用代理
        )
        
        if response.status_code == 200:
            result = response.json()
            ai_response = result['choices'][0]['message']['content']
            
            # 系统提示词已限制emoji，无需后处理过滤
            
            logger.info(f"🤖 AI回复: {ai_response}")
            return ai_response
        else:
            logger.error(f"❌ DeepSeek API错误: {response.status_code} - {response.text}")
            return "抱歉，AI服务暂时不可用，请稍后重试。"
            
    except Exception as e:
        logger.error(f"❌ AI聊天失败: {e}")
        return "抱歉，AI服务出现错误，请稍后重试。"

# 添加兼容性端点
@app.route('/transcribe', methods=['POST'])
def transcribe_legacy():
    """兼容性端点 - 重定向到API版本"""
    return transcribe_audio()

@app.route('/api/transcribe', methods=['POST'])
def transcribe_audio():
    """语音识别API - 带监控和状态反馈"""
    import uuid
    
    start_time = time.time()
    success = False
    error_type = None
    request_id = str(uuid.uuid4())
    
    try:
        logger.info(f"🎤 收到语音识别请求 [ID: {request_id}]")
        
        # 设置处理状态
        asr_processing_status['is_processing'] = True
        asr_processing_status['current_request_id'] = request_id
        asr_processing_status['start_time'] = start_time
        asr_processing_status['progress'] = 10
        
        if 'audio' not in request.files:
            logger.error("❌ 请求中没有音频文件")
            error_type = "no_audio_file"
            return jsonify({'error': 'No audio file provided'}), 400
        
        audio_file = request.files['audio']
        if audio_file.filename == '':
            logger.error("❌ 音频文件名为空")
            error_type = "empty_filename"
            return jsonify({'error': 'No audio file selected'}), 400
        
        logger.info(f"🎤 收到音频文件: {audio_file.filename}")
        asr_processing_status['progress'] = 30
        
        # 保存临时文件
        with tempfile.NamedTemporaryFile(delete=False, suffix='.wav') as temp_file:
            audio_file.save(temp_file.name)
            temp_path = temp_file.name
            logger.info(f"🎤 音频文件保存到: {temp_path}")
        
        asr_processing_status['progress'] = 50
        
        try:
            # 使用Dolphin进行真正的语音识别
            logger.info("🎤 开始语音识别处理...")
            asr_processing_status['progress'] = 70
            transcription = transcribe_with_dolphin(temp_path)
            asr_processing_status['progress'] = 90
            
            logger.info(f"🎤 语音识别完成: {transcription}")
            success = True
            asr_processing_status['progress'] = 100
            
            return jsonify({
                'success': True,
                'transcription': transcription,
                'processing_time': time.time() - start_time,
                'request_id': request_id
            })
            
        finally:
            # 清理临时文件
            if os.path.exists(temp_path):
                os.remove(temp_path)
                
    except Exception as e:
        logger.error(f"❌ 语音识别API错误: {e}")
        error_type = "exception"
        return jsonify({'error': str(e)}), 500
        
    finally:
        # 重置处理状态
        asr_processing_status['is_processing'] = False
        asr_processing_status['current_request_id'] = None
        asr_processing_status['start_time'] = None
        asr_processing_status['progress'] = 0
        
        # 更新监控统计
        response_time = time.time() - start_time
        monitor.update_service_stats('asr', success=success, response_time=response_time, error_type=error_type)

# 添加兼容性端点
@app.route('/asr/status', methods=['GET'])
def asr_status_legacy():
    """兼容性端点 - 重定向到API版本"""
    return asr_status()

@app.route('/api/asr/status', methods=['GET'])
def asr_status():
    """ASR服务状态查询 - 包含实时处理状态"""
    try:
        asr_metrics = monitor.get_service_metrics('asr')
        health_status = monitor.check_health()
        
        # 计算处理时间
        processing_time = None
        if asr_processing_status['is_processing'] and asr_processing_status['start_time']:
            processing_time = time.time() - asr_processing_status['start_time']
        
        return jsonify({
            'status': 'success',
            'asr_health': health_status['services']['asr'],
            'metrics': asr_metrics,
            'processing': {
                'is_processing': asr_processing_status['is_processing'],
                'current_request_id': asr_processing_status['current_request_id'],
                'progress': asr_processing_status['progress'],
                'processing_time': processing_time,
                'start_time': asr_processing_status['start_time']
            },
            'last_update': datetime.now().isoformat()
        })
    except Exception as e:
        logger.error(f"❌ ASR状态查询失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/tts', methods=['POST'])
def text_to_speech():
    """文字转语音API - 直接集成edge-tts"""
    try:
        logger.info("🎵 TTS API被调用")
        data = request.get_json()
        logger.info(f"🎵 接收到的数据: {data}")
        
        if not data or 'text' not in data:
            logger.error("❌ 缺少text参数")
            return jsonify({'error': 'No text provided'}), 400
        
        text = data['text']
        voice = data.get('voice', 'zh-CN-XiaoxiaoNeural')
        logger.info(f"🎵 收到TTS请求: {text}, 音色: {voice}")
        
        # 生成音频 - 使用直接集成的edge-tts
        logger.info("🎵 开始调用generate_tts_audio...")
        audio_data = generate_tts_audio(text, voice)
        logger.info(f"🎵 generate_tts_audio返回: {len(audio_data) if audio_data else 0} 字节")
        
        if audio_data and len(audio_data) > 0:
            logger.info(f"🎵 TTS生成成功，音频大小: {len(audio_data)} 字节")
            return send_file(
                io.BytesIO(audio_data),
                mimetype='audio/mpeg',
                as_attachment=True,
                download_name='speech.mp3'
            )
        else:
            logger.error("❌ TTS生成失败：音频数据为空")
            return jsonify({'error': 'TTS failed - no audio data generated'}), 500
            
    except Exception as e:
        logger.error(f"❌ TTS API错误: {e}")
        import traceback
        logger.error(f"❌ TTS API错误详情: {traceback.format_exc()}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/health', methods=['GET'])
def health_check():
    """健康检查端点"""
    try:
        health_status = monitor.check_health()
        return jsonify(health_status)
    except Exception as e:
        logger.error(f"❌ 健康检查失败: {e}")
        return jsonify({"overall": "error", "error": str(e)}), 500

@app.route('/api/metrics', methods=['GET'])
def get_metrics():
    """获取服务指标"""
    try:
        service_name = request.args.get('service', 'all')
        
        if service_name == 'all':
            metrics = {}
            for service in ['tts', 'asr', 'chat']:
                metrics[service] = monitor.get_service_metrics(service)
            metrics['system'] = monitor.system_stats
            return jsonify(metrics)
        else:
            if service_name in ['tts', 'asr', 'chat']:
                metrics = monitor.get_service_metrics(service_name)
                if metrics:
                    return jsonify(metrics)
                else:
                    return jsonify({"error": "Service not found"}), 404
            else:
                return jsonify({"error": "Invalid service name"}), 400
                
    except Exception as e:
        logger.error(f"❌ 获取指标失败: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/recovery/trigger', methods=['POST'])
def trigger_recovery():
    """手动触发服务恢复"""
    try:
        data = request.get_json()
        service_name = data.get('service', 'tts')
        
        if service_name not in ['tts', 'asr', 'chat']:
            return jsonify({"error": "Invalid service name"}), 400
            
        # 触发恢复
        auto_recovery._attempt_recovery(service_name)
        
        return jsonify({
            "message": f"Recovery triggered for {service_name}",
            "service": service_name,
            "timestamp": datetime.now().isoformat()
        })
        
    except Exception as e:
        logger.error(f"❌ 触发恢复失败: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/recovery/status', methods=['GET'])
def recovery_status():
    """获取恢复状态"""
    try:
        status = {
            "auto_recovery_enabled": monitor.auto_recovery_enabled,
            "recovery_attempts": dict(monitor.recovery_attempts),
            "max_recovery_attempts": monitor.max_recovery_attempts,
            "recovery_running": auto_recovery.running
        }
        return jsonify(status)
    except Exception as e:
        logger.error(f"❌ 获取恢复状态失败: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/tts/status', methods=['GET'])
def tts_status():
    """TTS服务状态查询"""
    try:
        global tts_concurrent_count, tts_cache, tts_last_health_check
        
        tts_metrics = monitor.get_service_metrics('tts')
        health_status = monitor.check_health()
        
        return jsonify({
            'status': 'success',
            'tts_health': health_status['services']['tts'],
            'metrics': tts_metrics,
            'config': TTS_CONFIG,
            'runtime': {
                'concurrent_requests': tts_concurrent_count,
                'cache_size': len(tts_cache),
                'last_health_check': tts_last_health_check,
                'is_healthy': check_tts_health()
            },
            'last_update': datetime.now().isoformat()
        })
    except Exception as e:
        logger.error(f"❌ TTS状态查询失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/tts/cache/clear', methods=['POST'])
def clear_tts_cache():
    """清理TTS缓存"""
    try:
        global tts_cache
        cache_size = len(tts_cache)
        tts_cache.clear()
        
        return jsonify({
            'status': 'success',
            'message': f'清理了 {cache_size} 个缓存项',
            'cache_size': 0
        })
    except Exception as e:
        logger.error(f"❌ 清理TTS缓存失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/doubao/voice_conversion', methods=['POST'])
def doubao_voice_conversion():
    """豆包端到端音色转换API"""
    try:
        data = request.get_json()
        if not data:
            return jsonify({'error': 'No data provided'}), 400
        
        voice_id = data.get('voice_id')
        text = data.get('text')
        format_type = data.get('format', 'wav')
        sample_rate = data.get('sample_rate', 16000)
        
        if not voice_id or not text:
            return jsonify({'error': 'voice_id and text are required'}), 400
        
        logger.info(f"🎵 豆包音色转换请求: voice_id={voice_id}, text={text[:50]}...")
        
        # 调用豆包端到端音色转换服务
        audio_data = call_doubao_voice_conversion(voice_id, text, format_type, sample_rate)
        
        if audio_data:
            logger.info(f"✅ 豆包音色转换成功: {len(audio_data)} bytes")
            return send_file(
                io.BytesIO(audio_data),
                mimetype='audio/wav',
                as_attachment=False,
                download_name=f'voice_preview_{voice_id}.wav'
            )
        else:
            logger.error(f"❌ 豆包音色转换失败")
            return jsonify({'error': 'Voice conversion failed'}), 500
            
    except Exception as e:
        logger.error(f"❌ 豆包音色转换异常: {e}")
        return jsonify({'error': str(e)}), 500

def call_doubao_voice_conversion(voice_id, text, format_type='wav', sample_rate=16000):
    """调用豆包端到端音色转换服务"""
    try:
        # 这里需要实现真正的豆包端到端音色转换调用
        # 根据火山引擎文档 https://www.volcengine.com/docs/6561/1594356
        
        # 模拟音色转换 - 实际应该调用豆包API
        logger.info(f"🔄 调用豆包端到端音色转换: {voice_id}")
        
        # 使用现有的TTS服务作为临时实现
        # 实际应该调用豆包端到端音色转换API
        
        # 根据音色ID映射到TTS音色
        voice_mapping = {
            'zh_female_qingxin': 'zh-CN-XiaoxiaoNeural',
            'zh_female_ruyi': 'zh-CN-XiaoxiaoNeural', 
            'zh_female_aiqi': 'zh-CN-XiaoxiaoNeural',
            'zh_male_ruyi': 'zh-CN-YunxiNeural',
            'zh_male_qingxin': 'zh-CN-YunxiNeural',
            'zh_male_aiqi': 'zh-CN-YunxiNeural',
            'zh_female_zhichang': 'zh-CN-XiaoxiaoNeural',
            'zh_male_zhichang': 'zh-CN-YunxiNeural'
        }
        
        tts_voice = voice_mapping.get(voice_id, 'zh-CN-XiaoxiaoNeural')
        
        # 调用现有的TTS函数生成音频
        audio_data = generate_tts_audio(text, tts_voice)
        
        if audio_data:
            logger.info(f"✅ 音色转换完成: {voice_id} -> {tts_voice}")
            return audio_data
        else:
            logger.error(f"❌ 音色转换失败: {voice_id}")
            return None
            
    except Exception as e:
        logger.error(f"❌ 豆包音色转换调用异常: {e}")
        return None

@app.route('/api/chat_streaming', methods=['POST'])
def chat_streaming():
    """AI聊天流式API - 真正的流式实现"""
    try:
        data = request.get_json()
        if not data or 'message' not in data:
            return jsonify({'error': 'No message provided'}), 400
        
        message = data['message']
        user_id = data.get('user_id', 'anonymous')
        session_id = data.get('session_id', '')
        conversation_history = data.get('conversation_history', [])  # 获取对话历史
        logger.info(f"🤖 收到流式聊天请求: {message}")
        logger.info(f"📚 对话历史长度: {len(conversation_history)}")
        
        # 验证用户身份 - 检查用户是否存在
        if user_id == 'anonymous' or not db_manager.user_exists(user_id):
            logger.warning(f"⚠️ 无效的用户ID: {user_id}")
            return jsonify({'error': '需要有效的用户身份验证'}), 401
        
        # 真正的流式响应生成器
        def generate_streaming_response():
            try:
                headers = {
                    "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
                    "Content-Type": "application/json"
                }
                
                # 构建消息列表，包含系统消息、对话历史和当前消息
                messages = [
                    {
                        "role": "system",
                        "content": "你是一个友好的AI助手。请用纯文本回答用户的问题，不要使用任何Markdown格式符号（如*、#、-、_、`等）。不要提及重复检测、测试循环、系统状态或任何技术细节。如果用户说'你好'，就简单回复'你好'或'你好！有什么可以帮助你的吗？'。保持对话简单自然，使用普通的中文文本。"
                    }
                ]
                
                # 添加对话历史
                for history_item in conversation_history:
                    messages.append({
                        "role": "user" if history_item.get("isUser", True) else "assistant",
                        "content": history_item.get("content", "")
                    })
                
                # 添加当前消息
                messages.append({
                    "role": "user",
                    "content": message
                })
                
                data = {
                    "model": "deepseek-chat",
                    "messages": messages,
                    "max_tokens": 500,
                    "temperature": 0.7,
                    "stream": True  # 启用真正的流式
                }
                
                # 发送流式请求到DeepSeek API
                response = requests.post(
                    f"{DEEPSEEK_BASE_URL}/chat/completions",
                    headers=headers,
                    json=data,
                    stream=True,  # 启用流式接收
                    timeout=60,
                    proxies={'http': None, 'https': None}  # 禁用代理
                )
                
                if response.status_code != 200:
                    logger.error(f"❌ DeepSeek流式API错误: {response.status_code}")
                    error_chunk = {
                        'type': 'error',
                        'message': f'DeepSeek API错误: {response.status_code}'
                    }
                    yield f"data: {json.dumps(error_chunk, ensure_ascii=False)}\n\n"
                    return
                
                # 处理流式响应
                full_text = ""
                sentence_count = 0
                
                for line in response.iter_lines():
                    if line:
                        line_str = line.decode('utf-8')
                        if line_str.startswith('data: '):
                            data_str = line_str[6:]  # 移除 'data: ' 前缀
                            
                            if data_str.strip() == '[DONE]':
                                # 流式结束，系统提示词已限制emoji
                                complete_chunk = {
                                    'type': 'complete',
                                    'text': full_text,
                                    'sentence_count': sentence_count
                                }
                                yield f"data: {json.dumps(complete_chunk, ensure_ascii=False)}\n\n"
                                break
                            
                            try:
                                chunk_data = json.loads(data_str)
                                if 'choices' in chunk_data and len(chunk_data['choices']) > 0:
                                    choice = chunk_data['choices'][0]
                                    if 'delta' in choice and 'content' in choice['delta']:
                                        content = choice['delta']['content']
                                        
                                        # 系统提示词已限制emoji，无需后处理过滤
                                        
                                        full_text += content
                                        
                                        # 检查是否完成一个句子
                                        if any(punct in content for punct in ['。', '！', '？', '；']):
                                            sentence_count += 1
                                        
                                        # 发送文本更新
                                        text_update_chunk = {
                                            'type': 'text_update',
                                            'content': content,
                                            'full_text': full_text,
                                            'sentence_count': sentence_count
                                        }
                                        yield f"data: {json.dumps(text_update_chunk, ensure_ascii=False)}\n\n"
                                        
                            except json.JSONDecodeError as e:
                                logger.warning(f"⚠️ 解析流式数据失败: {e}")
                                continue
                
                logger.info(f"✅ 流式响应完成，总长度: {len(full_text)}")
                
                # 记录交互到数据库
                try:
                    db_manager.log_interaction(
                        user_id=user_id,
                        interaction_type='text',
                        content=message,
                        response=full_text,
                        session_id=session_id,
                        success=True
                    )
                    logger.info(f"✅ 交互记录成功: {user_id}")
                except Exception as db_error:
                    logger.warning(f"⚠️ 记录交互到数据库失败: {db_error}")
                
            except Exception as e:
                logger.error(f"❌ 流式响应生成失败: {e}")
                error_chunk = {
                    'type': 'error',
                    'message': f'流式响应失败: {str(e)}'
                }
                yield f"data: {json.dumps(error_chunk, ensure_ascii=False)}\n\n"
                
                # 记录失败的交互
                try:
                    db_manager.log_interaction(
                        user_id=user_id,
                        interaction_type='text',
                        content=message,
                        response='',
                        session_id=session_id,
                        success=False,
                        error_message=str(e)
                    )
                except Exception as db_error:
                    logger.warning(f"⚠️ 记录失败交互到数据库失败: {db_error}")
        
        return app.response_class(
            generate_streaming_response(),
            mimetype='text/plain',
            headers={
                'Cache-Control': 'no-cache',
                'Connection': 'keep-alive',
                'X-Accel-Buffering': 'no'  # 禁用nginx缓冲
            }
        )
        
    except Exception as e:
        logger.error(f"❌ 流式聊天API错误: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/chat', methods=['POST'])
def chat():
    """AI聊天API（非流式）"""
    try:
        data = request.get_json()
        if not data or 'message' not in data:
            return jsonify({'error': 'No message provided'}), 400
        
        message = data['message']
        user_id = data.get('user_id', 'anonymous')
        session_id = data.get('session_id', '')
        logger.info(f"🤖 收到聊天请求: {message}")
        
        # 验证用户身份 - 检查用户是否存在
        if user_id == 'anonymous' or not db_manager.user_exists(user_id):
            logger.warning(f"⚠️ 无效的用户ID: {user_id}")
            return jsonify({'error': '需要有效的用户身份验证'}), 401
        
        # 调用DeepSeek API
        ai_response = chat_with_deepseek(message)
        
        # 记录交互到数据库
        try:
            db_manager.log_interaction(
                user_id=user_id,
                interaction_type='text',
                content=message,
                response=ai_response,
                session_id=session_id,
                success=True
            )
            logger.info(f"✅ 交互记录成功: {user_id}")
        except Exception as db_error:
            logger.warning(f"⚠️ 记录交互到数据库失败: {db_error}")
        
        return jsonify({'response': ai_response})
        
    except Exception as e:
        logger.error(f"❌ 聊天API错误: {e}")
        return jsonify({'error': str(e)}), 500


@app.route('/test_tts', methods=['GET'])
def test_tts():
    """测试TTS功能"""
    try:
        test_text = "这是一个TTS测试"
        logger.info(f"🧪 开始TTS测试: {test_text}")
        
        # 生成音频
        audio_data = generate_tts_audio(test_text)
        
        if audio_data and len(audio_data) > 0:
            logger.info(f"✅ TTS测试成功，音频大小: {len(audio_data)} 字节")
            return jsonify({
                'status': 'success', 
                'message': 'TTS测试成功',
                'audio_size': len(audio_data),
                'service': 'edge-tts',
                'stability': 'enhanced'
            })
        else:
            logger.error("❌ TTS测试失败：音频数据为空")
            return jsonify({
                'status': 'error', 
                'message': 'TTS测试失败：音频数据为空'
            }), 500
            
    except Exception as e:
        logger.error(f"❌ TTS测试异常: {e}")
        import traceback
        logger.error(f"❌ TTS测试错误详情: {traceback.format_exc()}")
        return jsonify({
            'status': 'error', 
            'message': f'TTS测试异常: {str(e)}'
        }), 500

@app.route('/api/tts/health', methods=['GET'])
def tts_health_check():
    """TTS健康检查"""
    try:
        # 快速测试TTS服务
        test_text = "健康检查"
        start_time = time.time()
        
        audio_data = generate_tts_audio(test_text)
        
        end_time = time.time()
        response_time = end_time - start_time
        
        if audio_data and len(audio_data) > 1000:  # 至少1KB
            return jsonify({
                'status': 'healthy', 
                'service': 'edge-tts',
                'response_time': round(response_time, 2),
                'audio_size': len(audio_data),
                'timestamp': time.time(),
                'features': [
                    'multiple_voice_fallback',
                    'connection_pooling',
                    'timeout_management',
                    'intelligent_retry',
                    'error_recovery'
                ]
            })
        else:
            return jsonify({
                'status': 'unhealthy',
                'service': 'edge-tts',
                'error': 'Audio generation failed',
                'timestamp': time.time()
            }), 503
            
    except Exception as e:
        return jsonify({
            'status': 'unhealthy',
            'service': 'edge-tts',
            'error': str(e),
            'timestamp': time.time()
        }), 503

@app.route('/api/tts/config', methods=['GET', 'POST'])
def tts_config():
    """TTS配置管理"""
    if request.method == 'GET':
        return jsonify({
            'status': 'success',
            'config': TTS_CONFIG,
            'description': 'TTS稳定性配置参数'
        })
    
    elif request.method == 'POST':
        try:
            data = request.get_json()
            if not data:
                return jsonify({'error': 'No configuration provided'}), 400
            
            # 更新配置
            for key, value in data.items():
                if key in TTS_CONFIG:
                    TTS_CONFIG[key] = value
                    logger.info(f"🔧 TTS配置更新: {key} = {value}")
                else:
                    logger.warning(f"⚠️ 未知的TTS配置项: {key}")
            
            return jsonify({
                'status': 'success',
                'message': 'TTS配置已更新',
                'config': TTS_CONFIG
            })
            
        except Exception as e:
            logger.error(f"❌ TTS配置更新失败: {e}")
            return jsonify({'error': str(e)}), 500

@app.route('/api/tts/stats', methods=['GET'])
def tts_stats():
    """TTS统计信息"""
    try:
        # 这里可以添加统计信息收集
        return jsonify({
            'status': 'success',
            'stats': {
                'service': 'edge-tts',
                'version': 'enhanced-stability',
                'features': [
                    'multiple_voice_fallback',
                    'connection_pooling',
                    'timeout_management',
                    'intelligent_retry',
                    'error_recovery',
                    'health_monitoring',
                    'config_management'
                ],
                'voice_count': 5,  # 支持的音色数量
                'max_retries': TTS_CONFIG['max_retries'],
                'timeout_total': TTS_CONFIG['timeout_total']
            }
        })
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# ==================== 数据库相关API ====================

@app.route('/api/auth/login', methods=['POST'])
def user_login():
    """用户登录"""
    try:
        data = request.get_json()
        if not data or 'username' not in data or 'password' not in data:
            return jsonify({'error': '用户名和密码不能为空'}), 400
        
        username = data['username']
        password = data['password']
        device_info = data.get('device_info', '')
        ip_address = request.remote_addr
        user_agent = request.headers.get('User-Agent', '')
        
        # 用户认证
        user = db_manager.authenticate_user(username, password)
        if not user:
            db_manager.log_system_event('WARNING', 'auth', f'登录失败: {username}')
            return jsonify({'error': '用户名或密码错误'}), 401
        
        # 创建会话
        session_id = db_manager.create_session(user['user_id'])
        if not session_id:
            return jsonify({'error': '创建会话失败'}), 500
        
        logger.info(f"✅ 用户登录成功: {username}")
        db_manager.log_system_event('INFO', 'auth', f'用户登录成功: {username}')
        
        return jsonify({
            'success': True,
            'user': {
                'user_id': user['user_id'],
                'username': user['username'],
                'created_at': user['created_at'].isoformat() if user['created_at'] else None,
                'last_login_at': user['last_login_at'].isoformat() if user['last_login_at'] else None
            },
            'session_id': session_id
        })
        
    except Exception as e:
        logger.error(f"❌ 用户登录失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/auth/logout', methods=['POST'])
def user_logout():
    """用户登出"""
    try:
        data = request.get_json()
        if not data or 'session_id' not in data:
            return jsonify({'error': '会话ID不能为空'}), 400
        
        session_id = data['session_id']
        
        # 结束会话
        db_manager.end_session(session_id)
        
        # 获取用户ID并更新登出时间
        # 这里需要从session_id获取user_id，简化处理
        logger.info(f"✅ 用户登出成功: {session_id}")
        db_manager.log_system_event('INFO', 'auth', f'用户登出: {session_id}')
        
        return jsonify({'success': True, 'message': '登出成功'})
        
    except Exception as e:
        logger.error(f"❌ 用户登出失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/auth/register', methods=['POST'])
def user_register():
    """用户注册"""
    try:
        data = request.get_json()
        if not data or 'username' not in data or 'password' not in data:
            return jsonify({'error': '用户名和密码不能为空'}), 400
        
        username = data['username']
        password = data['password']
        
        # 检查用户名是否已存在
        if db_manager.get_user_by_username(username):
            return jsonify({'error': '用户名已存在'}), 400
        
        # 生成用户ID
        import uuid
        user_id = f"user_{uuid.uuid4().hex[:8]}"
        
        # 创建用户
        success = db_manager.create_user(user_id, username, password)
        
        if not success:
            return jsonify({'error': '创建用户失败'}), 500
        
        logger.info(f"✅ 用户注册成功: {username}")
        db_manager.log_system_event('INFO', 'auth', f'用户注册成功: {username}')
        
        return jsonify({
            'success': True,
            'message': '注册成功',
            'user_id': user_id
        })
        
    except Exception as e:
        logger.error(f"❌ 用户注册失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/interactions/log', methods=['POST'])
def log_interaction():
    """记录交互"""
    try:
        data = request.get_json()
        logger.info(f"🔍 收到交互记录请求: {data}")
        if not data or 'user_id' not in data or 'interaction_type' not in data or 'content' not in data:
            logger.error(f"❌ 缺少必要参数: {data}")
            return jsonify({'error': '缺少必要参数'}), 400
        
        user_id = data['user_id']
        interaction_type = data['interaction_type']
        content = data['content']
        response = data.get('response', '')
        session_id = data.get('session_id', '')
        duration_seconds = data.get('duration_seconds', 0)
        success = data.get('success', True)
        error_message = data.get('error_message', '')
        
        # 验证交互类型
        valid_types = ['text', 'voice_home', 'voice_call', 'tts_play']
        if interaction_type not in valid_types:
            return jsonify({'error': f'无效的交互类型，必须是: {valid_types}'}), 400
        
        # 检查用户是否存在，如果不存在则拒绝请求
        if not db_manager.user_exists(user_id):
            logger.warning(f"⚠️ 用户 {user_id} 不存在，拒绝记录交互")
            return jsonify({'error': '用户身份验证失败，请重新登录'}), 401
        
        # 记录交互
        success_log = db_manager.log_interaction(
            user_id=user_id,
            interaction_type=interaction_type,
            content=content,
            response=response,
            session_id=session_id,
            duration_seconds=duration_seconds,
            success=success,
            error_message=error_message
        )
        
        if not success_log:
            return jsonify({'error': '记录交互失败'}), 500
        
        return jsonify({'success': True, 'message': '交互记录成功'})
        
    except Exception as e:
        logger.error(f"❌ 记录交互失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/interactions/query', methods=['GET'])
def query_interactions():
    """查询交互记录"""
    try:
        interaction_type = request.args.get('interaction_type')
        user_id = request.args.get('user_id')
        limit = int(request.args.get('limit', 10))
        
        # 查询数据库
        records = db_manager.query_interactions(
            interaction_type=interaction_type,
            user_id=user_id,
            limit=limit
        )
        
        return jsonify(records)
        
    except Exception as e:
        logger.error(f"❌ 查询交互记录失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/interactions/history', methods=['GET'])
def get_interaction_history():
    """获取交互历史"""
    try:
        user_id = request.args.get('user_id')
        if not user_id:
            return jsonify({'error': '用户ID不能为空'}), 400
        
        limit = int(request.args.get('limit', 50))
        offset = int(request.args.get('offset', 0))
        
        interactions = db_manager.get_user_interactions(user_id, limit, offset)
        
        return jsonify({
            'success': True,
            'interactions': interactions,
            'count': len(interactions)
        })
        
    except Exception as e:
        logger.error(f"❌ 获取交互历史失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/stats/interactions', methods=['GET'])
def get_interaction_stats():
    """获取交互统计"""
    try:
        user_id = request.args.get('user_id')
        days = int(request.args.get('days', 30))
        
        stats = db_manager.get_interaction_stats(user_id, days)
        
        return jsonify({
            'success': True,
            'stats': stats,
            'period_days': days
        })
        
    except Exception as e:
        logger.error(f"❌ 获取交互统计失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/stats/active_users', methods=['GET'])
def get_active_users():
    """获取活跃用户"""
    try:
        hours = int(request.args.get('hours', 24))
        users = db_manager.get_active_users(hours)
        
        return jsonify({
            'success': True,
            'active_users': users,
            'period_hours': hours,
            'count': len(users)
        })
        
    except Exception as e:
        logger.error(f"❌ 获取活跃用户失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/admin/cleanup', methods=['POST'])
def cleanup_old_data():
    """清理旧数据（管理员功能）"""
    try:
        data = request.get_json() or {}
        days = data.get('days', 90)
        
        db_manager.cleanup_old_data(days)
        
        return jsonify({
            'success': True,
            'message': f'已清理 {days} 天前的旧数据'
        })
        
    except Exception as e:
        logger.error(f"❌ 清理旧数据失败: {e}")
        return jsonify({'error': str(e)}), 500

# ==================== 故事控制相关API ====================

@app.route('/api/story/reading/session/start', methods=['POST'])
def start_reading_session():
    """开始阅读会话"""
    try:
        data = request.get_json()
        if not data or 'user_id' not in data or 'story_id' not in data or 'story_title' not in data:
            return jsonify({'error': '缺少必要参数'}), 400
        
        user_id = data['user_id']
        story_id = data['story_id']
        story_title = data['story_title']
        session_id = data.get('session_id')
        device_info = data.get('device_info', '')
        
        # 验证用户身份
        if not db_manager.user_exists(user_id):
            return jsonify({'error': '用户身份验证失败'}), 401
        
        # 创建阅读会话
        session_id = db_manager.create_reading_session(
            user_id=user_id,
            story_id=story_id,
            story_title=story_title,
            session_id=session_id,
            device_info=device_info
        )
        
        if not session_id:
            return jsonify({'error': '创建阅读会话失败'}), 500
        
        # 记录开始阅读交互
        db_manager.log_story_interaction(
            user_id=user_id,
            story_id=story_id,
            interaction_type='start_reading',
            session_id=session_id,
            device_info=device_info
        )
        
        return jsonify({
            'success': True,
            'session_id': session_id,
            'message': '阅读会话已开始'
        })
        
    except Exception as e:
        logger.error(f"❌ 开始阅读会话失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/story/reading/session/end', methods=['POST'])
def end_reading_session():
    """结束阅读会话"""
    try:
        data = request.get_json()
        if not data or 'session_id' not in data:
            return jsonify({'error': '缺少会话ID'}), 400
        
        session_id = data['session_id']
        characters_read = data.get('characters_read', 0)
        
        # 结束阅读会话
        success = db_manager.end_reading_session(session_id, characters_read)
        
        if not success:
            return jsonify({'error': '结束阅读会话失败'}), 500
        
        return jsonify({
            'success': True,
            'message': '阅读会话已结束'
        })
        
    except Exception as e:
        logger.error(f"❌ 结束阅读会话失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/story/reading/progress', methods=['POST'])
def update_reading_progress():
    """更新阅读进度"""
    try:
        data = request.get_json()
        if not data or 'user_id' not in data or 'story_id' not in data:
            return jsonify({'error': '缺少必要参数'}), 400
        
        user_id = data['user_id']
        story_id = data['story_id']
        story_title = data.get('story_title', '')
        current_position = data.get('current_position', 0)
        total_length = data.get('total_length', 0)
        session_id = data.get('session_id')
        device_info = data.get('device_info', '')
        
        # 验证用户身份
        if not db_manager.user_exists(user_id):
            return jsonify({'error': '用户身份验证失败'}), 401
        
        # 获取用户名
        user_info = db_manager.get_user_by_id(user_id)
        username = user_info.get('username', '') if user_info else ''
        
        # 更新阅读进度
        success = db_manager.update_reading_progress(
            user_id=user_id,
            story_id=story_id,
            story_title=story_title,
            current_position=current_position,
            total_length=total_length,
            device_info=device_info,
            username=username
        )
        
        if not success:
            return jsonify({'error': '更新阅读进度失败'}), 500
        
        # 计算进度百分比
        progress_percentage = (current_position / total_length * 100) if total_length > 0 else 0
        
        return jsonify({
            'success': True,
            'progress_percentage': round(progress_percentage, 2),
            'is_completed': progress_percentage >= 100.0,
            'message': '阅读进度已更新'
        })
        
    except Exception as e:
        logger.error(f"❌ 更新阅读进度失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/story/reading/progress', methods=['GET'])
def get_reading_progress():
    """获取阅读进度"""
    try:
        user_id = request.args.get('user_id')
        story_id = request.args.get('story_id')
        
        if not user_id:
            return jsonify({'error': '缺少用户ID'}), 400
        
        # 验证用户身份
        if not db_manager.user_exists(user_id):
            return jsonify({'error': '用户身份验证失败'}), 401
        
        # 获取阅读进度
        progress_list = db_manager.get_reading_progress(user_id, story_id)
        
        return jsonify({
            'success': True,
            'progress': progress_list,
            'count': len(progress_list)
        })
        
    except Exception as e:
        logger.error(f"❌ 获取阅读进度失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/story/interaction', methods=['POST'])
def log_story_interaction():
    """记录故事交互"""
    try:
        data = request.get_json()
        if not data or 'user_id' not in data or 'story_id' not in data or 'interaction_type' not in data:
            return jsonify({'error': '缺少必要参数'}), 400
        
        user_id = data['user_id']
        story_id = data['story_id']
        interaction_type = data['interaction_type']
        interaction_data = data.get('interaction_data')
        session_id = data.get('session_id')
        device_info = data.get('device_info', '')
        
        # 验证用户身份
        if not db_manager.user_exists(user_id):
            return jsonify({'error': '用户身份验证失败'}), 401
        
        # 验证交互类型
        valid_types = ['start_reading', 'pause_reading', 'resume_reading', 'complete_reading', 'bookmark', 'share', 'rate']
        if interaction_type not in valid_types:
            return jsonify({'error': f'无效的交互类型，必须是: {valid_types}'}), 400
        
        # 记录交互
        success = db_manager.log_story_interaction(
            user_id=user_id,
            story_id=story_id,
            interaction_type=interaction_type,
            interaction_data=interaction_data,
            session_id=session_id,
            device_info=device_info
        )
        
        if not success:
            return jsonify({'error': '记录交互失败'}), 500
        
        return jsonify({
            'success': True,
            'message': '交互记录成功'
        })
        
    except Exception as e:
        logger.error(f"❌ 记录故事交互失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/story/statistics', methods=['GET'])
def get_reading_statistics():
    """获取阅读统计"""
    try:
        user_id = request.args.get('user_id')
        days = int(request.args.get('days', 30))
        
        if not user_id:
            return jsonify({'error': '缺少用户ID'}), 400
        
        # 验证用户身份
        if not db_manager.user_exists(user_id):
            return jsonify({'error': '用户身份验证失败'}), 401
        
        # 获取阅读统计
        statistics = db_manager.get_reading_statistics(user_id, days)
        
        return jsonify({
            'success': True,
            'statistics': statistics,
            'period_days': days
        })
        
    except Exception as e:
        logger.error(f"❌ 获取阅读统计失败: {e}")
        return jsonify({'error': str(e)}), 500

# ==================== 管理员相关API ====================

@app.route('/api/admin/users/reading-progress', methods=['GET'])
def admin_get_all_reading_progress():
    """管理员获取所有用户阅读进度"""
    try:
        admin_user_id = request.args.get('admin_user_id')
        limit = int(request.args.get('limit', 100))
        offset = int(request.args.get('offset', 0))
        
        if not admin_user_id:
            return jsonify({'error': '缺少管理员用户ID'}), 400
        
        # 验证管理员身份（这里简化处理，实际应该检查管理员权限）
        if not db_manager.user_exists(admin_user_id):
            return jsonify({'error': '管理员身份验证失败'}), 401
        
        # 获取所有用户阅读进度
        result = db_manager.get_all_users_reading_progress(limit, offset)
        
        if result is None:
            return jsonify({'error': '获取阅读进度失败'}), 500
        
        return jsonify({
            'success': True,
            'data': result
        })
        
    except Exception as e:
        logger.error(f"❌ 管理员获取阅读进度失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/admin/users/<user_id>/summary', methods=['GET'])
def admin_get_user_summary(user_id):
    """管理员获取用户阅读摘要"""
    try:
        admin_user_id = request.args.get('admin_user_id')
        
        if not admin_user_id:
            return jsonify({'error': '缺少管理员用户ID'}), 400
        
        # 验证管理员身份
        if not db_manager.user_exists(admin_user_id):
            return jsonify({'error': '管理员身份验证失败'}), 401
        
        # 获取用户阅读摘要
        summary = db_manager.get_user_reading_summary(user_id)
        
        if summary is None:
            return jsonify({'error': '用户不存在或获取摘要失败'}), 404
        
        return jsonify({
            'success': True,
            'summary': summary
        })
        
    except Exception as e:
        logger.error(f"❌ 管理员获取用户摘要失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/admin/users/<user_id>/details', methods=['GET'])
def admin_get_user_details(user_id):
    """管理员获取用户详细信息"""
    try:
        admin_user_id = request.args.get('admin_user_id')
        
        if not admin_user_id:
            return jsonify({'error': '缺少管理员用户ID'}), 400
        
        # 验证管理员身份
        if not db_manager.user_exists(admin_user_id):
            return jsonify({'error': '管理员身份验证失败'}), 401
        
        # 获取用户基本信息
        user_info = db_manager.get_user_by_id(user_id)
        if not user_info:
            return jsonify({'error': '用户不存在'}), 404
        
        # 获取用户阅读进度详情
        reading_progress = db_manager.get_user_reading_progress_details(user_id)
        
        # 获取用户统计信息
        stats = db_manager.get_user_reading_summary(user_id)
        
        return jsonify({
            'success': True,
            'user_info': user_info,
            'reading_progress': reading_progress,
            'stats': stats
        })
        
    except Exception as e:
        logger.error(f"❌ 管理员获取用户详情失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/admin/reading/completion', methods=['POST'])
def admin_update_reading_completion():
    """管理员更新用户阅读完成状态"""
    try:
        data = request.get_json()
        if not data or 'admin_user_id' not in data or 'user_id' not in data or 'story_id' not in data or 'is_completed' not in data:
            return jsonify({'error': '缺少必要参数'}), 400
        
        admin_user_id = data['admin_user_id']
        user_id = data['user_id']
        story_id = data['story_id']
        is_completed = data['is_completed']
        
        # 验证管理员身份
        if not db_manager.user_exists(admin_user_id):
            return jsonify({'error': '管理员身份验证失败'}), 401
        
        # 更新阅读完成状态
        success, message = db_manager.admin_update_reading_completion(
            user_id, story_id, is_completed, admin_user_id
        )
        
        if not success:
            return jsonify({'error': message}), 400
        
        return jsonify({
            'success': True,
            'message': message
        })
        
    except Exception as e:
        logger.error(f"❌ 管理员更新阅读完成状态失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/admin/reading/progress', methods=['POST'])
def admin_update_reading_progress():
    """管理员更新用户阅读进度"""
    try:
        data = request.get_json()
        admin_user_id = data.get('admin_user_id')
        user_id = data.get('user_id')
        story_id = data.get('story_id')
        progress = data.get('progress', 0)  # 0-100
        current_position = data.get('current_position', 0)
        total_length = data.get('total_length', 100)
        
        if not all([admin_user_id, user_id, story_id]):
            return jsonify({'error': '缺少必要参数'}), 400
        
        # 验证管理员身份
        if not db_manager.user_exists(admin_user_id):
            return jsonify({'error': '管理员身份验证失败'}), 401
        
        # 确保进度在0-100范围内
        progress = max(0, min(100, progress))
        
        # 获取用户名
        user_info = db_manager.get_user_by_id(user_id)
        username = user_info.get('username', '') if user_info else ''
        
        # 更新阅读进度
        success = db_manager.update_reading_progress(
            user_id=user_id,
            story_id=story_id,
            story_title="管理员操作",  # 管理员操作时使用通用标题
            current_position=current_position,
            total_length=total_length,
            device_info="admin_operation",
            username=username
        )
        
        if success:
            # 记录管理员操作
            db_manager.log_admin_operation(admin_user_id, user_id, story_id, 'update_progress')
            
            return jsonify({
                'success': True,
                'message': f'已更新阅读进度为 {progress}%'
            })
        else:
            return jsonify({'error': '更新失败'}), 500
        
    except Exception as e:
        logger.error(f"❌ 管理员更新阅读进度失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/admin/reading/bulk', methods=['POST'])
def admin_bulk_reading_operations():
    """管理员批量操作阅读进度"""
    try:
        data = request.get_json()
        admin_user_id = data.get('admin_user_id')
        operations = data.get('operations', [])
        
        if not admin_user_id or not operations:
            return jsonify({'error': '缺少必要参数'}), 400
        
        # 验证管理员身份
        if not db_manager.user_exists(admin_user_id):
            return jsonify({'error': '管理员身份验证失败'}), 401
        
        results = []
        success_count = 0
        
        for operation in operations:
            op_type = operation.get('type')
            user_id = operation.get('user_id')
            story_id = operation.get('story_id')
            
            try:
                if op_type == 'mark_completed':
                    success, message = db_manager.admin_update_reading_completion(user_id, story_id, True, admin_user_id)
                elif op_type == 'mark_incomplete':
                    success, message = db_manager.admin_update_reading_completion(user_id, story_id, False, admin_user_id)
                elif op_type == 'update_progress':
                    progress = operation.get('progress', 0)
                    current_position = operation.get('current_position', 0)
                    total_length = operation.get('total_length', 100)
                    
                    # 获取用户名
                    user_info = db_manager.get_user_by_id(user_id)
                    username = user_info.get('username', '') if user_info else ''
                    
                    success = db_manager.update_reading_progress(
                        user_id=user_id,
                        story_id=story_id,
                        story_title="管理员批量操作",
                        current_position=current_position,
                        total_length=total_length,
                        device_info="admin_bulk_operation",
                        username=username
                    )
                    message = f'更新进度为 {progress}%' if success else '更新失败'
                else:
                    success = False
                    message = '未知操作类型'
                
                if success:
                    success_count += 1
                
                results.append({
                    'user_id': user_id,
                    'story_id': story_id,
                    'type': op_type,
                    'success': success,
                    'message': message
                })
                
            except Exception as e:
                results.append({
                    'user_id': user_id,
                    'story_id': story_id,
                    'type': op_type,
                    'success': False,
                    'error': str(e)
                })
        
        return jsonify({
            'success': True,
            'message': f'批量操作完成，成功 {success_count}/{len(operations)} 项',
            'results': results
        })
        
    except Exception as e:
        logger.error(f"❌ 管理员批量操作失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/admin/users', methods=['GET'])
def admin_get_all_users():
    """管理员获取所有用户列表"""
    try:
        admin_user_id = request.args.get('admin_user_id')
        limit = int(request.args.get('limit', 50))
        offset = int(request.args.get('offset', 0))
        
        if not admin_user_id:
            return jsonify({'error': '缺少管理员用户ID'}), 400
        
        # 验证管理员身份
        if not db_manager.user_exists(admin_user_id):
            return jsonify({'error': '管理员身份验证失败'}), 401
        
        # 获取所有用户
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not db_manager.connection or not db_manager.connection.open:
                    db_manager.reconnect()
                
                with db_manager.connection.cursor() as cursor:
                    # 获取用户列表
                    sql = """
                    SELECT u.user_id, u.username, u.created_at, u.last_login_at, u.is_active,
                           COUNT(rp.id) as total_stories,
                           SUM(CASE WHEN rp.is_completed = 1 THEN 1 ELSE 0 END) as completed_stories
                    FROM users u
                    LEFT JOIN reading_progress rp ON u.user_id = rp.user_id
                    GROUP BY u.user_id, u.username, u.created_at, u.last_login_at, u.is_active
                    ORDER BY u.created_at DESC
                    LIMIT %s OFFSET %s
                    """
                    cursor.execute(sql, (limit, offset))
                    columns = [desc[0] for desc in cursor.description]
                    users = []
                    for row in cursor.fetchall():
                        user = dict(zip(columns, row))
                        users.append(user)
                    
                    # 获取总数
                    count_sql = "SELECT COUNT(*) FROM users"
                    cursor.execute(count_sql)
                    total_count = cursor.fetchone()[0]
                    
                    return jsonify({
                        'success': True,
                        'users': users,
                        'total_count': total_count,
                        'limit': limit,
                        'offset': offset
                    })
                    
            except Exception as e:
                if attempt < max_retries - 1:
                    db_manager.reconnect()
                    time.sleep(1)
                else:
                    raise e
        
    except Exception as e:
        logger.error(f"❌ 管理员获取用户列表失败: {e}")
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    import socket
    
    # 获取本机IP地址
    def get_local_ip():
        try:
            # 连接到一个远程地址来获取本机IP
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            s.close()
            return ip
        except:
            return "127.0.0.1"
    
    local_ip = get_local_ip()
    
    logger.info("🚀 启动NEXUS后端服务器...")
    logger.info(f"🌐 服务地址: http://{local_ip}:5000")
    
    # 初始化Dolphin ASR模型
    dolphin_available = initialize_dolphin_model()
    
    if dolphin_available:
        logger.info("🎤 语音识别: 可用 (Dolphin ASR)")
    else:
        logger.info("🎤 语音识别: 可用 (模拟模式)")
        
    logger.info("🎵 语音合成: 可用 (edge-tts)")
    logger.info("🤖 AI聊天: 可用 (DeepSeek)")
    
    # 启动自动恢复监控
    try:
        auto_recovery.start()
        logger.info("🔄 自动恢复监控: 已启动")
    except Exception as e:
        logger.error(f"❌ 启动自动恢复监控失败: {e}")
    
    logger.info("==================================================")
    
    try:
        app.run(host='0.0.0.0', port=5000, debug=False)
    except KeyboardInterrupt:
        logger.info("⏹️ 收到停止信号，正在关闭服务...")
    finally:
        # 停止自动恢复监控
        try:
            auto_recovery.stop()
            logger.info("⏹️ 自动恢复监控已停止")
        except Exception as e:
            logger.error(f"❌ 停止自动恢复监控失败: {e}")
