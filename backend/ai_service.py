# -*- coding: utf-8 -*-
"""
AI服务模块 - DeepSeek API集成
"""
import json
import requests
from backend.logger_config import logger
from backend.config import DEEPSEEK_API_KEY, DEEPSEEK_BASE_URL


SYSTEM_PROMPT = """你是一个贴心的AI助手，名字叫小美。请用温暖、耐心、易懂的方式回答用户的问题。
重要：你必须用完整的中文句子回答，绝对不要只返回数字、代码或时间戳。

回答要求：
用温暖、亲切的语气与用户交流，就像对待朋友一样。
语言要简单易懂，避免使用复杂的专业术语和网络用语。
说话要慢一点，每个要点都要说清楚，不要着急。
如果涉及健康、医疗、养生等问题，要特别谨慎，建议咨询专业医生。
对于生活常识和日常问题，要详细解释，让用户能够理解。
如果涉及科技产品使用，要一步一步详细说明。
对于天气、日期、节日等日常信息，要说得具体清楚。
如果用户问重复的问题，要耐心回答，不要表现出不耐烦。
对于家庭、子女、孙辈等话题，要给予理解和关怀。
如果涉及金钱、投资等敏感话题，要提醒谨慎，建议与家人商量。
用词要通俗易懂，避免使用年轻人常用的网络词汇。
句子要完整，表达要清晰，让用户容易理解。

格式要求：
绝对不要使用任何markdown格式符号(*、#、-、_、`等)。
绝对不要使用emoji表情符号或特殊符号。
保持简洁明了，句子之间用句号分隔，不要使用多余空格。
不要使用列表格式，用句号连接各个要点。
不要使用换行符，所有内容在一行内表达。
标点符号前后不要添加空格。

请确保你的回答是完整的中文句子，包含具体信息，格式简洁清晰，没有多余的空格和符号，特别适合用户理解和接受。"""


def chat_with_deepseek(message: str, conversation_history: list = None) -> str:
    """与DeepSeek API聊天"""
    try:
        logger.info(f"🤖 开始AI聊天: {message}")

        headers = {
            "Authorization": f"Bearer {DEEPSEEK_API_KEY}",
            "Content-Type": "application/json"
        }

        # 构建消息列表
        messages = [
            {
                "role": "system",
                "content": SYSTEM_PROMPT
            }
        ]

        # 添加对话历史（如果提供）
        if conversation_history:
            # 只保留最近10条对话
            for hist_msg in conversation_history[-10:]:
                if isinstance(hist_msg, dict) and 'role' in hist_msg and 'content' in hist_msg:
                    messages.append(hist_msg)

        # 添加当前消息
        messages.append({
            "role": "user",
            "content": message
        })

        data = {
            "model": "deepseek-chat",
            "messages": messages,
            "max_tokens": 1000,
            "temperature": 0.7
        }

        response = requests.post(
            f"{DEEPSEEK_BASE_URL}/chat/completions",
            headers=headers,
            json=data,
            timeout=30,
            proxies={'http': None, 'https': None}  # 禁用代理
        )

        if response.status_code == 200:
            result = response.json()
            ai_response = result['choices'][0]['message']['content']
            logger.info(f"🤖 AI回复: {ai_response}")
            return ai_response
        else:
            logger.error(
                f"❌ DeepSeek API错误: {response.status_code} - {response.text}"
            )
            return "抱歉，AI服务暂时不可用，请稍后重试。"

    except Exception as e:
        logger.error(f"❌ AI聊天失败: {e}")
        return "抱歉，AI服务出现错误，请稍后重试。"


