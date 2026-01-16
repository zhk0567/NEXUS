# -*- coding: utf-8 -*-
"""
TTS路由模块
"""
import io
from flask import request, jsonify, send_file
from backend.logger_config import logger
from backend.tts_service import (
    generate_tts_audio,
    check_tts_health,
    cleanup_tts_cache,
    tts_cache,
    tts_concurrent_count,
    EDGE_TTS_AVAILABLE
)
from backend.config import TTS_CONFIG, DOUBAO_BOT_NAME, DOUBAO_TTS_SPEAKER
from backend.service_monitor import ServiceMonitor


def register_tts_routes(app, monitor: ServiceMonitor):
    """注册TTS相关路由"""

    @app.route('/api/tts', methods=['POST'])
    def text_to_speech():
        """文字转语音API"""
        try:
            logger.info("🎵 TTS API被调用")
            data = request.get_json()
            logger.info(f"🎵 接收到的数据: {data}")

            if not data or 'text' not in data:
                logger.error("❌ 缺少text参数")
                return jsonify({'success': False, 'error': 'No text provided'}), 400

            text = data['text']
            voice = data.get('voice', 'zh-CN-XiaoxiaoNeural')
            logger.info(f"🎵 收到TTS请求: {text}, 音色: {voice}")

            # 生成音频
            logger.info("🎵 开始调用generate_tts_audio...")
            audio_data = generate_tts_audio(text, voice)
            logger.info(
                f"🎵 generate_tts_audio返回: "
                f"{len(audio_data) if audio_data else 0} 字节"
            )

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
                return jsonify({
                    'success': False,
                    'error': 'TTS服务暂时不可用，可能是网络连接问题。请检查：\n1. 网络连接是否正常\n2. 是否可以访问Microsoft TTS服务\n3. 防火墙或代理设置',
                    'error_code': 'TTS_SERVICE_UNAVAILABLE'
                }), 503  # 使用503表示服务暂时不可用

        except Exception as e:
            error_msg = str(e)
            logger.error(f"❌ TTS API错误: {error_msg}")
            import traceback
            logger.error(f"❌ TTS API错误详情: {traceback.format_exc()}")
            
            # 检查是否是edge-tts相关的错误
            if "No audio was received" in error_msg or "NoAudioReceived" in error_msg:
                return jsonify({
                    'success': False,
                    'error': 'TTS服务无法获取音频数据，可能是网络连接问题。请稍后重试或联系管理员检查网络设置。',
                    'error_code': 'TTS_NETWORK_ERROR'
                }), 503
            else:
                return jsonify({
                    'success': False,
                    'error': f'TTS服务错误: {error_msg}',
                    'error_code': 'TTS_ERROR'
                }), 500

    @app.route('/api/tts/status', methods=['GET'])
    def tts_status():
        """获取TTS服务状态"""
        try:
            status = {
                'available': True,
                'concurrent_count': tts_concurrent_count,
                'concurrent_limit': TTS_CONFIG['concurrent_limit'],
                'cache_size': len(tts_cache),
                'cache_enabled': TTS_CONFIG['cache_enabled'],
                'health_check': check_tts_health()
            }
            return jsonify(status)
        except Exception as e:
            logger.error(f"❌ 获取TTS状态失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/tts/cache/clear', methods=['POST'])
    def clear_tts_cache():
        """清理TTS缓存"""
        try:
            cleanup_tts_cache()
            return jsonify({'success': True, 'message': 'TTS缓存已清理'})
        except Exception as e:
            logger.error(f"❌ 清理TTS缓存失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/tts/health', methods=['GET'])
    def tts_health_check():
        """TTS健康检查"""
        try:
            is_healthy = check_tts_health()
            return jsonify({
                'healthy': is_healthy,
                'message': 'TTS服务正常' if is_healthy else 'TTS服务异常'
            })
        except Exception as e:
            logger.error(f"❌ TTS健康检查失败: {e}")
            return jsonify({'healthy': False, 'error': str(e)}), 500

    @app.route('/api/tts/diagnose', methods=['GET'])
    def tts_diagnose():
        """TTS服务诊断 - 检查edge-tts服务可用性"""
        try:
            import asyncio
            from backend.tts_service import run_async_tts
            
            diagnosis = {
                'edge_tts_available': EDGE_TTS_AVAILABLE,
                'service_status': 'unknown',
                'test_result': None,
                'error': None
            }
            
            if not EDGE_TTS_AVAILABLE:
                diagnosis['service_status'] = 'unavailable'
                diagnosis['error'] = 'edge-tts模块未安装或导入失败'
                return jsonify(diagnosis), 200
            
            # 尝试测试TTS服务
            try:
                test_audio = run_async_tts("测试", "zh-CN-XiaoxiaoNeural")
                if test_audio and len(test_audio) > 0:
                    diagnosis['service_status'] = 'available'
                    diagnosis['test_result'] = f'成功生成测试音频 ({len(test_audio)} 字节)'
                else:
                    diagnosis['service_status'] = 'error'
                    diagnosis['error'] = '测试音频生成失败，返回空数据'
            except Exception as test_error:
                diagnosis['service_status'] = 'error'
                diagnosis['error'] = str(test_error)
                if "No audio was received" in str(test_error):
                    diagnosis['error'] += ' - 可能是网络连接问题，无法访问Microsoft TTS服务'
            
            return jsonify(diagnosis), 200
            
        except Exception as e:
            logger.error(f"❌ TTS诊断失败: {e}")
            return jsonify({
                'edge_tts_available': EDGE_TTS_AVAILABLE,
                'service_status': 'error',
                'error': str(e)
            }), 500

    @app.route('/api/tts/config', methods=['GET', 'POST'])
    def tts_config():
        """获取或更新TTS配置"""
        try:
            if request.method == 'GET':
                return jsonify({
                    'config': TTS_CONFIG,
                    'doubao': {
                        'bot_name': DOUBAO_BOT_NAME,
                        'tts_speaker': DOUBAO_TTS_SPEAKER
                    }
                })
            else:
                # POST方法可以用于更新配置（如果需要）
                return jsonify({'message': '配置更新功能暂未实现'}), 501
        except Exception as e:
            logger.error(f"❌ TTS配置操作失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/tts/stats', methods=['GET'])
    def tts_stats():
        """获取TTS统计信息"""
        try:
            metrics = monitor.get_service_metrics('tts')
            return jsonify({
                'metrics': metrics,
                'cache': {
                    'size': len(tts_cache),
                    'enabled': TTS_CONFIG['cache_enabled']
                },
                'concurrent': {
                    'current': tts_concurrent_count,
                    'limit': TTS_CONFIG['concurrent_limit']
                }
            })
        except Exception as e:
            logger.error(f"❌ 获取TTS统计失败: {e}")
            return jsonify({'error': str(e)}), 500

