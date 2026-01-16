# -*- coding: utf-8 -*-
"""
聊天路由模块
"""
import json
import requests
from flask import request, jsonify
from backend.logger_config import logger
from backend.ai_service import (
    chat_with_deepseek,
    build_chat_messages,
    validate_messages,
    SYSTEM_PROMPT
)
from backend.config import DEEPSEEK_API_KEY, DEEPSEEK_BASE_URL
from database_manager import db_manager


def register_chat_routes(app):
    """注册聊天相关路由"""

    @app.route('/api/conversation/start', methods=['POST'])
    def start_new_conversation():
        """开始新对话"""
        try:
            data = request.get_json()
            if not data or 'user_id' not in data:
                return jsonify({'error': 'user_id is required'}), 400

            user_id = data['user_id']
            app_type = data.get('app_type', 'ai_chat')
            device_info = data.get('device_info', '')
            ip_address = request.remote_addr

            # 创建新会话
            session_id = db_manager.create_session(
                user_id=user_id,
                app_type=app_type,
                device_info=device_info,
                ip_address=ip_address
            )

            if session_id:
                return jsonify({
                    'success': True,
                    'session_id': session_id,
                    'message': '新对话已创建'
                }), 200
            else:
                return jsonify({
                    'success': False,
                    'error': '无法创建会话'
                }), 500

        except Exception as e:
            logger.error(f"❌ 创建新对话失败: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/chat_streaming', methods=['POST'])
    def chat_streaming():
        """AI聊天流式API"""
        try:
            data = request.get_json()
            if not data or 'message' not in data:
                return jsonify({'error': 'No message provided'}), 400

            message = data['message']
            user_id = data.get('user_id', 'anonymous')
            session_id = data.get('session_id', '')
            conversation_history = data.get('conversation_history', [])
            is_refresh = data.get('is_refresh', False)  # 是否为刷新请求
            
            # 验证对话历史格式
            if conversation_history and not isinstance(conversation_history, list):
                logger.error(f"❌ 对话历史格式错误: {type(conversation_history)}")
                conversation_history = []

            logger.info(f"🤖 收到流式聊天请求: {message}")
            logger.info(f"🔍 Session ID: {session_id}")
            logger.info(f"📚 对话历史类型: {type(conversation_history)}, 长度: {len(conversation_history) if conversation_history else 0}")

            # 验证用户身份
            if user_id == 'anonymous' or not db_manager.user_exists(user_id):
                logger.warning(f"⚠️ 无效的用户ID: {user_id}")
                return jsonify({
                    'error': '需要有效的用户身份验证，请先登录'
                }), 401

            # 处理session_id
            if not session_id or session_id.strip() == '':
                session_id = db_manager.create_session(user_id)
                if not session_id:
                    return jsonify({'error': '无法创建session'}), 500
                logger.info(f"ℹ️ [新历史对话] 创建新session: {session_id}")
            else:
                logger.info(f"ℹ️ [继续历史对话] 使用session: {session_id}")

            # 流式响应生成器
            def generate_streaming_response():
                try:
                    headers = {
                        "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
                        "Content-Type": "application/json"
                    }

                    # 构建消息列表
                    logger.info(
                        f"📝 构建消息列表: message_len={len(message)}, "
                        f"history_len={len(conversation_history) if conversation_history else 0}"
                    )
                    messages = build_chat_messages(message, conversation_history)
                    logger.info(f"📝 构建后消息数: {len(messages)}")
                    messages = validate_messages(messages)
                    logger.info(f"📝 验证后消息数: {len(messages)}")

                    if not messages:
                        logger.error("❌ 没有有效的消息")
                        error_chunk = {
                            'type': 'error',
                            'message': '没有有效的消息'
                        }
                        yield f"data: {json.dumps(error_chunk, ensure_ascii=False)}\n\n"
                        return

                    logger.info(
                        f"✅ 验证后有效消息数: {len(messages)}"
                    )
                    # 打印前3条消息的摘要用于调试
                    for i, msg in enumerate(messages[:3]):
                        content = str(msg.get('content', ''))
                        content_preview = content[:100] + '...' if len(content) > 100 else content
                        logger.info(
                            f"📝 消息{i}: role={msg.get('role')}, "
                            f"content_len={len(content)}, "
                            f"content_preview={content_preview}"
                        )

                    # 检测是否需要联网搜索
                    need_web_search = False
                    search_keywords = [
                        '今天', '明天', '后天', '天气', '日期', '星期',
                        '几号', '几月', '几号了', '现在几点', '现在几点了',
                        '今天是', '现在', '当前', '实时', '最新'
                    ]
                    message_lower = message.lower()
                    for keyword in search_keywords:
                        if keyword in message_lower:
                            need_web_search = True
                            break

                    # 构建请求数据
                    # 刷新请求使用更高的temperature以增加回答的变化程度
                    temperature = 0.9 if is_refresh else 0.7
                    request_data = {
                        "model": "deepseek-chat",
                        "messages": messages,
                        "max_tokens": 500,
                        "temperature": temperature,
                        "stream": True
                    }

                    # 验证JSON序列化
                    try:
                        json.dumps(request_data, ensure_ascii=False)
                    except Exception as e:
                        logger.error(f"❌ 请求数据JSON序列化失败: {e}")
                        error_chunk = {
                            'type': 'error',
                            'message': f'请求数据格式错误: {str(e)}'
                        }
                        yield f"data: {json.dumps(error_chunk, ensure_ascii=False)}\n\n"
                        return

                    # DeepSeek API的联网搜索功能
                    # 注意：DeepSeek的web_search功能可能需要特定的API版本或格式
                    # 如果遇到400错误，可能需要检查API文档或使用不同的方式启用联网搜索
                    # 暂时移除tools参数，避免400错误
                    # if need_web_search:
                    #     request_data["tools"] = [{"type": "web_search"}]
                    #     logger.info("检测到需要联网搜索，已添加tools参数")

                    logger.info(
                        f"📤 发送DeepSeek API请求: model={request_data['model']}, "
                        f"messages_count={len(request_data['messages'])}, "
                        f"stream={request_data['stream']}, "
                        f"temperature={temperature}, "
                        f"is_refresh={is_refresh}, "
                        f"tools={'已启用' if need_web_search else '未启用'}"
                    )

                    # 发送流式请求
                    response = requests.post(
                        f"{DEEPSEEK_BASE_URL}/chat/completions",
                        headers=headers,
                        json=request_data,
                        stream=True,
                        timeout=60,
                        proxies={'http': None, 'https': None}
                    )

                    if response.status_code != 200:
                        try:
                            error_text = (
                                response.text if hasattr(response, 'text')
                                else '无法获取错误详情'
                            )
                        except:
                            error_text = '无法读取错误响应'
                        
                        logger.error(
                            f"❌ DeepSeek流式API错误: {response.status_code}"
                        )
                        logger.error(f"❌ 错误详情: {error_text[:500]}")
                        logger.error(f"❌ 请求模型: {request_data.get('model')}")
                        logger.error(
                            f"❌ 消息数量: {len(request_data.get('messages', []))}"
                        )
                        # 打印前3条消息的详细内容
                        for i, msg in enumerate(request_data.get('messages', [])[:3]):
                            content = str(msg.get('content', ''))
                            content_preview = content[:500] + '...' if len(content) > 500 else content
                            logger.error(
                                f"❌ 消息{i}: role={msg.get('role')}, "
                                f"content_len={len(content)}, "
                                f"content_preview={content_preview}"
                            )
                        
                        # 打印完整的请求数据（如果不太长）
                        try:
                            request_json = json.dumps(
                                request_data, ensure_ascii=False
                            )
                            if len(request_json) > 1000:
                                logger.error(
                                    f"❌ 请求数据（前1000字符）: "
                                    f"{request_json[:1000]}..."
                                )
                            else:
                                logger.error(f"❌ 请求数据: {request_json}")
                        except Exception as e:
                            logger.error(f"❌ 无法序列化请求数据: {e}")

                        error_chunk = {
                            'type': 'error',
                            'message': (
                                f'DeepSeek API错误: {response.status_code} - '
                                f'{error_text[:200]}'
                            )
                        }
                        yield f"data: {json.dumps(error_chunk, ensure_ascii=False)}\n\n"
                        return

                    # 处理流式响应
                    full_text = ""
                    sentence_count = 0

                    for line in response.iter_lines():
                        if line:
                            line_str = line.decode('utf-8')
                            if line_str.startswith('data: '):
                                data_str = line_str[6:]

                                if data_str.strip() == '[DONE]':
                                    break

                                try:
                                    chunk_data = json.loads(data_str)
                                    if ('choices' in chunk_data and
                                            len(chunk_data['choices']) > 0):
                                        choice = chunk_data['choices'][0]

                                        # 处理tool_calls（联网搜索）
                                        if ('delta' in choice and
                                                'tool_calls' in choice['delta']):
                                            tool_calls = choice['delta']['tool_calls']
                                            if tool_calls:
                                                logger.info("🔍 检测到联网搜索请求")
                                                search_chunk = {
                                                    'type': 'search_status',
                                                    'message': '正在搜索最新信息...'
                                                }
                                                yield (
                                                    f"data: {json.dumps(search_chunk, ensure_ascii=False)}\n\n"
                                                )

                                        # 处理文本内容
                                        if ('delta' in choice and
                                                'content' in choice['delta']):
                                            content = choice['delta']['content']
                                            full_text += content

                                            # 检查是否完成一个句子
                                            if any(punct in content for punct in
                                                   ['。', '！', '？', '；']):
                                                sentence_count += 1

                                            # 发送文本更新
                                            text_update_chunk = {
                                                'type': 'text_update',
                                                'content': content,
                                                'full_text': full_text,
                                                'sentence_count': sentence_count
                                            }
                                            yield (
                                                f"data: {json.dumps(text_update_chunk, ensure_ascii=False)}\n\n"
                                            )

                                except json.JSONDecodeError as e:
                                    logger.warning(f"⚠️ 解析流式数据失败: {e}")
                                    continue

                    try:
                        logger.info(
                            f"✅ 流式响应完成，总长度: {len(full_text)}"
                        )
                    except Exception:
                        logger.info("✅ 流式响应完成")

                    # 记录交互到数据库
                    actual_session_id = session_id
                    try:
                        success_log, actual_session_id = db_manager.log_interaction(
                            user_id=user_id,
                            interaction_type='text',
                            content=message,
                            response=full_text,
                            session_id=session_id,
                            success=True
                        )
                        if success_log:
                            try:
                                logger.info(
                                    f"✅ 交互记录成功: {user_id}, "
                                    f"session_id: {actual_session_id}"
                                )
                            except Exception:
                                logger.info("✅ 交互记录成功")
                    except Exception as db_error:
                        try:
                            logger.warning(
                                f"⚠️ 记录交互到数据库失败: {db_error}"
                            )
                        except Exception:
                            logger.warning("⚠️ 记录交互到数据库失败")

                    # 发送完成消息
                    complete_chunk = {
                        'type': 'complete',
                        'text': full_text,
                        'sentence_count': sentence_count,
                        'session_id': actual_session_id
                    }
                    yield f"data: {json.dumps(complete_chunk, ensure_ascii=False)}\n\n"

                except Exception as e:
                    # 安全地处理错误信息，避免编码问题
                    try:
                        error_msg = str(e).encode('utf-8', errors='replace').decode('utf-8')
                        logger.error(f"❌ 流式响应生成失败: {error_msg}")
                    except:
                        logger.error("❌ 流式响应生成失败: 编码错误")
                    
                    error_chunk = {
                        'type': 'error',
                        'message': '流式响应失败，请稍后重试'
                    }
                    try:
                        yield f"data: {json.dumps(error_chunk, ensure_ascii=False)}\n\n"
                    except Exception as yield_error:
                        # 如果yield也失败，使用ASCII编码
                        error_chunk_ascii = {
                            'type': 'error',
                            'message': 'Streaming response failed'
                        }
                        yield f"data: {json.dumps(error_chunk_ascii)}\n\n"

                    # 记录失败的交互
                    try:
                        db_manager.log_interaction(
                            user_id=user_id,
                            interaction_type='text',
                            content=message,
                            response='',
                            session_id=session_id,
                            success=False,
                            error_message=str(e)
                        )
                    except Exception as db_error:
                        logger.warning(
                            f"⚠️ 记录失败交互到数据库失败: {db_error}"
                        )

            return app.response_class(
                generate_streaming_response(),
                mimetype='text/plain',
                headers={
                    'Cache-Control': 'no-cache',
                    'Connection': 'keep-alive',
                    'X-Accel-Buffering': 'no'
                }
            )

        except Exception as e:
            logger.error(f"❌ 流式聊天API错误: {e}")
            return jsonify({'error': str(e)}), 500

    @app.route('/api/chat', methods=['POST'])
    def chat():
        """AI聊天API（非流式）"""
        try:
            data = request.get_json()
            if not data or 'message' not in data:
                return jsonify({'error': 'No message provided'}), 400

            message = data['message']
            user_id = data.get('user_id', 'anonymous')
            session_id = data.get('session_id', '')
            conversation_history = data.get('conversation_history', [])

            logger.info(f"🤖 收到聊天请求: {message}")

            # 验证用户身份
            if user_id == 'anonymous' or not db_manager.user_exists(user_id):
                logger.warning(f"⚠️ 无效的用户ID: {user_id}")
                return jsonify({
                    'error': '需要有效的用户身份验证'
                }), 401

            # 处理session_id
            if not session_id or session_id.strip() == '':
                session_id = db_manager.create_session(user_id)
                if not session_id:
                    return jsonify({'error': '无法创建session'}), 500

            # 调用DeepSeek API
            ai_response = chat_with_deepseek(message, conversation_history)

            # 记录交互
            try:
                db_manager.log_interaction(
                    user_id=user_id,
                    interaction_type='text',
                    content=message,
                    response=ai_response,
                    session_id=session_id,
                    success=True
                )
            except Exception as db_error:
                logger.warning(f"⚠️ 记录交互失败: {db_error}")

            return jsonify({
                'success': True,
                'response': ai_response,
                'session_id': session_id
            }), 200

        except Exception as e:
            logger.error(f"❌ 聊天API错误: {e}")
            return jsonify({'error': str(e)}), 500

