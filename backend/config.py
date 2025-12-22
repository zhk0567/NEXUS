# -*- coding: utf-8 -*-
"""
配置模块 - 所有配置常量
"""
import os

# 服务器IP配置
PUBLIC_IP = "115.191.10.104"  # 公网IP（供客户端外网访问）
PRIVATE_IP = "172.31.0.3"  # 私网IP（服务器本地访问）

# DeepSeek API配置
DEEPSEEK_API_KEY = "sk-beeadaf084744dcab32aeeb1534789f4"
DEEPSEEK_BASE_URL = "https://api.deepseek.com/v1"

# 火山引擎（小美）API配置
VOLCANO_ASR_URL = "https://openspeech.bytedance.com/api/v1/asr"
VOLCANO_TTS_URL = "https://openspeech.bytedance.com/api/v1/tts"
VOLCANO_ACCESS_KEY = "2AmQpw1aTtuIaRdMcrPX7K4PChZWus82"  # Access Token
VOLCANO_APP_ID = "9065017641"
VOLCANO_RESOURCE_ID = "volc.speech.dialog"
VOLCANO_SECRET_KEY = "1-QSPcc75MckNFBAJqQK63KJTNhbDu0d"  # Secret Key (用于URL签名)
VOLCANO_APP_KEY = "PlgvMymc7f3tQnJ6"  # App Key (用于Headers中的X-Api-App-Key)
VOLCANO_REALTIME_WS_URL = "wss://openspeech.bytedance.com/api/v3/realtime/dialogue"

# 小美语音对话配置
DOUBAO_BOT_NAME = "小美"
DOUBAO_SYSTEM_ROLE = (
    "你是一个智能的AI助手，名字叫小美。你使用活泼灵动的女声，"
    "性格开朗，热爱生活。你的说话风格简洁明了，语速适中，语调自然。"
    "你可以帮助用户解答问题、聊天、提供建议等。请用友好、专业的语气与用户交流。"
)
DOUBAO_SPEAKING_STYLE = (
    "你的说话风格简洁明了，语速适中，语调自然，能够进行智能对话。"
)
DOUBAO_TTS_SPEAKER = "zh_female_vv_jupiter_bigtts"  # vv音色，活泼灵动的女声

# Dolphin ASR配置
DOLPHIN_MODEL_PATH = "models/dolphin"
DOLPHIN_MODEL = None

# TTS配置
TTS_CONFIG = {
    'max_retries': 2,
    'timeout_total': 60,
    'timeout_connect': 10,
    'retry_delay': 0.5,
    'max_consecutive_failures': 3,
    'recovery_delay': 3,
    'concurrent_limit': 3,
    'cache_enabled': True,
    'health_check_interval': 30,
    'use_edge_tts_only': True,
    'text_length_limit': 1000,
    'enable_compression': True,
    'fast_mode': True,
    'chunk_size': 1024
}

# 允许登录的用户白名单
ALLOWED_USERS = {
    'user01', 'user02', 'user03', 'user04', 'user05',
    'user06', 'user07', 'user08', 'user09', 'user10'
}

