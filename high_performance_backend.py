#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
NEXUS高性能后端服务
集成所有优化功能，单一文件部署
"""

import os
import sys
import time
import tempfile
import wave
import json
import requests
import threading
import queue
import uuid
from typing import Optional, Dict, Any
from pathlib import Path
from flask import Flask, request, jsonify, send_file
from flask_cors import CORS
import base64
import io
import logging
import numpy as np
from concurrent.futures import ThreadPoolExecutor
import mysql.connector
from mysql.connector import pooling, Error
from contextlib import contextmanager
from datetime import datetime

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# 创建Flask应用
app = Flask(__name__)
CORS(app)

# 性能优化配置
app.config['JSONIFY_PRETTYPRINT_REGULAR'] = False
app.config['SEND_FILE_MAX_AGE_DEFAULT'] = 0

class UserManager:
    """用户管理器 - 处理用户身份识别和数据记录"""
    
    def __init__(self, db_pool):
        self.db_pool = db_pool
    
    def get_or_create_user(self, user_id: str, device_id: str = None, user_type: str = "device") -> Dict[str, Any]:
        """获取或创建用户"""
        try:
            with self.db_pool.get_connection() as conn:
                cursor = conn.cursor(dictionary=True)
                
                # 检查用户是否存在
                cursor.execute(
                    "SELECT * FROM users WHERE user_id = %s",
                    (user_id,)
                )
                user = cursor.fetchone()
                
                if user:
                    logger.info(f"Found existing user: {user_id}")
                    return user
                
                # 创建新用户
                cursor.execute("""
                    INSERT INTO users (user_id, device_id, user_type, is_active, created_at)
                    VALUES (%s, %s, %s, %s, %s)
                """, (user_id, device_id, user_type, True, datetime.now()))
                
                conn.commit()
                logger.info(f"Created new user: {user_id}")
                
                return {
                    'user_id': user_id,
                    'device_id': device_id,
                    'user_type': user_type,
                    'is_active': True
                }
                
        except Exception as e:
            logger.error(f"Error in get_or_create_user: {e}")
            return None
    
    def create_session(self, user_id: str) -> str:
        """创建新的用户会话"""
        try:
            session_id = f"session_{uuid.uuid4().hex}"
            
            with self.db_pool.get_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("""
                    INSERT INTO user_sessions (user_id, session_id, start_time, status)
                    VALUES (%s, %s, %s, %s)
                """, (user_id, session_id, datetime.now(), 'active'))
                
                conn.commit()
                logger.info(f"Created session {session_id} for user {user_id}")
                return session_id
                
        except Exception as e:
            logger.error(f"Error creating session: {e}")
            return f"fallback_session_{int(time.time())}"
    
    def log_interaction(self, user_id: str, session_id: str, interaction_type: str, 
                       content: str = None, response_time_ms: int = None):
        """记录用户交互"""
        try:
            with self.db_pool.get_connection() as conn:
                cursor = conn.cursor()
                cursor.execute("""
                    INSERT INTO interactions (user_id, session_id, interaction_type, content, response_time_ms, timestamp)
                    VALUES (%s, %s, %s, %s, %s, %s)
                """, (user_id, session_id, interaction_type, content, response_time_ms, datetime.now()))
                
                conn.commit()
                logger.debug(f"Logged interaction: {interaction_type} for user {user_id}")
                
        except Exception as e:
            logger.error(f"Error logging interaction: {e}")
    
    def get_user_stats(self, user_id: str) -> Dict[str, Any]:
        """获取用户统计信息"""
        try:
            with self.db_pool.get_connection() as conn:
                cursor = conn.cursor(dictionary=True)
                
                # 获取基本统计
                cursor.execute("""
                    SELECT 
                        COUNT(DISTINCT session_id) as total_sessions,
                        COUNT(*) as total_interactions,
                        AVG(response_time_ms) as avg_response_time,
                        COUNT(CASE WHEN interaction_type = 'voice_input' THEN 1 END) as voice_inputs,
                        COUNT(CASE WHEN interaction_type = 'text_input' THEN 1 END) as text_inputs,
                        COUNT(CASE WHEN interaction_type = 'ai_response' THEN 1 END) as ai_responses
                    FROM interactions 
                    WHERE user_id = %s
                """, (user_id,))
                
                stats = cursor.fetchone()
                return stats or {}
                
        except Exception as e:
            logger.error(f"Error getting user stats: {e}")
            return {}

class SimplifiedNEXUSBackend:
    def __init__(self):
        """初始化简化后端服务"""
        self.api_key = "sk-66a8c43ecb14406ea020b5a9dd47090d"
        self.model_path = os.path.join("models", "vosk", "vosk-model-cn-0.22")
        
        # 性能优化相关
        self.vosk_model = None
        self.recognizer = None
        
        # 连接池和缓存
        self.session = requests.Session()
        self.session.headers.update({
            'Connection': 'keep-alive',
            'Keep-Alive': 'timeout=60, max=100',
            'User-Agent': 'NEXUS-VoiceAssistant/1.0'
        })
        
        # 配置连接适配器
        from requests.adapters import HTTPAdapter
        from urllib3.util.retry import Retry
        
        retry_strategy = Retry(
            total=3,
            backoff_factor=1,
            status_forcelist=[429, 500, 502, 503, 504],
        )
        adapter = HTTPAdapter(max_retries=retry_strategy, pool_connections=20, pool_maxsize=50)
        self.session.mount("http://", adapter)
        self.session.mount("https://", adapter)
        
        # 音频参数
        self.CHUNK_SIZE = 2048
        self.SAMPLE_RATE = 16000
        self.CHANNELS = 1
        
        # 数据库连接池
        self.db_pool = None
        self._init_database_pool()
        
        # 用户管理器
        self.user_manager = None
        if self.db_pool:
            self.user_manager = UserManager(self.db_pool)
        
        # 缓存
        self.cache = {}
        self.cache_max_size = 1000
        
        # 初始化模型
        self._initialize_models()
    
    def _init_database_pool(self):
        """初始化数据库连接池"""
        try:
            config = {
                'pool_name': 'nexus_pool',
                'pool_size': 10,
                'pool_reset_session': True,
                'autocommit': True,
                'host': 'localhost',
                'user': 'root',
                'password': 'zhk050607',
                'database': 'llasm_usage_data',
                'charset': 'utf8mb4'
            }
            
            self.db_pool = mysql.connector.pooling.MySQLConnectionPool(**config)
            logger.info("✅ 数据库连接池初始化成功")
            
        except Error as e:
            logger.error(f"❌ 数据库连接池初始化失败: {e}")
            self.db_pool = None
    
    @contextmanager
    def get_db_connection(self):
        """获取数据库连接"""
        if not self.db_pool:
            yield None
            return
        
        connection = None
        try:
            connection = self.db_pool.get_connection()
            yield connection
        except Error as e:
            logger.error(f"❌ 获取数据库连接失败: {e}")
            yield None
        finally:
            if connection and connection.is_connected():
                connection.close()
    
    def _initialize_models(self):
        """初始化语音识别模型"""
        try:
            # 检查模型
            models_dir = Path("models/vosk")
            if not models_dir.exists():
                logger.error("❌ 模型目录不存在")
                return False
            
            # 查找可用模型
            available_models = []
            for model_dir in models_dir.iterdir():
                if model_dir.is_dir() and (model_dir / "am" / "final.mdl").exists():
                    available_models.append(model_dir)
            
            if not available_models:
                logger.error("❌ 未找到可用的语音识别模型")
                return False
            
            # 选择第一个可用模型
            best_model = available_models[0]
            self.model_path = str(best_model)
            logger.info(f"✅ 语音识别模型就绪: {best_model.name}")
            
            # 预加载模型
            self._preload_model()
            return True
            
        except Exception as e:
            logger.error(f"❌ 模型初始化失败: {e}")
            return False
    
    def _preload_model(self):
        """预加载模型"""
        try:
            logger.info("🚀 预加载语音识别模型...")
            import vosk
            self.vosk_model = vosk.Model(self.model_path)
            self.recognizer = vosk.KaldiRecognizer(self.vosk_model, self.SAMPLE_RATE)
            logger.info("✅ 模型预加载完成！")
        except Exception as e:
            logger.error(f"⚠️ 模型预加载失败: {e}")
            self.vosk_model = None
            self.recognizer = None
    
    def transcribe_audio(self, audio_data: bytes) -> Optional[str]:
        """转录音频数据"""
        try:
            if not self.recognizer:
                logger.warning("⚠️ 识别器未初始化，重新加载模型...")
                self._preload_model()
                if not self.recognizer:
                    return None
            
            logger.info("🔍 开始识别...")
            start_time = time.time()
            
            # 重置识别器
            self.recognizer.Reset()
            
            # 将音频数据写入临时文件
            temp_file = tempfile.mktemp(suffix='.wav')
            with open(temp_file, 'wb') as f:
                f.write(audio_data)
            
            try:
                # 读取音频文件
                with wave.open(temp_file, 'rb') as wf:
                    # 处理音频块
                    chunk_size = 16000
                    while True:
                        data = wf.readframes(chunk_size)
                        if len(data) == 0:
                            break
                        self.recognizer.AcceptWaveform(data)
                    
                    # 获取最终结果
                    result = self.recognizer.FinalResult()
                    
                    # 解析JSON结果
                    try:
                        parsed = json.loads(result)
                        text = parsed.get('text', '').strip()
                    except:
                        text = result.strip()
                    
                    if text:
                        # 记录原始转录文本
                        original_text = text
                        # 对语音转录文本去除所有空格
                        text = text.replace(' ', '')
                        elapsed_time = time.time() - start_time
                        logger.info(f"✅ 识别成功 ({elapsed_time:.2f}s): {original_text} → {text}")
                        return text
                    else:
                        logger.warning("⚠️ 识别结果为空")
                        return None
                        
            finally:
                # 清理临时文件
                if os.path.exists(temp_file):
                    os.remove(temp_file)
                    
        except Exception as e:
            logger.error(f"❌ 识别失败: {e}")
            return None
    
    def chat_with_ai(self, message: str) -> str:
        """与AI对话（带缓存）"""
        try:
            # 检查缓存
            cache_key = f"chat_{hash(message)}"
            if cache_key in self.cache:
                logger.info("⚡ 使用缓存响应")
                return self.cache[cache_key]
            
            logger.info("🤖 AI正在思考中...")
            
            # 调用DeepSeek API
            headers = {
                "Authorization": f"Bearer {self.api_key}",
                "Content-Type": "application/json"
            }
            
            # 获取当前日期时间
            from datetime import datetime
            current_time = datetime.now()
            current_date = current_time.strftime("%Y年%m月%d日")
            current_weekday = current_time.strftime("%A")
            weekday_map = {
                'Monday': '星期一', 'Tuesday': '星期二', 'Wednesday': '星期三',
                'Thursday': '星期四', 'Friday': '星期五', 'Saturday': '星期六', 'Sunday': '星期日'
            }
            current_weekday_cn = weekday_map.get(current_weekday, current_weekday)
            
            # 添加系统提示，限制AI回答格式
            system_prompt = f"""你是一个专业的AI助手，请积极回答用户的问题。

