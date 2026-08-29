# Android App 接入指南

## 依赖边界

宿主 App 只依赖 `local-llm-sdk`。不要直接引用 `ai.mlc.mlcllm`、TVM 类型或
模型文件名。

```gradle
dependencies {
    implementation project(":local-llm-sdk")
}
```

当前源码工程还需要生成的 `:mlc4j` 模块作为内部运行时依赖。

## 生命周期

1. App 进程内创建一个 `LocalLlmClient`。
2. 调用 `initialize()` 并读取 `capabilities()`。
3. Model Manager 下载并校验与 APK 内 `modelLib` 匹配的模型数据。
4. 调用 `load()`；同一时间只加载一个模型。
5. 调用 `stream()` 并收集事件。
6. 切后台或用户停止时调用 `cancel()`。
7. 不再使用模型时调用 `unload()`，销毁客户端时调用 `close()`。

不要为每次请求创建客户端或重复加载模型。

## 请求与事件

```kotlin
val request = LocalLlmRequest(
    model = modelId,
    messages = conversation,
    temperature = 0.7f,
    topP = 0.9f,
    seed = 42,
    maxOutputTokens = 128,
    stop = listOf("</answer>"),
)
```

正常事件顺序为：

```text
Started → Delta* → Usage? → Completed
```

取消终态为 `Cancelled`。MVP 同时只允许一个生成请求；并发调用返回
`queue_full`，宿主应排队或稍后重试。

## 模型制品

业务使用逻辑 ID，例如 `local/qwen2.5-0.5b-instruct@1`。`modelPath` 指向 App
私有目录中的已校验模型数据，`modelLib` 必须是随 APK 编译的兼容模型库 ID。

不能只替换任意 Hugging Face 权重：模型架构、量化、tokenizer、chat template、
运行库和 Android ABI 必须匹配。新架构通常需要重新编译并发布新版 App。

## 错误处理

捕获 `LocalLlmException`，依据 `code` 与 `retryable` 决定 UI：

- `model_not_loaded`：先安装并加载模型
- `model_incompatible`：模型 ID 与当前加载制品不匹配
- `queue_full`：已有生成任务
- `backend_error`：记录不含提示原文的诊断信息，可重试或降级
- `client_closed`：重新创建客户端

## 生产化清单

- 使用自有 CDN，不依赖公共模型镜像
- 权重分片、断点续传、SHA-256、签名与原子安装
- 设备能力检测、内存/温度治理和明确的 Lite/Remote 降级
- 前后台、低内存、取消、超长上下文和损坏模型测试
- 不记录用户提示、响应或本地敏感路径
- 每个模型单独审核许可证
