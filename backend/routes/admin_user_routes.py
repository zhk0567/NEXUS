# -*- coding: utf-8 -*-
"""
管理员用户路由模块
"""
from flask import request, jsonify
import time
import pymysql
from backend.logger_config import logger
from database_manager import db_manager


def register_admin_user_routes(app):
    """注册管理员用户相关路由"""

    @app.route('/api/admin/users/reading-progress', methods=['GET'])
    def admin_get_all_reading_progress():
        """管理员获取所有用户阅读进度"""
        try:
            admin_user_id = request.args.get('admin_user_id')
            limit = int(request.args.get('limit', 100))
            offset = int(request.args.get('offset', 0))
            
            if not admin_user_id:
                return jsonify({'error': '缺少管理员用户ID'}), 400
            
            # 验证管理员身份
            if not db_manager.user_exists(admin_user_id):
                return jsonify({'error': '管理员身份验证失败'}), 401
            
            # 获取所有用户阅读进度
            result = db_manager.get_all_users_reading_progress(limit, offset)
            
            if result is None:
                return jsonify({'error': '获取阅读进度失败'}), 500
            
            return jsonify({
                'success': True,
                'data': result
            })
            
        except Exception as e:
            logger.error(f"❌ 管理员获取阅读进度失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/admin/users/interaction-progress', methods=['GET'])
    def admin_get_all_interaction_progress():
        """管理员获取所有用户AI使用进度"""
        try:
            admin_user_id = request.args.get('admin_user_id')
            limit = int(request.args.get('limit', 100))
            offset = int(request.args.get('offset', 0))
            
            if not admin_user_id:
                return jsonify({'error': '缺少管理员用户ID'}), 400
            
            # 验证管理员身份
            if not db_manager.user_exists(admin_user_id):
                return jsonify({'error': '管理员身份验证失败'}), 401
            
            # 获取所有用户AI使用进度
            result = db_manager.get_all_users_interaction_progress(limit, offset)
            
            if result is None:
                return jsonify({'error': '获取AI使用进度失败'}), 500
            
            return jsonify({
                'success': True,
                'data': result
            })
            
        except Exception as e:
            logger.error(f"❌ 管理员获取AI使用进度失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/admin/users/<user_id>/summary', methods=['GET'])
    def admin_get_user_summary(user_id):
        """管理员获取用户阅读摘要"""
        try:
            admin_user_id = request.args.get('admin_user_id')
            
            if not admin_user_id:
                return jsonify({'error': '缺少管理员用户ID'}), 400
            
            # 验证管理员身份
            if not db_manager.user_exists(admin_user_id):
                return jsonify({'error': '管理员身份验证失败'}), 401
            
            # 获取用户阅读摘要
            summary = db_manager.get_user_reading_summary(user_id)
            
            if summary is None:
                return jsonify({
                    'error': '用户不存在或获取摘要失败'
                }), 404
            
            return jsonify({
                'success': True,
                'summary': summary
            })
            
        except Exception as e:
            logger.error(f"❌ 管理员获取用户摘要失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/admin/users/<user_id>/details', methods=['GET'])
    def admin_get_user_details(user_id):
        """管理员获取用户详细信息"""
        try:
            admin_user_id = request.args.get('admin_user_id')
            
            if not admin_user_id:
                return jsonify({'error': '缺少管理员用户ID'}), 400
            
            # 验证管理员身份
            if not db_manager.user_exists(admin_user_id):
                return jsonify({'error': '管理员身份验证失败'}), 401
            
            # 获取用户基本信息
            user_info = db_manager.get_user_by_id(user_id)
            if not user_info:
                return jsonify({'error': '用户不存在'}), 404
            
            # 获取用户阅读进度详情
            reading_progress = db_manager.get_user_reading_progress_details(
                user_id
            )
            
            # 获取用户统计信息
            stats = db_manager.get_user_reading_summary(user_id)
            
            return jsonify({
                'success': True,
                'user_info': user_info,
                'reading_progress': reading_progress,
                'stats': stats
            })
            
        except Exception as e:
            logger.error(f"❌ 管理员获取用户详情失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/admin/reading/completion', methods=['POST'])
    def admin_update_reading_completion():
        """管理员更新用户阅读完成状态"""
        try:
            data = request.get_json()
            if (not data or 'admin_user_id' not in data or
                    'user_id' not in data or 'story_id' not in data or
                    'is_completed' not in data):
                return jsonify({'error': '缺少必要参数'}), 400
            
            admin_user_id = data['admin_user_id']
            user_id = data['user_id']
            story_id = data['story_id']
            is_completed = data['is_completed']
            
            # 验证管理员身份
            if not db_manager.user_exists(admin_user_id):
                return jsonify({'error': '管理员身份验证失败'}), 401
            
            # 更新阅读完成状态
            success, message = db_manager.admin_update_reading_completion(
                user_id, story_id, is_completed, admin_user_id
            )
            
            if not success:
                return jsonify({'error': message}), 400
            
            return jsonify({
                'success': True,
                'message': message
            })
            
        except Exception as e:
            logger.error(f"❌ 管理员更新阅读完成状态失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/admin/reading/progress', methods=['POST'])
    def admin_update_reading_progress():
        """管理员更新用户阅读进度"""
        try:
            data = request.get_json()
            admin_user_id = data.get('admin_user_id')
            user_id = data.get('user_id')
            story_id = data.get('story_id')
            progress = data.get('progress', 0)
            current_position = data.get('current_position', 0)
            total_length = data.get('total_length', 100)
            
            if not all([admin_user_id, user_id, story_id]):
                return jsonify({'error': '缺少必要参数'}), 400
            
            # 验证管理员身份
            if not db_manager.user_exists(admin_user_id):
                return jsonify({'error': '管理员身份验证失败'}), 401
            
            # 确保进度在0-100范围内
            progress = max(0, min(100, progress))
            
            # 获取用户名
            user_info = db_manager.get_user_by_id(user_id)
            username = user_info.get('username', '') if user_info else ''
            
            # 更新阅读进度
            success = db_manager.update_reading_progress(
                user_id=user_id,
                story_id=story_id,
                story_title="管理员操作",
                current_position=current_position,
                total_length=total_length,
                device_info="admin_operation",
                username=username
            )
            
            if success:
                # 记录管理员操作
                db_manager.log_admin_operation(
                    admin_user_id, user_id, story_id, 'update_progress'
                )
                
                return jsonify({
                    'success': True,
                    'message': f'已更新阅读进度为 {progress}%'
                })
            else:
                return jsonify({'error': '更新失败'}), 500
            
        except Exception as e:
            logger.error(f"❌ 管理员更新阅读进度失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/admin/reading/bulk', methods=['POST'])
    def admin_bulk_reading_operations():
        """管理员批量操作阅读进度"""
        try:
            data = request.get_json()
            admin_user_id = data.get('admin_user_id')
            operations = data.get('operations', [])
            
            if not admin_user_id or not operations:
                return jsonify({'error': '缺少必要参数'}), 400
            
            # 验证管理员身份
            if not db_manager.user_exists(admin_user_id):
                return jsonify({'error': '管理员身份验证失败'}), 401
            
            results = []
            success_count = 0
            
            for operation in operations:
                op_type = operation.get('type')
                user_id = operation.get('user_id')
                story_id = operation.get('story_id')
                
                try:
                    if op_type == 'mark_completed':
                        success, message = (
                            db_manager.admin_update_reading_completion(
                                user_id, story_id, True, admin_user_id
                            )
                        )
                    elif op_type == 'mark_incomplete':
                        success, message = (
                            db_manager.admin_update_reading_completion(
                                user_id, story_id, False, admin_user_id
                            )
                        )
                    elif op_type == 'update_progress':
                        progress = operation.get('progress', 0)
                        current_position = operation.get('current_position', 0)
                        total_length = operation.get('total_length', 100)
                        
                        # 获取用户名
                        user_info = db_manager.get_user_by_id(user_id)
                        username = (
                            user_info.get('username', '')
                            if user_info else ''
                        )
                        
                        success = db_manager.update_reading_progress(
                            user_id=user_id,
                            story_id=story_id,
                            story_title="管理员批量操作",
                            current_position=current_position,
                            total_length=total_length,
                            device_info="admin_bulk_operation",
                            username=username
                        )
                        message = (
                            f'更新进度为 {progress}%' if success else '更新失败'
                        )
                    else:
                        success = False
                        message = '未知操作类型'
                    
                    if success:
                        success_count += 1
                    
                    results.append({
                        'user_id': user_id,
                        'story_id': story_id,
                        'type': op_type,
                        'success': success,
                        'message': message
                    })
                    
                except Exception as e:
                    results.append({
                        'user_id': user_id,
                        'story_id': story_id,
                        'type': op_type,
                        'success': False,
                        'error': str(e)
                    })
            
            return jsonify({
                'success': True,
                'message': (
                    f'批量操作完成，成功 {success_count}/{len(operations)} 项'
                ),
                'results': results
            })
            
        except Exception as e:
            logger.error(f"❌ 管理员批量操作失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/admin/reading/delete', methods=['POST'])
    def admin_delete_reading_record():
        """管理员删除阅读记录"""
        try:
            data = request.get_json()
            admin_user_id = data.get('admin_user_id')
            record_id = data.get('record_id')
            
            if not admin_user_id or not record_id:
                return jsonify({'error': '缺少必要参数'}), 400
            
            # 验证管理员身份
            if not db_manager.user_exists(admin_user_id):
                return jsonify({'error': '管理员身份验证失败'}), 401
            
            # 删除阅读记录
            success = db_manager.delete_reading_record(record_id)
            
            if success:
                # 记录管理员操作
                db_manager.log_admin_operation(
                    admin_user_id, None, None, 'delete_reading_record'
                )
                
                return jsonify({
                    'success': True,
                    'message': '记录删除成功'
                })
            else:
                return jsonify({'error': '删除失败'}), 500
            
        except Exception as e:
            logger.error(f"❌ 管理员删除阅读记录失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/admin/reading/bulk-delete', methods=['POST'])
    def admin_bulk_delete_reading_records():
        """管理员批量删除阅读记录"""
        try:
            data = request.get_json()
            admin_user_id = data.get('admin_user_id')
            record_ids = data.get('record_ids', [])
            
            if not admin_user_id or not record_ids:
                return jsonify({'error': '缺少必要参数'}), 400
            
            # 验证管理员身份
            if not db_manager.user_exists(admin_user_id):
                return jsonify({'error': '管理员身份验证失败'}), 401
            
            # 批量删除阅读记录
            success_count = 0
            failed_count = 0
            
            for record_id in record_ids:
                if db_manager.delete_reading_record(record_id):
                    success_count += 1
                else:
                    failed_count += 1
            
            # 记录管理员操作
            db_manager.log_admin_operation(
                admin_user_id, None, None, 'bulk_delete_reading_records'
            )
            
            return jsonify({
                'success': True,
                'message': (
                    f'批量删除完成：成功 {success_count} 条，'
                    f'失败 {failed_count} 条'
                ),
                'success_count': success_count,
                'failed_count': failed_count
            })
            
        except Exception as e:
            logger.error(f"❌ 管理员批量删除阅读记录失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/admin/users', methods=['GET'])
    def admin_get_all_users():
        """管理员获取所有用户列表"""
        try:
            admin_user_id = request.args.get('admin_user_id')
            limit = int(request.args.get('limit', 50))
            offset = int(request.args.get('offset', 0))
            
            if not admin_user_id:
                return jsonify({'error': '缺少管理员用户ID'}), 400
            
            # 验证管理员身份
            if not db_manager.user_exists(admin_user_id):
                return jsonify({'error': '管理员身份验证失败'}), 401
            
            # 获取所有用户
            max_retries = 3
            for attempt in range(max_retries):
                try:
                    if (not db_manager.connection or
                            not db_manager.connection.open):
                        db_manager.reconnect()
                    
                    with db_manager.connection.cursor(
                            pymysql.cursors.DictCursor) as cursor:
                        # 获取用户列表
                        sql = """
                        SELECT u.user_id, u.username, u.created_at,
                               u.last_login_at, u.is_active,
                               COUNT(rp.id) as total_stories,
                               SUM(CASE WHEN rp.is_completed = 1
                                   THEN 1 ELSE 0 END) as completed_stories
                        FROM users u
                        LEFT JOIN reading_progress rp ON u.user_id = rp.user_id
                        GROUP BY u.user_id, u.username, u.created_at,
                                 u.last_login_at, u.is_active
                        ORDER BY u.created_at DESC
                        LIMIT %s OFFSET %s
                        """
                        cursor.execute(sql, (limit, offset))
                        users = cursor.fetchall()
                        
                        # 获取总数
                        count_sql = "SELECT COUNT(*) as count FROM users"
                        cursor.execute(count_sql)
                        total_count = cursor.fetchone()['count']
                        
                        return jsonify({
                            'success': True,
                            'users': users,
                            'total_count': total_count,
                            'limit': limit,
                            'offset': offset
                        })
                        
                except Exception as e:
                    if attempt < max_retries - 1:
                        db_manager.reconnect()
                        time.sleep(1)
                    else:
                        raise e
            
        except Exception as e:
            logger.error(f"❌ 管理员获取用户列表失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/admin/users/<user_id>/password', methods=['POST'])
    def admin_reset_user_password(user_id):
        """管理员重置用户密码"""
        try:
            data = request.get_json()
            admin_user_id = data.get('admin_user_id')
            new_password = data.get('new_password')
            
            if not admin_user_id or not user_id or not new_password:
                return jsonify({'error': '缺少必要参数'}), 400
            
            # 验证管理员身份
            if not db_manager.user_exists(admin_user_id):
                return jsonify({'error': '管理员身份验证失败'}), 401
            
            # 重置用户密码
            success = db_manager.reset_user_password(user_id, new_password)
            
            if success:
                # 记录管理员操作
                db_manager.log_admin_operation(
                    admin_user_id, user_id, None, 'reset_password'
                )
                
                return jsonify({
                    'success': True,
                    'message': '密码重置成功'
                })
            else:
                return jsonify({'error': '密码重置失败'}), 500
            
        except Exception as e:
            logger.error(f"❌ 管理员重置用户密码失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/admin/users/<user_id>/password', methods=['GET'])
    def admin_get_user_password_info(user_id):
        """管理员获取用户密码信息"""
        try:
            admin_user_id = request.args.get('admin_user_id')
            
            if not admin_user_id or not user_id:
                return jsonify({'error': '缺少必要参数'}), 400
            
            # 验证管理员身份
            if not db_manager.user_exists(admin_user_id):
                return jsonify({'error': '管理员身份验证失败'}), 401
            
            # 获取用户密码信息
            password_info = db_manager.get_user_password_info(user_id)
            
            if password_info:
                return jsonify({
                    'success': True,
                    'password_info': password_info
                })
            else:
                return jsonify({'error': '获取密码信息失败'}), 500
            
        except Exception as e:
            logger.error(f"❌ 管理员获取用户密码信息失败: {e}")
            return jsonify({'error': str(e)}), 500

