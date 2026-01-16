# -*- coding: utf-8 -*-
"""
ASR服务模块
"""
import os
from backend.logger_config import logger
from backend.config import DOLPHIN_MODEL_PATH

# 导入Dolphin ASR
try:
    import dolphin
    DOLPHIN_AVAILABLE = True
    logger.info("✅ Dolphin ASR模块导入成功")
except ImportError as e:
    DOLPHIN_AVAILABLE = False
    logger.warning(f"⚠️ Dolphin ASR模块导入失败: {e}")
    logger.warning("将使用模拟ASR结果")

DOLPHIN_MODEL = None

# ASR处理状态跟踪
asr_processing_status = {
    'is_processing': False,
    'current_request_id': None,
    'start_time': None,
    'progress': 0
}


def initialize_dolphin_model():
    """初始化Dolphin ASR模型"""
    global DOLPHIN_MODEL

    if not DOLPHIN_AVAILABLE:
        logger.warning("Dolphin不可用，跳过模型初始化")
        return False

    try:
        logger.info("🔄 正在初始化Dolphin ASR模型...")

        # 获取项目根目录的绝对路径
        current_dir = os.path.dirname(os.path.abspath(__file__))
        # 从backend目录回到项目根目录
        project_root = os.path.dirname(current_dir)
        # 使用相对路径配置
        dolphin_model_path = os.path.join(project_root, DOLPHIN_MODEL_PATH)

        # 检查模型目录是否存在
        if not os.path.exists(dolphin_model_path):
            logger.error(f"❌ Dolphin模型路径不存在: {dolphin_model_path}")
            return False

        # 检查模型文件是否存在（small.pt）
        model_file = os.path.join(dolphin_model_path, "small.pt")
        if not os.path.exists(model_file):
            logger.error(f"❌ Dolphin模型文件不存在: {model_file}")
            return False

        logger.info(f"🎤 使用模型路径: {dolphin_model_path}")
        logger.info(f"🎤 模型文件: {model_file}")

        # 加载模型 - 使用small模型
        DOLPHIN_MODEL = dolphin.load_model("small", dolphin_model_path, "cpu")
        logger.info("✅ Dolphin ASR模型初始化成功")
        return True

    except Exception as e:
        logger.error(f"❌ Dolphin模型初始化失败: {e}")
        import traceback
        logger.error(f"❌ 错误详情: {traceback.format_exc()}")
        DOLPHIN_MODEL = None
        return False


def transcribe_with_dolphin(audio_file_path: str) -> str:
    """使用Dolphin进行语音识别"""
    try:
        logger.info(f"🎤 开始Dolphin语音识别，文件: {audio_file_path}")

        if not DOLPHIN_AVAILABLE:
            logger.warning("Dolphin模块不可用，返回模拟结果")
            return "这是模拟的语音识别结果"

        if DOLPHIN_MODEL is None:
            logger.warning("Dolphin模型未初始化，返回模拟结果")
            return "这是模拟的语音识别结果"

        logger.info(f"🎤 使用Dolphin进行语音识别: {audio_file_path}")

        # 加载音频 - 优先使用torchaudio（更稳定），失败时尝试ffmpeg
        waveform = None
        import logging
        
        # 临时抑制 dolphin 库内部的错误输出
        dolphin_logger = logging.getLogger('dolphin')
        original_level = dolphin_logger.level
        dolphin_logger.setLevel(logging.ERROR)
        
        try:
            # 优先使用torchaudio（更稳定，不依赖ffmpeg）
            try:
                import torchaudio
                logger.debug("🔄 尝试使用torchaudio加载音频...")
                
                # 使用torchaudio加载音频
                waveform_tensor, sample_rate = torchaudio.load(audio_file_path)
                
                # 转换为numpy数组并处理格式
                import torch
                if waveform_tensor.dim() > 1:
                    # 如果是多声道，转换为单声道
                    waveform_tensor = torch.mean(waveform_tensor, dim=0)
                
                # 转换为numpy
                waveform = waveform_tensor.numpy()
                
                # 确保是1D数组
                if waveform.ndim > 1:
                    waveform = waveform.flatten()
                
                logger.debug(f"🎤 使用torchaudio加载音频成功，形状: {waveform.shape}, 采样率: {sample_rate}")
            except Exception as torch_error:
                logger.debug(f"⚠️ torchaudio加载音频失败，尝试ffmpeg: {torch_error}")
                
                # 如果torchaudio失败，尝试使用ffmpeg（dolphin的默认方法）
                try:
                    waveform = dolphin.load_audio(audio_file_path)
                    logger.debug(f"🎤 使用ffmpeg加载音频成功，形状: {waveform.shape}")
                except Exception as ffmpeg_error:
                    # 两个方法都失败
                    error_msg = str(ffmpeg_error)
                    logger.warning(f"⚠️ 音频加载失败（torchaudio和ffmpeg都失败）: {error_msg}")
                    raise RuntimeError(f"无法加载音频文件，torchaudio和ffmpeg都失败")
        finally:
            # 恢复原始日志级别
            dolphin_logger.setLevel(original_level)
        
        if waveform is None:
            raise RuntimeError("音频加载失败，waveform为None")

        # 进行识别
        result = DOLPHIN_MODEL(waveform, lang_sym="zh", region_sym="CN")
        logger.info(f"🎤 原始识别结果: {result.text}")

        # 提取纯文本结果（去除特殊标记）
        text = result.text
        if text.startswith("<zh><CN><asr>"):
            # 移除语言和区域标记
            text = text.replace("<zh><CN><asr>", "")
            # 移除时间标记
            import re
            text = re.sub(r'<[0-9.]+>', '', text)
            text = text.strip()

        logger.info(f"🎤 处理后识别结果: {text}")
        return text if text else "识别结果为空"

    except RuntimeError as e:
        # 音频加载失败，返回友好提示
        error_msg = str(e)
        if "无法加载音频文件" in error_msg or "Failed to load audio" in error_msg:
            logger.warning(f"⚠️ 音频加载失败: {error_msg}")
            return "音频文件无法加载，请检查文件格式"
        else:
            logger.error(f"❌ Dolphin语音识别失败: {e}")
            return "语音识别失败"
    except Exception as e:
        error_msg = str(e)
        # 检查是否是音频太短的错误
        if "too short" in error_msg.lower() or "TooShortUttError" in error_msg:
            logger.warning(f"⚠️ 音频太短，无法识别: {error_msg}")
            return "音频太短，无法识别"
        else:
            logger.error(f"❌ Dolphin语音识别失败: {e}")
            import traceback
            logger.debug(f"❌ 错误详情: {traceback.format_exc()}")
            return "语音识别失败"

