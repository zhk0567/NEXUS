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
    tts_concurrent_count
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
                    'error': 'TTS failed - no audio data generated'
                }), 500

        except Exception as e:
            logger.error(f"❌ TTS API错误: {e}")
            import traceback
            logger.error(f"❌ TTS API错误详情: {traceback.format_exc()}")
            return jsonify({'error': str(e)}), 500

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

