# -*- coding: utf-8 -*-
"""
实时语音WebSocket路由模块
"""
import uuid
from flask import request, jsonify
from backend.logger_config import logger
from backend.config import (
    VOLCANO_REALTIME_WS_URL,
    VOLCANO_ACCESS_KEY,
    VOLCANO_APP_ID,
    VOLCANO_RESOURCE_ID,
    VOLCANO_SECRET_KEY,
    VOLCANO_APP_KEY
)
import hmac
import hashlib
import base64
import time
from urllib.parse import quote


def register_realtime_routes(app):
    """注册实时语音WebSocket相关路由"""

    @app.route('/api/realtime/ws_config', methods=['GET'])
    def get_realtime_ws_config():
        """获取实时语音WebSocket配置"""
        try:
            session_id = request.args.get('session_id', str(uuid.uuid4()))
            
            # 使用固定的资源ID（根据火山引擎文档，实时语音使用固定resource_id）
            resource_id = VOLCANO_RESOURCE_ID
            
            # 生成认证参数
            timestamp = str(int(time.time()))
            nonce = str(uuid.uuid4())
            
            # 构建签名字符串（按照火山引擎文档格式：appid\nresourceid\ntimestamp\nnonce）
            # 注意：参数顺序很重要，必须是：appid, resourceid, timestamp, nonce
            sign_string = f"{VOLCANO_APP_ID}\n{resource_id}\n{timestamp}\n{nonce}"
            
            # 计算签名（使用Secret Key进行签名）
            signature = base64.b64encode(
                hmac.new(
                    VOLCANO_SECRET_KEY.encode('utf-8'),
                    sign_string.encode('utf-8'),
                    hashlib.sha256
                ).digest()
            ).decode('utf-8')
            
            # 构建WebSocket URL（带认证参数）
            ws_url = f"{VOLCANO_REALTIME_WS_URL}?appid={VOLCANO_APP_ID}&resourceid={VOLCANO_RESOURCE_ID}&timestamp={timestamp}&nonce={nonce}&signature={quote(signature)}"
            
            # 构建认证headers（根据火山引擎API文档，使用X-Api-*前缀）
            # 注意：错误信息"request and grant appid mismatch"说明appid不匹配
            # 可能需要确保URL参数和headers中的appid一致
            headers = {
                "Authorization": f"Bearer {VOLCANO_ACCESS_KEY}",
                "X-Api-App-Key": VOLCANO_APP_KEY,
                "X-Api-Access-Key": VOLCANO_ACCESS_KEY,
                "X-Api-Resource-Id": VOLCANO_RESOURCE_ID,
                "X-Api-Request-Id": nonce,  # 使用nonce作为request id
                # 尝试添加appid到headers（可能有助于解决appid不匹配问题）
                "X-Api-App-Id": VOLCANO_APP_ID,
            }
            
            # 记录详细信息用于调试
            logger.info(f"📡 实时语音WebSocket配置请求: session_id={session_id}")
            logger.info(f"🔗 WebSocket URL: {ws_url}")
            logger.info(f"📋 URL参数: appid={VOLCANO_APP_ID}, resourceid={resource_id}, timestamp={timestamp}, nonce={nonce}")
            logger.info(f"🔐 签名: {signature[:50]}... (长度: {len(signature)})")
            logger.info(f"📦 Headers: {headers}")
            
            return jsonify({
                "success": True,
                "websocket": {
                    "base_url": ws_url,
                    "resource_id": resource_id,
                    "headers": headers
                }
            })
        except Exception as e:
            logger.error(f"获取实时语音WebSocket配置失败: {e}")
            return jsonify({
                "success": False,
                "error": str(e)
            }), 500

