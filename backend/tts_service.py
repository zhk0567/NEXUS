# -*- coding: utf-8 -*-
"""
TTS服务模块
"""
import os
import time
import asyncio
import random
import tempfile
import concurrent.futures
from backend.logger_config import logger
from backend.config import TTS_CONFIG, DOLPHIN_MODEL_PATH

# 导入edge-tts
try:
    import edge_tts
    EDGE_TTS_AVAILABLE = True
    logger.info("✅ edge-tts模块导入成功")
except ImportError as e:
    EDGE_TTS_AVAILABLE = False
    logger.error(f"❌ edge-tts模块导入失败: {e}")

# TTS缓存和并发控制
tts_cache = {}
tts_concurrent_count = 0
tts_last_health_check = 0


def cleanup_tts_cache():
    """清理TTS缓存"""
    global tts_cache
    try:
        # 限制缓存大小，保留最近使用的
        if len(tts_cache) > 50:  # 最多保留50个缓存
            # 删除最旧的缓存项
            items_to_remove = list(tts_cache.keys())[:len(tts_cache) - 50]
            for key in items_to_remove:
                del tts_cache[key]
            logger.info(f"🧹 清理TTS缓存，删除 {len(items_to_remove)} 项")
    except Exception as e:
        logger.error(f"❌ 缓存清理失败: {e}")


def check_tts_health():
    """检查TTS服务健康状态"""
    global tts_last_health_check
    current_time = time.time()

    # 如果距离上次检查时间太短，跳过
    if current_time - tts_last_health_check < TTS_CONFIG.get('health_check_interval', 10):
        return True

    try:
        # 简单的健康检查 - 直接调用TTS函数
        test_audio = generate_tts_audio("测试", "zh-CN-XiaoxiaoNeural")
        tts_last_health_check = current_time
        return len(test_audio) > 100
    except Exception as e:
        logger.warning(f"⚠️ TTS健康检查失败: {e}")
        return False


