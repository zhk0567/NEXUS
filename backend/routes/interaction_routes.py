# -*- coding: utf-8 -*-
"""
交互路由模块
"""
from flask import request, jsonify
from backend.logger_config import logger
from database_manager import db_manager


def register_interaction_routes(app):
    """注册交互相关路由"""

    @app.route('/api/interactions/log', methods=['POST'])
    def log_interaction():
        """记录交互"""
        try:
            data = request.get_json()
            logger.info(f"🔍 收到交互记录请求: {data}")
            if (not data or 'user_id' not in data or
                    'interaction_type' not in data or 'content' not in data):
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
            is_new_conversation = data.get('is_new_conversation', False)

            # 验证交互类型
            valid_types = ['text', 'voice_home', 'voice_call', 'tts_play']
            if interaction_type not in valid_types:
                return jsonify({
                    'error': f'无效的交互类型，必须是: {valid_types}'
                }), 400

            # 检查用户是否存在
            if not db_manager.user_exists(user_id):
                logger.warning(f"⚠️ 用户 {user_id} 不存在，拒绝记录交互")
                return jsonify({
                    'error': '用户身份验证失败，请重新登录'
                }), 401

            # 处理session_id
            if is_new_conversation:
                old_session_id = session_id
                session_id = db_manager.create_session(user_id)
                if not session_id:
                    return jsonify({'error': '无法创建session'}), 500
                logger.info(
                    f"ℹ️ [新历史对话] 创建新session: {session_id} "
                    f"(旧session_id被忽略: {old_session_id})"
                )
            elif not session_id or session_id.strip() == '':
                session_id = db_manager.create_session(user_id)
                if not session_id:
                    return jsonify({'error': '无法创建session'}), 500
                logger.info(f"ℹ️ [新历史对话] 创建新session: {session_id}")
            else:
                logger.info(f"ℹ️ [继续历史对话] 使用session: {session_id}")

            # 记录交互
            success_log, actual_session_id = db_manager.log_interaction(
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

            return jsonify({
                'success': True,
                'message': '交互记录成功',
                'session_id': actual_session_id
            })

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

            interactions = db_manager.get_user_interactions(
                user_id, limit, offset
            )

            return jsonify({
                'success': True,
                'interactions': interactions,
                'count': len(interactions)
            })

        except Exception as e:
            logger.error(f"❌ 获取交互历史失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/interactions/session/<session_id>', methods=['GET'])
    def get_session_interactions(session_id):
        """获取指定session下的所有交互记录"""
        try:
            if not session_id:
                return jsonify({'error': 'session_id不能为空'}), 400

            limit = int(request.args.get('limit', 100))
            offset = int(request.args.get('offset', 0))

            interactions = db_manager.get_session_interactions(
                session_id, limit, offset
            )

            return jsonify({
                'success': True,
                'session_id': session_id,
                'interactions': interactions,
                'count': len(interactions)
            })

        except Exception as e:
            logger.error(f"❌ 获取session交互记录失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/conversations/list', methods=['GET'])
    def list_conversations():
        """获取用户的所有历史对话列表"""
        try:
            user_id = request.args.get('user_id')
            if not user_id:
                return jsonify({'error': '用户ID不能为空'}), 400

            limit = int(request.args.get('limit', 50))
            offset = int(request.args.get('offset', 0))

            # 获取用户的所有会话
            sessions = db_manager.get_user_sessions(user_id, limit, offset)

            # 为每个会话获取交互记录数量
            conversations = []
            for session in sessions:
                session_id = session.get('session_id')
                interactions = db_manager.get_session_interactions(
                    session_id, limit=1, offset=0
                )
                conversations.append({
                    'session_id': session_id,
                    'login_time': session.get('login_time'),
                    'app_type': session.get('app_type'),
                    'interaction_count': len(interactions),
                    'last_interaction': interactions[0] if interactions else None
                })

            return jsonify({
                'success': True,
                'conversations': conversations,
                'count': len(conversations)
            })

        except Exception as e:
            logger.error(f"❌ 获取对话列表失败: {e}")
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
                'stats': stats
            })

        except Exception as e:
            logger.error(f"❌ 获取交互统计失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/stats/active_users', methods=['GET'])
    def get_active_users():
        """获取活跃用户"""
        try:
            hours = int(request.args.get('hours', 24))

            active_users = db_manager.get_active_users(hours)

            return jsonify({
                'success': True,
                'active_users': active_users,
                'count': len(active_users)
            })

        except Exception as e:
            logger.error(f"❌ 获取活跃用户失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/interactions/progress', methods=['GET'])
    def get_interaction_progress():
        """获取用户AI使用进度"""
        try:
            user_id = request.args.get('user_id')
            usage_date = request.args.get('usage_date')  # 可选，格式：YYYY-MM-DD
            
            if not user_id:
                return jsonify({'error': '缺少用户ID'}), 400
            
            # 验证用户身份
            if not db_manager.user_exists(user_id):
                return jsonify({'error': '用户身份验证失败'}), 401
            
            # 获取AI使用进度
            progress_list = db_manager.get_interaction_progress(user_id, usage_date)
            
            return jsonify({
                'success': True,
                'progress': progress_list,
                'count': len(progress_list)
            })
            
        except Exception as e:
            logger.error(f"❌ 获取AI使用进度失败: {e}")
            return jsonify({'error': str(e)}), 500
