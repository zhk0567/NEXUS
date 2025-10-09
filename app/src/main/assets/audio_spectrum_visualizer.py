import numpy as np
import matplotlib.pyplot as plt
import matplotlib.animation as animation
import pyaudio
import wave
import os
import operator
from functools import reduce
import time
from scipy import signal


class SubplotAnimation(animation.TimedAnimation):
    def __init__(self, static=False, path=None):
        """
        音频波形动态显示，实时显示波形
        :param static: 是否为静态模式
        :param path:   wav 文件路径
        """
        self.static = static
        if static and os.path.isfile(path):
            self.stream = wave.open(path)
            # 采样频率
            self.rate = self.stream.getparams()[2]
            self.chunk = self.rate / 2
            self.read = self.stream.readframes
        else:
            self.rate = 8000  # 采样率
            self.chunk = 256  # 适中的语音块大小，平衡响应速度和数据量
            self.deviceindex = 0  # 录音设备编号
            p = pyaudio.PyAudio()
            # frames_per_buffer=self.chunk 设置音频流的缓冲区大小，即每次从音频设备读取的数据块的大小
            self.stream = p.open(format=pyaudio.paInt16, channels=1, rate=self.rate,
                                 input_device_index=self.deviceindex,
                                 input=True, frames_per_buffer=self.chunk)
            self.read = self.stream.read

        self.chunknum = 1  # 减少到1个块，消除延迟
        self.voicedatas = []
        self.zero = [0 for i in range(self.chunk)]
        for index in range(self.chunknum):
            self.voicedatas.insert(0, self.zero)
        
        # 添加音频检测和美化参数
        self.audio_threshold = 500  # 提高音频检测阈值，减少误触发
        self.smoothing_factor = 0.15  # 减慢升起速度
        self.last_amplitude = 0
        self.frame_count = 0
        
        # 频谱分析参数
        self.spectrum_bins = 64  # 频谱柱数量
        self.spectrum_data = np.zeros(self.spectrum_bins)
        self.smoothed_spectrum = np.zeros(self.spectrum_bins)
        
        # 灵动效果参数
        self.bounce_factor = 1.1  # 温和弹跳因子
        self.random_factor = 0.15  # 温和随机因子
        self.energy_decay = 0.85  # 减慢衰减速度
        self.delay_factors = np.random.uniform(0.7, 1.3, self.spectrum_bins)  # 温和延迟因子变化范围

    # 定义频谱显示图的横纵坐标大小及类别并选用读取实时音频数据方式显示，设定更新间隔
    def start(self):
        # 设置中文字体和样式
        plt.rcParams['font.sans-serif'] = ['SimHei', 'DejaVu Sans']
        plt.rcParams['axes.unicode_minus'] = False
        
        fig = plt.figure(figsize=(12, 6))
        fig.patch.set_facecolor('black')
        
        ax1 = fig.add_subplot(1, 1, 1)
        ax1.set_facecolor('black')
        ax1.set_xlabel("频率", color='white', fontsize=12)
        ax1.set_ylabel("强度", color='white', fontsize=12)
        ax1.set_title("实时音频频谱可视化", color='white', fontsize=16, fontweight='bold')
        
        # 设置频谱显示范围（双镜像，中心为0.5）
        ax1.set_xlim(0, self.spectrum_bins)
        ax1.set_ylim(0, 1.0)
        
        # 添加中心线
        ax1.axhline(y=0.5, color='white', linestyle='-', alpha=0.3, linewidth=1)
        
        # 设置坐标轴颜色
        ax1.tick_params(colors='white')
        ax1.spines['bottom'].set_color('white')
        ax1.spines['top'].set_color('white')
        ax1.spines['right'].set_color('white')
        ax1.spines['left'].set_color('white')
        
        # 隐藏坐标轴刻度
        ax1.set_xticks([])
        ax1.set_yticks([])
        
        # 创建双镜像频谱柱状图（使用矩形绘制圆角效果）
        self.bars_top = []
        self.bars_bottom = []
        
        # 创建上半部分和下半部分的矩形
        for i in range(self.spectrum_bins):
            # 上半部分矩形
            rect_top = plt.Rectangle((i, 0.5), 0.8, 0, 
                                   color='#006400', alpha=0.3, 
                                   capstyle='round', joinstyle='round')
            ax1.add_patch(rect_top)
            self.bars_top.append(rect_top)
            
            # 下半部分矩形（镜像）
            rect_bottom = plt.Rectangle((i, 0.5), 0.8, 0, 
                                      color='#006400', alpha=0.3, 
                                      capstyle='round', joinstyle='round')
            ax1.add_patch(rect_bottom)
            self.bars_bottom.append(rect_bottom)
        
        # 合并所有柱状图
        self.bars = self.bars_top + self.bars_bottom
        
        # 保存ax1引用
        self.ax1 = ax1

        # 更新间隔/ms
        interval = int(1000 * self.chunk / self.rate)
        animation.TimedAnimation.__init__(self, fig, interval=interval, blit=True)

    # 初始化绘图，将频谱柱高度设为0
    def _init_draw(self):
        for bar in self.bars_top:
            bar.set_height(0)
        for bar in self.bars_bottom:
            bar.set_height(0)

    def new_frame_seq(self):
        return iter(range(self.chunk))

    def _draw_frame(self, framedata):
        self.frame_count += 1
        
        if self.static:
            # 读取静态wav文件波形
            y = np.frombuffer(self.read(self.chunk / 2 + 1), dtype=np.int16)[:-1]
        else:
            # 实时读取声频（直接使用当前块，无延迟）
            data = np.frombuffer(self.read(self.chunk, exception_on_overflow=False), dtype=np.int16)
            y = data  # 直接使用当前音频数据，不进行历史累积
        
        # 计算音频强度（安全处理）
        if len(y) > 0:
            # 安全计算RMS值
            y_array = np.array(y, dtype=np.float64)
            rms = np.sqrt(np.mean(y_array**2))
            current_amplitude = rms if not np.isnan(rms) and not np.isinf(rms) else 0
            # 平滑处理
            self.last_amplitude = self.smoothing_factor * current_amplitude + (1 - self.smoothing_factor) * self.last_amplitude
        else:
            current_amplitude = 0
            self.last_amplitude = 0
        
        # 计算频谱（使用当前音频强度而不是历史强度）
        current_audio_detected = len(y) > 0 and current_amplitude > self.audio_threshold
        
        if current_audio_detected:
            # 应用窗函数
            windowed_data = np.array(y) * np.hanning(len(y))
            
            # 计算FFT
            fft_data = np.fft.fft(windowed_data)
            magnitude = np.abs(fft_data[:len(fft_data)//2])
            
            # 转换为dB
            magnitude_db = 20 * np.log10(magnitude + 1e-10)
            
            # 归一化到0-1范围
            if magnitude_db.max() > magnitude_db.min():
                magnitude_normalized = (magnitude_db - magnitude_db.min()) / (magnitude_db.max() - magnitude_db.min())
            else:
                magnitude_normalized = np.zeros_like(magnitude_db)
            
            # 降采样到频谱柱数量
            if len(magnitude_normalized) >= self.spectrum_bins:
                step = len(magnitude_normalized) // self.spectrum_bins
                self.spectrum_data = np.array([
                    np.mean(magnitude_normalized[i*step:(i+1)*step]) 
                    for i in range(self.spectrum_bins)
                ])
            else:
                self.spectrum_data = np.interp(
                    np.linspace(0, len(magnitude_normalized)-1, self.spectrum_bins),
                    np.arange(len(magnitude_normalized)),
                    magnitude_normalized
                )
            
            # 温和随机性和弹跳效果
            # 基础随机噪声
            random_noise = np.random.normal(0, self.random_factor, self.spectrum_bins)
            # 温和的随机波动
            extra_random = np.random.uniform(-0.1, 0.1, self.spectrum_bins)
            # 温和的频率随机性
            freq_random = np.random.uniform(0.8, 1.2, self.spectrum_bins)
            
            enhanced_spectrum = self.spectrum_data + random_noise + extra_random
            enhanced_spectrum = np.clip(enhanced_spectrum, 0, 1)  # 限制在0-1范围内
            
            # 应用延迟因子和频率随机性，让不同频率有不同的响应速度
            enhanced_spectrum = enhanced_spectrum * self.delay_factors * freq_random
            
            # 平滑频谱数据（减慢升起速度）
            self.smoothed_spectrum = self.smoothing_factor * enhanced_spectrum + (1 - self.smoothing_factor) * self.smoothed_spectrum
            
            # 温和弹跳效果和随机性
            bounce_noise = np.random.uniform(0.9, 1.1, self.spectrum_bins)  # 温和的弹跳范围
            random_multiplier = np.random.uniform(0.95, 1.05, self.spectrum_bins)  # 温和的随机乘数
            self.smoothed_spectrum *= self.bounce_factor * bounce_noise * random_multiplier
            self.smoothed_spectrum = np.clip(self.smoothed_spectrum, 0, 1)
        else:
            # 减慢衰减速度（与升起速度匹配）
            self.smoothed_spectrum *= self.energy_decay  # 保留85%
            # 添加温和的随机衰减，让衰减也有轻微随机性
            random_decay = np.random.uniform(0.95, 1.0, self.spectrum_bins)
            self.smoothed_spectrum *= random_decay
            # 如果值很小，直接设为0
            self.smoothed_spectrum[self.smoothed_spectrum < 0.01] = 0
        
        # 更新双镜像频谱柱
        for i in range(self.spectrum_bins):
            height = self.smoothed_spectrum[i] * 0.5  # 缩放高度，因为要分成上下两部分
            
            # 根据高度设置绿色渐变
            if height > 0.2:
                color = '#00FF00'  # 亮绿色
                alpha = min(0.95, 0.6 + height * 2)
            elif height > 0.1:
                color = '#32CD32'  # 酸橙绿
                alpha = min(0.8, 0.4 + height * 3)
            elif height > 0.03:
                color = '#228B22'  # 森林绿
                alpha = min(0.6, 0.2 + height * 4)
            else:
                color = '#006400'  # 深绿色
                alpha = max(0.1, height * 5)
            
            # 更新上半部分（向上延伸）
            self.bars_top[i].set_height(height)
            self.bars_top[i].set_xy((i, 0.5))  # 从中心线开始向上
            self.bars_top[i].set_color(color)
            self.bars_top[i].set_alpha(alpha)
            
            # 更新下半部分（向下延伸，镜像）
            self.bars_bottom[i].set_height(height)
            self.bars_bottom[i].set_xy((i, 0.5 - height))  # 从中心线开始向下
            self.bars_bottom[i].set_color(color)
            self.bars_bottom[i].set_alpha(alpha)
        
        # 每50帧打印一次调试信息
        if self.frame_count % 50 == 0:
            max_height = np.max(self.smoothed_spectrum)
            print(f"🎵 当前音频: {current_amplitude:.1f}, 历史音频: {self.last_amplitude:.1f}, 阈值: {self.audio_threshold}, 检测: {current_audio_detected}, 最大频谱: {max_height:.3f}")


def main():
    """主函数"""
    print("🎵 实时音频波形可视化器")
    print("=" * 50)
    print("🎤 请对着麦克风说话或播放音乐...")
    print("📱 按 Ctrl+C 停止程序")
    
    try:
        ani = SubplotAnimation()
        ani.start()
        plt.show()
    except KeyboardInterrupt:
        print("\n🛑 用户停止程序")
    except Exception as e:
        print(f"❌ 程序运行出错: {e}")
        print("💡 请确保已安装必要的依赖包:")
        print("   pip install numpy matplotlib pyaudio")

if __name__ == "__main__":
    main()

