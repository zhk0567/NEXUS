# -*- coding: utf-8 -*-
"""
管理员故事路由模块
"""
from flask import request, jsonify
from backend.logger_config import logger
from database_manager import db_manager


def register_admin_story_routes(app):
    """注册管理员故事相关路由"""

    @app.route('/api/admin/stories', methods=['GET'])
    def get_all_stories():
        """获取所有故事（管理员）"""
        try:
            include_inactive = (
                request.args.get('include_inactive', 'false').lower() == 'true'
            )
            stories = db_manager.get_all_stories(include_inactive=include_inactive)
            return jsonify({
                'success': True,
                'stories': stories,
                'total': len(stories)
            })
        except Exception as e:
            logger.error(f"❌ 获取故事列表失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/admin/stories/<story_id>', methods=['GET'])
    def get_story(story_id):
        """获取单个故事详情（管理员）"""
        try:
            story = db_manager.get_story(story_id)
            if story:
                return jsonify({
                    'success': True,
                    'story': story
                })
            else:
                return jsonify({'error': '故事不存在'}), 404
        except Exception as e:
            logger.error(f"❌ 获取故事详情失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/admin/stories', methods=['POST'])
    def create_story():
        """创建新故事（管理员）"""
        try:
            data = request.get_json()
            if not data:
                return jsonify({'error': '请求数据不能为空'}), 400
            
            story_id = data.get('story_id')
            title = data.get('title')
            content = data.get('content')
            audio_file_path = data.get('audio_file_path')
            audio_duration_seconds = data.get('audio_duration_seconds', 0)
            is_active = data.get('is_active', True)
            
            if not story_id or not title or not content:
                return jsonify({
                    'error': 'story_id、title和content不能为空'
                }), 400
            
            # 创建故事
            success = db_manager.create_story(
                story_id=story_id,
                title=title,
                content=content,
                audio_file_path=audio_file_path,
                audio_duration_seconds=audio_duration_seconds,
                is_active=is_active
            )
            
            if success:
                return jsonify({
                    'success': True,
                    'message': '故事创建成功'
                })
            else:
                return jsonify({'error': '故事创建失败'}), 500
                
        except Exception as e:
            logger.error(f"❌ 创建故事失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/admin/stories/<story_id>', methods=['PUT'])
    def update_story(story_id):
        """更新故事（管理员）"""
        try:
            data = request.get_json()
            if not data:
                return jsonify({'error': '请求数据不能为空'}), 400
            
            title = data.get('title')
            content = data.get('content')
            audio_file_path = data.get('audio_file_path')
            audio_duration_seconds = data.get('audio_duration_seconds')
            is_active = data.get('is_active')
            
            # 更新故事
            success = db_manager.update_story(
                story_id=story_id,
                title=title,
                content=content,
                audio_file_path=audio_file_path,
                audio_duration_seconds=audio_duration_seconds,
                is_active=is_active
            )
            
            if success:
                return jsonify({
                    'success': True,
                    'message': '故事更新成功'
                })
            else:
                return jsonify({'error': '故事更新失败'}), 500
                
        except Exception as e:
            logger.error(f"❌ 更新故事失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/admin/stories/<story_id>', methods=['DELETE'])
    def delete_story(story_id):
        """删除故事（管理员）"""
        try:
            # 删除故事（实际是标记为不活跃）
            success = db_manager.update_story(
                story_id=story_id, is_active=False
            )
            
            if success:
                return jsonify({
                    'success': True,
                    'message': '故事删除成功'
                })
            else:
                return jsonify({'error': '故事删除失败'}), 500
                
        except Exception as e:
            logger.error(f"❌ 删除故事失败: {e}")
            return jsonify({'error': str(e)}), 500

