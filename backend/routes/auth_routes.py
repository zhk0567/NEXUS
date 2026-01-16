# -*- coding: utf-8 -*-
"""
认证路由模块
"""
from flask import request, jsonify
from backend.logger_config import logger
from backend.config import ALLOWED_USERS
from database_manager import db_manager


def register_auth_routes(app):
    """注册认证相关路由"""

    @app.route('/api/auth/login', methods=['POST'])
    def user_login():
        """用户登录"""
        try:
            data = request.get_json()
            if not data or 'username' not in data or 'password' not in data:
                return jsonify({
                    'error': '用户名和密码不能为空'
                }), 400

            username = data['username']
            password = data['password']
            device_info = data.get('device_info', '')
            ip_address = request.remote_addr
            user_agent = request.headers.get('User-Agent', '')

            # 从User-Agent或device_info判断app类型
            app_type = 'unknown'
            if 'story' in user_agent.lower() or 'story' in device_info.lower():
                app_type = 'story_control'
            elif ('nexus' in user_agent.lower() or
                  'ai' in user_agent.lower() or
                  'chat' in user_agent.lower()):
                app_type = 'ai_chat'
            elif device_info:
                if 'story' in device_info.lower():
                    app_type = 'story_control'
                elif 'ai' in device_info.lower() or 'chat' in device_info.lower():
                    app_type = 'ai_chat'

            # 用户认证（只允许user01-user10这10个账号）
            user = db_manager.authenticate_user(username, password)
            if not user:
                db_manager.log_system_event(
                    'WARNING', 'auth', f'登录失败: {username}'
                )
                # 检查是否是白名单问题
                if username not in ALLOWED_USERS:
                    return jsonify({
                        'error': '该账号不允许登录，请联系管理员'
                    }), 403
                return jsonify({
                    'error': '用户名或密码错误'
                }), 401

            # 检查同一账号同一app是否已在其他设备登录
            active_sessions = db_manager.get_active_sessions(
                user['user_id'], app_type
            )
            if active_sessions:
                # 结束旧会话（踢掉其他设备的登录）
                ended_count = db_manager.end_user_sessions(
                    user['user_id'], app_type
                )
                logger.info(
                    f"⚠️ 用户 {username} 在 {app_type} app 已有 "
                    f"{len(active_sessions)} 个活跃会话，已结束旧会话"
                )
                db_manager.log_system_event(
                    'INFO', 'auth',
                    f'用户 {username} 在新设备登录，已结束 {ended_count} 个旧会话'
                )

            # 创建新会话
            session_id = db_manager.create_session(
                user['user_id'], app_type, device_info, ip_address
            )
            if not session_id:
                return jsonify({'error': '创建会话失败'}), 500

            logger.info(f"✅ 用户登录成功: {username}")
            db_manager.log_system_event(
                'INFO', 'auth', f'用户登录成功: {username}'
            )

            return jsonify({
                'success': True,
                'user': {
                    'user_id': user['user_id'],
                    'username': user['username'],
                    'created_at': (
                        user['created_at'].isoformat()
                        if user['created_at'] else None
                    ),
                    'last_login_at': (
                        user['last_login_at'].isoformat()
                        if user['last_login_at'] else None
                    )
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

            logger.info(f"✅ 用户登出成功: {session_id}")
            db_manager.log_system_event(
                'INFO', 'auth', f'用户登出: {session_id}'
            )

            return jsonify({'success': True, 'message': '登出成功'})

        except Exception as e:
            logger.error(f"❌ 用户登出失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/auth/register', methods=['POST'])
    def user_register():
        """用户注册 - 已禁用，只允许使用预置的10个账号"""
        try:
            data = request.get_json()
            if not data or 'username' not in data or 'password' not in data:
                return jsonify({
                    'error': '用户名和密码不能为空'
                }), 400

            username = data['username']
            password = data['password']

            # 只允许注册白名单中的账号
            if username not in ALLOWED_USERS:
                logger.warning(
                    f"⚠️ 拒绝注册：用户名 '{username}' 不在允许列表中"
                )
                return jsonify({
                    'error': '注册功能已禁用，只允许使用预置账号'
                }), 403

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
            db_manager.log_system_event(
                'INFO', 'auth', f'用户注册成功: {username}'
            )

            return jsonify({
                'success': True,
                'message': '注册成功',
                'user_id': user_id
            })

        except Exception as e:
            logger.error(f"❌ 用户注册失败: {e}")
            return jsonify({'error': str(e)}), 500

