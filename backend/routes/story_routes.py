# -*- coding: utf-8 -*-
"""
故事路由模块
"""
from flask import request, jsonify
from backend.logger_config import logger
from database_manager import db_manager


def register_story_routes(app):
    """注册故事相关路由"""

    @app.route('/api/story/reading/progress', methods=['POST'])
    def update_reading_progress():
        """更新阅读进度"""
        try:
            data = request.get_json()
            user_id = data.get('user_id')
            story_id = data.get('story_id')
            story_title = data.get('story_title', '')
            current_position = data.get('current_position', 0)
            total_length = data.get('total_length', 100)
            device_info = data.get('device_info', '')
            
            if not user_id or not story_id:
                return jsonify({'error': '缺少必要参数'}), 400
            
            # 验证用户身份
            if not db_manager.user_exists(user_id):
                return jsonify({'error': '用户身份验证失败'}), 401
            
            # 获取用户信息以获取正确的username
            user_info = db_manager.get_user_details(user_id)
            username = user_info.get('username', 'unknown') if user_info else 'unknown'
            
            # 更新阅读进度
            success = db_manager.update_reading_progress(
                user_id=user_id,
                story_id=story_id,
                story_title=story_title,
                current_position=current_position,
                total_length=total_length,
                device_info=device_info,
                username=username
            )
            
            if not success:
                return jsonify({'error': '更新阅读进度失败'}), 500
            
            # 计算进度百分比
            progress_percentage = (
                (current_position / total_length * 100)
                if total_length > 0 else 0
            )
            
            # 获取故事的实际完成状态
            reading_progress_list = db_manager.get_reading_progress(
                user_id, story_id
            )
            reading_progress = (
                reading_progress_list[0]
                if reading_progress_list else None
            )
            is_completed = (
                reading_progress.get('is_completed', False)
                if reading_progress else False
            )
            
            return jsonify({
                'success': True,
                'progress_percentage': round(progress_percentage, 2),
                'is_completed': is_completed,
                'message': '阅读进度已更新'
            })
            
        except Exception as e:
            logger.error(f"❌ 更新阅读进度失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/story/reading/progress', methods=['GET'])
    def get_reading_progress():
        """获取阅读进度"""
        try:
            user_id = request.args.get('user_id')
            story_id = request.args.get('story_id')
            
            if not user_id:
                return jsonify({'error': '缺少用户ID'}), 400
            
            # 验证用户身份
            if not db_manager.user_exists(user_id):
                return jsonify({'error': '用户身份验证失败'}), 401
            
            # 获取阅读进度
            progress_list = db_manager.get_reading_progress(user_id, story_id)
            
            return jsonify({
                'success': True,
                'progress': progress_list,
                'count': len(progress_list)
            })
            
        except Exception as e:
            logger.error(f"❌ 获取阅读进度失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/story/interaction', methods=['POST'])
    def log_story_interaction():
        """记录故事交互"""
        try:
            data = request.get_json()
            if (not data or 'user_id' not in data or
                    'story_id' not in data or 'interaction_type' not in data):
                return jsonify({'error': '缺少必要参数'}), 400
            
            user_id = data['user_id']
            story_id = data['story_id']
            interaction_type = data['interaction_type']
            interaction_data = data.get('interaction_data')
            session_id = data.get('session_id')
            device_info = data.get('device_info', '')
            
            # 验证用户身份
            if not db_manager.user_exists(user_id):
                return jsonify({'error': '用户身份验证失败'}), 401
            
            # 验证交互类型
            valid_types = [
                'app_open', 'app_close', 'audio_play', 'audio_pause',
                'audio_stop', 'text_complete', 'audio_complete',
                'view_details', 'first_scroll', 'complete_button_click',
                'audio_play_click', 'audio_complete_button_click',
                'text_complete_button_click'
            ]
            if interaction_type not in valid_types:
                return jsonify({
                    'error': f'无效的交互类型，必须是: {valid_types}'
                }), 400
            
            # 记录交互
            success = db_manager.log_story_interaction(
                user_id=user_id,
                story_id=story_id,
                interaction_type=interaction_type,
                interaction_data=interaction_data,
                device_info=device_info
            )
            
            if not success:
                return jsonify({'error': '记录交互失败'}), 500
            
            return jsonify({
                'success': True,
                'message': '交互记录成功'
            })
            
        except Exception as e:
            logger.error(f"❌ 记录故事交互失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/story/complete', methods=['POST'])
    def complete_story_reading():
        """完成故事阅读"""
        try:
            data = request.get_json()
            user_id = data.get('user_id')
            story_id = data.get('story_id')
            story_title = data.get('story_title', '')
            completion_mode = data.get('completion_mode')
            device_info = data.get('device_info', '')
            
            if not user_id or not story_id or not completion_mode:
                return jsonify({'error': '缺少必要参数'}), 400
            
            # 验证用户身份
            if not db_manager.user_exists(user_id):
                return jsonify({'error': '用户身份验证失败'}), 401
            
            # 验证完成方式
            valid_modes = ['text', 'audio', 'mixed']
            if completion_mode not in valid_modes:
                return jsonify({
                    'error': f'无效的完成方式，必须是: {valid_modes}'
                }), 400
            
            # 获取用户信息以获取正确的username
            user_info = db_manager.get_user_details(user_id)
            username = (
                user_info.get('username', 'unknown')
                if user_info else 'unknown'
            )
            
            # 标记故事完成
            success = db_manager.complete_reading(
                user_id=user_id,
                story_id=story_id,
                story_title=story_title,
                completion_mode=completion_mode,
                device_info=device_info,
                username=username
            )
            
            if success:
                # 记录交互
                interaction_type = (
                    'text_complete' if completion_mode == 'text'
                    else 'audio_complete'
                )
                db_manager.log_story_interaction(
                    user_id=user_id,
                    story_id=story_id,
                    interaction_type=interaction_type,
                    interaction_data={'completion_mode': completion_mode},
                    device_info=device_info
                )
                
                return jsonify({
                    'success': True,
                    'message': '故事阅读完成',
                    'completion_mode': completion_mode
                })
            else:
                return jsonify({'error': '标记完成失败'}), 500
                
        except Exception as e:
            logger.error(f"❌ 完成故事阅读失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/story/statistics', methods=['GET'])
    def get_reading_statistics():
        """获取阅读统计"""
        try:
            user_id = request.args.get('user_id')
            days = int(request.args.get('days', 30))
            
            if not user_id:
                return jsonify({'error': '缺少用户ID'}), 400
            
            # 验证用户身份
            if not db_manager.user_exists(user_id):
                return jsonify({'error': '用户身份验证失败'}), 401
            
            # 获取阅读统计
            statistics = db_manager.get_reading_statistics(user_id, days)
            
            return jsonify({
                'success': True,
                'statistics': statistics,
                'period_days': days
            })
            
        except Exception as e:
            logger.error(f"❌ 获取阅读统计失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/stories/active', methods=['GET'])
    def get_active_stories():
        """获取活跃故事列表（30天循环）"""
        try:
            all_stories = db_manager.get_all_stories(include_inactive=False)
            
            if not all_stories:
                return jsonify({
                    'success': True,
                    'stories': [],
                    'total': 0
                })
            
            # 计算今天应该显示哪个故事（30天循环）
            from datetime import datetime
            today = datetime.now().date()
            base_date = datetime(2025, 1, 1).date()
            days_from_base = (today - base_date).days
            day_index = days_from_base % 30
            story_index = day_index if day_index >= 0 else day_index + 30
            
            # 根据索引选择对应的故事
            sorted_stories = sorted(
                all_stories, key=lambda x: x.get('story_id', '')
            )
            
            # 确保有30个故事，如果不足30个，循环使用
            if len(sorted_stories) >= 30:
                today_story = sorted_stories[story_index]
            else:
                today_story = sorted_stories[story_index % len(sorted_stories)]
            
            # 只返回今天的故事
            user_story = {
                'id': today_story['story_id'],
                'title': today_story['title'],
                'content': today_story['content'],
                'audio_file_path': today_story.get('audio_file_path'),
                'audio_duration_seconds': today_story.get(
                    'audio_duration_seconds', 0
                )
            }
            
            logger.info(
                f"📖 返回今天的故事（30天循环，索引{story_index}）: "
                f"{user_story['title']}"
            )
            
            return jsonify({
                'success': True,
                'stories': [user_story],
                'total': 1
            })
        except Exception as e:
            logger.error(f"❌ 获取活跃故事列表失败: {e}")
            import traceback
            logger.error(traceback.format_exc())
            return jsonify({'error': str(e)}), 500

