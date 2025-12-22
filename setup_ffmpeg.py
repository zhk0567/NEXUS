#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
自动配置ffmpeg脚本
"""
import os
import sys
if sys.platform == 'win32':
    os.environ['PYTHONIOENCODING'] = 'utf-8'

import zipfile
import shutil
import urllib.request
import subprocess
from pathlib import Path

def check_ffmpeg():
    """检查ffmpeg是否已安装"""
    try:
        result = subprocess.run(['ffmpeg', '-version'], 
                              capture_output=True, 
                              text=True,
                              timeout=5)
        if result.returncode == 0:
            print("[OK] ffmpeg is installed")
            print(f"Version: {result.stdout.split(chr(10))[0]}")
            return True
    except (FileNotFoundError, subprocess.TimeoutExpired):
        pass
    return False

def try_conda_install():
    """尝试使用conda安装ffmpeg"""
    try:
        print("Trying to install ffmpeg via conda...")
        result = subprocess.run(['conda', 'install', '-c', 'conda-forge', 'ffmpeg', '-y'], 
                              capture_output=True, 
                              text=True,
                              timeout=300)
        if result.returncode == 0:
            print("[OK] ffmpeg installed via conda")
            return True
    except (FileNotFoundError, subprocess.TimeoutExpired) as e:
        print(f"[WARN] conda install failed: {e}")
    return False

def download_ffmpeg():
    """下载ffmpeg Windows版本"""
    # 先尝试conda安装
    if try_conda_install():
        return True
    
    print("Downloading ffmpeg...")
    
    # 使用更小的构建版本（essential build）
    download_url = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl-shared.zip"
    
    ffmpeg_dir = Path("ffmpeg")
    zip_path = Path("ffmpeg.zip")
    
    max_retries = 3
    for attempt in range(max_retries):
        try:
            # 下载文件（带进度显示）
            print(f"Downloading from {download_url} (attempt {attempt + 1}/{max_retries})...")
            
            def show_progress(block_num, block_size, total_size):
                if total_size > 0:
                    percent = min(100, (block_num * block_size * 100) // total_size)
                    print(f"\rProgress: {percent}%", end='', flush=True)
            
            urllib.request.urlretrieve(download_url, zip_path, show_progress)
            print("\n[OK] Download completed")
        
        # 解压文件
        print("Extracting...")
        with zipfile.ZipFile(zip_path, 'r') as zip_ref:
            zip_ref.extractall(".")
        
        # 查找ffmpeg.exe的位置
        extracted_dir = None
        for item in Path(".").iterdir():
            if item.is_dir() and "ffmpeg" in item.name.lower():
                extracted_dir = item
                break
        
        if not extracted_dir:
            print("[ERROR] Cannot find extracted ffmpeg directory")
            return False
        
        # 查找bin目录中的ffmpeg.exe
        bin_dir = extracted_dir / "bin"
        if not bin_dir.exists():
            # 可能直接在根目录
            bin_dir = extracted_dir
        
        ffmpeg_exe = bin_dir / "ffmpeg.exe"
        if not ffmpeg_exe.exists():
            print("[ERROR] Cannot find ffmpeg.exe")
            return False
        
        # 创建项目内的ffmpeg目录
        if ffmpeg_dir.exists():
            shutil.rmtree(ffmpeg_dir)
        ffmpeg_dir.mkdir()
        
        # 复制ffmpeg.exe和相关文件
        print("Configuring ffmpeg...")
        shutil.copy2(ffmpeg_exe, ffmpeg_dir / "ffmpeg.exe")
        
        # 复制其他可能需要的文件
        for file in bin_dir.glob("*.exe"):
            if file.name in ["ffmpeg.exe", "ffprobe.exe"]:
                shutil.copy2(file, ffmpeg_dir / file.name)
        
        # 清理临时文件
        print("Cleaning up temporary files...")
        if zip_path.exists():
            zip_path.unlink()
        if extracted_dir.exists():
            shutil.rmtree(extracted_dir)
        
        print(f"[OK] ffmpeg configured at: {ffmpeg_dir.absolute()}")
        return True
        
    except Exception as e:
        print(f"\n[ERROR] Download or configuration failed: {e}")
        # 清理临时文件
        if zip_path.exists():
            zip_path.unlink()
        if attempt < max_retries - 1:
            print(f"Retrying in 5 seconds...")
            import time
            time.sleep(5)
        else:
            return False
    return False

def configure_pydub():
    """配置pydub使用本地ffmpeg"""
    ffmpeg_path = Path("ffmpeg") / "ffmpeg.exe"
    if not ffmpeg_path.exists():
        print("[WARN] Local ffmpeg not found, will use system PATH")
        return False
    
    # 创建配置脚本
    config_content = f'''# -*- coding: utf-8 -*-
"""
ffmpeg配置 - 自动生成
"""
import os
from pathlib import Path

# 设置ffmpeg路径
FFMPEG_PATH = Path(__file__).parent / "ffmpeg" / "ffmpeg.exe"
if FFMPEG_PATH.exists():
    os.environ["PATH"] = str(FFMPEG_PATH.parent) + os.pathsep + os.environ.get("PATH", "")
    # 配置pydub
    try:
        import pydub
        pydub.AudioSegment.converter = str(FFMPEG_PATH)
        pydub.AudioSegment.ffmpeg = str(FFMPEG_PATH)
        ffprobe_path = FFMPEG_PATH.parent / "ffprobe.exe"
        if ffprobe_path.exists():
            pydub.AudioSegment.ffprobe = str(ffprobe_path)
    except ImportError:
        pass  # pydub未安装时忽略
'''
    
    config_file = Path("ffmpeg_config.py")
    with open(config_file, 'w', encoding='utf-8') as f:
        f.write(config_content)
    
    print(f"[OK] Created ffmpeg config file: {config_file}")
    return True

def add_to_path():
    """将ffmpeg添加到当前会话的PATH"""
    ffmpeg_dir = Path("ffmpeg").absolute()
    if ffmpeg_dir.exists():
        current_path = os.environ.get("PATH", "")
        if str(ffmpeg_dir) not in current_path:
            os.environ["PATH"] = str(ffmpeg_dir) + os.pathsep + current_path
            print(f"[OK] Added ffmpeg to current session PATH: {ffmpeg_dir}")
            return True
    return False

def main():
    """主函数"""
    print("=" * 50)
    print("FFmpeg Auto Setup Tool")
    print("=" * 50)
    
    # 检查是否已安装
    if check_ffmpeg():
        print("\n[INFO] ffmpeg is already available, no configuration needed")
        return
    
    # 检查本地ffmpeg
    local_ffmpeg = Path("ffmpeg") / "ffmpeg.exe"
    if local_ffmpeg.exists():
        print(f"[OK] Found local ffmpeg: {local_ffmpeg}")
        add_to_path()
        configure_pydub()
        print("\n[OK] Configuration completed!")
        return
    
    # 下载并配置
    print("\n[INFO] Starting to download and configure ffmpeg...")
    if download_ffmpeg():
        # 如果通过conda安装，不需要额外配置
        local_ffmpeg = Path("ffmpeg") / "ffmpeg.exe"
        if local_ffmpeg.exists():
            add_to_path()
            configure_pydub()
        print("\n[OK] ffmpeg configuration completed!")
        if local_ffmpeg.exists():
            print("\nNote:")
            print("1. ffmpeg has been downloaded to the ffmpeg folder in project directory")
            print("2. Please import ffmpeg_config at the beginning of app.py to enable configuration")
            print("   Example: import ffmpeg_config")
    else:
        print("\n[ERROR] ffmpeg configuration failed")
        print("\nAlternative solutions:")
        print("1. Install via conda: conda install -c conda-forge ffmpeg")
        print("2. Manually download ffmpeg: https://ffmpeg.org/download.html")
        print("3. Extract to ffmpeg folder in project directory")
        print("4. Or use chocolatey: choco install ffmpeg")

if __name__ == "__main__":
    main()

