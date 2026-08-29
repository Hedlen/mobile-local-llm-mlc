# Local LLM Android SDK

在 Android App 内以接近云端 Chat API 的方式运行本地大模型。当前 MVP 使用
[MLC LLM](https://github.com/mlc-ai/mlc-llm) + Android GPU，已在真实 arm64
手机完成离线推理、流式输出、取消和连续 20 轮对话验证。

> 当前是 Android MVP，不是通用生产发行版。模型运行库需要在本地编译；模型
> 权重不会提交到 Git。

## 能力

- 进程内 Kotlin API，不启动 HTTP 服务
- `system` / `user` / `assistant` 文本消息
- 流式 token、usage、prefill/decode 性能
- temperature、top-p、seed、最大输出和 stop
- 生成取消、会话重置、模型卸载
- 公共 API 不暴露 MLC/TVM 类型
- 已安装模型可在飞行模式下运行

## SDK 调用

```kotlin
val client = LocalLlmClient()
client.initialize()

client.load(
    model = "local/qwen2.5-0.5b-instruct@1",
    modelPath = installedModelDirectory.absolutePath,
    modelLib = "qwen2_q4f16_1_ec234c98ba1f1f6d014a60148428520a",
)

client.stream(
    LocalLlmRequest(
        model = "local/qwen2.5-0.5b-instruct@1",
        messages = listOf(
            LocalLlmMessage(LocalLlmRole.SYSTEM, "You are a game companion."),
            LocalLlmMessage(LocalLlmRole.USER, "Summarize the current quest."),
        ),
        maxOutputTokens = 128,
    )
).collect { event ->
    when (event) {
        is LocalLlmEvent.Delta -> render(event.text)
        is LocalLlmEvent.Usage -> record(event.usage)
        is LocalLlmEvent.Completed -> finish(event.finishReason)
        is LocalLlmEvent.Cancelled -> showCancelled()
        is LocalLlmEvent.Started -> showLoading()
    }
}
```

生成过程中调用 `client.cancel()`；页面销毁时调用 `client.close()`。

## 快速开始

### 1. 环境

- Windows 11 + WSL2 Ubuntu
- JDK 17
- Android SDK 35、NDK r27、ADB
- Python 3.12、Rust 和从锁定源码构建的 TVM/MLC
- arm64-v8a Android 真机（Android 10+ 为产品建议下限）

```powershell
git clone --recurse-submodules https://github.com/Hedlen/local-llm-android-mlc.git
cd local-llm-android
powershell -ExecutionPolicy Bypass -File scripts/check-environment.ps1
```

### 2. 生成 MLC Android 运行库

模型权重和约 126 MB 的本机原生库不进入 Git。先准备 MLC 模型、编译后的模型
库和 TVM，再在 WSL 中设置：

```bash
export MLC_PYTHON=/path/to/python3.12
export ANDROID_NDK=/path/to/android-ndk-r27
export TVM_BUILD=/path/to/tvm/build
export LOCAL_MODEL=/path/to/Qwen2.5-0.5B-Instruct-q4f16_1-MLC
export MODEL_LIB=/path/to/qwen2.5-0.5b-q4f16_1-android.tar
./scripts/package-android-wsl.sh
```

生成目录为 `android-mvp/dist/lib/mlc4j`。

### 3. 构建与安装 Demo

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17'
cd android-mvp
.\gradlew.bat :local-llm-sdk:testDebugUnitTest :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

App 首次使用从配置地址下载约 265 MB 的量化权重；下载完成后可离线推理。
生产环境必须换成自有 CDN，并增加签名、哈希和设备兼容策略。

## 项目结构

```text
android-mvp/local-llm-sdk/  面向宿主 App 的 Kotlin SDK
android-mvp/app/            Compose Demo 与模型下载 UI
android-mvp/mlc4j-source/   MLC Android adapter 源码快照
scripts/                    环境检查和可复现打包脚本
vendor/mlc-llm/             锁定的 MLC Git submodule
PRD.md                      完整产品路线
ARCHITECTURE.md             分层与依赖方向
```

更多内容见 [SDK 接入指南](docs/INTEGRATION.md)、
[真机验收报告](android-mvp/DEVICE_TEST_REPORT.md) 和
[锁定版本](VERSIONS.md)。

## 当前限制

- MVP 仅 Android、arm64-v8a、单模型、单并发文本生成
- 当前正式后端为 MLC GPU，不代表已使用各厂商 NPU
- iOS、JSON Schema、记忆、Tool Calling、多模态和云端回退尚未实现
- `capabilities()` 中的能力必须以认证设备和制品为准

## License

项目自有代码采用 Apache License 2.0。模型、MLC、TVM 及其他依赖分别遵循其
自身许可证；发布 App 前必须逐项审查。
