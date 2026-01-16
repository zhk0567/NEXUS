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

        # 检查edge-tts服务可用性（仅在第一次）
        service_checked = False

        # 直接使用edge-tts - 重试机制
        for retry in range(TTS_CONFIG['max_retries']):
            # 在第一次尝试时检查服务可用性
            if not service_checked and retry == 0:
                try:
                    # 尝试列出可用音色来验证服务
                    logger.info("🎵 检查edge-tts服务可用性...")
                    voices = await edge_tts.list_voices()
                    if voices:
                        logger.info(f"🎵 edge-tts服务可用，找到 {len(voices)} 个音色")
                        # 验证请求的音色是否存在
                        voice_names = [v.get('ShortName', '') for v in voices if isinstance(v, dict)]
                        if voice not in voice_names:
                            logger.warning(f"⚠️ 音色 {voice} 不在可用列表中，将尝试使用")
                    else:
                        logger.warning("⚠️ edge-tts服务可能不可用，无法列出音色")
                    service_checked = True
                except Exception as check_error:
                    logger.warning(f"⚠️ edge-tts服务检查失败: {check_error}")
                    logger.warning(f"⚠️ 可能无法访问Microsoft TTS服务，继续尝试...")
                    service_checked = True
                    # 不中断流程，继续尝试
            try:
                logger.info(f"🎵 edge-tts尝试 {retry + 1}/{TTS_CONFIG['max_retries']}")

                # 增加重试延迟
                if retry > 0:
                    delay = TTS_CONFIG['retry_delay'] + random.uniform(0, 1)
                    logger.info(f"🎵 等待 {delay:.1f} 秒后重试edge-tts...")
                    await asyncio.sleep(delay)

                # 使用edge-tts - 尝试不同的调用方式
                try:
                    # 方法1: 直接使用Communicate
                    communicate = edge_tts.Communicate(
                        processed_text,
                        voice
                    )
                    logger.info(f"🎵 edge-tts Communicate对象创建成功 (文本长度: {len(processed_text)}, 音色: {voice})")
                except Exception as create_error:
                    logger.error(f"❌ 创建edge-tts Communicate对象失败: {create_error}")
                    if retry < TTS_CONFIG['max_retries'] - 1:
                        continue
                    else:
                        raise

                # 初始化变量
                audio_data = b""
                chunk_count = 0
                metadata_received = False

                # 处理音频流 - edge-tts的stream()返回字典格式
                async def process_audio_stream():
                    nonlocal audio_data, chunk_count, metadata_received

                    try:
                        # 尝试使用stream()方法
                        stream_iter = communicate.stream()
                        has_data = False
                        first_chunk_time = None
                        
                        logger.info(f"🎵 开始处理音频流...")
                        
                        async for chunk in stream_iter:
                            if first_chunk_time is None:
                                first_chunk_time = time.time()
                                logger.info(f"🎵 收到第一个chunk (延迟: {first_chunk_time - start_time:.2f}秒)")
                            
                            has_data = True
                            
                            if chunk is None:
                                logger.warning("⚠️ 收到空的chunk")
                                continue
                            
                            # edge-tts返回的chunk是字典格式: {"type": "audio"/"metadata", "data": bytes}
                            if isinstance(chunk, dict):
                                chunk_type = chunk.get("type", "unknown")
                                chunk_data = chunk.get("data", b"")
                                
                                if chunk_type == "audio":
                                    if chunk_data and len(chunk_data) > 0:
                                        audio_data += chunk_data
                                        chunk_count += 1
                                    else:
                                        logger.warning("⚠️ 收到空的audio数据")
                                elif chunk_type == "metadata":
                                    metadata_received = True
                                    logger.debug(f"🎵 收到metadata")
                                else:
                                    logger.debug(f"🎵 收到chunk类型: {chunk_type}")
                            else:
                                logger.warning(f"⚠️ 未知chunk类型: {type(chunk)}")
                            
                            if chunk_count > 0 and chunk_count % 5 == 0:
                                logger.info(
                                    f"🎵 已处理 {chunk_count} 块，"
                                    f"当前大小: {len(audio_data)} 字节"
                                )
                        
                        if not has_data:
                            raise Exception("edge-tts stream()未返回任何数据，可能是网络问题或服务不可用")
                            
                    except Exception as stream_error:
                        error_msg = str(stream_error)
                        logger.error(f"❌ 处理音频流失败: {error_msg}")
                        logger.error(f"   已收到 {chunk_count} 块，总大小: {len(audio_data)} 字节")
                        logger.error(f"   文本: {processed_text[:50]}...")
                        logger.error(f"   音色: {voice}")
                        
                        # 如果是NoAudioReceived错误，提供更详细的诊断信息
                        if "No audio was received" in error_msg or "NoAudioReceived" in error_msg:
                            logger.error(f"❌ edge-tts诊断信息:")
                            logger.error(f"   1. 检查网络连接是否正常")
                            logger.error(f"   2. 检查是否可以访问 https://speech.platform.bing.com")
                            logger.error(f"   3. 检查防火墙或代理设置")
                            logger.error(f"   4. 尝试使用其他音色或稍后重试")
                        
                        raise

                try:
                    await asyncio.wait_for(
                        process_audio_stream(),
                        timeout=TTS_CONFIG['timeout_total']
                    )
                    logger.info(f"🎵 音频流处理完成，收到 {chunk_count} 块")
                except asyncio.TimeoutError:
                    logger.warning(f"⚠️ 音频流处理超时，已收到 {chunk_count} 块，大小: {len(audio_data)} 字节")
                    # 即使超时，如果已经有数据，继续处理
                    if len(audio_data) == 0:
                        raise

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
                error_msg = str(e)
                logger.warning(f"⚠️ edge-tts尝试 {retry + 1} 失败: {error_msg}")
                
                # 检查是否是"No audio was received"错误
                if "No audio was received" in error_msg or "no audio" in error_msg.lower():
                    logger.error(f"❌ edge-tts未收到音频数据，可能原因：")
                    logger.error(f"   1. 网络连接问题，无法访问Microsoft TTS服务")
                    logger.error(f"   2. 文本或音色参数无效")
                    logger.error(f"   3. edge-tts服务暂时不可用")
                    logger.error(f"   文本: {processed_text[:50]}...")
                    logger.error(f"   音色: {voice}")
                    
                    # 如果是最后一次重试，尝试使用默认音色
                    if retry == TTS_CONFIG['max_retries'] - 1 and voice != 'zh-CN-XiaoxiaoNeural':
                        logger.info("🔄 尝试使用默认音色重试...")
                        try:
                            communicate_default = edge_tts.Communicate(processed_text, 'zh-CN-XiaoxiaoNeural')
                            audio_data_fallback = b""
                            async for chunk in communicate_default.stream():
                                if isinstance(chunk, dict) and chunk.get("type") == "audio":
                                    audio_data_fallback += chunk.get("data", b"")
                            if len(audio_data_fallback) > 0:
                                logger.info(f"✅ 使用默认音色成功生成音频: {len(audio_data_fallback)} 字节")
                                return audio_data_fallback
                        except Exception as fallback_error:
                            logger.error(f"❌ 默认音色重试也失败: {fallback_error}")
                
                if retry < TTS_CONFIG['max_retries'] - 1:
                    continue
                else:
                    logger.error(f"❌ edge-tts执行异常: {error_msg}")
                    import traceback
                    logger.error(f"❌ edge-tts错误堆栈: {traceback.format_exc()}")
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

