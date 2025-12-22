# -*- coding: utf-8 -*-
"""
健康检查路由模块
"""
from flask import request, jsonify
from backend.logger_config import logger
from backend.config import PUBLIC_IP, PRIVATE_IP
from backend.service_monitor import ServiceMonitor, AutoRecovery


def register_health_routes(app, monitor: ServiceMonitor, auto_recovery: AutoRecovery):
    """注册健康检查相关路由"""

    @app.route('/api/health', methods=['GET'])
    def health_check():
        """健康检查端点"""
        try:
            health_status = monitor.check_health()
            return jsonify(health_status)
        except Exception as e:
            logger.error(f"❌ 健康检查失败: {e}")
            return jsonify({"overall": "error", "error": str(e)}), 500

    @app.route('/api/config', methods=['GET'])
    def get_config():
        """获取客户端配置（不包含敏感信息）"""
        try:
            server_port = 5000

            # 返回公网IP配置（供客户端外网访问）
            config = {
                'success': True,
                'server': {
                    'base_url': f"http://{PUBLIC_IP}:{server_port}",
                    'websocket_url': f"ws://{PUBLIC_IP}:{server_port}",
                    'api_base': f"http://{PUBLIC_IP}:{server_port}/api"
                },
                'fallback': {
                    'base_url': f"http://{PRIVATE_IP}:{server_port}",
                    'websocket_url': f"ws://{PRIVATE_IP}:{server_port}",
                    'api_base': f"http://{PRIVATE_IP}:{server_port}/api"
                }
            }

            return jsonify(config)
        except Exception as e:
            logger.error(f"❌ 获取配置失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/metrics', methods=['GET'])
    def get_metrics():
        """获取服务指标"""
        try:
            metrics = {
                'tts': monitor.get_service_metrics('tts'),
                'asr': monitor.get_service_metrics('asr'),
                'chat': monitor.get_service_metrics('chat'),
                'system': monitor.system_stats
            }
            return jsonify(metrics)
        except Exception as e:
            logger.error(f"❌ 获取指标失败: {e}")
            return jsonify({'error': str(e)}), 500

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
            
            from datetime import datetime
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

