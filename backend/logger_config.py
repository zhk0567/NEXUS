# -*- coding: utf-8 -*-
"""
日志配置模块
"""
import sys
import io
import logging

# 设置标准输出编码为UTF-8，解决Windows PowerShell编码问题
if sys.platform == 'win32':
    # 使用环境变量设置编码，避免直接替换sys.stdout导致的问题
    # 直接替换可能导致Flask内部编码问题
    import locale
    try:
        # 尝试设置locale为UTF-8
        locale.setlocale(locale.LC_ALL, 'en_US.UTF-8')
    except:
        try:
            locale.setlocale(locale.LC_ALL, 'C.UTF-8')
        except:
            pass  # 如果都失败，继续使用默认locale

# 配置日志 - 临时提高级别以调试400错误
# 创建UTF-8编码的StreamHandler
class UTF8StreamHandler(logging.StreamHandler):
    """UTF-8编码的StreamHandler，避免Windows编码问题"""
    def emit(self, record):
        try:
            msg = self.format(record)
            # 确保使用UTF-8编码
            if sys.platform == 'win32':
                # Windows下使用UTF-8编码
                stream = self.stream
                if hasattr(stream, 'buffer'):
                    stream.buffer.write(msg.encode('utf-8', errors='replace'))
                    stream.buffer.write(b'\n')
                    self.flush()
                else:
                    stream.write(msg)
                    stream.write('\n')
                    self.flush()
            else:
                super().emit(record)
        except Exception:
            self.handleError(record)

logging.basicConfig(
    level=logging.ERROR,  # 只显示ERROR级别
    format='%(levelname)s - %(message)s',  # 简化格式，移除时间戳
    handlers=[UTF8StreamHandler()],
)
logger = logging.getLogger(__name__)

# 禁用Flask和Werkzeug的请求日志
werkzeug_logger = logging.getLogger('werkzeug')
werkzeug_logger.setLevel(logging.ERROR)


class HTTPErrorFilter(logging.Filter):
    """过滤HTTP错误（通常是外部扫描或无效请求）"""

    def filter(self, record):
        """过滤掉HTTP 400和505错误"""
        message = str(record.getMessage())
        # 过滤HTTP 400错误（Bad request）
        if 'code 400' in message or 'Bad request' in message or 'Bad HTTP' in message:
            return False
        # 过滤HTTP 505错误（Invalid HTTP version 2.0）
        if 'code 505' in message or 'Invalid HTTP version' in message or 'HTTP version (2.0)' in message:
            return False
        return True


werkzeug_logger.addFilter(HTTPErrorFilter())
logging.getLogger('flask').setLevel(logging.ERROR)

# 为关键启动信息创建单独的logger
startup_logger = logging.getLogger('startup')
startup_logger.setLevel(logging.INFO)
startup_handler = UTF8StreamHandler()
startup_handler.setFormatter(logging.Formatter('%(message)s'))
startup_logger.addHandler(startup_handler)
startup_logger.propagate = False

