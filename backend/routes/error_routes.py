# -*- coding: utf-8 -*-
"""
错误路由模块
"""
from flask import request, jsonify
from backend.logger_config import logger
from database_manager import db_manager


def register_error_routes(app):
    """注册错误相关路由"""

    @app.route('/api/error/test', methods=['GET'])
    def test_error_endpoint():
        """测试错误报告端点是否可用"""
        return jsonify({
            'success': True,
            'message': 'Error report endpoint is working',
            'endpoint': '/api/error/report'
        }), 200

    @app.route('/api/error/report', methods=['POST'])
    def report_error():
        """接收客户端错误报告"""
        try:
            logger.info("📝 收到错误报告请求")
            data = request.get_json()
            if not data:
                logger.warning("⚠️ 错误报告请求数据为空")
                return jsonify({'error': 'No data provided'}), 400
            
            # 提取错误信息
            user_id = data.get('user_id')
            username = data.get('username')
            error_type = data.get('error_type', 'unknown')
            error_level = data.get('error_level', 'ERROR')
            error_message = data.get('error_message', '')
            error_stack = data.get('error_stack')
            error_context = data.get('error_context', {})
            app_version = data.get('app_version')
            device_info = data.get('device_info')
            os_version = data.get('os_version')
            screen_info = data.get('screen_info')
            network_type = data.get('network_type')
            session_id = data.get('session_id')
            api_endpoint = data.get('api_endpoint')
            request_data = data.get('request_data')
            response_data = data.get('response_data')
            
            # 验证必填字段
            if not error_message:
                return jsonify({'error': 'error_message is required'}), 400
            
            # 记录错误到数据库
            success = db_manager.report_error(
                user_id=user_id,
                username=username,
                error_type=error_type,
                error_level=error_level,
                error_message=error_message,
                error_stack=error_stack,
                error_context=error_context,
                app_version=app_version,
                device_info=device_info,
                os_version=os_version,
                screen_info=screen_info,
                network_type=network_type,
                session_id=session_id,
                api_endpoint=api_endpoint,
                request_data=request_data,
                response_data=response_data
            )
            
            if success:
                logger.error(
                    f"📝 错误报告已记录: {error_type} - "
                    f"{error_message[:100]}"
                )
                return jsonify({
                    'success': True,
                    'message': 'Error reported successfully'
                }), 200
            else:
                return jsonify({
                    'success': False,
                    'message': 'Failed to save error report'
                }), 500
                
        except Exception as e:
            logger.error(f"❌ 处理错误报告失败: {e}")
            import traceback
            logger.error(traceback.format_exc())
            return jsonify({'error': str(e)}), 500

    @app.route('/api/error/reports', methods=['GET'])
    def get_error_reports():
        """获取错误报告列表（管理员接口）"""
        try:
            # 验证管理员身份
            admin_user_id = request.args.get('admin_user_id')
            if not admin_user_id or admin_user_id != 'admin_001':
                return jsonify({'error': 'Unauthorized'}), 401
            
            # 获取查询参数
            user_id = request.args.get('user_id')
            error_type = request.args.get('error_type')
            error_level = request.args.get('error_level')
            is_resolved = request.args.get('is_resolved')
            if is_resolved is not None:
                is_resolved = is_resolved.lower() == 'true'
            
            limit = int(request.args.get('limit', 100))
            offset = int(request.args.get('offset', 0))
            
            # 获取错误报告
            reports = db_manager.get_error_reports(
                user_id=user_id,
                error_type=error_type,
                error_level=error_level,
                is_resolved=is_resolved,
                limit=limit,
                offset=offset
            )
            
            return jsonify({
                'success': True,
                'data': reports,
                'total': len(reports)
            }), 200
            
        except Exception as e:
            logger.error(f"❌ 获取错误报告失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/error/resolve', methods=['POST'])
    def resolve_error():
        """标记错误为已解决（管理员接口）"""
        try:
            data = request.get_json()
            if not data:
                return jsonify({'error': 'No data provided'}), 400
            
            # 验证管理员身份
            admin_user_id = data.get('admin_user_id')
            if not admin_user_id or admin_user_id != 'admin_001':
                return jsonify({'error': 'Unauthorized'}), 401
            
            error_id = data.get('error_id')
            resolution_note = data.get('resolution_note')
            
            if not error_id:
                return jsonify({'error': 'error_id is required'}), 400
            
            # 标记为已解决
            success = db_manager.resolve_error(
                error_id=error_id,
                resolved_by=admin_user_id,
                resolution_note=resolution_note
            )
            
            if success:
                return jsonify({
                    'success': True,
                    'message': 'Error marked as resolved'
                }), 200
            else:
                return jsonify({
                    'success': False,
                    'message': 'Failed to resolve error'
                }), 500
                
        except Exception as e:
            logger.error(f"❌ 标记错误已解决失败: {e}")
            return jsonify({'error': str(e)}), 500