def build_chat_messages(message: str, conversation_history: list = None) -> list:
    """构建聊天消息列表"""
    from datetime import datetime
    
    # 检查消息是否涉及日期/时间查询
    date_keywords = ['今天', '明天', '后天', '日期', '星期', '几号', '几月', 
                     '现在几点', '现在几点了', '今天是', '什么日子', '几月几号']
    message_lower = message.lower() if message else ''
    needs_date_info = any(keyword in message_lower for keyword in date_keywords)
    
    # 构建系统提示词
    system_content = SYSTEM_PROMPT.strip()
    
    # 只在需要时添加日期信息，避免系统提示词过长
    if needs_date_info:
        now = datetime.now()
        current_date = now.strftime("%Y年%m月%d日")
        current_weekday = ["星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"][now.weekday()]
        current_time = now.strftime("%H:%M")
        
        date_info = f"当前日期：{current_date} {current_weekday}，当前时间：{current_time}。回答日期时间问题时请使用此准确信息。"
        system_content = system_content + "\n\n" + date_info
        logger.info(f"📅 添加日期信息: {current_date} {current_weekday}")
    
    # 清理系统提示词，移除所有换行符，改为空格（DeepSeek API可能不接受换行符）
    # 将换行符替换为空格，然后清理多余空格
    system_content = ' '.join(system_content.split())
    
    # 检查系统提示词长度（DeepSeek API可能有长度限制）
    if len(system_content) > 2000:
        logger.warning(f"⚠️ 系统提示词过长: {len(system_content)} 字符，将截断")
        system_content = system_content[:2000] + "..."
    
    messages = [
        {
            "role": "system",
            "content": system_content
        }
    ]
    
    logger.info(f"📝 系统消息长度: {len(system_content)} 字符")

    # 添加对话历史
    if conversation_history:
        for idx, history_item in enumerate(conversation_history):
            # 确保 history_item 是字典类型
            if not isinstance(history_item, dict):
                logger.warning(f"⚠️ 对话历史项{idx}不是字典类型: {type(history_item)}")
                continue

            # 获取角色和内容（支持 isUser 和 is_user 两种格式）
            is_user = history_item.get("isUser", history_item.get("is_user", True))
            content = history_item.get("content", "")

            # 跳过空内容
            if not content:
                logger.warning(f"⚠️ 对话历史项{idx}内容为空，跳过")
                continue
            
            # 确保内容是字符串类型
            try:
                content_str = str(content).strip()
                if not content_str:
                    logger.warning(f"⚠️ 对话历史项{idx}内容为空字符串，跳过")
                    continue
            except Exception as e:
                logger.warning(f"⚠️ 对话历史项{idx}内容转换失败: {e}")
                continue

            role = "user" if is_user else "assistant"
            messages.append({
                "role": role,
                "content": content_str
            })

    # 添加当前消息
    if not message or not message.strip():
        return messages

    messages.append({
        "role": "user",
        "content": message.strip()
    })

    return messages


def validate_messages(messages: list) -> list:
    """验证消息格式"""
    valid_messages = []
    has_system_message = False
    
    for i, msg in enumerate(messages):
        if not isinstance(msg, dict):
            logger.warning(f"⚠️ 消息{i}无效格式: {type(msg)}")
            continue
        if 'role' not in msg or 'content' not in msg:
            logger.warning(f"⚠️ 消息{i}缺少必要字段: {list(msg.keys())}")
            continue
        if msg['role'] not in ['system', 'user', 'assistant']:
            logger.warning(f"⚠️ 消息{i}无效角色: {msg['role']}")
            continue
        
        # 对于system消息，即使内容为空也要保留（但确保不是None）
        is_system = msg['role'] == 'system'
        if is_system:
            has_system_message = True
        
        content = msg.get('content', '')
        # system消息允许为空，但user和assistant消息不能为空
        if not is_system and (not content or not str(content).strip()):
            logger.warning(f"⚠️ 消息{i}内容为空，跳过")
            continue
        
        # 确保内容不是None且可以转换为字符串
        try:
            content_str = str(content).strip() if content else ''
            # system消息即使为空也保留，但确保不是None
            if not is_system and not content_str:
                logger.warning(f"⚠️ 消息{i}内容为空字符串，跳过")
                continue
            
            valid_messages.append({
                'role': msg['role'],
                'content': content_str if content_str else ''  # 确保system消息至少是空字符串
            })
        except Exception as e:
            logger.warning(f"⚠️ 消息{i}内容转换失败: {e}")
            continue

    # 确保至少有一条system消息
    if not has_system_message and valid_messages:
        logger.warning("⚠️ 没有system消息，添加默认system消息")
        valid_messages.insert(0, {
            'role': 'system',
            'content': SYSTEM_PROMPT.strip()
        })

    if len(valid_messages) != len(messages):
        logger.warning(
            f"⚠️ 消息验证: 原始{len(messages)}条，有效{len(valid_messages)}条"
        )
    
    # 确保至少有一条消息
    if not valid_messages:
        logger.error("❌ 验证后没有有效消息，返回默认system消息")
        return [{
            'role': 'system',
            'content': SYSTEM_PROMPT.strip()
        }]

    return valid_messages

