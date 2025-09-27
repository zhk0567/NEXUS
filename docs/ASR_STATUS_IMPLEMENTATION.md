# ASR状态监控实现指南

## 🎯 功能概述

为NEXUS应用实现了语音识别过程中的状态显示功能，用户在进行语音识别时可以看到"正在识别中..."的提示。

## 📡 API端点

### 1. ASR状态查询
- **URL**: `GET /api/asr/status`
- **功能**: 获取语音识别的实时状态
- **返回数据**:
```json
{
  "status": "success",
  "asr_health": "healthy",
  "metrics": {
    "total_requests": 0,
    "success_rate": 0.0,
    "consecutive_failures": 0,
    "avg_response_time": 0.0,
    "last_success": null,
    "last_failure": null
  },
  "processing": {
    "is_processing": false,
    "current_request_id": null,
    "progress": 0,
    "processing_time": null,
    "start_time": null
  },
  "last_update": "2025-09-27T07:46:04.994700"
}
```

### 2. 语音识别API
- **URL**: `POST /api/transcribe`
- **功能**: 执行语音识别
- **返回数据**:
```json
{
  "success": true,
  "transcription": "识别结果",
  "processing_time": 2.5,
  "request_id": "uuid-string"
}
```

## 🔄 状态跟踪机制

### 处理状态字段
- `is_processing`: 是否正在处理语音识别
- `current_request_id`: 当前请求的唯一ID
- `progress`: 处理进度 (0-100%)
- `processing_time`: 已处理时间（秒）
- `start_time`: 开始处理时间戳

### 进度阶段
1. **10%**: 请求接收，开始处理
2. **30%**: 音频文件保存完成
3. **50%**: 文件准备就绪
4. **70%**: 开始语音识别处理
5. **90%**: 识别完成，准备返回结果
6. **100%**: 处理完成

## 📱 Android实现示例

### 1. 状态轮询服务
```kotlin
class ASRStatusMonitor {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var statusJob: Job? = null
    
    fun startMonitoring(callback: (ASRStatus) -> Unit) {
        statusJob = coroutineScope.launch {
            while (isActive) {
                try {
                    val status = fetchASRStatus()
                    withContext(Dispatchers.Main) {
                        callback(status)
                    }
                    delay(1000) // 每秒查询一次
                } catch (e: Exception) {
                    Log.e("ASRStatusMonitor", "状态查询失败", e)
                }
            }
        }
    }
    
    fun stopMonitoring() {
        statusJob?.cancel()
    }
    
    private suspend fun fetchASRStatus(): ASRStatus {
        val response = httpClient.get("$BASE_URL/api/asr/status")
        return parseASRStatus(response.body?.string())
    }
}
```

### 2. 状态数据类
```kotlin
data class ASRStatus(
    val isProcessing: Boolean,
    val progress: Int,
    val currentRequestId: String?,
    val processingTime: Float?,
    val health: String
)

data class ProcessingInfo(
    val isProcessing: Boolean,
    val currentRequestId: String?,
    val progress: Int,
    val processingTime: Float?,
    val startTime: Long?
)
```

### 3. UI状态显示
```kotlin
@Composable
fun ASRStatusDisplay(status: ASRStatus) {
    when {
        status.isProcessing -> {
            Column {
                Text(
                    text = "🎤 正在识别中...",
                    color = Color.Blue,
                    fontWeight = FontWeight.Bold
                )
                
                // 进度条
                LinearProgressIndicator(
                    progress = status.progress / 100f,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    text = "进度: ${status.progress}%",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                
                status.processingTime?.let { time ->
                    Text(
                        text = "处理时间: ${String.format("%.1f", time)}s",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
        else -> {
            Text(
                text = "🎤 点击开始语音识别",
                color = Color.Gray
            )
        }
    }
}
```

### 4. 语音识别流程
```kotlin
class VoiceRecognitionService {
    private val statusMonitor = ASRStatusMonitor()
    
    fun startVoiceRecognition(audioFile: File) {
        // 开始状态监控
        statusMonitor.startMonitoring { status ->
            // 更新UI状态
            updateUIStatus(status)
        }
        
        // 执行语音识别
        coroutineScope.launch {
            try {
                val result = performTranscription(audioFile)
                // 处理识别结果
                handleTranscriptionResult(result)
            } finally {
                // 停止状态监控
                statusMonitor.stopMonitoring()
            }
        }
    }
    
    private suspend fun performTranscription(audioFile: File): TranscriptionResult {
        val requestBody = audioFile.asRequestBody("audio/wav".toMediaType())
        val multipartBody = MultipartBody.Part.createFormData("audio", "recording.wav", requestBody)
        
        return apiService.transcribeAudio(multipartBody)
    }
}
```

## 🎨 UI设计建议

### 1. 状态指示器
- **空闲状态**: 显示麦克风图标和"点击开始"提示
- **处理中状态**: 显示旋转的麦克风图标和进度条
- **完成状态**: 显示成功图标和识别结果

### 2. 进度显示
- 使用 `LinearProgressIndicator` 显示处理进度
- 显示百分比和已用时间
- 添加动画效果增强用户体验

### 3. 错误处理
- 网络错误时显示重试按钮
- 识别失败时显示错误信息和重试选项
- 超时处理：超过30秒自动停止并提示

## 🔧 配置参数

### 轮询间隔
- **推荐**: 1-2秒
- **最小**: 500毫秒
- **最大**: 5秒

### 超时设置
- **识别超时**: 30秒
- **状态查询超时**: 5秒
- **重试次数**: 3次

### 状态缓存
- 缓存最近的状态信息
- 避免频繁的网络请求
- 离线状态下的降级处理

## 📊 监控和调试

### 1. 日志记录
```kotlin
Log.d("ASRStatus", "状态更新: 处理中=${status.isProcessing}, 进度=${status.progress}%")
```

### 2. 调试端点
- 使用 `/api/asr/status` 端点查看实时状态
- 检查 `/api/metrics?service=asr` 查看统计信息
- 监控 `/api/health` 查看整体服务状态

### 3. 测试工具
```bash
# 测试ASR状态
curl http://192.168.64.85:5000/api/asr/status

# 测试语音识别
curl -X POST -F "audio=@test.wav" http://192.168.64.85:5000/api/transcribe
```

## 🚀 最佳实践

### 1. 性能优化
- 使用协程避免阻塞UI线程
- 合理设置轮询间隔
- 及时停止不需要的监控

### 2. 用户体验
- 提供清晰的视觉反馈
- 显示处理进度和预计时间
- 支持取消操作

### 3. 错误处理
- 优雅处理网络异常
- 提供重试机制
- 显示友好的错误信息

## 📝 注意事项

1. **网络状态**: 确保设备网络连接正常
2. **权限管理**: 确保有录音和网络权限
3. **资源清理**: 及时停止状态监控和释放资源
4. **异常处理**: 妥善处理各种异常情况
5. **性能考虑**: 避免过于频繁的状态查询

---

通过以上实现，用户在进行语音识别时可以看到实时的处理状态，大大提升了用户体验。