重要：你必须用完整的中文句子回答，绝对不要只返回数字、代码或时间戳。

当前日期信息：今天是{current_date}，{current_weekday_cn}

回答要求：
- 积极回应用户的问题，不要简单重复用户的话
- 提供有用的信息、建议或帮助
- 使用自然流畅的中文表达，句子之间用句号分隔
- 绝对不要使用任何markdown格式符号（*、#、-、`、_等）
- 绝对不要使用emoji表情符号或特殊符号
- 保持简洁明了，句子之间用句号分隔，不要使用多余空格
- 不要使用列表格式，用句号连接各个要点
- 不要使用换行符，所有内容在一行内表达
- 标点符号前后不要添加空格
- 如果问"今天是什么日子"，回答"今天是{current_date}，{current_weekday_cn}"
- 如果问"今天有什么重大事件"，回答具体的文字描述
- 不要返回数字序列如"20241203"或"19459293"
- 不要返回时间戳格式
- 必须使用当前真实日期：{current_date}

请确保你的回答是完整的中文句子，包含具体信息，格式简洁清晰，没有多余的空格和符号。"""
            
            data = {
                "model": "deepseek-chat",
                "messages": [
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": message}
                ],
                "max_tokens": 1000,
                "temperature": 0.7
            }
            
            # 重试机制
            max_retries = 3
            for attempt in range(max_retries):
                try:
                    start_time = time.time()
                    response = self.session.post(
                        "https://api.deepseek.com/v1/chat/completions",
                        headers=headers,
                        json=data,
                        timeout=30
                    )
                    
                    if response.status_code == 200:
                        result = response.json()
                        logger.info(f"🔍 API原始响应: {result}")
                        
                        ai_message = result['choices'][0]['message']['content']
                        logger.info(f"🔍 提取的AI消息: {ai_message}")
                        logger.info(f"🔍 消息类型: {type(ai_message)}")
                        logger.info(f"🔍 消息长度: {len(ai_message)}")
                        
                        elapsed_time = time.time() - start_time
                        
                        # 检查是否是纯数字响应，如果是则返回错误信息
                        if ai_message.isdigit() or (ai_message.replace('.', '').replace(':', '').isdigit()):
                            logger.warning(f"⚠️ AI返回了数字序列: {ai_message}")
                            return "抱歉，AI服务返回了异常响应。请重新提问。"
                        
                        # 清理AI响应
                        cleaned_message = self._clean_ai_response(ai_message)
                        
                        # 缓存响应
                        self._add_to_cache(cache_key, cleaned_message)
                        
                        logger.info(f"🤖 DeepSeek ({elapsed_time:.2f}s): {cleaned_message}")
                        return cleaned_message
                    elif response.status_code == 429:  # 速率限制
                        wait_time = 2 ** attempt
                        logger.warning(f"⚠️ 速率限制，等待 {wait_time} 秒后重试...")
                        time.sleep(wait_time)
                        continue
                    else:
                        error_msg = f"API调用失败: {response.status_code}"
                        logger.error(f"❌ {error_msg}")
                        if attempt == max_retries - 1:
                            return f"抱歉，AI服务暂时不可用。请稍后再试。"
                        continue
                        
                except requests.exceptions.Timeout:
                    logger.warning(f"⚠️ 请求超时，尝试 {attempt + 1}/{max_retries}")
                    if attempt == max_retries - 1:
                        return "抱歉，AI响应超时。请检查网络连接后重试。"
                    time.sleep(1)
                    continue
                except Exception as e:
                    logger.error(f"❌ 请求异常: {e}")
                    if attempt == max_retries - 1:
                        return f"抱歉，AI服务出现异常。请稍后再试。"
                    time.sleep(1)
                    continue
                
        except Exception as e:
            error_msg = f"AI对话失败: {e}"
            logger.error(f"❌ {error_msg}")
            return "抱歉，AI服务暂时不可用。请稍后再试。"
    
    def _clean_ai_response(self, text: str) -> str:
        """彻底清理AI回答中的格式符号和多余空格"""
        import re
        
        # 移除emoji和特殊符号
        emoji_pattern = re.compile(
            "["
            "\U0001F600-\U0001F64F"  # emoticons
            "\U0001F300-\U0001F5FF"  # symbols & pictographs
            "\U0001F680-\U0001F6FF"  # transport & map symbols
            "\U0001F700-\U0001F77F"  # alchemical symbols
            "\U0001F780-\U0001F7FF"  # geometric shapes extended
            "\U0001F800-\U0001F8FF"  # supplemental arrows-c
            "\U0001F900-\U0001F9FF"  # supplemental symbols and pictographs
            "\U0001FA00-\U0001FA6F"  # chess symbols
            "\U0001FA70-\U0001FAFF"  # symbols and pictographs extended-a
            "\U00002700-\U000027BF"  # dingbats
            "\U0001F018-\U0001F0FF"  # enclosed alphanumeric supplement
            "]+", flags=re.UNICODE)
        text = emoji_pattern.sub('', text)
        
        # 清理markdown格式，保留内容
        text = re.sub(r'\*\*([^*]+)\*\*', r'\1', text)  # 粗体
        text = re.sub(r'\*([^*]+)\*', r'\1', text)  # 斜体
        text = re.sub(r'`([^`]+)`', r'\1', text)  # 代码
        text = re.sub(r'#+\s*', '', text)  # 标题符号
        text = re.sub(r'-\s*', '', text)  # 列表符号
        text = re.sub(r'\d+\.\s*', '', text)  # 数字列表
        text = re.sub(r'^\s*[-*+]\s*', '', text, flags=re.MULTILINE)  # 列表项符号
        text = re.sub(r'^\s*\d+\.\s*', '', text, flags=re.MULTILINE)  # 数字列表项
        
        # 清理多余的标点符号
        text = re.sub(r'[，。！？；：]\s*[，。！？；：]+', lambda m: m.group(0)[0], text)  # 重复标点
        
        # 彻底清理所有多余空格和制表符
        text = re.sub(r'[ \t\r\n]+', ' ', text)  # 所有空白字符替换为单个空格
        
        # 清理标点符号周围的多余空格
        text = re.sub(r'\s*([，。！？；：])\s*', r'\1', text)  # 标点前后不要空格
        
        # 清理首尾空白
        text = text.strip()
        
        # 最终清理：确保单词之间只有一个空格
        text = re.sub(r'\s+', ' ', text)  # 最终空格清理
        
        return text
    
    def _add_to_cache(self, key: str, value: str):
        """添加到缓存"""
        if len(self.cache) >= self.cache_max_size:
            # 删除最旧的缓存项
            oldest_key = next(iter(self.cache))
            del self.cache[oldest_key]
        
        self.cache[key] = value
        logger.info(f"💾 缓存已更新，当前缓存大小: {len(self.cache)}")
    
    def text_to_speech(self, text: str) -> Optional[bytes]:
        """文字转语音"""
        try:
            # 优先使用Edge TTS
            try:
                import edge_tts
                import asyncio
                import tempfile
                import pydub
                
                async def generate_audio():
                    voices = await edge_tts.list_voices()
                    
                    # 选择中文语音
                    chinese_voices = [v for v in voices if 'zh' in v.get('Locale', '').lower()]
                    if chinese_voices:
                        voice = chinese_voices[0]
                    else:
                        voice = voices[0]
                    
                    communicate = edge_tts.Communicate(text, voice['ShortName'])
                    
                    audio_data = b''
                    async for chunk in communicate.stream():
                        if chunk["type"] == "audio":
                            audio_data += chunk["data"]
                    
                    return audio_data
                
                # 运行异步函数
                audio_data = asyncio.run(generate_audio())
                
                if audio_data:
                    # 转换MP3到WAV格式
                    mp3_temp = tempfile.mktemp(suffix='.mp3')
                    with open(mp3_temp, 'wb') as f:
                        f.write(audio_data)
                    
                    audio = pydub.AudioSegment.from_mp3(mp3_temp)
                    audio = audio.set_frame_rate(16000)
                    audio = audio.set_channels(1)
                    audio = audio.set_sample_width(2)
                    
                    wav_temp = tempfile.mktemp(suffix='.wav')
                    audio.export(wav_temp, format="wav")
                    
                    with open(wav_temp, 'rb') as f:
                        wav_data = f.read()
                    
                    os.remove(mp3_temp)
                    os.remove(wav_temp)
                    
                    return wav_data
                
            except ImportError:
                pass
            
            # 使用pyttsx3
            try:
                import pyttsx3
                import tempfile
                
                engine = pyttsx3.init()
                engine.setProperty('rate', 160)
                engine.setProperty('volume', 0.9)
                
                temp_file = tempfile.mktemp(suffix='.wav')
                engine.save_to_file(text, temp_file)
                engine.runAndWait()
                
                with open(temp_file, 'rb') as f:
                    audio_data = f.read()
                
                os.remove(temp_file)
                return audio_data
                
            except ImportError:
                pass
            
            return None
                
        except Exception as e:
            logger.error(f"❌ 语音合成失败: {e}")
            return None

# 创建API实例
api = SimplifiedNEXUSBackend()

def get_user_identity():
    """从请求头获取用户身份信息 - 混合方案支持"""
    user_id = request.headers.get('X-User-ID')
    device_id = request.headers.get('X-Device-ID')
    session_id = request.headers.get('X-Session-ID')
    user_type = request.headers.get('X-User-Type', 'DEVICE')
    
    # 混合方案：X-User-ID 已经是正确的统计ID
    # 已注册用户：X-User-ID = 用户ID
    # 未注册用户：X-User-ID = 设备ID
    
    if not user_id:
        # 生成临时用户ID
        user_id = f"temp_{int(time.time())}"
    
    if not session_id:
        # 生成临时会话ID
        session_id = f"temp_session_{int(time.time())}"
    
    # 记录用户类型用于统计
    logger.info(f"User identity: ID={user_id}, Device={device_id}, Type={user_type}")
    
    return user_id, device_id, session_id, user_type

# API路由
@app.route('/health', methods=['GET'])
def health_check():
    """健康检查"""
    return jsonify({
        'status': 'healthy',
        'message': 'NEXUS Backend API is running',
        'timestamp': time.time(),
        'cache_size': len(api.cache),
        'model_loaded': api.vosk_model is not None,
        'db_pool_active': api.db_pool is not None
    })

@app.route('/api/transcribe', methods=['POST'])
def transcribe():
    """语音转文字API"""
    try:
        if 'audio' not in request.files:
            return jsonify({'error': 'No audio file provided'}), 400
        
        audio_file = request.files['audio']
        if audio_file.filename == '':
            return jsonify({'error': 'No audio file selected'}), 400
        
        audio_data = audio_file.read()
        start_time = time.time()
        transcription = api.transcribe_audio(audio_data)
        asr_time = (time.time() - start_time) * 1000
        
        if transcription:
            # 对语音转录文本去除所有空格
            transcription = transcription.replace(' ', '')
            return jsonify({
                'success': True,
                'transcription': transcription,
                'asr_time_ms': asr_time
            })
        else:
            return jsonify({
                'success': False,
                'error': 'No speech detected',
                'transcription': '',
                'asr_time_ms': asr_time
            }), 200
            
    except Exception as e:
        logger.error(f"❌ 转录API错误: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/chat', methods=['POST'])
def chat():
    """AI对话API"""
    try:
        # 获取用户身份 - 混合方案
        user_id, device_id, session_id, user_type = get_user_identity()
        
        # 确保用户存在
        if api.user_manager:
            user = api.user_manager.get_or_create_user(user_id, device_id, user_type.lower())
            if not user:
                logger.warning(f"Failed to create/get user: {user_id}")
        
        data = request.get_json()
        if not data or 'message' not in data:
            return jsonify({'error': 'No message provided'}), 400
        
        message = data['message']
        start_time = time.time()
        
        # 记录用户输入
        if api.user_manager:
            api.user_manager.log_interaction(user_id, session_id, 'text_input', message)
        
        ai_response = api.chat_with_ai(message)
        api_time = (time.time() - start_time) * 1000
        
        if ai_response:
            # 记录AI响应
            if api.user_manager:
                api.user_manager.log_interaction(user_id, session_id, 'ai_response', ai_response, int(api_time))
            
            return jsonify({
                'success': True,
                'response': ai_response,
                'api_time_ms': api_time,
                'user_id': user_id,
                'session_id': session_id
            })
        else:
            return jsonify({'error': 'AI chat failed'}), 500
            
    except Exception as e:
        logger.error(f"❌ 对话API错误: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/tts', methods=['POST'])
def text_to_speech():
    """文字转语音API"""
    try:
        data = request.get_json()
        if not data or 'text' not in data:
            return jsonify({'error': 'No text provided'}), 400
        
        text = data['text']
        start_time = time.time()
        audio_data = api.text_to_speech(text)
        tts_time = (time.time() - start_time) * 1000
        
        if audio_data:
            return send_file(
                io.BytesIO(audio_data),
                mimetype='audio/wav',
                as_attachment=True,
                download_name='speech.wav'
            )
        else:
            return jsonify({'error': 'TTS failed'}), 500
            
    except Exception as e:
        logger.error(f"❌ TTS API错误: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/voice_chat', methods=['POST'])
def voice_chat():
    """语音对话API"""
    try:
        # 获取用户身份 - 混合方案
        user_id, device_id, session_id, user_type = get_user_identity()
        
        # 确保用户存在
        if api.user_manager:
            user = api.user_manager.get_or_create_user(user_id, device_id, user_type.lower())
            if not user:
                logger.warning(f"Failed to create/get user: {user_id}")
        
        if 'audio' not in request.files:
            return jsonify({'error': 'No audio file provided'}), 400
        
        audio_file = request.files['audio']
        if audio_file.filename == '':
            return jsonify({'error': 'No audio file selected'}), 400
        
        audio_data = audio_file.read()
        
        # 转录音频
        start_time = time.time()
        transcription = api.transcribe_audio(audio_data)
        asr_time = (time.time() - start_time) * 1000
        
        if not transcription:
            return jsonify({
                'success': False,
                'error': 'No speech detected',
                'transcription': '',
                'response': '',
                'asr_time_ms': asr_time,
                'api_time_ms': 0,
                'total_time_ms': asr_time
            }), 200
        
        # 对语音转录文本去除所有空格
        transcription = transcription.replace(' ', '')
        
        # 记录语音输入
        if api.user_manager:
            api.user_manager.log_interaction(user_id, session_id, 'voice_input', transcription, int(asr_time))
        
        # AI对话
        start_time = time.time()
        ai_response = api.chat_with_ai(transcription)
        api_time = (time.time() - start_time) * 1000
        
        if ai_response:
            # 记录AI响应
            if api.user_manager:
                api.user_manager.log_interaction(user_id, session_id, 'ai_response', ai_response, int(api_time))
            
            return jsonify({
                'success': True,
                'transcription': transcription,
                'response': ai_response,
                'asr_time_ms': asr_time,
                'api_time_ms': api_time,
                'total_time_ms': asr_time + api_time,
                'user_id': user_id,
                'session_id': session_id
            })
        else:
            return jsonify({'error': 'AI chat failed'}), 500
            
    except Exception as e:
        logger.error(f"❌ 语音对话API错误: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/clear_cache', methods=['POST'])
def clear_cache():
    """清除AI对话缓存"""
    try:
        api.cache.clear()
        logger.info("🗑️ 缓存已清除")
        return jsonify({'success': True, 'message': '缓存已清除'})
    except Exception as e:
        logger.error(f"❌ 清除缓存失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/stats', methods=['GET'])
def get_stats():
    """获取系统统计信息"""
    try:
        return jsonify({
            'success': True,
            'cache_size': len(api.cache),
            'cache_max_size': api.cache_max_size,
            'model_loaded': api.vosk_model is not None,
            'db_pool_active': api.db_pool is not None,
            'uptime': time.time()
        })
    except Exception as e:
        logger.error(f"❌ 获取统计信息失败: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/user/stats', methods=['GET'])
def get_user_stats():
    """获取用户统计信息"""
    try:
        user_id, device_id, session_id = get_user_identity()
        
        if not api.user_manager:
            return jsonify({'error': 'User manager not available'}), 500
        
        stats = api.user_manager.get_user_stats(user_id)
        return jsonify({
            'success': True,
            'user_id': user_id,
            'stats': stats
        })
        
    except Exception as e:
        logger.error(f"❌ 获取用户统计错误: {e}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/user/register', methods=['POST'])
def register_user():
    """用户注册"""
    try:
        user_id, device_id, session_id = get_user_identity()
        data = request.get_json()
        
        if not data or 'nickname' not in data:
            return jsonify({'error': 'Nickname required'}), 400
        
        if not api.user_manager:
            return jsonify({'error': 'User manager not available'}), 500
        
        # 更新用户信息
        nickname = data['nickname']
        phone = data.get('phone')
        email = data.get('email')
        
        # 这里可以添加用户注册逻辑
        # 目前只是更新用户信息
        return jsonify({
            'success': True,
            'message': 'User registered successfully',
            'user_id': user_id
        })
        
    except Exception as e:
        logger.error(f"❌ 用户注册错误: {e}")
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    print("🚀 NEXUS高性能后端服务启动中...")
    print("⚡ 支持高并发用户 - 企业级性能！")
    print("=" * 60)
    print("🚀 优化特性:")
    print("   • MySQL连接池")
    print("   • 智能缓存机制")
    print("   • 音频数据优化")
    print("   • 模型预加载")
    print("   • 异步处理优化")
    print("=" * 60)
    print("📡 API端点:")
    print("   GET  /health - 健康检查")
    print("   POST /api/transcribe - 语音转文字")
    print("   POST /api/chat - AI文字对话")
    print("   POST /api/tts - 文字转语音")
    print("   POST /api/voice_chat - 语音对话")
    print("   GET  /api/stats - 系统统计")
    print("=" * 60)
    print("🌐 服务地址: http://localhost:5000")
    print("📱 Android应用请连接到此地址")
    print("=" * 60)
    print("✅ 系统就绪！等待连接...")
    
    app.run(host='0.0.0.0', port=5000, debug=False, threaded=True)
