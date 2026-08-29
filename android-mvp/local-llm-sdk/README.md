# Local LLM Android SDK（MVP）

`local-llm-sdk` 是宿主 App/游戏应依赖的进程内 Kotlin API。公共包
`com.porter.local.llm` 不暴露 MLC、TVM 或模型文件格式；当前内部后端为
MLC Android GPU。

## 接入

```gradle
dependencies {
    implementation project(":local-llm-sdk")
}
```

模型管理器完成下载后，将认证模型的逻辑 ID、私有目录和随 APK 编译的
`modelLib` 交给 SDK：

```kotlin
val client = LocalLlmClient()
val capabilities = client.initialize()

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
        temperature = 0.7f,
        maxOutputTokens = 128,
    )
).collect { event ->
    when (event) {
        is LocalLlmEvent.Delta -> renderToken(event.text)
        is LocalLlmEvent.Usage -> recordPerformance(event.usage)
        is LocalLlmEvent.Completed -> finishUi(event.finishReason)
        is LocalLlmEvent.Cancelled -> showCancelled()
        is LocalLlmEvent.Started -> showGenerating()
    }
}
```

调用 `client.cancel()` 可中断当前生成；`reset()` 清除运行时会话状态，
`unload()` 释放模型，宿主销毁时调用 `close()`。

## MVP 契约

- 单模型、单并发生成；并发请求明确返回 `queue_full`。
- 流式事件顺序：`Started → Delta* → Usage? → Completed`，取消终态为
  `Cancelled`。
- 支持 system/user/assistant 文本消息、temperature、topP、seed、
  maxOutputTokens 和 stop。
- 当前正式后端是 MLC GPU；`capabilities()` 不承诺 NPU。
- 模型下载、哈希/签名和设备分档仍由 Model Manager 负责，不由推理 Client
  隐式联网处理。
- 多模态、记忆、JSON Schema、Tool Calling、云端回退和 iOS 不属于本期接口。