async def generate_tts_audio_async(text: str, voice: str = "zh-CN-XiaoxiaoNeural") -> bytes:
    """异步生成TTS音频"""
    global tts_concurrent_count
    start_time = time.time()
    success = False
    error_type = None

    try:
        logger.info(f"🎵 开始TTS处理: {text}, 音色: {voice}")

        # 并发控制
        if tts_concurrent_count >= TTS_CONFIG['concurrent_limit']:
            logger.warning("⚠️ TTS并发限制，拒绝请求")
            error_type = "concurrent_limit"
            return b""

        tts_concurrent_count += 1

        # 缓存检查
        cache_key = f"{text}_{voice}"
        if TTS_CONFIG['cache_enabled'] and cache_key in tts_cache:
            logger.info("🎵 使用缓存音频")
            return tts_cache[cache_key]

        # 预处理文本
        processed_text = text.strip()
        if not processed_text:
            logger.warning("⚠️ 文本为空，使用默认文本")
            processed_text = "测试"

        # 限制文本长度
        text_limit = TTS_CONFIG.get('text_length_limit', 500)
        if len(processed_text) > text_limit:
            processed_text = processed_text[:text_limit]
            logger.info(f"🎵 文本过长，截取前{text_limit}字符")

        # 验证和标准化音色
        valid_voices = [
            'zh-CN-XiaoxiaoNeural',
            'zh-CN-YunxiNeural',
            'zh-CN-YunyangNeural',
            'zh-CN-XiaoyiNeural',
            'zh-CN-YunjianNeural'
        ]

        if voice not in valid_voices:
            logger.warning(f"⚠️ 无效音色: {voice}，使用默认音色")
            voice = 'zh-CN-XiaoxiaoNeural'

        logger.info(f"🎵 使用音色: {voice}")

        # 直接使用edge-tts - 重试机制
        for retry in range(TTS_CONFIG['max_retries']):
            try:
                logger.info(f"🎵 edge-tts尝试 {retry + 1}/{TTS_CONFIG['max_retries']}")

                # 增加重试延迟
                if retry > 0:
                    delay = TTS_CONFIG['retry_delay'] + random.uniform(0, 1)
                    logger.info(f"🎵 等待 {delay:.1f} 秒后重试edge-tts...")
                    await asyncio.sleep(delay)

                # 使用edge-tts
                communicate = edge_tts.Communicate(
                    processed_text,
                    voice,
                    rate="+10%",
                    pitch="+0Hz",
                    volume="+0%"
                )

                # 初始化变量
                audio_data = b""
                chunk_count = 0

                # 处理音频流
                async def process_audio_stream():
                    nonlocal audio_data, chunk_count

                    async for chunk in communicate.stream():
                        chunk_type = chunk.get("type", "unknown")
                        chunk_data = chunk.get("data", b"")

                        if chunk_type == "audio" and chunk_data:
                            audio_data += chunk_data
                            chunk_count += 1
                        if chunk_count % 5 == 0:
                            logger.info(
                                f"🎵 已处理 {chunk_count} 块，"
                                f"当前大小: {len(audio_data)} 字节"
                            )

                await asyncio.wait_for(
                    process_audio_stream(),
                    timeout=TTS_CONFIG['timeout_total']
                )

                # 验证音频数据
                if len(audio_data) == 0:
                    logger.warning("⚠️ 音频数据为空，重试...")
                    if retry < TTS_CONFIG['max_retries'] - 1:
                        continue
                    else:
                        logger.error("❌ 音频数据为空")
                        error_type = "audio_empty"
                        return b""
                elif len(audio_data) < 1000:
                    logger.warning(f"⚠️ 音频数据过小: {len(audio_data)} 字节，重试...")
                    if retry < TTS_CONFIG['max_retries'] - 1:
                        continue
                    else:
                        logger.error(f"❌ 音频数据过小: {len(audio_data)} 字节")
                        error_type = "audio_too_small"
                        return b""

                logger.info(f"🎵 edge-tts生成成功，音频大小: {len(audio_data)} 字节")

                # 缓存音频数据
                if TTS_CONFIG['cache_enabled']:
                    cache_key = f"{processed_text}_{voice}"
                    tts_cache[cache_key] = audio_data
                    cleanup_tts_cache()

                success = True
                return audio_data

            except asyncio.TimeoutError:
                logger.warning(f"⚠️ edge-tts尝试 {retry + 1} 超时")
                if retry < TTS_CONFIG['max_retries'] - 1:
                    continue
                else:
                    logger.error("❌ edge-tts超时")
                    error_type = "timeout"
                    return b""
            except Exception as e:
                logger.warning(f"⚠️ edge-tts尝试 {retry + 1} 失败: {e}")
                if retry < TTS_CONFIG['max_retries'] - 1:
                    continue
                else:
                    logger.error(f"❌ edge-tts执行异常: {e}")
                    error_type = "exception"
                    return b""

        return b""

    except Exception as e:
        logger.error(f"❌ TTS处理失败: {e}")
        import traceback
        logger.error(f"❌ TTS错误详情: {traceback.format_exc()}")
        error_type = "exception"
        return b""

    finally:
        # 更新并发计数
        tts_concurrent_count = max(0, tts_concurrent_count - 1)


def run_async_tts(text: str, voice: str) -> bytes:
    """在线程中运行异步TTS"""
    loop = None
    try:
        # 创建新的事件循环（在线程中）
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            return loop.run_until_complete(
                generate_tts_audio_async(text, voice)
            )
        except Exception as e:
            logger.error(f"❌ 线程异步TTS执行失败: {e}")
            import traceback
            logger.error(f"❌ 线程异步TTS错误详情: {traceback.format_exc()}")
            return b""
        finally:
            # 确保清理事件循环
            if loop and not loop.is_closed():
                try:
                    # 取消所有待处理的任务
                    pending = asyncio.all_tasks(loop)
                    for task in pending:
                        task.cancel()
                    if pending:
                        loop.run_until_complete(
                            asyncio.gather(*pending, return_exceptions=True)
                        )
                except:
                    pass
                finally:
                    try:
                        loop.close()
                    except:
                        pass
    except Exception as e:
        logger.error(f"❌ 线程异步TTS失败: {e}")
        return b""


def generate_tts_audio(text: str, voice: str = "zh-CN-XiaoxiaoNeural") -> bytes:
    """同步包装器 - 调用异步TTS生成"""
    try:
        # 在Flask的同步上下文中，使用线程池运行异步函数
        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as executor:
            future = executor.submit(run_async_tts, text, voice)
            try:
                timeout = TTS_CONFIG['timeout_total'] + 10
                return future.result(timeout=timeout)
            except concurrent.futures.TimeoutError:
                logger.error(f"❌ TTS生成超时（超过 {timeout} 秒）")
                return b""
    except Exception as e:
        logger.error(f"❌ 同步TTS包装器失败: {e}")
        import traceback
        logger.error(f"❌ 同步TTS包装器错误详情: {traceback.format_exc()}")
        return b""

