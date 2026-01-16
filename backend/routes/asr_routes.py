# -*- coding: utf-8 -*-
"""
ASR路由模块
"""
import os
import time
import uuid
import tempfile
from flask import request, jsonify
from backend.logger_config import logger
from backend.asr_service import (
    transcribe_with_dolphin,
    asr_processing_status,
    initialize_dolphin_model
)
from backend.service_monitor import ServiceMonitor
from datetime import datetime


def register_asr_routes(app, monitor: ServiceMonitor):
    """注册ASR相关路由"""

    @app.route('/transcribe', methods=['POST'])
    def transcribe_legacy():
        """兼容性端点 - 重定向到API版本"""
        return transcribe_audio()

    @app.route('/api/transcribe', methods=['POST'])
    def transcribe_audio():
        """语音识别API - 带监控和状态反馈"""
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
                return jsonify({
                    'success': False,
                    'error': 'No audio file provided'
                }), 400

            audio_file = request.files['audio']
            if audio_file.filename == '':
                logger.error("❌ 音频文件名为空")
                error_type = "empty_filename"
                return jsonify({
                    'success': False,
                    'error': 'No audio file selected'
                }), 400

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
                    'text': transcription,  # Android代码期望的字段名
                    'transcription': transcription,  # 保持向后兼容
                    'processing_time': time.time() - start_time,
                    'duration': time.time() - start_time,
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
            monitor.update_service_stats(
                'asr', success=success, response_time=response_time,
                error_type=error_type
            )

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
            if (asr_processing_status['is_processing'] and
                    asr_processing_status['start_time']):
                processing_time = (
                    time.time() - asr_processing_status['start_time']
                )

            return jsonify({
                'status': 'success',
                'asr_health': health_status['services']['asr'],
                'metrics': asr_metrics,
                'processing': {
                    'is_processing': asr_processing_status['is_processing'],
                    'current_request_id': (
                        asr_processing_status['current_request_id']
                    ),
                    'progress': asr_processing_status['progress'],
                    'processing_time': processing_time,
                    'start_time': asr_processing_status['start_time']
                },
                'last_update': datetime.now().isoformat()
            })
        except Exception as e:
            logger.error(f"❌ ASR状态查询失败: {e}")
            return jsonify({'error': str(e)}), 500

